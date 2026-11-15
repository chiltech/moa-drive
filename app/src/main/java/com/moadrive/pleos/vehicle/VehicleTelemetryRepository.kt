package com.moadrive.pleos.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.listener.EvBatteryEfficiencyListener
import ai.pleos.playground.vehicle.listener.EvBatteryStateOfChargeListener
import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.moadrive.pleos.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Owns read-only vehicle subscriptions for one foreground session at a time. */
class VehicleTelemetryRepository(context: Context, private val onReading: (VehicleReading) -> Unit = {}) {
    private val context = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val session = TelemetrySession()
    private val mutableState = MutableStateFlow(session.value)
    val state: StateFlow<VehicleTelemetry> = mutableState.asStateFlow()

    private var carConnection: CarConnection? = null
    private var pleosConnection: PleosConnection? = null
    private val sessionEfficiency = SessionEfficiencyCalculator()
    private val sampleReadMutex = Mutex()
    private var directEfficiency: Double? = null

    fun start() = onMain {
        if (!session.value.active) startSession()
    }

    fun stop() = onMain { stopSession() }

    fun restart() = onMain {
        stopSession()
        startSession()
    }

    private fun startSession() {
        sessionEfficiency.reset()
        directEfficiency = null
        val token = session.start()
        mutableState.value = session.value
        carConnection = CarConnection(token).also { it.start() }
        pleosConnection = PleosConnection(token).also { it.start() }
    }

    private fun stopSession() {
        // Invalidate first: unregister/release can themselves deliver callbacks.
        session.stop()
        sessionEfficiency.reset()
        directEfficiency = null
        mutableState.value = session.value
        carConnection?.stop()
        carConnection = null
        pleosConnection?.stop()
        pleosConnection = null
    }

    private fun onMain(action: () -> Unit) {
        if (Looper.myLooper() == mainHandler.looper) action() else mainHandler.post { action() }
    }

    private fun callback(token: Long, action: () -> Unit) {
        // Always enqueue, including synchronous SDK completions during initialization.
        mainHandler.post { if (session.accepts(token)) action() }
    }

    private fun update(token: Long, transform: (VehicleTelemetry) -> VehicleTelemetry) {
        if (session.update(token, transform)) mutableState.value = session.value
    }

    private fun updateDirectEfficiency(token: Long, value: Float?) {
        if (!session.accepts(token)) return
        directEfficiency = VehicleValues.efficiency(value)
        publishEfficiency(token)
    }

    private fun sampleEfficiency(token: Long, speed: Float?, energy: Float?, timestampNanos: Long) {
        if (!session.accepts(token)) return
        sessionEfficiency.sample(speed, energy, timestampNanos)
        publishEfficiency(token)
        val current = session.value
        onReading(VehicleReading(
            wallTimeMs = System.currentTimeMillis(), monotonicMs = timestampNanos / 1_000_000L,
            speedMps = speed?.toDouble(), batteryWh = energy?.toDouble(), gear = current.gear,
            batteryPercent = current.batteryPercent, ignitionOn = current.ignitionOn,
        ))
    }

    private fun resetCalculatedEfficiency(token: Long) {
        if (!session.accepts(token)) return
        sessionEfficiency.reset()
        publishEfficiency(token)
    }

    private fun publishEfficiency(token: Long) {
        update(token) {
            it.copy(
                efficiencyKmPerKwh = directEfficiency ?: sessionEfficiency.value,
                // The fallback estimates distance from speed during this foreground session.
                efficiencyLabel = if (directEfficiency != null) "운행 전비" else "추정 전비",
            )
        }
    }

    private fun hasPermission(permission: String) =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private inner class CarConnection(private val token: Long) {
        private var car: Car? = null
        private var propertyManager: CarPropertyManager? = null
        private var propertyCallback: CarPropertyManager.CarPropertyEventCallback? = null
        private val propertyTimestamps = mutableMapOf<Int, Long>()
        private var pollingScope: CoroutineScope? = null
        private var activePoll: Any? = null
        private var pollTimeout: Runnable? = null

        fun start() {
            try {
                car = Car.createCar(context, mainHandler, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { connectedCar, ready ->
                    callback(token) {
                        if (ready) attach(connectedCar) else detach()
                    }
                }
            } catch (_: Exception) {
                detach()
            }
        }

        private fun attach(connectedCar: Car) {
            val manager = try {
                connectedCar.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
            } catch (_: Exception) {
                null
            }
            if (manager == null) {
                detach()
                return
            }
            if (propertyManager === manager && propertyCallback != null) return
            detach()
            propertyManager = manager
            // A fresh callback for each binding rejects events queued before disconnect.
            val listener = object : CarPropertyManager.CarPropertyEventCallback {
                override fun onChangeEvent(value: CarPropertyValue<*>) {
                    callback(token) {
                        if (propertyCallback === this && propertyManager === manager) apply(value)
                    }
                }

                override fun onErrorEvent(propertyId: Int, areaId: Int) {
                    callback(token) {
                        if (propertyCallback === this && propertyManager === manager && areaId == GLOBAL_AREA) {
                            clear(propertyId)
                        }
                    }
                }

                override fun onErrorEvent(propertyId: Int, areaId: Int, errorCode: Int) =
                    onErrorEvent(propertyId, areaId)
            }
            propertyCallback = listener
            observe(manager, listener, VehiclePropertyIds.PERF_VEHICLE_SPEED, 10f)
            observe(manager, listener, VehiclePropertyIds.GEAR_SELECTION, CarPropertyManager.SENSOR_RATE_ONCHANGE)
            observe(manager, listener, VehiclePropertyIds.RANGE_REMAINING, CarPropertyManager.SENSOR_RATE_ONCHANGE)
            // Optional: the current Pleos emulator does not advertise this signal.
            observe(manager, listener, VehiclePropertyIds.IGNITION_STATE, CarPropertyManager.SENSOR_RATE_ONCHANGE)
            startEfficiencyPolling(manager, listener)
        }

        private fun startEfficiencyPolling(
            manager: CarPropertyManager,
            listener: CarPropertyManager.CarPropertyEventCallback,
        ) {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            pollingScope = scope
            scope.launch {
                while (isActive && session.accepts(token) && propertyManager === manager && propertyCallback === listener) {
                    val request = Any()
                    activePoll = request
                    val startedNanos = SystemClock.elapsedRealtimeNanos()
                    var timedOut = false
                    val timeout = Runnable {
                        if (isCurrentPoll(manager, listener, request)) {
                            timedOut = true
                            sampleEfficiency(token, null, null, SystemClock.elapsedRealtimeNanos())
                        }
                    }
                    pollTimeout = timeout
                    mainHandler.postDelayed(timeout, REQUEST_TIMEOUT_MS)
                    val reading = withContext(Dispatchers.IO) {
                        // A cancelled Binder call may finish late; never overlap it with a new pair.
                        sampleReadMutex.withLock {
                            ensureActive()
                            val speed = readAvailableFloat(manager, VehiclePropertyIds.PERF_VEHICLE_SPEED)
                            ensureActive()
                            val energy = readAvailableFloat(manager, VehiclePropertyIds.EV_BATTERY_LEVEL)
                            EfficiencyReading(speed, energy, SystemClock.elapsedRealtimeNanos())
                        }
                    }
                    // withContext resumes on main; all calculator changes share the callback boundary.
                    if (isCurrentPoll(manager, listener, request)) {
                        mainHandler.removeCallbacks(timeout)
                        pollTimeout = null
                        activePoll = null
                        if (!timedOut) {
                            if (reading.timestampNanos - startedNanos <= REQUEST_TIMEOUT_MS * 1_000_000L) {
                                sampleEfficiency(token, reading.speed, reading.energy, reading.timestampNanos)
                            } else {
                                sampleEfficiency(token, null, null, reading.timestampNanos)
                            }
                        }
                    }
                    val elapsedMillis = (SystemClock.elapsedRealtimeNanos() - startedNanos) / 1_000_000L
                    delay((EFFICIENCY_POLL_INTERVAL_MS - elapsedMillis).coerceAtLeast(1L))
                }
            }
        }

        private fun isCurrentPoll(
            manager: CarPropertyManager,
            listener: CarPropertyManager.CarPropertyEventCallback,
            request: Any,
        ) = session.accepts(token) && propertyManager === manager && propertyCallback === listener && activePoll === request

        private fun readAvailableFloat(manager: CarPropertyManager, propertyId: Int): Float? = try {
            if (!canRead(propertyId)) {
                null
            } else {
                val property = manager.getProperty<Float>(propertyId, GLOBAL_AREA)
                if (property?.status == CarPropertyValue.STATUS_AVAILABLE) {
                    property.value?.takeIf { it.isFinite() }
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }

        private fun observe(
            manager: CarPropertyManager,
            listener: CarPropertyManager.CarPropertyEventCallback,
            propertyId: Int,
            rate: Float,
        ) {
            if (!canRead(propertyId)) {
                clear(propertyId)
                return
            }
            try {
                val initial = manager.getProperty<Any>(propertyId, GLOBAL_AREA)
                if (initial == null) clear(propertyId) else apply(initial)
            } catch (_: Exception) {
                clear(propertyId)
            }
            try {
                if (!manager.registerCallback(listener, propertyId, rate)) clear(propertyId)
            } catch (_: Exception) {
                clear(propertyId)
            }
        }

        private fun canRead(propertyId: Int): Boolean = when (propertyId) {
            VehiclePropertyIds.PERF_VEHICLE_SPEED -> hasPermission(Car.PERMISSION_SPEED)
            VehiclePropertyIds.GEAR_SELECTION -> hasPermission(Car.PERMISSION_POWERTRAIN)
            VehiclePropertyIds.IGNITION_STATE -> hasPermission(Car.PERMISSION_POWERTRAIN)
            VehiclePropertyIds.RANGE_REMAINING, VehiclePropertyIds.EV_BATTERY_LEVEL -> hasPermission(Car.PERMISSION_ENERGY)
            else -> false
        }

        private fun apply(value: CarPropertyValue<*>) {
            if (value.areaId != GLOBAL_AREA) return
            val propertyId = value.propertyId
            if (!canRead(propertyId)) {
                clear(propertyId)
                return
            }
            // Registration may enqueue a cached event older than the initial synchronous read.
            if (value.timestamp > 0L) {
                if (value.timestamp < (propertyTimestamps[propertyId] ?: 0L)) return
                propertyTimestamps[propertyId] = value.timestamp
            }
            if (value.status != CarPropertyValue.STATUS_AVAILABLE) {
                clear(propertyId)
                return
            }
            when (propertyId) {
                VehiclePropertyIds.PERF_VEHICLE_SPEED -> update(token) {
                    val speed = VehicleValues.speedKmh(value.value as? Float)
                    it.copy(speedKmh = speed, speedObservedElapsedMs = if (speed != null) SystemClock.elapsedRealtime() else null)
                }
                VehiclePropertyIds.GEAR_SELECTION -> update(token) {
                    it.copy(gear = VehicleValues.gear(value.value as? Int))
                }
                VehiclePropertyIds.RANGE_REMAINING -> update(token) {
                    it.copy(rangeKm = VehicleValues.rangeKm(value.value as? Float))
                }
                VehiclePropertyIds.IGNITION_STATE -> update(token) {
                    it.copy(ignitionOn = when (value.value as? Int) { 4, 5 -> true; 1, 2 -> false; else -> null })
                }
            }
        }

        private fun clear(propertyId: Int) {
            when (propertyId) {
                VehiclePropertyIds.PERF_VEHICLE_SPEED -> update(token) { it.copy(speedKmh = null, speedObservedElapsedMs = null) }
                VehiclePropertyIds.GEAR_SELECTION -> update(token) { it.copy(gear = null) }
                VehiclePropertyIds.RANGE_REMAINING -> update(token) { it.copy(rangeKm = null) }
                VehiclePropertyIds.IGNITION_STATE -> update(token) { it.copy(ignitionOn = null) }
            }
        }

        private fun detach() {
            val manager = propertyManager
            val listener = propertyCallback
            propertyManager = null
            propertyCallback = null
            activePoll = null
            pollTimeout?.let(mainHandler::removeCallbacks)
            pollTimeout = null
            pollingScope?.cancel()
            pollingScope = null
            propertyTimestamps.clear()
            if (manager != null && listener != null) {
                try {
                    manager.unregisterCallback(listener)
                } catch (_: Exception) {
                    // The disconnected car service may already have removed its subscriptions.
                }
            }
            update(token) { it.copy(speedKmh = null, speedObservedElapsedMs = null, gear = null, rangeKm = null, ignitionOn = null) }
            resetCalculatedEfficiency(token)
        }

        fun stop() {
            detach()
            val oldCar = car
            car = null
            try {
                oldCar?.disconnect()
            } catch (_: Exception) {
                // Session invalidation still prevents any late event from becoming visible.
            }
        }
    }

    private inner class PleosConnection(private val token: Long) {
        private var vehicle: Vehicle? = null
        private var battery: PleosSignal<Int>? = null
        private var efficiency: PleosSignal<Float>? = null
        private val batteryListener = object : EvBatteryStateOfChargeListener {
            override fun onEvBatteryStateOfChargeUpdated(value: Int?) =
                callback(token) { battery?.onListener(value) }

            override fun onFailed(e: Exception) = callback(token) { battery?.onListener(null, e) }
        }
        private val efficiencyListener = object : EvBatteryEfficiencyListener {
            override fun onEvBatteryEfficiencyUpdated(value: Float?) =
                callback(token) { efficiency?.onListener(value) }

            override fun onFailed(e: Exception) = callback(token) { efficiency?.onListener(null, e) }
        }
        private val refresh = object : Runnable {
            override fun run() {
                if (!session.accepts(token)) return
                battery?.refresh()
                efficiency?.refresh()
                mainHandler.postDelayed(this, REFRESH_INTERVAL_MS)
            }
        }

        fun start() {
            try {
                val client = Vehicle(context)
                vehicle = client
                client.initialize()
                val api = client.getEvBattery()
                battery = PleosSignal(
                    token = token,
                    checkCapability = { complete, failed -> api.checkEvBatteryStateOfChargeCapabilities(complete, failed) },
                    read = { complete, failed -> api.getEvBatteryStateOfCharge(complete, failed) },
                    register = { api.registerEvBatteryStateOfCharge(batteryListener) },
                    unregister = { api.unregisterEvBatteryStateOfCharge(batteryListener) },
                    publish = { value -> update(token) { it.copy(batteryPercent = VehicleValues.batteryPercent(value)) } },
                )
                efficiency = PleosSignal(
                    token = token,
                    diagnosticName = "currentEfficiency",
                    checkCapability = { complete, failed -> api.checkCurrentEvEfficiencyCapabilities(complete, failed) },
                    read = { complete, failed -> api.getCurrentEvEfficiency(complete, failed) },
                    register = { api.registerCurrentEvEfficiency(efficiencyListener) },
                    unregister = { api.unregisterCurrentEvEfficiency(efficiencyListener) },
                    // The SDK reports the current drive's average directly in km/kWh.
                    publish = { value -> updateDirectEfficiency(token, value) },
                )
                // initialize() only binds the SDK; actual read callbacks establish data availability.
                refresh.run()
            } catch (error: Exception) {
                stop()
                update(token) { it.copy(batteryPercent = null) }
                logInitializationFailure("currentEfficiency", error)
            }
        }

        fun stop() {
            mainHandler.removeCallbacks(refresh)
            battery?.stop()
            efficiency?.stop()
            battery = null
            efficiency = null
            updateDirectEfficiency(token, null)
            val oldVehicle = vehicle
            vehicle = null
            try {
                oldVehicle?.release()
            } catch (_: Exception) {
                // The listeners were already invalidated and unregistered individually.
            }
        }

        private fun logInitializationFailure(field: String, error: Exception) {
            if (!BuildConfig.DEBUG) return
            val message = error.message.orEmpty().replace('\n', ' ').replace('\r', ' ').take(160)
            Log.d("MoaVehicle", "$field initialization failed ${error.javaClass.simpleName}: $message")
        }
    }

    /**
     * The SDK has no public disconnect callback. A bounded read detects a silent service loss.
     * Each field owns its request, timeout and listener revision, so one failure cannot erase
     * another field and an old read cannot replace a more recent subscription event.
     */
    private inner class PleosSignal<T>(
        private val token: Long,
        private val diagnosticName: String? = null,
        private val checkCapability: ((CapabilityConfig) -> Unit, (Exception) -> Unit) -> Unit,
        private val read: ((T?) -> Unit, (Exception) -> Unit) -> Unit,
        private val register: () -> Unit,
        private val unregister: () -> Unit,
        private val publish: (T?) -> Unit,
    ) {
        private var readable: Boolean? = null
        private var capabilityRequest: PendingRequest? = null
        private var readRequest: PendingRequest? = null
        private var listenerRevision = 0L
        private var registrationAttempted = false
        private var stopped = false
        private var lastDiagnostic: String? = null

        fun refresh() {
            if (!isActive()) return
            when (readable) {
                null -> inspectCapability()
                true -> readCurrent()
                false -> Unit
            }
        }

        private fun isActive() = !stopped && session.accepts(token)

        private fun inspectCapability() {
            if (capabilityRequest != null) return
            val request = PendingRequest(listenerRevision)
            capabilityRequest = request
            request.timeout = Runnable { finishCapability(request, null, timedOut = true) }.also {
                mainHandler.postDelayed(it, REQUEST_TIMEOUT_MS)
            }
            try {
                checkCapability(
                    { config -> callback(token) { finishCapability(request, config) } },
                    { error -> callback(token) { finishCapability(request, null, error) } },
                )
            } catch (error: Exception) {
                finishCapability(request, null, error)
            }
        }

        private fun finishCapability(
            request: PendingRequest,
            config: CapabilityConfig?,
            error: Exception? = null,
            timedOut: Boolean = false,
        ) {
            if (!isActive() || capabilityRequest !== request) return
            request.cancel()
            capabilityRequest = null
            readable = config?.let { it.accessType == AccessType.READ || it.accessType == AccessType.READ_WRITE }
            when {
                timedOut -> diagnose("capability timeout")
                error != null -> diagnose("capability failed", error)
                config != null -> diagnose("capability ${config.accessType.name}")
            }
            if (readable != true) {
                publish(null)
                return
            }
            // Remember even a failed attempt so cleanup unregisters that exact listener.
            if (!registrationAttempted) {
                registrationAttempted = true
                try {
                    register()
                } catch (error: Exception) {
                    diagnose("subscription failed", error)
                    publish(null)
                }
            }
            readCurrent()
        }

        private fun readCurrent() {
            if (readRequest != null || !isActive()) return
            val request = PendingRequest(listenerRevision)
            readRequest = request
            request.timeout = Runnable { finishRead(request, null, timedOut = true) }.also {
                mainHandler.postDelayed(it, REQUEST_TIMEOUT_MS)
            }
            try {
                read(
                    { value -> callback(token) { finishRead(request, value) } },
                    { error -> callback(token) { finishRead(request, null, error) } },
                )
            } catch (error: Exception) {
                finishRead(request, null, error)
            }
        }

        private fun finishRead(
            request: PendingRequest,
            value: T?,
            error: Exception? = null,
            timedOut: Boolean = false,
        ) {
            if (!isActive() || readRequest !== request) return
            request.cancel()
            readRequest = null
            if (request.listenerRevision != listenerRevision) return
            when {
                timedOut -> diagnose("read timeout")
                error != null -> diagnose("read failed", error)
                value == null -> diagnose("read unavailable")
                else -> lastDiagnostic = null
            }
            publish(value)
        }

        fun onListener(value: T?, error: Exception? = null) {
            if (!isActive() || readable != true) return
            listenerRevision++
            when {
                error != null -> diagnose("listener failed", error)
                value == null -> diagnose("listener unavailable")
                else -> lastDiagnostic = null
            }
            publish(value)
        }

        private fun diagnose(event: String, error: Exception? = null) {
            if (!BuildConfig.DEBUG || diagnosticName == null) return
            val detail = error?.let {
                " ${it.javaClass.simpleName}: ${it.message.orEmpty().replace('\n', ' ').replace('\r', ' ').take(160)}"
            }.orEmpty()
            val message = "$diagnosticName $event$detail"
            if (message == lastDiagnostic) return
            lastDiagnostic = message
            Log.d("MoaVehicle", message)
        }

        fun stop() {
            stopped = true
            capabilityRequest?.cancel()
            readRequest?.cancel()
            capabilityRequest = null
            readRequest = null
            if (registrationAttempted) {
                registrationAttempted = false
                try {
                    unregister()
                } catch (_: Exception) {
                    // release() follows even if the service is already gone.
                }
            }
        }
    }

    private data class EfficiencyReading(val speed: Float?, val energy: Float?, val timestampNanos: Long)

    private inner class PendingRequest(val listenerRevision: Long) {
        var timeout: Runnable? = null

        fun cancel() {
            timeout?.let(mainHandler::removeCallbacks)
            timeout = null
        }
    }

    private companion object {
        const val GLOBAL_AREA = 0
        const val REFRESH_INTERVAL_MS = 5_000L
        const val EFFICIENCY_POLL_INTERVAL_MS = 1_000L
        const val REQUEST_TIMEOUT_MS = 3_000L
    }
}
