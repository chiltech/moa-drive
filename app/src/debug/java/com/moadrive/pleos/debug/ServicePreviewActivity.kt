package com.moadrive.pleos.debug

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moadrive.pleos.assist.AiGuideContext
import com.moadrive.pleos.assist.AiGuideMessage
import com.moadrive.pleos.assist.AiGuideOptions
import com.moadrive.pleos.assist.AiGuideSession
import com.moadrive.pleos.home.HomeWeatherScene
import com.moadrive.pleos.home.HomeWeatherState
import com.moadrive.pleos.navigation.GeoPoint
import com.moadrive.pleos.safety.AreaForecast
import com.moadrive.pleos.safety.WeatherReading
import com.moadrive.pleos.safety.WeatherSample
import com.moadrive.pleos.ui.BackgroundColor
import com.moadrive.pleos.ui.HomeScreen
import com.moadrive.pleos.ui.HomeUiState
import com.moadrive.pleos.ui.moaWindowScale
import com.moadrive.pleos.ui.MoaTheme
import com.moadrive.pleos.ui.assist.AiGuideCard
import com.moadrive.pleos.ui.common.DimmedDialog
import com.moadrive.pleos.ui.condition.ConditionNoteScreen
import com.moadrive.pleos.ui.trips.TripsScreen
import com.moadrive.pleos.trips.TripRecord
import com.moadrive.pleos.trips.TripStatus
import java.time.LocalDate
import java.time.ZoneId
import com.moadrive.pleos.ui.drive.serviceRailWidth
import com.moadrive.pleos.vehicle.VehicleTelemetry
import java.time.LocalDateTime
import java.util.Locale

/** Shared service UI fixtures: no vehicle session, network, TTS or stored trip access. */
class ServicePreviewActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        showPreview(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        showPreview(intent)
    }

    private fun showPreview(request: Intent) {
        val screen = request.getStringExtra("screen")?.lowercase(Locale.ROOT) ?: "briefing"
        val variant = request.getStringExtra("variant")?.lowercase(Locale.ROOT) ?: "rain"
        val widthDp = request.getIntExtra("widthDp", 0).takeIf { it >= 320 }
        val darkOverride = request.getStringExtra("theme")?.let { it == "dark" }
        val message = briefingFixture(variant)
        setContent {
            // The common theme reads appearance only. This fixture writes no preference.
            MoaTheme(darkOverride = darkOverride) {
                ServicePreviewViewport(widthDp) { contentWidth ->
                    if (screen == "condition") {
                        ConditionNoteScreen(records = emptyList(), visits = emptyList(),
                            browsingAllowed = true, onHome = {}, onService = {})
                    } else if (screen == "trips") {
                        TripsScreen(records = if (variant == "empty") emptyList() else tripFixtures(),
                            current = null, recordingEnabled = true, statusMessage = "화면 미리보기",
                            browsingAllowed = true, onRecordingEnabled = {}, onHome = {}, onService = {},
                            textInputAllowed = false)
                    } else {
                        BriefingFixture(message, contentWidth)
                    }
                }
            }
        }
    }
}

@Composable
private fun BriefingFixture(message: AiGuideMessage, contentWidth: Dp) {
    var open by remember(message) { mutableStateOf(true) }
    HomeScreen(
        state = HomeUiState(gear = "P", speedKmh = 0, batteryPercent = 82, rangeKm = 386,
            efficiencyKmPerKwh = 6.2, connectionLabel = "서비스 화면 미리보기"),
        servicesEnabled = true, onServiceClick = {}, onRoutineScenario = {},
        weather = HomeWeatherState(HomeWeatherScene.CLEAR, 22.0, "맑음", "화면 미리보기"),
        animateWeather = false, aiGuideEnabled = true, onAiBriefing = { open = true },
    )
    if (open) {
        DimmedDialog(onDismissRequest = { open = false }) {
            // A Dialog is a separate window. Constrain the card as well as the preview viewport.
            val cardWidth = minOf(660.dp, (contentWidth - serviceRailWidth(contentWidth) - 40.dp).coerceAtLeast(200.dp))
            AiGuideCard(message, onDismiss = { open = false },
                modifier = Modifier.padding(20.dp).widthIn(max = cardWidth).fillMaxWidth())
        }
    }
}

@Composable
private fun ServicePreviewViewport(widthDp: Int?, content: @Composable (Dp) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().background(BackgroundColor), contentAlignment = Alignment.TopEnd) {
        val previewWidth = widthDp?.dp?.coerceAtMost(maxWidth) ?: maxWidth
        BoxWithConstraints(Modifier.width(previewWidth).fillMaxHeight()) {
            val density = LocalDensity.current
            val scale = moaWindowScale(maxWidth.value, maxHeight.value)
            CompositionLocalProvider(LocalDensity provides Density(density.density * scale, density.fontScale)) {
                BoxWithConstraints(Modifier.fillMaxSize()) { content(maxWidth) }
            }
        }
    }
}

private fun briefingFixture(variant: String): AiGuideMessage {
    val now = 1_800_000_000_000L
    val elapsed = 10_000L
    val point = GeoPoint(37.5665, 126.9780)
    val rain = variant == "rain"
    val hour = now / 3_600_000L * 3_600_000L
    fun reading(time: Long) = WeatherReading(time, if (rain) 61 else 0, if (rain) 12.0 else 22.0,
        if (rain) 1.2 else 0.0, 0.0, 3.0, 4.0, 10_000.0, if (rain) 80.0 else 0.0)
    val forecast = if (variant == "unknown") null else AreaForecast(point, now, (0..4).map { index ->
        WeatherSample("미리보기 지점 $index", point, reading(now),
            (0 until if (variant == "partial") 3 else 6).map { offset -> reading(hour + offset * 3_600_000L) })
    })
    val session = AiGuideSession(nowMs = { now }, nowElapsedMs = { elapsed },
        localNow = { LocalDateTime.of(2026, 11, 12, 8, 0) })
    session.setVisible(true)
    session.updateContext(AiGuideContext(
        telemetry = VehicleTelemetry(active = true, gear = "P", speedKmh = 0, ignitionOn = true,
            speedObservedElapsedMs = elapsed),
        browsingAllowed = true, forecast = forecast, options = AiGuideOptions(voiceEnabled = false),
    ))
    session.dismiss()
    session.requestBriefing()
    return requireNotNull(session.state.value.message)
}

/** In-memory visual records only; the production repository is never opened. */
private fun tripFixtures(): List<TripRecord> {
    val today = LocalDate.now()
    val zone = ZoneId.systemDefault()
    return listOf(Triple(8, 24_600.0, 5.8), Triple(18, 18_200.0, 6.2)).mapIndexed { index, (hour, distance, efficiency) ->
        val start = today.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
        val duration = (if (index == 0) 42 else 31) * 60_000L
        TripRecord("visual-preview-$index", start, start + duration, today.toString(), zone.id, duration,
            distance, distance / efficiency, 82 - index * 8, 76 - index * 8, TripStatus.COMPLETED)
    }
}
