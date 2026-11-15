package com.moadrive.pleos.navigation

import java.util.Locale

enum class NavigationTurn { STRAIGHT, LEFT, RIGHT, LEFT_FORK, RIGHT_FORK, U_TURN, ROUNDABOUT, ARRIVAL, MERGE, OTHER }
data class NavigationManeuver(val turn: NavigationTurn, val instruction: String, val roadName: String, val distanceMeters: Int?, val clockDirection: Int? = null)
data class NavigationTraffic(val roadName: String, val averageSpeedKmh: Int?, val condition: String)
enum class NavigationSectionPhase { APPROACH, ACTIVE, EXIT }
data class NavigationSpeedCamera(val limitKmh: Int, val distanceMeters: Int?, val label: String, val sectionAverageKmh: Int? = null,
    val isMobile: Boolean = false, val sectionPhase: NavigationSectionPhase? = null)
data class NavigationRestArea(val name: String, val distanceMeters: Int, val amenities: List<String>)

/** Keep unsupported provider instructions neutral rather than turning them into a false straight arrow. */
fun maneuverPresentation(providerCode: String): Pair<NavigationTurn, String> {
    val code = providerCode.removePrefix("KNRGCode_")
    val clock = code.substringAfterLast('_').toIntOrNull()?.takeIf { it in 1..12 }
    if (clock != null) return when {
        code.startsWith("RoundaboutDirection_") -> NavigationTurn.ROUNDABOUT to "회전교차로 ${clock}시 방향"
        code.startsWith("RotaryDirection_") -> NavigationTurn.ROUNDABOUT to "로터리 ${clock}시 방향"
        code.startsWith("Direction_") -> NavigationTurn.OTHER to "${clock}시 방향"
        else -> NavigationTurn.OTHER to "경로 안내"
    }
    return when (code) {
        "Start" -> NavigationTurn.STRAIGHT to "출발"
        "Goal" -> NavigationTurn.ARRIVAL to "목적지 도착"
        "Via" -> NavigationTurn.ARRIVAL to "경유지 도착"
        "Straight" -> NavigationTurn.STRAIGHT to "직진"
        "LeftTurn" -> NavigationTurn.LEFT to "좌회전"
        "UnprotectedLeftTurn" -> NavigationTurn.LEFT to "비보호 좌회전"
        "RightTurn" -> NavigationTurn.RIGHT to "우회전"
        "UTurn" -> NavigationTurn.U_TURN to "유턴"
        "LeftStraight" -> NavigationTurn.LEFT_FORK to "왼쪽으로 직진"
        "RightStraight" -> NavigationTurn.RIGHT_FORK to "오른쪽으로 직진"
        "LeftDirection" -> NavigationTurn.LEFT_FORK to "왼쪽 방향"
        "RightDirection" -> NavigationTurn.RIGHT_FORK to "오른쪽 방향"
        "OutHighway", "OutCityway" -> NavigationTurn.OTHER to "출구 방향"
        "InHighway", "InCityway" -> NavigationTurn.OTHER to "진입 방향"
        "LeftOutHighway", "LeftOutCityway" -> NavigationTurn.LEFT_FORK to "왼쪽 고속도로 출구"
        "RightOutHighway", "RightOutCityway" -> NavigationTurn.RIGHT_FORK to "오른쪽 고속도로 출구"
        "LeftInHighway", "LeftInCityway" -> NavigationTurn.LEFT_FORK to "왼쪽 고속도로 진입"
        "RightInHighway", "RightInCityway" -> NavigationTurn.RIGHT_FORK to "오른쪽 고속도로 진입"
        "ChangeLeftHighway" -> NavigationTurn.LEFT_FORK to "왼쪽 도로로 이동"
        "ChangeRightHighway" -> NavigationTurn.RIGHT_FORK to "오른쪽 도로로 이동"
        "JoinAfterBranch" -> NavigationTurn.MERGE to "합류"
        "Tollgate" -> NavigationTurn.STRAIGHT to "요금소"
        "NonstopTollgate" -> NavigationTurn.STRAIGHT to "하이패스"
        "InFerry" -> NavigationTurn.OTHER to "선착장 진입"
        "OutFerry" -> NavigationTurn.OTHER to "선착장 진출"
        "IndoorEnterance" -> NavigationTurn.OTHER to "실내 진입"
        "IndoorExit" -> NavigationTurn.OTHER to "실내 출구"
        "IndoorToUpFloor" -> NavigationTurn.OTHER to "위층으로 이동"
        "IndoorToDownFloor" -> NavigationTurn.OTHER to "아래층으로 이동"
        else -> {
            val structure = when {
                code.contains("UnderPath") -> "지하차도"
                code.contains("OverPath") -> "고가도로"
                code.contains("Tunnel") -> "터널"
                else -> null
            }
            if (structure == null) NavigationTurn.OTHER to "경로 안내"
            else {
                val left = code.startsWith("Left"); val right = code.startsWith("Right")
                val turn = if (left) NavigationTurn.LEFT_FORK else if (right) NavigationTurn.RIGHT_FORK else NavigationTurn.STRAIGHT
                turn to ((if (left) "좌측 " else if (right) "우측 " else "") + structure + if (code.endsWith("Side")) " 옆길" else "")
            }
        }
    }
}

/** A missing/unrelated camera cannot become a speed limit. A passed warning is removed, not declared safe. */
fun speedCameraWarning(code: String, limitKmh: Int, distanceMeters: Int?, passed: Boolean,
    sectionAverageKmh: Int? = null, options: NavigationSafetyOptions = NavigationSafetyOptions()): NavigationSpeedCamera? {
    if (passed || limitKmh !in 1..200 || !isSpeedEnforcementCode(code)) return null
    val phase = navigationSectionPhase(code)
    val section = phase == NavigationSectionPhase.ACTIVE
    if (!options.allowsSpeedWarning(code, distanceMeters)) return null
    val label = when {
        section -> "구간 단속"
        phase == NavigationSectionPhase.APPROACH -> "구간 단속 시작 지점"
        phase == NavigationSectionPhase.EXIT -> "구간 단속 종료 지점"
        code.contains("Movable") -> "이동식 단속"
        code.contains("SignalAndSpeed") -> "신호 · 과속 단속"
        else -> "과속 단속"
    }
    return NavigationSpeedCamera(limitKmh, if (section) null else distanceMeters, label,
        sectionAverageKmh?.takeIf { section && it in 1..300 }, code.contains("Movable"), phase)
}

fun navigationDistance(meters: Int?): String = when {
    meters == null || meters < 0 -> "—"
    meters < 1_000 -> "$meters m"
    else -> String.format(Locale.KOREAN, "%.1f km", meters / 1000.0)
}
