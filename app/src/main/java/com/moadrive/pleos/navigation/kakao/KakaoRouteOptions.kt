package com.moadrive.pleos.navigation.kakao

import com.kakaomobility.knsdk.KNRouteAvoidOption
import com.kakaomobility.knsdk.KNRoutePriority
import com.moadrive.pleos.navigation.NavigationRoutePreference

/** The same snapshot must be used for preview and KNNaviView's guidance/rerouting state. */
internal data class KakaoRouteOptions(val priority: KNRoutePriority, val avoid: Int)

internal fun NavigationRoutePreference.kakaoOptions(): KakaoRouteOptions = when (this) {
    NavigationRoutePreference.RECOMMENDED -> KakaoRouteOptions(KNRoutePriority.KNRoutePriority_Recommand, KNRouteAvoidOption.KNRouteAvoidOption_None.value)
    NavigationRoutePreference.FREE_ROADS -> KakaoRouteOptions(KNRoutePriority.KNRoutePriority_Recommand, KNRouteAvoidOption.KNRouteAvoidOption_Fare.value)
    NavigationRoutePreference.HIGHWAY_FIRST -> KakaoRouteOptions(KNRoutePriority.KNRoutePriority_HighWay, KNRouteAvoidOption.KNRouteAvoidOption_None.value)
}
