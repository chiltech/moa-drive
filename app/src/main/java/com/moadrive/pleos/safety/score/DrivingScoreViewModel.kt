package com.moadrive.pleos.safety.score

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.moadrive.pleos.navigation.NavigationSectionPhase
import com.moadrive.pleos.navigation.NavigationSpeedCamera
import com.moadrive.pleos.trips.TripRecord
import com.moadrive.pleos.vehicle.VehicleTelemetry
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DrivingScoreState(
    val loading: Boolean = false,
    val recordingEnabled: Boolean = true,
    val records: List<DrivingScoreRecord> = emptyList(),
    val current: DrivingScoreRecord? = null,
    val storageError: String? = null,
) {
    val eligibility: DrivingScoreEligibilityResult get() = DrivingScoreEligibility.evaluate(records)
}

/** Observes only while the app is visible. It never infers behavior from old trip totals. */
class DrivingScoreViewModel(application: Application) : AndroidViewModel(application) {
    private val store = DrivingScoreStore(File(application.filesDir, "safety/driving-scores.bin"))
    private val mutableState = MutableStateFlow(DrivingScoreState(loading = true))
    val state = mutableState.asStateFlow()
    private var saved = StoredDrivingScores()
    private var storageReady = false
    private var engine: DrivingScoreEngine? = null
    private var engineTripId: String? = null
    private var latest: Input? = null
    private var lastSavedElapsedMs = 0L
    private var lastEnqueued: StoredDrivingScores? = null
    private var loadInFlight = false
    private val writes = Channel<StoredDrivingScores>(Channel.CONFLATED)

    private data class Input(
        val telemetry: VehicleTelemetry,
        val currentTrip: TripRecord?,
        val tripHistory: List<TripRecord>,
        val appVisible: Boolean,
        val camera: NavigationSpeedCamera?,
        val cameraObservedElapsedMs: Long?,
        val nowElapsedMs: Long,
        val nowMs: Long,
    )

    init {
        reloadStorage()
        viewModelScope.launch {
            for (snapshot in writes) {
                try {
                    withContext(Dispatchers.IO) { store.save(snapshot) }
                    mutableState.value = mutableState.value.copy(storageError = null)
                } catch (cancelled: CancellationException) { throw cancelled
                } catch (_: Exception) {
                    mutableState.value = mutableState.value.copy(storageError = "기록을 저장하지 못했어요. 저장 공간을 확인한 뒤 다시 시도해 주세요.")
                }
            }
        }
    }

    fun observe(
        telemetry: VehicleTelemetry,
        currentTrip: TripRecord?,
        tripHistory: List<TripRecord>,
        appVisible: Boolean,
        camera: NavigationSpeedCamera? = null,
        cameraObservedElapsedMs: Long? = null,
        nowElapsedMs: Long = SystemClock.elapsedRealtime(),
        nowMs: Long = System.currentTimeMillis(),
    ) {
        val input = Input(telemetry, currentTrip, tripHistory, appVisible, camera, cameraObservedElapsedMs, nowElapsedMs, nowMs)
        latest = input
        if (!storageReady) return
        val byId = saved.records.associateBy { it.tripId }.toMutableMap()
        var finalized = false
        input.tripHistory.filterNot { it.isActive }.forEach { trip ->
            val draft = byId[trip.id]?.takeIf { it.endedAtMs == null } ?: return@forEach
            val pending = if (engineTripId == trip.id) engine else DrivingScoreEngine(draft)
            pending?.snapshot(trip)?.let { byId[trip.id] = it; finalized = true }
        }
        val trip = currentTrip?.takeIf { it.isActive && it.id !in saved.deletedTripIds }
        if (engineTripId != trip?.id) {
            engine?.breakContinuity()
            engineTripId = trip?.id
            engine = trip?.let { DrivingScoreEngine(byId[it.id]) }
        }
        if (trip != null) {
            if (saved.recordingEnabled && appVisible && telemetry.active) {
                val limit = camera?.takeIf {
                    it.limitKmh in 1..200 && (it.sectionPhase == NavigationSectionPhase.ACTIVE ||
                        it.distanceMeters?.let { distance -> distance in 0..1_000 } == true)
                }?.limitKmh
                engine?.observe(trip, telemetry.speedKmh, telemetry.speedObservedElapsedMs,
                    nowElapsedMs, limit, cameraObservedElapsedMs, nowMs)
            } else engine?.breakContinuity()
            engine?.snapshot(trip)?.let { byId[trip.id] = it }
        }
        saved = saved.copy(records = byId.values.sortedByDescending { it.startedAtMs }.take(DrivingScoreStore.MAX_RECORDS))
        publish(trip?.id)
        if (finalized || !appVisible || nowElapsedMs - lastSavedElapsedMs >= 15_000L) persist(nowElapsedMs)
    }

    fun setRecordingEnabled(enabled: Boolean) {
        if (!storageReady || enabled == saved.recordingEnabled) return
        engine?.breakContinuity()
        saved = saved.copy(recordingEnabled = enabled)
        publish(engineTripId)
        persist()
    }

    /** Called directly from lifecycle events; recomposition may never run on disposal. */
    fun pauseObservation() {
        latest = latest?.copy(appVisible = false)
        engine?.breakContinuity()
        persist()
    }

    /** Tombstones stop a delayed trip-history update from bringing a deleted score back. */
    fun deleteRecord(tripId: String) {
        if (!storageReady || saved.records.none { it.tripId == tripId }) return
        saved = saved.copy(
            records = saved.records.filterNot { it.tripId == tripId },
            deletedTripIds = (saved.deletedTripIds + tripId).toList().takeLast(DrivingScoreStore.MAX_RECORDS).toSet(),
        )
        if (engineTripId == tripId) { engine?.breakContinuity(); engine = null; engineTripId = null }
        publish(engineTripId)
        persist()
    }

    fun retryStorage() {
        if (storageReady) persist() else reloadStorage()
    }

    private fun reloadStorage() {
        if (storageReady || loadInFlight) return
        loadInFlight = true
        mutableState.value = mutableState.value.copy(loading = true)
        viewModelScope.launch {
            try {
                saved = withContext(Dispatchers.IO) { store.load() }
                storageReady = true
                publish(null)
                latest?.let { input ->
                    observe(input.telemetry, input.currentTrip, input.tripHistory, input.appVisible,
                        input.camera, input.cameraObservedElapsedMs, SystemClock.elapsedRealtime(), System.currentTimeMillis())
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                mutableState.value = DrivingScoreState(recordingEnabled = false,
                    storageError = "저장된 점수를 읽지 못했어요. 기존 파일을 유지하고 있어요. 다시 불러와 주세요.")
            } finally { loadInFlight = false }
        }
    }

    private fun publish(currentId: String?) {
        mutableState.value = DrivingScoreState(
            recordingEnabled = saved.recordingEnabled,
            records = saved.records.filterNot { it.tripId == currentId },
            current = saved.records.firstOrNull { it.tripId == currentId },
            storageError = mutableState.value.storageError,
        )
    }

    private fun persist(nowElapsedMs: Long = SystemClock.elapsedRealtime()) {
        if (!storageReady) return
        lastSavedElapsedMs = nowElapsedMs
        if (saved == lastEnqueued && mutableState.value.storageError == null) return
        lastEnqueued = saved
        writes.trySend(saved)
    }
}
