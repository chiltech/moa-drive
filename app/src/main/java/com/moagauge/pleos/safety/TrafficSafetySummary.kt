package com.moagauge.pleos.safety

import com.moagauge.pleos.assist.NearbyIncident
import com.moagauge.pleos.cctv.CctvState
import com.moagauge.pleos.cctv.CctvStatus

enum class TrafficMetricStatus { UNAVAILABLE, FRESH, CACHED, DEMO }
enum class TrafficDemoScenario { QUIET, ALERTS }

data class TrafficSafetyMetric(
    val count: Int? = null,
    val status: TrafficMetricStatus = TrafficMetricStatus.UNAVAILABLE,
    val statusLabel: String = "연결 대기",
    val updatedAtMs: Long? = null,
)

data class TrafficSafetySummary(
    val accidents: TrafficSafetyMetric = TrafficSafetyMetric(),
    val pedestrians: TrafficSafetyMetric = TrafficSafetyMetric(statusLabel = "이력 연결 대기"),
    val cctv: TrafficSafetyMetric = TrafficSafetyMetric(statusLabel = "조회 전"),
    val demo: Boolean = false,
)

/** A refresh may temporarily clear the controller's list. Only READY proves a zero count. */
fun trafficCatalogForSummary(current: CctvState, lastSuccessful: CctvState): CctvState {
    if (current.demo || current.status == CctvStatus.READY) return current
    if (lastSuccessful.status != CctvStatus.READY || lastSuccessful.lastUpdatedMs == null || lastSuccessful.demo) {
        return current.copy(cameras = emptyList(), lastUpdatedMs = null)
    }
    return when (current.status) {
        CctvStatus.LOADING, CctvStatus.ERROR, CctvStatus.OFFLINE -> lastSuccessful.copy(
            status = current.status, networkOnline = current.networkOnline, selected = null, panelOpen = false,
        )
        CctvStatus.IDLE -> lastSuccessful.copy(networkOnline = current.networkOnline, selected = null, panelOpen = false)
        else -> current
    }
}

/** Read-only projection of the existing map catalog; never starts another data owner or request. */
fun currentTrafficSafetySummary(cctvState: CctvState, nowMs: Long): TrafficSafetySummary {
    val unavailableLabel = when (cctvState.status) {
        CctvStatus.IDLE -> "조회 전"
        CctvStatus.NEEDS_CONFIGURATION -> "연결 대기"
        CctvStatus.LOADING -> "확인 중"
        CctvStatus.READY -> "조회 전"
        CctvStatus.NO_LOCATION -> "위치 대기"
        CctvStatus.OFFLINE -> "연결 대기"
        CctvStatus.ERROR -> "조회 실패"
        CctvStatus.QUOTA_EXCEEDED -> "조회 제한"
    }
    val actualCameras = cctvState.cameras.filterNot { it.isDemo }.distinctBy { it.id }
    val timestamp = cctvState.lastUpdatedMs
    val ageMs = timestamp?.let { nowMs - it }
    val hasCatalog = !cctvState.demo &&
        (cctvState.cameras.isEmpty() || actualCameras.isNotEmpty()) &&
        cctvState.status in setOf(CctvStatus.READY, CctvStatus.LOADING, CctvStatus.OFFLINE, CctvStatus.ERROR) &&
        ageMs != null && ageMs >= 0
    val metric = if (hasCatalog) {
        val fresh = cctvState.status == CctvStatus.READY && cctvState.networkOnline && ageMs!! < 300_000L
        TrafficSafetyMetric(
            count = actualCameras.size,
            status = if (fresh) TrafficMetricStatus.FRESH else TrafficMetricStatus.CACHED,
            statusLabel = if (fresh) "확인됨" else when (cctvState.status) {
                CctvStatus.LOADING -> "이전 정보 · 확인 중"
                CctvStatus.OFFLINE -> "이전 정보 · 오프라인"
                else -> "이전 정보"
            },
            updatedAtMs = timestamp,
        )
    } else TrafficSafetyMetric(statusLabel = when {
        cctvState.demo || cctvState.cameras.any { it.isDemo } -> "체험 데이터 제외"
        ageMs != null && ageMs < 0 -> "다시 확인"
        else -> unavailableLabel
    })
    // AssistSession contains examples, not an accident feed. Pedestrian data is not connected.
    return TrafficSafetySummary(cctv = metric)
}

/** Explicit samples remain separate from current-data totals, including when the map is in demo mode. */
fun demoTrafficSafetySummary(
    scenario: TrafficDemoScenario,
    incidentDemo: NearbyIncident?,
    nowMs: Long,
): TrafficSafetySummary {
    val activeDemo = incidentDemo?.incident?.let { it.demo && nowMs >= it.occurredAtMs && nowMs < it.expiresAtMs } == true
    fun sample(count: Int) = TrafficSafetyMetric(count, TrafficMetricStatus.DEMO, "예시 데이터")
    val alerts = scenario == TrafficDemoScenario.ALERTS
    return TrafficSafetySummary(
        accidents = sample(if (alerts || activeDemo) 1 else 0),
        pedestrians = sample(if (alerts) 2 else 0).copy(statusLabel = "예시 사고 이력"),
        cctv = sample(if (alerts) 3 else 0),
        demo = true,
    )
}
