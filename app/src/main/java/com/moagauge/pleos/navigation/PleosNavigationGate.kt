package com.moagauge.pleos.navigation

import android.car.Car
import android.car.CarAppFocusManager
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** AAOS boundary. The caller must release on backgrounding and stop guidance on ownership loss. */
class PleosNavigationGate(
    context: Context,
    private val onOwnershipLost: () -> Unit,
) : AutoCloseable {
    private val main = Handler(Looper.getMainLooper())
    private val appContext = context.applicationContext
    private val mutablePolicy = MutableStateFlow(DrivingPolicy())
    val policy: StateFlow<DrivingPolicy> = mutablePolicy.asStateFlow()

    @Volatile private var closed = false
    private var car: Car? = null
    private var focus: CarAppFocusManager? = null
    private var ux: CarUxRestrictionsManager? = null
    private var request: FocusRequest? = null

    init {
        onMain {
            if (!closed) {
                try {
                    car = Car.createCar(appContext, main, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { service, ready ->
                        onMain {
                            if (closed) {
                                runCatching { service.disconnect() }
                            } else if (ready) {
                                attach(service)
                            } else {
                                disconnectManagers(notify = true)
                            }
                        }
                    }
                } catch (_: Exception) {
                    disconnectManagers(notify = true)
                }
            }
        }
    }

    /** Success requires this request's ownership, never merely another app's active focus. */
    suspend fun acquire(): Boolean = withContext(Dispatchers.Main.immediate) {
        if (closed || !mutablePolicy.value.connected) return@withContext false
        val manager = focus ?: return@withContext false
        // A second start does not share an earlier pending request or ownership callback.
        releaseOnMain()
        val candidate = FocusRequest(manager)
        request = candidate
        var acquired = false
        try {
            val result = manager.requestAppFocus(CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION, candidate)
            if (result != CarAppFocusManager.APP_FOCUS_REQUEST_SUCCEEDED) return@withContext false
            if (manager.isOwningFocus(candidate, CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION)) {
                candidate.result.complete(true)
            }
            acquired = withTimeoutOrNull(FOCUS_TIMEOUT_MS) { candidate.result.await() } == true &&
                !closed && request === candidate &&
                manager.isOwningFocus(candidate, CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION)
            candidate.owned = acquired
            acquired
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        } finally {
            if (!acquired) {
                if (request === candidate) request = null
                candidate.abandon()
            }
        }
    }

    fun release() = onMain { releaseOnMain() }

    override fun close() {
        closed = true
        onMain {
            disconnectManagers(notify = false)
            runCatching { car?.disconnect() }
            car = null
        }
    }

    private inner class FocusRequest(val manager: CarAppFocusManager) :
        CarAppFocusManager.OnAppFocusOwnershipCallback {
        val result = CompletableDeferred<Boolean>()
        var owned = false

        override fun onAppFocusOwnershipGranted(appType: Int) = onMain {
            if (appType == CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION) {
                if (closed || request !== this || focus !== manager) {
                    abandon() // A cancelled or timed-out request must not retain a late grant.
                } else {
                    result.complete(runCatching { manager.isOwningFocus(this, appType) }.getOrDefault(false))
                }
            }
        }

        override fun onAppFocusOwnershipLost(appType: Int) = onMain {
            if (appType == CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION && request === this) {
                request = null
                result.complete(false)
                val notify = owned
                owned = false
                if (notify && !closed) onOwnershipLost()
            }
        }

        fun abandon() {
            owned = false
            result.complete(false)
            runCatching { manager.abandonAppFocus(this, CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION) }
        }
    }

    private fun attach(service: Car) {
        disconnectManagers(notify = true)
        try {
            val newFocus = service.getCarManager(Car.APP_FOCUS_SERVICE) as? CarAppFocusManager
                ?: return
            val newUx = service.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager
                ?: return
            focus = newFocus
            ux = newUx
            newUx.registerListener { restrictions ->
                onMain {
                    if (!closed && ux === newUx) updatePolicy(restrictions)
                }
            }
            // Register first, then fetch: do not miss a change between the initial read and registration.
            updatePolicy(newUx.currentCarUxRestrictions)
        } catch (_: Exception) {
            disconnectManagers(notify = true)
        }
    }

    private fun updatePolicy(restrictions: CarUxRestrictions?) {
        mutablePolicy.value = DrivingPolicy.fromRestrictions(
            restrictions?.activeRestrictions,
            restrictions?.maxCumulativeContentItems ?: 0,
        )
    }

    private fun releaseOnMain() {
        val previous = request
        request = null
        previous?.abandon()
    }

    private fun disconnectManagers(notify: Boolean) {
        val wasOwned = request?.owned == true
        releaseOnMain()
        runCatching { ux?.unregisterListener() }
        ux = null
        focus = null
        mutablePolicy.value = DrivingPolicy(maxItems = 0)
        if (notify && wasOwned && !closed) onOwnershipLost()
    }

    private fun onMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else main.post(action)
    }

    private companion object {
        const val FOCUS_TIMEOUT_MS = 2_000L
    }
}
