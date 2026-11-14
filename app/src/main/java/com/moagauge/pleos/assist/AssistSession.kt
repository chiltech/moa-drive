package com.moagauge.pleos.assist

import com.moagauge.pleos.navigation.GeoPoint
import com.moagauge.pleos.vehicle.VehicleTelemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.DayOfWeek
import java.time.temporal.ChronoUnit
import kotlin.math.*

data class AssistState(
    val suggestion: RoutineSuggestion? = null,
    val nearbyIncident: NearbyIncident? = null,
    val lastIncident: NearbyIncident? = null,
    val message: String? = null,
    val incidentDemoArmed: Boolean = false,
    val demoDistanceMeters: Double? = null,
    val suggestionId: String? = null,
)

/** Foreground demo orchestration. No personal observation or real accident feed is collected. */
class AssistSession(
    private val scope: CoroutineScope,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val localNow: () -> LocalDateTime = { LocalDateTime.now(ZoneId.of("Asia/Seoul")) },
) {
    private val mutableState = MutableStateFlow(AssistState())
    val state = mutableState.asStateFlow()
    private val routineEngine = RoutineEngine()
    private val geofence = IncidentGeofence()
    private val shownRoutines = linkedSetOf<String>()
    private var visible = false
    private var allowed = false
    private var telemetry = VehicleTelemetry()
    private var point: GeoPoint? = null
    private var lastRoutineEvaluationMinute: LocalDateTime? = null
    private var routineSerial = 0L
    private var routinesEnabled = true
    private var incidentsEnabled = true
    private var incident: RoadIncident? = null
    private var virtual = false
    private var playback: Job? = null
    private var alertExpiry: Job? = null
    private var routineExpiry: Job? = null

    fun setVisible(value: Boolean) {
        if (visible == value) return
        visible = value
        if (!value) {
            resetDemo()
            lastRoutineEvaluationMinute = null
        }
    }

    fun updateContext(vehicle: VehicleTelemetry, location: GeoPoint?, browsingAllowed: Boolean) {
        telemetry = vehicle; point = location; allowed = browsingAllowed
        if (!visible) return
        val parked = vehicle.active && vehicle.ignitionOn != false && vehicle.gear == "P" && vehicle.speedKmh == 0 && allowed
        if (!parked || !routinesEnabled) {
            lastRoutineEvaluationMinute = null
            dismissRoutine()
        } else {
            val clock = localNow()
            val minute = clock.truncatedTo(ChronoUnit.MINUTES)
            if (lastRoutineEvaluationMinute != minute) {
                lastRoutineEvaluationMinute = minute
                routineEngine.suggest(DemoRoutines.observations(clock.toLocalDate()), clock)?.let { suggestion ->
                    val key = "${clock.toLocalDate()}/${suggestion.scenario}"
                    if (key !in shownRoutines) showRoutine(suggestion)
                }
            }
        }
        if (virtual) {
            if (!allowed) resetDemo()
            return
        }
        val event = incident ?: return
        if (nowMs() >= event.expiresAtMs) {
            incident = null
            mutableState.value = state.value.copy(incidentDemoArmed = false, nearbyIncident = null,
                message = "예시 사고의 유효 시간이 지났어요. 주차 후 다시 체험할 수 있어요.")
            return
        }
        if (location == null) {
            mutableState.value = state.value.copy(nearbyIncident = null,
                message = "정확한 현재 위치를 기다리고 있어요. 위치가 없으면 사고 거리를 판단하지 않아요.")
            return
        }
        mutableState.value = state.value.copy(demoDistanceMeters = distanceMeters(location, event.point))
        val moving = vehicle.active && vehicle.gear in setOf("D", "R") && (vehicle.speedKmh ?: 0) > 0
        geofence.evaluate(location, listOf(event), nowMs(), moving)?.let(::announce)
    }

    fun simulateRoutine(scenario: RoutineScenario) {
        if (!canInteract()) return
        if (!routinesEnabled) {
            mutableState.value = state.value.copy(message = "루틴 안내가 꺼져 있어요. 설정에서 켠 뒤 체험해 주세요.")
            return
        }
        // A manual scenario preview remains available on weekends; automatic suggestions are weekdays only.
        var exampleDate = localNow().toLocalDate()
        while (exampleDate.dayOfWeek in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)) exampleDate = exampleDate.minusDays(1)
        val exampleClock = exampleDate.atTime(scenario.hour, 0)
        routineEngine.suggest(DemoRoutines.observations(exampleDate), exampleClock)?.let(::showRoutine)
    }

    private fun showRoutine(suggestion: RoutineSuggestion) {
        routineExpiry?.cancel()
        shownRoutines.add("${localNow().toLocalDate()}/${suggestion.scenario}")
        while (shownRoutines.size > 32) shownRoutines.remove(shownRoutines.first())
        mutableState.value = state.value.copy(suggestion = suggestion, suggestionId = "moa-routine-${++routineSerial}")
        routineExpiry = scope.launch { delay(60_000); dismissRoutine() }
    }

    fun dismissRoutine() {
        routineExpiry?.cancel(); routineExpiry = null
        mutableState.value = state.value.copy(suggestion = null, suggestionId = null)
    }

    fun dismissIncident() {
        alertExpiry?.cancel(); alertExpiry = null
        mutableState.value = state.value.copy(nearbyIncident = null)
    }

    fun armIncident() {
        if (!canInteract()) return
        if (!incidentsEnabled) { incidentDisabledMessage(); return }
        val origin = point
        if (origin == null) {
            mutableState.value = state.value.copy(message = "정확한 현재 위치가 필요해요. 위치 권한과 GPS를 확인해 주세요.")
            return
        }
        resetDemo()
        val now = nowMs()
        incident = RoadIncident("DEMO-LOCAL-$now", "주변 도로 다중 추돌 예시", offsetNorth(origin, 3_000.0), now, now + 900_000)
        mutableState.value = state.value.copy(incidentDemoArmed = true, demoDistanceMeters = 3_000.0,
            message = "현재 위치에서 약 3km 떨어진 예시 사고를 준비했어요. 주행을 시작하면 알려드려요.")
    }

    fun playIncidentDemo() {
        if (!canInteract()) return
        if (!incidentsEnabled) { incidentDisabledMessage(); return }
        resetDemo()
        virtual = true
        val origin = GeoPoint(37.5665, 126.978)
        val now = nowMs()
        val example = RoadIncident("DEMO-VIRTUAL-$now", "가상 도로 다중 추돌 예시", origin, now, now + 900_000)
        mutableState.value = state.value.copy(demoDistanceMeters = 12_000.0,
            message = "가상 운행 · 12km 밖에서는 알리지 않아요. 잠시 후 8km 안으로 진입해요.")
        geofence.evaluate(offsetNorth(origin, 12_000.0), listOf(example), now, true)
        playback = scope.launch {
            delay(3_000)
            if (!visible || !allowed) return@launch
            mutableState.value = state.value.copy(demoDistanceMeters = 8_000.0)
            geofence.evaluate(offsetNorth(origin, 8_000.0), listOf(example), nowMs(), true)?.let(::announce)
            delay(12_000)
            virtual = false
        }
    }

    private fun announce(nearby: NearbyIncident) {
        dismissRoutine()
        alertExpiry?.cancel()
        mutableState.value = state.value.copy(nearbyIncident = nearby, lastIncident = nearby,
            message = "10km 안의 예시 사고를 한 번 안내했어요. 같은 사고는 반복해서 알리지 않아요.")
        alertExpiry = scope.launch {
            delay(12_000)
            mutableState.value = state.value.copy(nearbyIncident = null)
        }
    }

    fun resetDemo() {
        playback?.cancel(); playback = null
        alertExpiry?.cancel(); alertExpiry = null
        dismissRoutine()
        incident = null; virtual = false; geofence.reset()
        mutableState.value = AssistState()
    }

    fun setOptions(routines: Boolean, incidents: Boolean) {
        routinesEnabled = routines; incidentsEnabled = incidents
        if (!routines) {
            lastRoutineEvaluationMinute = null
            dismissRoutine()
        }
        if (!incidents) {
            playback?.cancel(); alertExpiry?.cancel()
            playback = null; alertExpiry = null; incident = null; virtual = false
            geofence.reset()
            mutableState.value = state.value.copy(nearbyIncident = null, lastIncident = null,
                incidentDemoArmed = false, demoDistanceMeters = null)
        }
    }

    private fun incidentDisabledMessage() {
        mutableState.value = state.value.copy(message = "사고 안내가 꺼져 있어요. 설정에서 켠 뒤 체험해 주세요.")
    }

    private fun canInteract() = visible && allowed && telemetry.active && telemetry.ignitionOn != false && telemetry.gear == "P" && telemetry.speedKmh == 0
}

/** Great-circle destination, bearing north; supports coordinates near a pole too. */
private fun offsetNorth(point: GeoPoint, meters: Double): GeoPoint {
    val delta = meters / 6_371_000.0
    val lat = Math.toRadians(point.latitude)
    val lon = Math.toRadians(point.longitude)
    val nextLat = asin((sin(lat) * cos(delta) + cos(lat) * sin(delta)).coerceIn(-1.0, 1.0))
    val nextLon = lon + atan2(0.0, cos(delta) - sin(lat) * sin(nextLat))
    return GeoPoint(Math.toDegrees(nextLat), ((Math.toDegrees(nextLon) + 540) % 360) - 180)
}
