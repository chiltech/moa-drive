package com.moagauge.pleos.ui.common

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.moagauge.pleos.R
import com.moagauge.pleos.ui.IsDarkTheme

enum class JourneyArt { HOME, TRIPS, PASSPORT, SAFETY, SOUND, INSURANCE, SETTINGS, CONDITION, BUDGET }
enum class JourneyWeather { CLEAR, RAIN, SNOW }
enum class SupportArt { ASSISTANCE, BATTERY, TOWING, TIRE }

/** Transparent service artwork; all text and controls remain native UI. */
@Composable
fun JourneyArtwork(kind: JourneyArt, modifier: Modifier = Modifier,
    contentDescription: String? = null, weather: JourneyWeather = JourneyWeather.CLEAR) {
    val dark = IsDarkTheme
    // Each theme has its own alpha cutout, preserving the approved materials and lighting.
    val service = when (kind) {
        JourneyArt.TRIPS -> if (dark)
            R.drawable.service_trips_dark_cutout_20261113 to Rect(42f, 84f, 1166f, 1197f)
        else R.drawable.service_trips_cutout_20261113 to Rect(44f, 85f, 1164f, 1198f)
        JourneyArt.PASSPORT -> if (dark)
            R.drawable.service_passport_dark_cutout_20261113 to Rect(55f, 110f, 1178f, 1097f)
        else R.drawable.service_passport_cutout_20261113 to Rect(57f, 113f, 1177f, 1094f)
        JourneyArt.SAFETY -> if (dark)
            R.drawable.service_safety_dark_cutout_20261113 to Rect(55f, 168f, 1200f, 1114f)
        else R.drawable.service_safety_cutout_20261113 to Rect(56f, 171f, 1199f, 1111f)
        JourneyArt.SOUND -> if (dark)
            R.drawable.service_sound_dark_cutout_20261113 to Rect(30f, 212f, 1228f, 1070f)
        else R.drawable.service_sound_cutout_20261113 to Rect(31f, 215f, 1226f, 1068f)
        JourneyArt.INSURANCE -> if (dark)
            R.drawable.service_insurance_dark_cutout_20261113 to Rect(75f, 105f, 1172f, 1184f)
        else R.drawable.service_insurance_cutout_20261113 to Rect(77f, 106f, 1171f, 1185f)
        JourneyArt.SETTINGS -> if (dark)
            R.drawable.service_settings_dark_cutout_20261113 to Rect(80f, 252f, 1173f, 1066f)
        else R.drawable.service_settings_cutout_20261113 to Rect(80f, 254f, 1171f, 1062f)
        JourneyArt.BUDGET -> if (dark)
            R.drawable.service_budget_dark_cutout_20261113 to Rect(14f, 186f, 1242f, 1116f)
        else R.drawable.service_budget_cutout_20261113 to Rect(14f, 189f, 1240f, 1109f)
        JourneyArt.CONDITION -> if (dark)
            R.drawable.service_condition_dark_cutout_20261113 to Rect(158f, 252f, 1096f, 1003f)
        else R.drawable.service_condition_cutout_20261113 to Rect(160f, 253f, 1091f, 1002f)
        JourneyArt.HOME -> null
    }
    if (service != null) {
        val original = painterResource(service.first)
        val fitted = remember(original, service) { VisibleArtworkPainter(original, service.second) }
        Image(fitted, contentDescription, modifier, contentScale = ContentScale.Fit)
        return
    }
    // Alpha bounds measured from the new 1254px cutouts, with an 18px breathing margin.
    val home = when (weather) {
        JourneyWeather.CLEAR -> if (dark)
            R.drawable.home_clear_dark_cutout_20261113 to Rect(47f, 250f, 1216f, 1055f)
        else R.drawable.home_clear_light_cutout_20261113 to Rect(49f, 251f, 1212f, 1051f)
        JourneyWeather.RAIN -> if (dark)
            R.drawable.home_rain_dark_cutout_20261113 to Rect(29f, 278f, 1216f, 1091f)
        else R.drawable.home_rain_light_cutout_20261113 to Rect(30f, 281f, 1213f, 1090f)
        JourneyWeather.SNOW -> if (dark)
            R.drawable.home_snow_dark_cutout_20261113 to Rect(50f, 173f, 1214f, 1121f)
        else R.drawable.home_snow_light_cutout_20261113 to Rect(51f, 175f, 1213f, 1115f)
    }
    val original = painterResource(home.first)
    val fitted = remember(original, home) { VisibleArtworkPainter(original, home.second) }
    Image(fitted, contentDescription, modifier, contentScale = ContentScale.Fit)
}

/** The four actual approved roadside-assistance illustrations, with native labels alongside. */
@Composable
fun SupportArtwork(kind: SupportArt, modifier: Modifier = Modifier) {
    val bounds = when (kind) {
        SupportArt.ASSISTANCE -> Rect(1306f, 1022f, 1404f, 1135f)
        SupportArt.BATTERY -> Rect(1885f, 1014f, 1985f, 1135f)
        SupportArt.TOWING -> Rect(1290f, 1187f, 1400f, 1290f)
        SupportArt.TIRE -> Rect(1869f, 1182f, 1985f, 1290f)
    }
    ApprovedArtwork(R.drawable.concept_insurance_20261110, bounds, modifier, null)
}

@Composable
private fun ApprovedArtwork(resource: Int, bounds: Rect, modifier: Modifier, description: String?) {
    val original = painterResource(resource)
    val fitted = remember(original, resource, bounds) { VisibleArtworkPainter(original, bounds) }
    Image(fitted, description, modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            // Fade only the outer margin into the app canvas, retaining the original
            // object, highlights and contact shadow without a rectangular image edge.
            drawRect(Brush.horizontalGradient(0f to Color.Transparent, .05f to Color.Black,
                .95f to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
            drawRect(Brush.verticalGradient(0f to Color.Transparent, .05f to Color.Black,
                .95f to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
        }, contentScale = ContentScale.Fit)
}

/** Fit the visible object, rather than the transparent square surrounding it. */
private class VisibleArtworkPainter(private val source: Painter, private val bounds: Rect) : Painter() {
    override val intrinsicSize = Size(bounds.width, bounds.height)
    override fun DrawScope.onDraw() {
        val scale = minOf(size.width / bounds.width, size.height / bounds.height)
        clipRect {
            withTransform({ translate(-bounds.left * scale, -bounds.top * scale) }) {
                with(source) { draw(Size(intrinsicSize.width * scale, intrinsicSize.height * scale)) }
            }
        }
    }
}

