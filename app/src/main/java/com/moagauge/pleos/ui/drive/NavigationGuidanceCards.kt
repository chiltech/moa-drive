package com.moagauge.pleos.ui.drive

import com.moagauge.pleos.ui.CardShadowElevation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import com.moagauge.pleos.ui.common.SoftSurface as Surface
import androidx.compose.material3.Text
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.sp
import com.moagauge.pleos.navigation.*
import com.moagauge.pleos.ui.Blue
import com.moagauge.pleos.ui.Ink
import com.moagauge.pleos.ui.MoaIcon
import com.moagauge.pleos.ui.MoaIconKind
import com.moagauge.pleos.ui.SurfaceColor

/** Passive route overlays. Preview values are opt-in and visibly marked; live absent data stays absent. */
@Composable
fun NavigationGuidanceCards(
    progress: NavigationProgress,
    guiding: Boolean,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
    accent: Color = Blue,
    cardColor: Color = SurfaceColor,
    contentColor: Color = Ink,
    showTraffic: Boolean = true,
    showSpeed: Boolean = true,
    showRestArea: Boolean = true,
    preview: Boolean = false,
    compact: Boolean = false,
    availableHeight: Dp = Dp.Infinity,
    currentSpeedKmh: Int? = null,
    speedWarningFlash: Boolean = true,
    previewProgress: NavigationProgress? = null,
) {
    val factor = scale.coerceIn(.75f, 1.3f)
    val numberScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val displayed = if (preview) previewProgress ?: navigationGuidancePreview() else progress
    val active = guiding || preview
    val cameraWarning = displayed.speedCamera.takeIf { active && showSpeed }
    val warningIntensity = speedWarningIntensity(cameraWarning?.let { isNavigationOverspeed(it, currentSpeedKmh) } == true,
        speedWarningFlash)
    if (compact) {
        CompactGuidanceCards(displayed, active, modifier, factor, accent, cardColor, contentColor,
            showTraffic, showSpeed, showRestArea, preview, availableHeight, warningIntensity)
        return
    }
    Column(modifier.width((246 * factor).dp).testTag("navigation.guidanceCards"),
        verticalArrangement = Arrangement.spacedBy((8 * factor).dp)) {
        if (preview) Surface(color = cardColor, shape = RoundedCornerShape((12 * factor).dp),
            border = BorderStroke(1.dp, accent.copy(alpha = .4f))) {
            Text("예시 정보 · 레이어 미리보기", Modifier.padding(horizontal = (12 * factor).dp, vertical = (7 * factor).dp),
                color = accent, fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold,
                fontSize = (11 * factor).sp)
        }
        val maneuver = displayed.maneuver.takeIf { active }
        GuidanceCard(cardColor, contentColor, factor, "navigation.maneuverCard") {
            Row(Modifier.fillMaxWidth().padding((15 * factor).dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy((12 * factor).dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy((3 * factor).dp)) {
                    if (maneuver == null) {
                        CardText(if (active) "길안내 준비 중" else "어디로 떠나볼까요?", 16f, factor,
                            contentColor, FontWeight.SemiBold, maxLines = 2)
                        CardText(if (active) "안내 정보를 받고 있어요" else "목적지를 검색해 주세요",
                            12f, factor, contentColor.copy(alpha = .72f), maxLines = 2)
                    } else {
                        CardText(navigationDistance(maneuver.distanceMeters), 32f, factor, contentColor, FontWeight.Bold)
                        CardText(maneuver.instruction, 14f, factor, accent, FontWeight.SemiBold, maxLines = 2)
                        if (maneuver.roadName.isNotBlank()) CardText(maneuver.roadName, 12f, factor,
                            contentColor.copy(alpha = .72f), maxLines = 2)
                    }
                }
                GuidanceIconTile(accent, factor, size = 52) {
                    RouteSymbol(maneuver, accent, Modifier.size((34 * factor).dp))
                }
            }
            if (maneuver != null) displayed.nextManeuver?.let { next ->
                Row(Modifier.fillMaxWidth().background(accent.copy(alpha = .07f))
                    .padding(horizontal = (15 * factor).dp, vertical = (8 * factor).dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy((7 * factor).dp)) {
                    RouteSymbol(next, accent, Modifier.size((16 * factor).dp))
                    CardText("다음 · ${next.instruction}", 11f, factor, contentColor, modifier = Modifier.weight(1f))
                    CardText(navigationDistance(next.distanceMeters), 11f, factor, contentColor.copy(alpha = .72f))
                }
            }
        }
        val traffic = displayed.traffic.takeIf { active && showTraffic }
        val restArea = displayed.restArea.takeIf { active && showRestArea }
        val sectionAverage = cameraWarning?.let(::cameraAverageLine)
        if (cameraWarning != null || traffic != null || restArea != null) {
            val dark = contentColor.luminance() > .5f
            val green = trafficColor("원활", accent, dark)
            val orange = trafficColor("지체", accent, dark)
            GuidanceCard(cardColor, contentColor, factor, "navigation.advisoryCard", warningIntensity) {
                if (cameraWarning != null) {
                    GuidanceAdvisoryRow(factor, "navigation.speedCameraCard", icon = {
                        SpeedLimitSign(cameraWarning.limitKmh, factor, numberScale)
                    }) {
                        cameraWarning.distanceMeters?.let {
                            CardText(navigationDistance(it), 18f, factor, contentColor, FontWeight.SemiBold)
                        }
                        CardText("제한속도 ${cameraWarning.limitKmh} km/h", 12f, factor, contentColor, maxLines = 2)
                        CardText(cameraStatusTitle(cameraWarning), 11f, factor, orange, FontWeight.SemiBold, maxLines = 2)
                    }
                }
                if (sectionAverage != null) {
                    AdvisoryDivider(contentColor, factor)
                    GuidanceAdvisoryRow(factor, "navigation.sectionAverageCard", icon = {
                        GuidanceIconTile(green, factor) {
                            MoaIcon(MoaIconKind.TRIPS, Modifier.size((22 * factor).dp), green, outline = true)
                        }
                    }) {
                        CardText("구간 평균속도", 13f, factor, contentColor, FontWeight.SemiBold)
                        CardText(sectionAverage, 12f, factor, contentColor.copy(alpha = .72f), maxLines = 2)
                    }
                }
                if (traffic != null) {
                    if (cameraWarning != null) AdvisoryDivider(contentColor, factor)
                    val trafficTint = trafficColor(traffic.condition, accent, dark)
                    GuidanceAdvisoryRow(factor, "navigation.trafficCard", icon = {
                        GuidanceIconTile(trafficTint, factor) {
                            MoaIcon(MoaIconKind.TRIPS, Modifier.size((22 * factor).dp), trafficTint, outline = true)
                        }
                    }) {
                        CardText(traffic.roadName, 14f, factor, contentColor, FontWeight.SemiBold)
                        CardText(traffic.condition, 12f, factor, trafficTint, FontWeight.SemiBold)
                        traffic.averageSpeedKmh?.let { speed ->
                            CardText("구간 평균 $speed km/h", 12f, factor, contentColor.copy(alpha = .72f))
                        }
                    }
                }
                if (restArea != null) {
                    if (cameraWarning != null || traffic != null) AdvisoryDivider(contentColor, factor)
                    GuidanceAdvisoryRow(factor, "navigation.restAreaCard", icon = {
                        GuidanceIconTile(green, factor) { RestAreaSymbol(green, Modifier.size((23 * factor).dp)) }
                    }) {
                        CardText("다음 휴게소 · ${navigationDistance(restArea.distanceMeters)}", 12f, factor,
                            green, FontWeight.SemiBold, maxLines = 2)
                        CardText(restArea.name, 14f, factor, contentColor, FontWeight.SemiBold, maxLines = 2)
                        if (restArea.amenities.isNotEmpty()) CardText(restArea.amenities.joinToString(" · "), 11f, factor,
                            contentColor.copy(alpha = .72f), maxLines = 2)
                    }
                }
            }
        }
    }
}

@Composable
private fun GuidanceIconTile(color: Color, factor: Float, size: Int = 42, content: @Composable () -> Unit) {
    Box(Modifier.size((size * factor).dp).background(color.copy(alpha = .09f), RoundedCornerShape((12 * factor).dp)),
        contentAlignment = Alignment.Center) {
        content()
    }
}

@Composable
private fun AdvisoryDivider(ink: Color, factor: Float) {
    HorizontalDivider(Modifier.padding(horizontal = (14 * factor).dp), color = ink.copy(alpha = .12f))
}

@Composable
private fun GuidanceAdvisoryRow(factor: Float, tag: String, icon: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().testTag(tag).padding(horizontal = (14 * factor).dp, vertical = (11 * factor).dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy((12 * factor).dp)) {
        icon()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy((3 * factor).dp), content = content)
    }
}

@Composable
private fun SpeedLimitSign(limit: Int, factor: Float, numberScale: Float, modifier: Modifier = Modifier) {
    Surface(modifier.size((42 * factor * numberScale).dp), shape = CircleShape, color = Color.White,
        border = BorderStroke((3 * factor).dp, Color(0xFFE74242))) {
        Box(contentAlignment = Alignment.Center) {
            CardText(limit.toString(), 17f, factor, Color(0xFF17202B), FontWeight.Bold)
        }
    }
}

/**
 * On a short display, current guidance and enforcement occupy the first two slots.
 * Optional rows are included only when their measured height fits, including the user's font scale.
 * The explicit budget is necessary because the caller's scroll container measures with infinite height.
 */
@Composable
private fun CompactGuidanceCards(
    progress: NavigationProgress,
    active: Boolean,
    modifier: Modifier,
    factor: Float,
    accent: Color,
    card: Color,
    ink: Color,
    showTraffic: Boolean,
    showSpeed: Boolean,
    showRestArea: Boolean,
    preview: Boolean,
    availableHeight: Dp,
    warningIntensity: Float,
) {
    val maneuver = progress.maneuver.takeIf { active }
    val camera = progress.speedCamera.takeIf { active && showSpeed }
    val numberScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    SubcomposeLayout(modifier.width((246 * factor).dp).testTag("navigation.guidanceCards")) { constraints ->
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else (246 * factor).dp.roundToPx()
        val childConstraints = Constraints(minWidth = width, maxWidth = width)
        val gap = (6 * factor).dp.roundToPx()
        val budget = minOf(constraints.maxHeight,
            if (availableHeight == Dp.Infinity) Constraints.Infinity else availableHeight.roundToPx().coerceAtLeast(0))
        fun measure(slot: String, content: @Composable () -> Unit) = subcompose(slot, content).single().measure(childConstraints)
        fun maneuverContent(condensed: Boolean): @Composable () -> Unit = {
            GuidanceCard(card, ink, factor, "navigation.maneuverCard") {
                Row(Modifier.padding(horizontal = (11 * factor).dp, vertical = (8 * factor).dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy((10 * factor).dp)) {
                    RouteSymbol(maneuver, accent, Modifier.size(((if (condensed) 28 else 36) * factor).dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy((2 * factor).dp)) {
                        if (maneuver != null) {
                            if (condensed) CardText("${navigationDistance(maneuver.distanceMeters)} · ${maneuver.instruction}",
                                13f, factor, ink, FontWeight.Bold, maxLines = 2)
                            else {
                                CardText(navigationDistance(maneuver.distanceMeters), 21f, factor, ink, FontWeight.ExtraBold)
                                CardText(maneuver.instruction, 11f, factor, accent, FontWeight.Bold)
                            }
                        } else {
                            CardText(if (active) "길안내 준비 중" else "목적지를 검색해 주세요", 13f, factor, ink, FontWeight.Bold)
                        }
                    }
                }
            }
        }
        val measured = mutableListOf<androidx.compose.ui.layout.Placeable>()
        if (preview) measured += measure("preview") {
            Surface(color = card, shape = RoundedCornerShape((9 * factor).dp)) {
                CardText("예시 정보 · 레이어 미리보기", 10f, factor, accent, FontWeight.SemiBold,
                    Modifier.padding(horizontal = (10 * factor).dp, vertical = (5 * factor).dp))
            }
        }
        var direction = measure("direction", maneuverContent(false))
        val enforcement = camera?.let { warning -> measure("camera") {
            GuidanceCard(card, ink, factor, "navigation.speedCameraCard", warningIntensity) {
                Row(Modifier.padding(horizontal = (11 * factor).dp, vertical = (8 * factor).dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy((10 * factor).dp)) {
                    SpeedLimitSign(warning.limitKmh, factor, numberScale)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy((2 * factor).dp)) {
                        CardText(cameraStatusTitle(warning), 11f, factor, ink, FontWeight.SemiBold, maxLines = 2)
                        CardText(warning.distanceMeters?.let(::navigationDistance) ?: "제한 ${warning.limitKmh} km/h",
                            15f, factor, ink, FontWeight.Bold)
                    }
                }
                cameraAverageLine(warning)?.let { average ->
                    CardText(average, 11f, factor, ink, FontWeight.SemiBold,
                        Modifier.fillMaxWidth().padding(start = (11 * factor).dp, end = (11 * factor).dp, bottom = (8 * factor).dp), maxLines = 2)
                }
            }
        } }
        val previewHeight = measured.sumOf { it.height } + if (measured.isEmpty()) 0 else gap
        if (previewHeight + direction.height + (enforcement?.height?.plus(gap) ?: 0) > budget) {
            direction = measure("direction-condensed", maneuverContent(true))
        }
        if (camera != null && enforcement != null && previewHeight + direction.height + enforcement.height + gap > budget) {
            // A large accessibility font can make two stacked cards too tall. Preserve both
            // critical readings in one row before considering any secondary information.
            measured += measure("combined-priority") {
                GuidanceCard(card, ink, factor, "navigation.priorityCard", warningIntensity) {
                    Column(Modifier.padding((9 * factor).dp), verticalArrangement = Arrangement.spacedBy((6 * factor).dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy((10 * factor).dp)) {
                            SpeedLimitSign(camera.limitKmh, factor, numberScale, modifier = Modifier.testTag("navigation.speedCameraCard"))
                            Column(Modifier.weight(1f).testTag("navigation.maneuverCard"),
                                verticalArrangement = Arrangement.spacedBy((5 * factor).dp)) {
                                CardText(navigationDistance(maneuver?.distanceMeters), 16f, factor, ink, FontWeight.ExtraBold)
                                Row(verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy((5 * factor).dp)) {
                                    RouteSymbol(maneuver, accent, Modifier.size((20 * factor).dp))
                                    CardText(maneuver?.instruction ?: "안내 준비 중", 10f, factor, accent,
                                        FontWeight.Bold, Modifier.weight(1f), maxLines = 2)
                                }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy((6 * factor).dp)) {
                            CardText(cameraStatusTitle(camera), 10f, factor, ink, FontWeight.SemiBold, Modifier.weight(1f), maxLines = 2)
                            camera.distanceMeters?.let { distance ->
                                CardText(navigationDistance(distance), 11f, factor, ink, FontWeight.Bold)
                            }
                        }
                        cameraAverageLine(camera)?.let { average ->
                            CardText(average, 11f, factor, ink, FontWeight.SemiBold, maxLines = 2)
                        }
                    }
                }
            }
        } else {
            measured += direction
            enforcement?.let(measured::add)
        }
        var used = measured.sumOf { it.height } + gap * (measured.size - 1)
        fun optional(slot: String, content: @Composable () -> Unit) {
            val candidate = measure(slot, content)
            if (used.toLong() + gap + candidate.height <= budget.toLong()) {
                measured += candidate
                used += gap + candidate.height
            }
        }
        if (active && showTraffic) progress.traffic?.let { traffic -> optional("traffic") {
            GuidanceCard(card, ink, factor, "navigation.trafficCard") {
                Row(Modifier.padding(horizontal = (10 * factor).dp, vertical = (5 * factor).dp),
                    horizontalArrangement = Arrangement.spacedBy((7 * factor).dp), verticalAlignment = Alignment.CenterVertically) {
                    CardText(traffic.condition, 10f, factor, trafficColor(traffic.condition, accent, ink.luminance() > .5f), FontWeight.Bold)
                    CardText(traffic.roadName, 10f, factor, ink, modifier = Modifier.weight(1f))
                    traffic.averageSpeedKmh?.let { speed -> CardText("평균 $speed km/h", 10f, factor, ink.copy(alpha = .7f)) }
                }
            }
        } }
        if (active && showRestArea) progress.restArea?.let { rest -> optional("rest-area") {
            GuidanceCard(card, ink, factor, "navigation.restAreaCard") {
                Row(Modifier.padding(horizontal = (10 * factor).dp, vertical = (5 * factor).dp),
                    horizontalArrangement = Arrangement.spacedBy((6 * factor).dp), verticalAlignment = Alignment.CenterVertically) {
                    RestAreaSymbol(accent, Modifier.size((15 * factor).dp))
                    CardText(rest.name, 10f, factor, ink, modifier = Modifier.weight(1f))
                    CardText(navigationDistance(rest.distanceMeters), 11f, factor, ink, FontWeight.Bold)
                }
            }
        } }
        layout(width, constraints.constrainHeight(used)) {
            var y = 0
            measured.forEach { child -> child.placeRelative(0, y); y += child.height + gap }
        }
    }
}

/** Only consumed by an explicitly labeled layer-settings preview, never live guidance. */
fun navigationGuidancePreview() = NavigationProgress(
    maneuver = NavigationManeuver(NavigationTurn.RIGHT, "우회전", "테헤란로 · 시청 방면", 350),
    nextManeuver = NavigationManeuver(NavigationTurn.STRAIGHT, "직진", "", 800),
    traffic = NavigationTraffic("테헤란로", 42, "원활"),
    speedCamera = NavigationSpeedCamera(60, 650, "과속 단속"),
    restArea = NavigationRestArea("서울만남의광장 휴게소", 12400, listOf("전기차 충전", "식사", "주유")),
)

/** Labeled preview only: active average-speed section; no live telemetry or provider state is changed. */
fun navigationSectionGuidancePreview() = navigationGuidancePreview().copy(
    speedCamera = NavigationSpeedCamera(80, null, "구간 단속", sectionAverageKmh = 88,
        sectionPhase = NavigationSectionPhase.ACTIVE),
)

@Composable
private fun GuidanceCard(card: Color, ink: Color, factor: Float, tag: String, warningIntensity: Float = 0f,
    content: @Composable ColumnScope.() -> Unit) {
    val warning = Color(0xFFE74242)
    Surface(Modifier.fillMaxWidth().testTag(tag), color = lerp(card, warning, warningIntensity * .12f),
        shape = RoundedCornerShape((19 * factor).dp), shadowElevation = CardShadowElevation,
        border = BorderStroke(if (warningIntensity > 0f) 1.5.dp else .75.dp,
            lerp(ink.copy(alpha = .09f), warning, warningIntensity))) { Column(content = content) }
}

/** A two-second cycle changes only decoration. Turning flashing off returns a steady value immediately. */
@Composable
private fun speedWarningIntensity(overspeed: Boolean, flash: Boolean): Float {
    if (!overspeed) return 0f
    if (!flash) return .7f
    val transition = rememberInfiniteTransition(label = "speedWarning")
    val intensity by transition.animateFloat(initialValue = .25f, targetValue = .85f,
        animationSpec = infiniteRepeatable(tween(1_000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "speedWarningDecoration")
    return intensity
}

@Composable
private fun CardText(text: String, size: Float, factor: Float, color: Color, weight: FontWeight = FontWeight.Medium,
    modifier: Modifier = Modifier, maxLines: Int = 1) {
    Text(text, modifier, color = color, fontFamily = FontFamily.SansSerif, fontSize = (size * factor).sp,
        lineHeight = (size * factor * 1.2f).sp, fontWeight = weight, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

private fun trafficColor(condition: String, accent: Color, dark: Boolean) = when (condition) {
    "원활" -> if (dark) Color(0xFF79D6B0) else Color(0xFF197A58)
    "서행" -> if (dark) Color(0xFFFFC87B) else Color(0xFFD45B08)
    "지체" -> if (dark) Color(0xFFFFAC66) else Color(0xFFD34D00)
    "정체", "통제" -> if (dark) Color(0xFFFF9090) else Color(0xFFBC3038)
    else -> accent
}

@Composable
private fun RouteSymbol(maneuver: NavigationManeuver?, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        scale(size.width / 24f, size.height / 24f, Offset.Zero) {
            val stroke = Stroke(2.8f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            fun line(vararg coordinates: Float) {
                val path = Path().apply { moveTo(coordinates[0], coordinates[1]);
                    for (i in 2 until coordinates.size step 2) lineTo(coordinates[i], coordinates[i + 1]) }
                drawPath(path, color, style = stroke)
            }
            fun straight() { line(12f, 21f, 12f, 3f); line(6f, 9f, 12f, 3f, 18f, 9f) }
            when (maneuver?.turn) {
                NavigationTurn.STRAIGHT -> straight()
                NavigationTurn.LEFT -> { line(17f, 21f, 17f, 10f, 4f, 10f); line(9f, 5f, 4f, 10f, 9f, 15f) }
                NavigationTurn.RIGHT -> { line(7f, 21f, 7f, 10f, 20f, 10f); line(15f, 5f, 20f, 10f, 15f, 15f) }
                NavigationTurn.LEFT_FORK -> { line(14f, 21f, 14f, 13f, 5f, 4f); line(5f, 12f, 5f, 4f, 13f, 4f) }
                NavigationTurn.RIGHT_FORK -> { line(10f, 21f, 10f, 13f, 19f, 4f); line(11f, 4f, 19f, 4f, 19f, 12f) }
                NavigationTurn.U_TURN -> {
                    val path = Path().apply { moveTo(18f, 21f); lineTo(18f, 9f); cubicTo(18f, 1f, 6f, 1f, 6f, 9f); lineTo(6f, 15f) }
                    drawPath(path, color, style = stroke); line(2f, 11f, 6f, 15f, 10f, 11f)
                }
                NavigationTurn.ROUNDABOUT -> {
                    drawArc(color, 35f, 285f, false, Offset(5f, 4f), Size(14f, 14f), style = stroke)
                    line(12f, 22f, 12f, 18f); line(19f, 6f, 19f, 12f, 13f, 12f)
                }
                NavigationTurn.ARRIVAL -> {
                    line(6f, 22f, 6f, 3f); line(6f, 3f, 19f, 3f, 17f, 9f, 6f, 9f)
                }
                NavigationTurn.MERGE -> { straight(); line(21f, 19f, 19f, 15f, 12f, 11f) }
                else -> {
                    if (maneuver?.clockDirection != null) rotate(maneuver.clockDirection * 30f, Offset(12f, 12f)) { straight() }
                    else {
                        drawCircle(color, 8.5f, Offset(12f, 12f), style = Stroke(1.8f))
                        drawCircle(color, 1.2f, Offset(12f, 7.5f))
                        line(12f, 11f, 12f, 16f)
                    }
                }
            }
        }
    }
}

@Composable
private fun RestAreaSymbol(color: Color, modifier: Modifier) {
    Canvas(modifier) {
        scale(size.width / 24f, size.height / 24f, Offset.Zero) {
            val stroke = Stroke(1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            val cup = Path().apply { moveTo(4f, 8f); lineTo(17f, 8f); lineTo(16f, 17f); lineTo(6f, 17f); close() }
            drawPath(cup, color, style = stroke)
            drawArc(color, -90f, 180f, false, Offset(15f, 9f), Size(7f, 6f), style = stroke)
            drawLine(color, Offset(3f, 21f), Offset(20f, 21f), 1.8f, StrokeCap.Round)
            drawLine(color, Offset(9f, 3f), Offset(9f, 5f), 1.8f, StrokeCap.Round)
            drawLine(color, Offset(14f, 3f), Offset(14f, 5f), 1.8f, StrokeCap.Round)
        }
    }
}
