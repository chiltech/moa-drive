package com.moadrive.pleos.assist

import com.moadrive.pleos.navigation.NavigationSectionPhase
import com.moadrive.pleos.navigation.NavigationSpeedCamera
import com.moadrive.pleos.navigation.isNavigationOverspeed
import com.moadrive.pleos.safety.assessWeather
import com.moadrive.pleos.vehicle.freshSpeedKmh
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

/** Deterministic co-driver scenarios from vehicle, forecast and navigation data. No cloud model or recording. */
class AiGuideSession(
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val nowElapsedMs: () -> Long = { System.nanoTime() / 1_000_000L },
    private val localNow: () -> LocalDateTime = { LocalDateTime.now(ZoneId.of("Asia/Seoul")) },
) {
    private val mutableState = MutableStateFlow(AiGuideState())
    val state = mutableState.asStateFlow()
    private var context = AiGuideContext()
    private var visible = false
    private var greeted = false
    private var boardingWaitSince: Long? = null
    private var serial = 0L
    private var cameraEncounter: String? = null
    private var cameraAbsentSinceMs: Long? = null
    private var previousCameraDistance: Int? = null
    private val cameraEvents = mutableSetOf<String>()
    private val incidentIds = linkedSetOf<String>()

    fun setVisible(value: Boolean) {
        if (visible == value) return
        visible = value
        if (!value) {
            dismiss()
            boardingWaitSince = null
        } else tick()
    }

    fun updateContext(value: AiGuideContext) {
        context = value
        // A temporary vehicle disconnection or Activity resize is not a new boarding event.
        if (value.telemetry.active && value.telemetry.ignitionOn == false) {
            greeted = false
            boardingWaitSince = null
            clearCameraEncounter()
            incidentIds.clear()
            dismiss()
        }
        tick()
    }

    fun tick() {
        val now = nowMs()
        if (state.value.message?.let { now >= it.expiresAtMs } == true) dismiss()
        // The routine card provides this trip's warm greeting; hazards still retain their own priority.
        if (context.routineSuggestion != null && context.telemetry.active && context.telemetry.ignitionOn != false) {
            greeted = true
            boardingWaitSince = null
            if (state.value.message?.kind in setOf(AiGuideKind.BOARDING, AiGuideKind.BRIEFING)) dismiss()
        }
        if (!visible || !context.options.enabled || !context.telemetry.active || context.telemetry.ignitionOn == false) {
            dismiss()
            boardingWaitSince = null
            return
        }
        // A long parked briefing must not remain on screen after the car starts moving.
        if (!parked() && state.value.message?.kind in setOf(AiGuideKind.BOARDING, AiGuideKind.BRIEFING)) dismiss()
        if (!context.options.voiceEnabled && state.value.message?.speech != null) {
            mutableState.value = state.value.copy(message = state.value.message?.copy(speech = null))
        }
        if (context.guiding && state.value.message?.speech != null) {
            mutableState.value = state.value.copy(message = state.value.message?.copy(speech = null))
        }
        evaluateIncident(now)
        evaluateCamera(now)
        if (!greeted && parked()) {
            val since = boardingWaitSince ?: now.also { boardingWaitSince = it }
            val weatherReady = context.forecast?.isFresh(now) == true
            if (!context.options.departureWeather || weatherReady || now - since >= 8_000L) {
                if (showBriefing(AiGuideKind.BOARDING, now)) {
                    greeted = true
                    boardingWaitSince = null
                }
            }
        } else boardingWaitSince = null
    }

    fun requestBriefing() {
        if (!visible || !context.options.enabled || !parked() || context.routineSuggestion != null) return
        if (showBriefing(AiGuideKind.BRIEFING, nowMs())) {
            greeted = true
            boardingWaitSince = null
        }
    }

    fun dismiss() { mutableState.value = state.value.copy(message = null) }

    private fun parked() = context.telemetry.active && context.telemetry.ignitionOn != false &&
        context.telemetry.gear == "P" && context.telemetry.speedKmh == 0 && context.browsingAllowed && !context.guiding

    private fun showBriefing(kind: AiGuideKind, now: Long): Boolean {
        val greeting = when (localNow().hour) {
            in 5..10 -> "좋은 아침이에요."
            in 11..17 -> "반가워요. 오후 길도 모아가 함께할게요."
            in 18..22 -> "오늘도 수고 많으셨어요."
            else -> "늦은 시간에도 모아가 함께할게요."
        }
        val assessment = context.forecast
            ?.takeIf { context.options.departureWeather && it.isFresh(now) }
            ?.let { assessWeather(it, now) }
        val weather = when {
            !context.options.departureWeather -> "안전벨트를 확인하고, 출발 전 주변을 살펴 주세요."
            assessment == null -> "현재 위치의 날씨 정보를 아직 확인하지 못했어요. 출발 전 날씨와 노면을 살펴 주세요."
            else -> {
                val notice = assessment.notices.firstOrNull()
                when {
                    notice != null -> "현재 위치 주변 예보에 ${notice.title} 신호가 있어요. ${notice.detail}"
                    !assessment.complete -> "일부 예보를 확인할 수 없어요. 출발 전 날씨와 노면을 살펴 주세요."
                    else -> "현재 위치 주변 예보에 뚜렷한 기상 주의 신호는 없어요. 안전벨트를 확인하고 차간 거리를 유지해 주세요."
                }
            }
        }
        val briefing = when {
            !context.options.departureWeather -> AiBriefingContent(greeting,
                "출발 전 기본 확인", "안전벨트를 확인하고, 출발 전 주변을 살펴 주세요.", AiBriefingStatus.DISABLED)
            assessment == null -> AiBriefingContent(greeting,
                "날씨 확인이 필요해요", "현재 위치의 날씨 정보를 아직 확인하지 못했어요. 출발 전 날씨와 노면을 살펴 주세요.",
                AiBriefingStatus.UNKNOWN)
            else -> {
                val notice = assessment.notices.firstOrNull()
                when {
                    notice != null -> AiBriefingContent(greeting, notice.title, notice.detail, AiBriefingStatus.CAUTION)
                    !assessment.complete -> AiBriefingContent(greeting,
                        "일부 예보를 확인할 수 없어요", "출발 전 날씨와 노면을 살펴 주세요.", AiBriefingStatus.UNKNOWN)
                    else -> AiBriefingContent(greeting,
                        "뚜렷한 기상 주의 신호 없음", "안전벨트를 확인하고 차간 거리를 유지해 주세요.", AiBriefingStatus.ROUTINE)
                }
            }
        }
        val body = "$greeting 오늘도 안전운전하세요. $weather"
        val message = newMessage(kind, if (kind == AiGuideKind.BOARDING) "오늘도 안전운전하세요" else "오늘의 안전 브리핑",
            body, if (context.options.voiceEnabled && !context.guiding) body else null, now, 35_000L).copy(briefing = briefing)
        if (!show(message)) return false
        mutableState.value = state.value.copy(lastBriefing = message)
        return true
    }

    private fun evaluateIncident(now: Long) {
        val nearby = context.incident
        val incident = nearby?.incident
        val valid = context.options.incidentWarnings && nearby != null && incident != null &&
            nearby.distanceMeters.isFinite() && nearby.distanceMeters in 0.0..10_000.0 &&
            incident.severe && incident.id.isNotBlank() && incident.occurredAtMs <= now && now < incident.expiresAtMs
        if (!valid) {
            if (state.value.message?.kind == AiGuideKind.INCIDENT) dismiss()
            return
        }
        incident!!; nearby!!
        if (incident.id in incidentIds) return
        val distance = String.format(Locale.KOREAN, "%.1f", nearby.distanceMeters / 1000.0)
        val prefix = if (incident.demo) "사고 안내 체험 · " else ""
        val message = newMessage(AiGuideKind.INCIDENT, "주변 도로를 살펴 주세요",
            "${prefix}반경 ${distance}km 안에 사고 알림이 있어요. 앞차와 거리를 두고 급한 조작을 피해 주세요.",
            null, now, 12_000L)
        if (show(message)) {
            incidentIds.add(incident.id)
            while (incidentIds.size > 64) incidentIds.remove(incidentIds.first())
        }
    }

    private fun evaluateCamera(now: Long) {
        val camera = context.camera?.takeIf { allowsCamera(it) }
        if (camera == null) {
            if (state.value.message?.kind in CAMERA_KINDS) dismiss()
            if (cameraAbsentSinceMs == null) cameraAbsentSinceMs = now
            return
        }
        val identity = if (camera.sectionPhase != null) "section:${camera.limitKmh}:${camera.isMobile}"
            else "${camera.label}:${camera.limitKmh}:${camera.isMobile}"
        // SDK warnings expose no stable camera ID. A clear gap or a new distant warning starts a new encounter.
        val distanceReset = previousCameraDistance?.let { previous ->
            previous <= 100 && (camera.distanceMeters ?: 0) >= previous + 300
        } == true
        if (cameraEncounter != identity || cameraAbsentSinceMs?.let { now - it >= 12_000L } == true || distanceReset) {
            cameraEvents.clear()
            cameraEncounter = identity
        }
        cameraAbsentSinceMs = null
        previousCameraDistance = camera.distanceMeters
        val speed = context.telemetry.freshSpeedKmh(nowElapsedMs())
        val over = isNavigationOverspeed(camera, speed)
        if (!over && state.value.message?.kind == AiGuideKind.OVERSPEED) dismiss()
        val kind = when {
            over -> AiGuideKind.OVERSPEED
            camera.sectionPhase != null -> AiGuideKind.SECTION
            else -> AiGuideKind.CAMERA
        }
        val event = "${kind}:${camera.sectionPhase}"
        if (event in cameraEvents) return
        val limit = camera.limitKmh
        val (title, body) = when {
            over && camera.sectionPhase == NavigationSectionPhase.ACTIVE && (camera.sectionAverageKmh ?: 0) > limit ->
                "구간 평균속도를 살펴 주세요" to "제한속도는 ${limit}km/h예요. 구간 평균속도가 기준을 넘고 있어요. 속도를 서서히 줄여 주세요."
            over -> "속도를 서서히 줄여 주세요" to "이 구간의 제한속도는 ${limit}km/h예요. 전방과 주변 차량을 살피며 속도를 조절해 주세요."
            camera.sectionPhase == NavigationSectionPhase.ACTIVE -> "구간단속 안내 중이에요" to "제한속도 ${limit}km/h와 구간 평균속도를 함께 확인해 주세요."
            camera.sectionPhase == NavigationSectionPhase.EXIT -> "구간단속 종료 지점이 가까워요" to "종료 지점을 지난 뒤에도 도로의 제한속도 ${limit}km/h를 지켜 주세요."
            camera.sectionPhase == NavigationSectionPhase.APPROACH -> "구간단속 시작 지점이 가까워요" to "전방 ${camera.distanceMeters}m부터 구간단속 안내가 있어요. 제한속도는 ${limit}km/h예요."
            else -> "전방 단속 안내를 확인해 주세요" to "전방 ${camera.distanceMeters}m에 ${camera.label} 안내가 있어요. 제한속도는 ${limit}km/h예요."
        }
        if (show(newMessage(kind, title, body, null, now, 9_000L))) cameraEvents.add(event)
    }

    private fun allowsCamera(camera: NavigationSpeedCamera): Boolean {
        val options = context.options
        if (!context.guiding || !options.speedWarnings || camera.limitKmh !in 1..200) return false
        if (camera.isMobile && !options.includeMobileCameras) return false
        if (camera.sectionPhase == NavigationSectionPhase.ACTIVE) return true
        val limit = if (options.warningDistanceMeters == 500) 500 else 1_000
        return camera.distanceMeters?.let { it in 0..limit } == true
    }

    private fun clearCameraEncounter() {
        cameraEncounter = null
        cameraAbsentSinceMs = null
        previousCameraDistance = null
        cameraEvents.clear()
    }

    private fun newMessage(kind: AiGuideKind, title: String, body: String, speech: String?, now: Long, duration: Long) =
        AiGuideMessage("moa-guide-${++serial}", kind, title, body, speech, now + duration)

    private fun show(message: AiGuideMessage): Boolean {
        val current = state.value.message
        if (current != null && priority(current.kind) > priority(message.kind)) return false
        mutableState.value = state.value.copy(message = message)
        return true
    }

    private fun priority(kind: AiGuideKind) = when (kind) {
        AiGuideKind.INCIDENT -> 100
        AiGuideKind.OVERSPEED -> 90
        AiGuideKind.CAMERA, AiGuideKind.SECTION -> 50
        AiGuideKind.BOARDING, AiGuideKind.BRIEFING -> 10
    }

    companion object {
        private val CAMERA_KINDS = setOf(AiGuideKind.CAMERA, AiGuideKind.OVERSPEED, AiGuideKind.SECTION)
    }
}
