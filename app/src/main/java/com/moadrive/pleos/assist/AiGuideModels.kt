package com.moadrive.pleos.assist

import com.moadrive.pleos.navigation.NavigationSpeedCamera
import com.moadrive.pleos.safety.AreaForecast
import com.moadrive.pleos.vehicle.VehicleTelemetry

enum class AiGuideKind { BOARDING, BRIEFING, CAMERA, OVERSPEED, SECTION, INCIDENT }

enum class AiBriefingStatus { ROUTINE, CAUTION, UNKNOWN, DISABLED }

/** Visual briefing sections from the same observations as speech, never parsed from spoken text. */
data class AiBriefingContent(
    val greeting: String,
    val weatherTitle: String,
    val weatherDetail: String,
    val status: AiBriefingStatus,
    val checks: List<String> = listOf("안전벨트 확인", "출발 전 주변 확인", "앞차와 거리 유지"),
)

data class AiGuideMessage(
    val id: String,
    val kind: AiGuideKind,
    val title: String,
    val body: String,
    /** Null for navigation events: the navigation SDK already owns their spoken guidance. */
    val speech: String? = null,
    val expiresAtMs: Long,
    val briefing: AiBriefingContent? = null,
)

data class AiGuideState(
    val message: AiGuideMessage? = null,
    val lastBriefing: AiGuideMessage? = null,
)

data class AiGuideOptions(
    val enabled: Boolean = true,
    val voiceEnabled: Boolean = true,
    val departureWeather: Boolean = true,
    val speedWarnings: Boolean = true,
    val incidentWarnings: Boolean = true,
    val warningDistanceMeters: Int = 1_000,
    val includeMobileCameras: Boolean = true,
)

data class AiGuideContext(
    val telemetry: VehicleTelemetry = VehicleTelemetry(),
    val browsingAllowed: Boolean = false,
    /** Current-location forecast only; its owner also checks the forecast's distance from current GPS. */
    val forecast: AreaForecast? = null,
    val guiding: Boolean = false,
    val camera: NavigationSpeedCamera? = null,
    val incident: NearbyIncident? = null,
    val options: AiGuideOptions = AiGuideOptions(),
    val routineSuggestion: RoutineSuggestion? = null,
)
