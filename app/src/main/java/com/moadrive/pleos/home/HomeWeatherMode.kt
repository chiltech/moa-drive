package com.moadrive.pleos.home

/** A presentation-only selection; the live weather source remains untouched. */
enum class HomeWeatherMode(val label: String) {
    ACTUAL("실제 날씨"),
    CLEAR("맑음"),
    CLOUDY("흐림"),
    RAIN("비"),
    SNOW("눈");

    fun stateFor(actual: HomeWeatherState): HomeWeatherState = when (this) {
        ACTUAL -> actual
        CLEAR -> sample(HomeWeatherScene.CLEAR, 22.0)
        CLOUDY -> sample(HomeWeatherScene.CLOUDY, 18.0)
        RAIN -> sample(HomeWeatherScene.RAIN, 16.0)
        SNOW -> sample(HomeWeatherScene.SNOW, -3.0)
    }

    private fun sample(scene: HomeWeatherScene, temperatureC: Double) = HomeWeatherState(
        scene = scene,
        temperatureC = temperatureC,
        conditionLabel = label,
        detail = "샘플 날씨",
    )
}
