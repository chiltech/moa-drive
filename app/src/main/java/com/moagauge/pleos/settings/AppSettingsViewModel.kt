package com.moagauge.pleos.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.moagauge.pleos.startup.StartupLaunch

class AppSettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("app_settings", 0)
    private val mutableState = MutableStateFlow(AppPreferences(
        appearance = AppearanceMode.entries.find { it.name == prefs.getString("appearance", null) } ?: AppearanceMode.LIGHT,
        mapAppearance = MapAppearance.entries.find { it.name == prefs.getString("map_appearance", null) } ?: MapAppearance.FOLLOW_APP,
        speedWarnings = prefs.getBoolean("speed_warnings", true), voiceGuidance = prefs.getBoolean("voice_guidance", true),
        showVehicle = prefs.getBoolean("show_vehicle", true), showCctv = prefs.getBoolean("show_cctv", true),
        showDrivingMetrics = prefs.getBoolean("show_metrics", true), routineSuggestions = prefs.getBoolean("routine_suggestions", true),
        incidentWarnings = prefs.getBoolean("incident_warnings", true), departureWeather = prefs.getBoolean("departure_weather", true),
        aiGuideEnabled = readBoolean("ai_guide_enabled"),
        aiGuideVoice = readBoolean("ai_guide_voice"),
        keepScreenOn = prefs.getBoolean("keep_screen_on", true),
        showIntro = runCatching { prefs.getBoolean("show_intro", true) }.getOrDefault(true),
        autoLaunchOnBoarding = runCatching { prefs.getBoolean("auto_launch_on_boarding", false) }.getOrDefault(false),
        speedWarningFlash = readBoolean("speed_warning_flash"),
        speedWarningDistanceMeters = runCatching { prefs.getInt("speed_warning_distance_meters", 1000) }.getOrDefault(1000),
        includeMobileCameras = readBoolean("include_mobile_cameras"),
        map3dEnabled = readBoolean("map_3d_enabled", defaultValue = false),
        navigationLayers = NavigationLayerPreferences(
            cardScale = NavigationCardScale.entries.find { it.name == readString("nav_layer_scale") } ?: NavigationCardScale.STANDARD,
            background = NavigationCardBackground.entries.find { it.name == readString("nav_layer_background") } ?: NavigationCardBackground.FOLLOW_THEME,
            opacity = runCatching { prefs.getFloat("nav_layer_opacity", .94f) }.getOrDefault(.94f),
            showTraffic = readBoolean("nav_layer_traffic"), showSpeed = readBoolean("nav_layer_speed"),
            showRestArea = readBoolean("nav_layer_rest_area"),
            accent = NavigationCardAccent.entries.find { it.name == readString("nav_layer_accent") } ?: NavigationCardAccent.BLUE,
        ).normalized(),
    ).normalized())
    val state = mutableState.asStateFlow()

    private fun readString(key: String) = runCatching { prefs.getString(key, null) }.getOrNull()
    private fun readBoolean(key: String, defaultValue: Boolean = true) = runCatching { prefs.getBoolean(key, defaultValue) }.getOrDefault(defaultValue)

    fun update(value: AppPreferences) {
        val normalized = value.normalized()
        mutableState.value = normalized
        prefs.edit().putString("appearance", value.appearance.name).putString("map_appearance", value.mapAppearance.name)
            .putBoolean("speed_warnings", value.speedWarnings).putBoolean("voice_guidance", value.voiceGuidance)
            .putBoolean("show_vehicle", value.showVehicle).putBoolean("show_cctv", value.showCctv)
            .putBoolean("show_metrics", value.showDrivingMetrics).putBoolean("routine_suggestions", value.routineSuggestions)
            .putBoolean("incident_warnings", value.incidentWarnings).putBoolean("departure_weather", value.departureWeather)
            .putBoolean("ai_guide_enabled", value.aiGuideEnabled)
            .putBoolean("ai_guide_voice", value.aiGuideVoice)
            .putBoolean("keep_screen_on", value.keepScreenOn)
            .putBoolean("show_intro", normalized.showIntro)
            .putBoolean("auto_launch_on_boarding", normalized.autoLaunchOnBoarding)
            .putBoolean("speed_warning_flash", normalized.speedWarningFlash)
            .putInt("speed_warning_distance_meters", normalized.speedWarningDistanceMeters)
            .putBoolean("include_mobile_cameras", normalized.includeMobileCameras)
            .putBoolean("map_3d_enabled", normalized.map3dEnabled)
            .putString("nav_layer_scale", normalized.navigationLayers.cardScale.name)
            .putString("nav_layer_background", normalized.navigationLayers.background.name)
            .putFloat("nav_layer_opacity", normalized.navigationLayers.opacity)
            .putBoolean("nav_layer_traffic", normalized.navigationLayers.showTraffic)
            .putBoolean("nav_layer_speed", normalized.navigationLayers.showSpeed)
            .putBoolean("nav_layer_rest_area", normalized.navigationLayers.showRestArea)
            .putString("nav_layer_accent", normalized.navigationLayers.accent.name).apply()
        if (!normalized.autoLaunchOnBoarding) StartupLaunch.cancelNotification(getApplication<Application>())
    }
}
