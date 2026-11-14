package com.moagauge.pleos.cctv

import com.moagauge.pleos.navigation.GeoPoint

data class CctvCamera(
    val id: String,
    val name: String,
    val point: GeoPoint,
    val streamUrl: String?,
    val isDemo: Boolean = false,
)

enum class CctvStatus { IDLE, NEEDS_CONFIGURATION, LOADING, READY, NO_LOCATION, OFFLINE, ERROR, QUOTA_EXCEEDED }

data class CctvState(
    val status: CctvStatus = CctvStatus.IDLE,
    val cameras: List<CctvCamera> = emptyList(),
    val message: String? = null,
    val selected: CctvCamera? = null,
    val panelOpen: Boolean = false,
    val lastUpdatedMs: Long? = null,
    val demo: Boolean = false,
    val networkOnline: Boolean = true,
    val reconnecting: Boolean = false,
    val playbackGeneration: Long = 0L,
)

enum class CctvFailureKind { GENERAL, MONTHLY_QUOTA_EXCEEDED }

class CctvFailure(val kind: CctvFailureKind = CctvFailureKind.GENERAL) :
    Exception(if (kind == CctvFailureKind.MONTHLY_QUOTA_EXCEEDED) "이번 달 CCTV 조회 한도를 초과했어요."
        else "CCTV 정보를 불러오지 못했어요.")

fun interface CctvSource {
    suspend fun fetchNearby(origin: GeoPoint): List<CctvCamera>
}
