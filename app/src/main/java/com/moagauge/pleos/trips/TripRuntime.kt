package com.moagauge.pleos.trips

import android.content.Context
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.moagauge.pleos.vehicle.VehicleReading
import com.moagauge.pleos.vehicle.VehicleTelemetry
import com.moagauge.pleos.vehicle.VehicleTelemetryRepository
import com.moagauge.pleos.passport.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.ZoneId

/** One process-wide owner; screen navigation never ends the vehicle recording session. */
class TripRuntime private constructor(context: Context) {
    private val app = context.applicationContext
    private val preferences = app.getSharedPreferences("trip_recording", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val store = TripStore(app)
    private val engine = TripRecorderEngine(zoneProvider = { ZoneId.systemDefault() })
    private sealed interface Write {
        data class Trip(val record: TripRecord) : Write
        data class Visit(val record: TripRecord, val visit: PassportVisit) : Write
        data class Name(val cellId: String, val name: String) : Write
    }
    private val writes = Channel<Write>(Channel.UNLIMITED)
    private val passportStore = PassportStore(app)
    private val visitDetector = PassportVisitDetector()
    private val mutableVisits = MutableStateFlow<List<PassportVisit>>(emptyList())
    private val mutablePassportMessage = MutableStateFlow("방문 기록을 불러오는 중")
    private val mutableLocationActive = MutableStateFlow(false)
    val passportVisits = mutableVisits.asStateFlow()
    val passportMessage = mutablePassportMessage.asStateFlow()
    val passportLocationActive = mutableLocationActive.asStateFlow()
    private val mutableTelemetry = MutableStateFlow(VehicleTelemetry())
    private val mutableHistory = MutableStateFlow<List<TripRecord>>(emptyList())
    private val mutableCurrent = MutableStateFlow<TripRecord?>(null)
    private val mutableEnabled = MutableStateFlow(preferences.getBoolean("enabled", true))
    private val mutableMessage = MutableStateFlow("주행기록을 불러오는 중")
    val telemetry = mutableTelemetry.asStateFlow()
    val history = mutableHistory.asStateFlow()
    val current = mutableCurrent.asStateFlow()
    val enabled = mutableEnabled.asStateFlow()
    val message = mutableMessage.asStateFlow()

    private var repository: VehicleTelemetryRepository? = null
    private var telemetryJob: Job? = null
    private var ticker: Job? = null
    private var serviceRunning = false
    private var uiVisible = false
    private var initialized = false
    private var storageError = false
    private var startError = false
    private var lastReadingMs = 0L
    private var lastCheckpointMs = 0L
    private var lastPersistedPhase: Pair<String, TripStatus>? = null
    private var passportError = false
    private var locationAuthorized = false
    private var locationIssue: String? = null
    private var lastGoodFixMs = 0L
    private var movingForPassport = false
    private val locationSource = PassportLocationSource(app, ::onPassportFix) {
        locationIssue = it
        updateMessage()
    }

    private val initialization = scope.launch {
        try {
            mutableHistory.value = withContext(Dispatchers.IO) { store.recoverInterrupted() }
        } catch (_: Exception) {
            storageError = true
        }
        try {
            mutableVisits.value = withContext(Dispatchers.IO) { passportStore.loadAll() }
        } catch (_: Exception) { passportError = true }
        initialized = true
        updateMessage()
    }

    init {
        scope.launch {
            initialization.join()
            for (write in writes) {
                try {
                    when (write) {
                        is Write.Trip -> persistTrip(write.record)
                        is Write.Visit -> {
                            // Ensure the linked trip checkpoint exists before saving its first stamp.
                            persistTrip(write.record)
                            withContext(Dispatchers.IO) { passportStore.upsert(write.visit) }
                            mutableVisits.value = withContext(Dispatchers.IO) { passportStore.loadAll() }
                        }
                        is Write.Name -> {
                            withContext(Dispatchers.IO) { passportStore.updateCellName(write.cellId, write.name) }
                            mutableVisits.value = withContext(Dispatchers.IO) { passportStore.loadAll() }
                        }
                    }
                } catch (_: Exception) {
                    if (write is Write.Trip) storageError = true else passportError = true
                    syncLocationCollection()
                    updateMessage()
                }
            }
        }
    }

    fun setUiVisible(visible: Boolean, permissionsChanged: Boolean = false) {
        uiVisible = visible
        if (visible) ensureRunning(permissionsChanged)
        else if (!enabled.value || !hasSpeedPermission(app)) stopService()
    }

    fun setEnabled(value: Boolean) {
        if (enabled.value == value) return
        preferences.edit().putBoolean("enabled", value).apply()
        mutableEnabled.value = value
        if (!value) {
            finishInterrupted()
            if (!uiVisible) stopService()
        } else ensureRunning()
        syncLocationCollection()
        updateMessage()
    }

    private suspend fun persistTrip(record: TripRecord) {
        try { withContext(Dispatchers.IO) { store.upsert(record) } }
        catch (error: Exception) { storageError = true; throw error }
        mutableHistory.value = (mutableHistory.value.filterNot { it.id == record.id } + record)
            .sortedByDescending { it.startedAtMs }
    }

    fun canPromotePassportLocation() = uiVisible && enabled.value && hasFineLocationPermission()
    fun hasFineLocationPermission() = ContextCompat.checkSelfPermission(app,
        Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    fun enablePassportLocation() { if (uiVisible) ensureRunning() }
    fun setPassportLocationAuthorized(authorized: Boolean) {
        locationAuthorized = authorized
        mutableLocationActive.value = authorized && hasFineLocationPermission()
        syncLocationCollection()
        updateMessage()
    }
    fun namePassportCell(cellId: String, name: String) {
        val clean = name.trim().take(120)
        if (clean.isNotEmpty() && mutableVisits.value.any { it.cell.id == cellId }) writes.trySend(Write.Name(cellId, clean))
    }

    private fun syncLocationCollection() {
        val shouldListen = serviceRunning && initialized && enabled.value && locationAuthorized &&
            hasFineLocationPermission() && current.value?.status == TripStatus.RECORDING && !passportError && !storageError
        if (shouldListen) locationSource.start() else {
            locationSource.stop()
            visitDetector.reset()
            lastGoodFixMs = 0L
        }
    }

    private fun onPassportFix(fix: PassportFix) {
        val now = SystemClock.elapsedRealtime()
        val record = current.value
        if (!enabled.value || !locationAuthorized || !initialized || passportError || storageError) return
        if (fix.accuracyMeters.isFinite() && fix.accuracyMeters in 0.0..50.0 && now - fix.monotonicMs in 0..10_000) {
            lastGoodFixMs = now
        }
        val moving = record?.status == TripStatus.RECORDING && movingForPassport && now - lastReadingMs <= 3_000
        visitDetector.accept(record?.id, moving, fix, now)?.let { visit ->
            if (record != null) writes.trySend(Write.Visit(record, visit))
        }
        updateMessage()
    }

    /** Called only by visible UI or an allowed system broadcast. */
    fun ensureRunning(refreshPermissions: Boolean = false) {
        if (!uiVisible && (!enabled.value || !hasSpeedPermission(app))) return
        try {
            val intent = Intent(app, TripRecordingService::class.java)
            if (refreshPermissions) intent.action = TripRecordingService.ACTION_REFRESH
            ContextCompat.startForegroundService(app, intent)
            startError = false
        } catch (_: RuntimeException) {
            startError = true
            updateMessage()
        }
    }

    fun serviceStarted(refreshPermissions: Boolean) {
        serviceRunning = true
        startError = false
        scope.launch {
            initialization.join()
            if (!serviceRunning) return@launch
            if (repository == null) {
                val source = VehicleTelemetryRepository(app, ::onReading)
                repository = source
                telemetryJob = scope.launch { source.state.collect { mutableTelemetry.value = it } }
                source.start()
                lastReadingMs = SystemClock.elapsedRealtime()
                ticker = scope.launch {
                    while (isActive && serviceRunning) {
                        delay(1_000)
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastReadingMs > 3_000) {
                            accept(TripSample(System.currentTimeMillis(), now, null, null, null, null))
                        }
                        updateMessage()
                    }
                }
            } else if (refreshPermissions) repository?.restart()
            syncLocationCollection()
            updateMessage()
        }
    }

    fun serviceStartFailed() {
        startError = true
        updateMessage()
    }

    fun serviceStopped() {
        serviceRunning = false
        ticker?.cancel(); ticker = null
        telemetryJob?.cancel(); telemetryJob = null
        repository?.stop(); repository = null
        locationAuthorized = false
        mutableLocationActive.value = false
        locationSource.stop()
        visitDetector.reset()
        mutableTelemetry.value = VehicleTelemetry()
        finishInterrupted()
        updateMessage()
    }

    private fun stopService() { app.stopService(Intent(app, TripRecordingService::class.java)) }

    private fun onReading(reading: VehicleReading) {
        lastReadingMs = SystemClock.elapsedRealtime()
        movingForPassport = isPassportMotion(reading.speedMps, reading.gear)
        if (!movingForPassport) visitDetector.reset()
        accept(TripSample(reading.wallTimeMs, reading.monotonicMs, reading.speedMps, reading.gear,
            reading.batteryWh, reading.batteryPercent, reading.ignitionOn))
    }

    private fun accept(sample: TripSample) {
        if (!serviceRunning || !initialized || storageError || !enabled.value) return
        val finished = engine.accept(sample)
        finished.forEach { writes.trySend(Write.Trip(it)) }
        val active = engine.current
        mutableCurrent.value = active
        if (active != null) {
            val phase = active.id to active.status
            if (phase != lastPersistedPhase || sample.monotonicMs - lastCheckpointMs >= 5_000) {
                writes.trySend(Write.Trip(active))
                lastCheckpointMs = sample.monotonicMs
                lastPersistedPhase = phase
            }
        } else lastPersistedPhase = null
        syncLocationCollection()
        updateMessage()
    }

    private fun finishInterrupted() {
        engine.interrupt()?.let { writes.trySend(Write.Trip(it)) }
        mutableCurrent.value = null
        lastPersistedPhase = null
        syncLocationCollection()
    }

    private fun updateMessage() {
        mutablePassportMessage.value = when {
            storageError -> "주행 기록을 저장할 수 없어 방문 기록도 대기 중이에요"
            passportError -> "방문 기록을 저장하지 못했어요. 저장 공간을 확인해 주세요"
            !initialized -> "방문 기록을 불러오는 중"
            !enabled.value -> "자동 주행 기록을 켜면 방문 구역도 함께 모아요"
            !hasFineLocationPermission() -> "정확한 위치를 허용하면 방문 구역을 기록해요"
            !locationAuthorized -> "앱을 열어 위치 기록을 시작해 주세요"
            locationIssue != null -> locationIssue!!
            current.value?.status != TripStatus.RECORDING -> "출발하면 GPS로 방문 구역을 기록해요"
            SystemClock.elapsedRealtime() - lastGoodFixMs > 10_000 -> "정확한 GPS 신호를 기다리고 있어요"
            else -> "주행기록과 함께 방문 구역을 기록하고 있어요"
        }
        mutableMessage.value = when {
            storageError -> "기록을 저장하지 못했어요. 차량의 저장 공간을 확인해 주세요."
            !initialized -> "주행기록을 불러오는 중"
            !enabled.value -> "자동 기록이 꺼져 있어요"
            !hasSpeedPermission(app) -> "자동 기록을 위해 차량 정보 권한을 허용해 주세요"
            startError -> "자동 기록을 시작하지 못했어요. 앱을 다시 열어 주세요"
            !serviceRunning -> "자동 기록 연결 대기"
            current.value?.status == TripStatus.PARKING -> "주차 확인 중 · 30초 정차 후 자동 저장"
            current.value != null -> if (current.value!!.hasDataGap) "주행 기록 중 · 일부 차량 신호 누락" else "주행 중 · 자동 기록 중"
            telemetry.value.speedKmh == null || telemetry.value.gear == null -> "차량 신호를 확인하고 있어요"
            else -> "자동 기록 켜짐 · 출발을 기다리고 있어요"
        }
    }

    companion object {
        @Volatile private var instance: TripRuntime? = null
        fun get(context: Context): TripRuntime = instance ?: synchronized(this) {
            instance ?: TripRuntime(context).also { instance = it }
        }
        fun hasSpeedPermission(context: Context) = ContextCompat.checkSelfPermission(context,
            "android.car.permission.CAR_SPEED") == PackageManager.PERMISSION_GRANTED
        fun shouldStartOnBoot(context: Context) =
            context.getSharedPreferences("trip_recording", Context.MODE_PRIVATE).getBoolean("enabled", true) && hasSpeedPermission(context)
    }
}
