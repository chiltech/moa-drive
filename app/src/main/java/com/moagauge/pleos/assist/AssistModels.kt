package com.moagauge.pleos.assist

import com.moagauge.pleos.navigation.GeoPoint
import java.time.LocalDateTime

/** Public example destinations, never the driver's real home, workplace or child's school. */
enum class RoutineScenario(
    val label: String,
    val hour: Int,
    val title: String,
    val question: String,
    val destinationQuery: String,
    val careMessage: String,
) {
    COMMUTE("출근", 8, "회사로 출근하는 길인가요?", "회사로 안내해 드릴까요?", "서울특별시청",
        "좋은 아침이에요. 서두르지 말고 오늘도 안전운전하세요."),
    SCHOOL_PICKUP("자녀 픽업", 15, "자녀를 데리러 가는 시간이에요", "자녀가 다니는 학교로 안내해 드릴까요?", "서울재동초등학교",
        "아이와 만나는 길, 여유 있게 출발해요. 오늘도 안전운전하세요."),
    RETURN_HOME("퇴근", 18, "퇴근하시나요?", "집으로 안내해 드릴까요?", "공덕역",
        "오늘도 수고 많으셨어요. 편안한 귀갓길 되세요. 오늘도 안전운전하세요.");

    val speechText: String get() = "$careMessage $title $question"
}

data class RoutineObservation(val scenario: RoutineScenario, val departedAt: LocalDateTime)
data class RoutineSuggestion(val scenario: RoutineScenario, val sampleCount: Int, val minuteOfDay: Int)

data class RoadIncident(
    val id: String,
    val title: String,
    val point: GeoPoint,
    val occurredAtMs: Long,
    val expiresAtMs: Long,
    val severe: Boolean = true,
    val demo: Boolean = true,
)

data class NearbyIncident(val incident: RoadIncident, val distanceMeters: Double)
