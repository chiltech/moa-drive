package com.moadrive.pleos.navigation

/** Routing intent stays independent of each navigation SDK's priority/avoid bit flags. */
enum class NavigationRoutePreference(val label: String, val description: String) {
    RECOMMENDED("추천", "교통 상황을 고려한 추천 경로를 찾아요."),
    FREE_ROADS("무료도로 우선", "유료도로를 피해 탐색해요. 경로에 따라 통행료가 발생할 수 있어요."),
    HIGHWAY_FIRST("고속도로 우선", "고속도로를 우선해서 찾아요. 유료 구간이 포함될 수 있어요."),
    ;

    companion object {
        fun fromStored(value: String?): NavigationRoutePreference =
            entries.firstOrNull { it.name == value } ?: RECOMMENDED
    }
}
