package com.moadrive.pleos.debug

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.moadrive.pleos.home.HomeWeatherScene
import com.moadrive.pleos.home.HomeWeatherState
import com.moadrive.pleos.ui.BackgroundColor
import com.moadrive.pleos.ui.HomeScreen
import com.moadrive.pleos.ui.HomeUiState
import com.moadrive.pleos.ui.moaWindowScale
import com.moadrive.pleos.ui.MoaTheme
import java.util.Locale

/** Debug-only shared-screen fixture; never constructs live vehicle, weather or trip sessions. */
class HomeWeatherPreviewActivity : AppCompatActivity() {
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
        val scene = when (request.getStringExtra("scene")?.uppercase(Locale.ROOT)) {
            "RAIN" -> HomeWeatherScene.RAIN
            "SNOW" -> HomeWeatherScene.SNOW
            else -> HomeWeatherScene.CLEAR
        }
        val temperature = when (scene) {
            HomeWeatherScene.RAIN -> 16.0
            HomeWeatherScene.SNOW -> -3.0
            else -> 22.0
        }
        val condition = when (scene) {
            HomeWeatherScene.RAIN -> "비"
            HomeWeatherScene.SNOW -> "눈"
            else -> "맑음"
        }
        val summary = when (scene) {
            HomeWeatherScene.RAIN -> "빗길에서는 속도를 줄이고 앞차와 충분한 거리를 두세요."
            HomeWeatherScene.SNOW -> "눈길에서는 천천히 출발하고 급제동을 피해 주세요."
            else -> "오늘도 안전운전하세요. 출발 전 주변을 확인해 주세요."
        }
        val widthDp = request.getIntExtra("widthDp", 0).takeIf { it >= 320 }
        val animate = request.getBooleanExtra("animate", false)
        val darkOverride = request.getStringExtra("theme")?.let { it == "dark" }
        setContent {
            // The shared theme only reads appearance preferences. No preference is updated here.
            MoaTheme(darkOverride = darkOverride) {
                PreviewViewport(widthDp) {
                    HomeScreen(
                        state = HomeUiState(
                            gear = "P", speedKmh = 0, batteryPercent = 82, rangeKm = 386,
                            efficiencyKmPerKwh = 6.2, connectionLabel = "날씨 미리보기",
                        ),
                        servicesEnabled = true,
                        onServiceClick = {},
                        onRoutineScenario = {},
                        routinesEnabled = true,
                        weather = HomeWeatherState(scene, temperature, condition, "날씨 화면 미리보기"),
                        animateWeather = animate,
                        aiGuideEnabled = true,
                        aiGuideSummary = summary,
                        onAiBriefing = {},
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewViewport(widthDp: Int?, content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().background(BackgroundColor), contentAlignment = Alignment.TopEnd) {
        val previewWidth = widthDp?.dp?.coerceAtMost(maxWidth) ?: maxWidth
        BoxWithConstraints(Modifier.width(previewWidth).fillMaxHeight()) {
            val density = LocalDensity.current
            // Match MoaDriveApp's scaling for both the actual OS window and a constrained QA viewport.
            val scale = moaWindowScale(maxWidth.value, maxHeight.value)
            CompositionLocalProvider(LocalDensity provides Density(density.density * scale, density.fontScale)) {
                Box(Modifier.fillMaxSize()) { content() }
            }
        }
    }
}
