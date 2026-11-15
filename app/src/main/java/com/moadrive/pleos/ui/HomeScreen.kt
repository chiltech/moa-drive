package com.moadrive.pleos.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import com.moadrive.pleos.ui.common.SoftSurface as Surface
import com.moadrive.pleos.ui.common.darkGlassBackground
import androidx.compose.material3.TextButton
import androidx.compose.material3.RadioButton
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import com.moadrive.pleos.assist.RoutineScenario
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import com.moadrive.pleos.ui.common.softSurfaceShadow
import androidx.compose.foundation.border
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moadrive.pleos.home.HomeWeatherScene
import com.moadrive.pleos.home.HomeWeatherState
import com.moadrive.pleos.home.HomeWeatherMode
import com.moadrive.pleos.home.homeWeatherGreeting
import com.moadrive.pleos.ui.home.HomeWeatherEffects
import com.moadrive.pleos.home.shouldAnimateHomeWeather
import com.moadrive.pleos.ui.common.JourneyArt
import com.moadrive.pleos.ui.common.JourneyArtwork
import com.moadrive.pleos.ui.common.JourneyWeather
import com.moadrive.pleos.ui.common.DimmedDialog
import com.moadrive.pleos.ui.common.LocalDialogMotion
import com.moadrive.pleos.ui.common.rememberDialogAction
import com.moadrive.pleos.ui.common.InfoRevealState
import com.moadrive.pleos.ui.common.infoReveal
import com.moadrive.pleos.ui.common.rememberInfoRevealState
import com.moadrive.pleos.ui.drive.ServiceRail
import com.moadrive.pleos.ui.drive.serviceRailWidth
import com.moadrive.pleos.ui.assist.AiGuideAvatar
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun HomeScreen(
    state: HomeUiState,
    servicesEnabled: Boolean = true,
    onServiceClick: (HomeService) -> Unit,
    onRoutineScenario: (RoutineScenario) -> Unit = {},
    routinesEnabled: Boolean = true,
    weather: HomeWeatherState = HomeWeatherState(),
    animateWeather: Boolean = false,
    aiGuideEnabled: Boolean = false,
    aiGuideSummary: String? = null,
    onAiBriefing: () -> Unit = {},
) {
    val revealState = rememberInfoRevealState()
    // A local preview never replaces observations used by safety or AI guidance.
    var weatherMode by rememberSaveable { mutableStateOf(HomeWeatherMode.ACTUAL) }
    var weatherPickerOpen by remember { mutableStateOf(false) }
    val shownWeather = weatherMode.stateFor(weather)
    val animate = shouldAnimateHomeWeather(animateWeather, true) && !weatherPickerOpen
    LaunchedEffect(servicesEnabled) { if (!servicesEnabled) weatherPickerOpen = false }
    BoxWithConstraints(Modifier.fillMaxSize().background(BackgroundColor).windowInsetsPadding(WindowInsets.safeDrawing)) {
        val railWidth = serviceRailWidth(maxWidth)
        Row(Modifier.fillMaxSize()) {
            BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
                val largeText = LocalDensity.current.fontScale > 1.15f
                val split = maxWidth >= 840.dp && !largeText
                val inset = if (split) 32.dp else if (maxWidth < 520.dp) 16.dp else 22.dp
                val innerWidth = maxWidth - inset * 2
                Column(
                    Modifier.fillMaxSize().padding(horizontal = inset, vertical = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    HomeHeader(state.connectionLabel, animateWeather)
                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                        val bodyHeight = maxHeight
                        if (split) {
                            // Use a bounded body so both columns share the same bottom edge.
                            // A short window can scroll the content while its service dock stays put.
                            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                                Row(Modifier.fillMaxWidth().height(bodyHeight.coerceAtLeast(550.dp)),
                                    horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                                    Column(Modifier.weight(.46f).fillMaxHeight()) {
                                        Greeting(shownWeather.scene, Modifier.fillMaxWidth(), true)
                                        Spacer(Modifier.height(18.dp))
                                        WeatherBadge(shownWeather, Modifier.fillMaxWidth(), servicesEnabled,
                                            sample = weatherMode != HomeWeatherMode.ACTUAL) { weatherPickerOpen = true }
                                        Spacer(Modifier.height(28.dp))
                                        CityScene(shownWeather.scene, animate, Modifier.weight(1f).fillMaxWidth())
                                    }
                                    HomeOverview(state, servicesEnabled, routinesEnabled, onRoutineScenario,
                                        aiGuideEnabled, aiGuideSummary, onAiBriefing,
                                        Modifier.weight(.54f).fillMaxHeight(), revealState, fillHeight = true)
                                }
                            }
                        } else {
                            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(22.dp)) {
                                Greeting(shownWeather.scene, Modifier.fillMaxWidth(), false)
                                WeatherBadge(shownWeather, Modifier.fillMaxWidth(), servicesEnabled,
                                    sample = weatherMode != HomeWeatherMode.ACTUAL) { weatherPickerOpen = true }
                                CityScene(shownWeather.scene, animate, Modifier.fillMaxWidth()
                                    .height((bodyHeight * .43f).coerceIn(180.dp, 280.dp)))
                                HomeOverview(state, servicesEnabled, routinesEnabled, onRoutineScenario,
                                    aiGuideEnabled, aiGuideSummary, onAiBriefing,
                                    Modifier.fillMaxWidth(), revealState, stacked = innerWidth < 440.dp || largeText)
                            }
                        }
                    }
                    ServiceShortcuts(servicesEnabled, onServiceClick, fitAll = split)
                }
            }
            Box(Modifier.width(1.dp).fillMaxHeight().background(Line))
            ServiceRail(width = railWidth, servicesEnabled = servicesEnabled, onHome = {},
                onService = onServiceClick, selectedService = null)
        }
    }
    if (weatherPickerOpen && servicesEnabled) {
        HomeWeatherPicker(weatherMode, weather, onSelect = {
            weatherMode = it
            weatherPickerOpen = false
        }, onDismiss = { weatherPickerOpen = false })
    }
}

@Composable
private fun HomeOverview(
    state: HomeUiState,
    servicesEnabled: Boolean,
    routinesEnabled: Boolean,
    onRoutineScenario: (RoutineScenario) -> Unit,
    aiGuideEnabled: Boolean,
    aiGuideSummary: String?,
    onAiBriefing: () -> Unit,
    modifier: Modifier,
    revealState: InfoRevealState,
    stacked: Boolean = false,
    fillHeight: Boolean = false,
) {
    // Keep the last metric row's shadow inside the vertical scroll viewport.
    // Padding on the outer screen would still leave that shadow outside its clip bounds.
    Column(modifier.padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (aiGuideEnabled) {
            HomeSupportCard("모아 AI · 일상 케어", aiGuideSummary ?: "탑승 인사부터 출퇴근·픽업 루틴까지 함께해요",
                Blue, servicesEnabled, onAiBriefing,
                (if (fillHeight) Modifier.weight(.23f).fillMaxWidth() else Modifier.fillMaxWidth()).infoReveal(revealState, 0)) {
                AiGuideAvatar(Modifier.size(42.dp))
            }
        }
        HomeRoutineCard(servicesEnabled && routinesEnabled, onRoutineScenario,
            (if (fillHeight) Modifier.weight(.29f).fillMaxWidth() else Modifier.fillMaxWidth()).infoReveal(revealState, 1),
            compact = !stacked, fillHeight = fillHeight)
        HomeDashboard(state, if (fillHeight) Modifier.weight(.48f).fillMaxWidth() else Modifier.fillMaxWidth(),
            revealState, stacked, fillHeight)
    }
}

@Composable
private fun HomeSupportCard(
    title: String, description: String, accent: Color, enabled: Boolean, onClick: () -> Unit,
    modifier: Modifier, icon: @Composable () -> Unit,
) {
    Surface(onClick = onClick, enabled = enabled,
        modifier = modifier.heightIn(min = 84.dp).semantics { contentDescription = "$title 열기" },
        shape = RoundedCornerShape(20.dp), color = CardColor,
        shadowElevation = CardShadowElevation,
        border = null) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) { icon() }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, color = if (enabled) Ink else Secondary, fontSize = 16.sp,
                    lineHeight = 21.sp, fontWeight = FontWeight.SemiBold)
                Text(description, color = Secondary, fontSize = 12.sp, lineHeight = 18.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text("›", color = if (enabled) accent else Secondary, fontSize = 22.sp)
        }
    }
}

@Composable
private fun HomeHeader(connectionLabel: String, active: Boolean) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(active) {
        now = LocalDateTime.now()
        if (active) while (isActive) { delay(15_000L); now = LocalDateTime.now() }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val headerWidth = maxWidth
        val wrap = headerWidth < 640.dp || LocalDensity.current.fontScale > 1.15f
        val clock = now.format(DateTimeFormatter.ofPattern("M월 d일 EEEE  HH:mm", Locale.KOREAN))
        if (wrap) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (headerWidth >= 340.dp && LocalDensity.current.fontScale <= 1.4f) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Brand(Modifier.weight(1f))
                        Text(connectionLabel, color = Secondary, fontSize = 12.sp)
                    }
                } else {
                    Brand()
                    Text(connectionLabel, color = Secondary, fontSize = 12.sp)
                }
                Text(clock, color = Secondary, fontSize = 13.sp)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Brand()
                Spacer(Modifier.weight(1f))
                Text(clock, color = Secondary, fontSize = 13.sp)
                Text(connectionLabel, color = Secondary, fontSize = 12.sp,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(CardColor).padding(horizontal = 12.dp, vertical = 7.dp))
            }
        }
    }
}

@Composable
private fun Brand(modifier: Modifier = Modifier) {
    Text("MOA DRIVE", color = Ink, fontSize = 15.sp, letterSpacing = 1.8.sp,
        fontWeight = FontWeight.Medium, fontFamily = MoaTypeface, modifier = modifier)
}

@Composable
private fun Greeting(scene: HomeWeatherScene, modifier: Modifier, wide: Boolean) {
    Text(homeWeatherGreeting(scene), color = Ink, fontSize = if (wide) 32.sp else 28.sp,
        lineHeight = if (wide) 45.sp else 38.sp, fontWeight = FontWeight.SemiBold,
        letterSpacing = (-.8).sp, fontFamily = MoaTypeface, modifier = modifier)
}

@Composable
private fun WeatherBadge(weather: HomeWeatherState, modifier: Modifier, enabled: Boolean, sample: Boolean, onClick: () -> Unit) {
    Row(modifier.clip(RoundedCornerShape(16.dp))
        .clickable(enabled = enabled, role = Role.Button, onClickLabel = "날씨 선택", onClick = onClick)
        .semantics { contentDescription = "날씨 선택" },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        WeatherSymbol(weather.scene, Modifier.size(64.dp))
        val temperature = weather.temperatureC?.roundToInt()?.let { "$it°" }
        if (temperature != null) {
            Text(temperature, color = Ink, fontSize = 52.sp,
                lineHeight = 62.sp, fontWeight = FontWeight.Medium,
                letterSpacing = (-1.5).sp, fontFamily = MoaTypeface)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(weather.conditionLabel, color = Ink, fontSize = 20.sp,
                lineHeight = 26.sp, fontWeight = FontWeight.Medium)
            Text(weather.detail + if (enabled) " · 변경" else "", color = if (sample) Blue else Secondary,
                fontSize = 12.sp, lineHeight = 18.sp)
        }
        if (enabled) Text("⌄", color = Blue, fontSize = 22.sp, modifier = Modifier.padding(end = 8.dp))
    }
}

@Composable
private fun HomeWeatherPicker(selected: HomeWeatherMode, actual: HomeWeatherState,
    onSelect: (HomeWeatherMode) -> Unit, onDismiss: () -> Unit) {
    DimmedDialog(onDismissRequest = onDismiss) {
        val close = rememberDialogAction(onDismiss)
        val motion = LocalDialogMotion.current
        Surface(Modifier.widthIn(max = 480.dp).fillMaxWidth(.92f).heightIn(max = 640.dp),
            shape = RoundedCornerShape(24.dp), color = SurfaceColor, shadowElevation = CardShadowElevation) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("날씨 미리보기", Modifier.weight(1f), color = Ink, fontSize = 23.sp,
                        fontWeight = FontWeight.Medium)
                    TextButton(onClick = close, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("닫기", fontSize = 15.sp)
                    }
                }
                Text("날씨를 골라 메인 화면의 문구와 도시 효과를 확인해 보세요.",
                    color = Secondary, fontSize = 14.sp, lineHeight = 21.sp)
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HomeWeatherMode.entries.forEach { mode ->
                        val preview = mode.stateFor(actual)
                        val active = mode == selected
                        Surface(Modifier.fillMaxWidth().selectable(selected = active, role = Role.RadioButton,
                            onClick = { motion.close { onSelect(mode) } }).semantics { contentDescription = "${mode.label} 선택" },
                            shape = RoundedCornerShape(16.dp), color = if (active) PaleBlue else CardColor,
                            border = BorderStroke(if (active) 1.5.dp else .7.dp, if (active) Blue else Line)) {
                            Row(Modifier.heightIn(min = 76.dp).padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                WeatherSymbol(preview.scene, Modifier.size(38.dp))
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(mode.label, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                                    Text(if (mode == HomeWeatherMode.ACTUAL) "현재 위치의 날씨로 돌아가요"
                                        else when (mode) {
                                            HomeWeatherMode.RAIN -> "빗방울이 내리는 도시"
                                            HomeWeatherMode.SNOW -> "눈송이가 흩날리는 도시"
                                            HomeWeatherMode.CLOUDY -> "구름 아래 차분한 도시"
                                            else -> "햇살이 비치는 도시"
                                        }, color = Secondary, fontSize = 12.sp, lineHeight = 18.sp)
                                }
                                RadioButton(selected = active, onClick = null)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeatherSymbol(scene: HomeWeatherScene, modifier: Modifier) {
    val blue = Blue
    val yellow = AccentYellow
    val cloud = Secondary.copy(alpha = .5f)
    Canvas(modifier.clearAndSetSemantics {}) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = size.minDimension * .18f
        when (scene) {
            HomeWeatherScene.CLEAR -> {
                drawCircle(yellow, radius, center)
                repeat(8) { index ->
                    val angle = index * PI / 4
                    val direction = Offset(cos(angle).toFloat(), sin(angle).toFloat())
                    drawLine(yellow, center + direction * (radius * 1.45f), center + direction * (radius * 1.9f), 2.dp.toPx(), StrokeCap.Round)
                }
            }
            HomeWeatherScene.SNOW -> repeat(6) { index ->
                val angle = index * PI / 3
                val direction = Offset(cos(angle).toFloat(), sin(angle).toFloat())
                drawLine(blue, center, center + direction * (radius * 2), 2.dp.toPx(), StrokeCap.Round)
                val branch = center + direction * radius
                listOf(-.7, .7).forEach { offset ->
                    drawLine(blue, branch, branch + Offset(cos(angle + offset).toFloat(), sin(angle + offset).toFloat()) * radius * .55f,
                        1.4.dp.toPx(), StrokeCap.Round)
                }
            }
            HomeWeatherScene.NEUTRAL -> {
                drawCircle(cloud, radius * 1.6f, center, style = Stroke(1.6.dp.toPx()))
                drawLine(cloud, center - Offset(radius * .6f, 0f), center + Offset(radius * .6f, 0f), 1.6.dp.toPx(), StrokeCap.Round)
            }
            else -> {
                drawCircle(cloud, radius * 1.2f, center - Offset(0f, radius * .4f))
                drawCircle(cloud, radius, center - Offset(radius * 1.1f, 0f))
                drawCircle(cloud, radius * .9f, center + Offset(radius, 0f))
                if (scene == HomeWeatherScene.RAIN) repeat(3) { index ->
                    val start = center + Offset((index - 1) * radius, radius * 1.35f)
                    drawLine(blue, start, start + Offset(-radius * .25f, radius * .65f), 1.6.dp.toPx(), StrokeCap.Round)
                }
                if (scene == HomeWeatherScene.FOG) repeat(2) { index ->
                    val y = center.y + radius * (1.45f + index * .6f)
                    drawLine(cloud, Offset(center.x - radius * 1.5f, y), Offset(center.x + radius * 1.5f, y), 1.4.dp.toPx(), StrokeCap.Round)
                }
            }
        }
    }
}

@Composable
private fun CityScene(scene: HomeWeatherScene, animate: Boolean, modifier: Modifier) {
    val artworkWeather = when (scene) {
        HomeWeatherScene.RAIN -> JourneyWeather.RAIN
        HomeWeatherScene.SNOW -> JourneyWeather.SNOW
        else -> JourneyWeather.CLEAR
    }
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val ratio = if (artworkWeather == JourneyWeather.CLEAR) 1.45f else 1.30f
        val sceneWidth = minOf(maxWidth, maxHeight * ratio)
        Box(Modifier.size(sceneWidth, sceneWidth / ratio).clip(RoundedCornerShape(28.dp)).semantics {
            contentDescription = when (scene) {
                HomeWeatherScene.RAIN -> "비 오는 도시 이미지"
                HomeWeatherScene.SNOW -> "눈 내리는 도시 이미지"
                else -> "도시 이미지"
            }
        }) {
            JourneyArtwork(JourneyArt.HOME, Modifier.fillMaxSize(), contentDescription = null, weather = artworkWeather)
            if (scene == HomeWeatherScene.RAIN || scene == HomeWeatherScene.SNOW) {
                HomeWeatherEffects(scene, animate, Modifier.matchParentSize())
            }
        }
    }
}

@Composable
private fun HomeDashboard(state: HomeUiState, modifier: Modifier, revealState: InfoRevealState, stacked: Boolean = false, fillHeight: Boolean = false) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (stacked) {
            DriveStatus(state, Modifier.fillMaxWidth().heightIn(min = 94.dp).infoReveal(revealState, 2))
            MetricCard("배터리", state.batteryPercent?.toString(), "%", Modifier.fillMaxWidth().infoReveal(revealState, 3), battery = true)
            MetricCard("주행가능거리", state.rangeKm?.toString(), "km", Modifier.fillMaxWidth().infoReveal(revealState, 4))
            MetricCard(state.efficiencyLabel, state.efficiencyKmPerKwh?.let { String.format(Locale.getDefault(), "%.1f", it) }, "km/kWh", Modifier.fillMaxWidth().infoReveal(revealState, 5))
        } else {
            Row(if (fillHeight) Modifier.weight(1f).fillMaxWidth() else Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DriveStatus(state, Modifier.weight(1f).then(if (fillHeight) Modifier.fillMaxHeight() else Modifier.heightIn(min = 94.dp)).infoReveal(revealState, 2))
                MetricCard("배터리", state.batteryPercent?.toString(), "%",
                    Modifier.weight(1f).then(if (fillHeight) Modifier.fillMaxHeight() else Modifier.heightIn(min = 94.dp)).infoReveal(revealState, 3), battery = true)
            }
            Row(if (fillHeight) Modifier.weight(1f).fillMaxWidth() else Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("주행가능거리", state.rangeKm?.toString(), "km",
                    Modifier.weight(1f).then(if (fillHeight) Modifier.fillMaxHeight() else Modifier.heightIn(min = 94.dp)).infoReveal(revealState, 4))
                MetricCard(state.efficiencyLabel, state.efficiencyKmPerKwh?.let { String.format(Locale.getDefault(), "%.1f", it) },
                    "km/kWh", Modifier.weight(1f).then(if (fillHeight) Modifier.fillMaxHeight() else Modifier.heightIn(min = 94.dp)).infoReveal(revealState, 5))
            }
        }
    }
}

@Composable
private fun DriveStatus(state: HomeUiState, modifier: Modifier) {
    val shape = RoundedCornerShape(20.dp)
    val background = if (IsDarkTheme) Modifier.darkGlassBackground(shape) else Modifier.background(CardColor)
    Column(modifier.softSurfaceShadow(CardShadowElevation, shape).clip(shape).then(background).padding(14.dp)
        .clearAndSetSemantics { contentDescription = "기어 ${state.gear ?: "정보 없음"}, 속도 ${state.speedKmh?.let { "$it km/h" } ?: "정보 없음"}" },
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)) {
        Text("기어/속도", color = Secondary, fontSize = 13.sp, lineHeight = 18.sp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(44.dp).background(PaleBlue, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center) {
                HomeMetricNumber(state.gear ?: "—")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                HomeMetricNumber(state.speedKmh?.toString() ?: "—", Modifier.alignByBaseline())
                Text("km/h", color = Secondary, fontSize = 12.sp, lineHeight = 18.sp,
                    modifier = Modifier.alignByBaseline(), maxLines = 1, softWrap = false)
            }
        }
    }
}

@Composable
private fun HomeMetricNumber(value: String, modifier: Modifier = Modifier) {
    // Match the bold numeric face used by the trip's animated electricity amount.
    Text(value, modifier = modifier, maxLines = 1, softWrap = false,
        style = TextStyle(color = Ink, fontWeight = FontWeight.Bold,
            fontSize = 30.sp, lineHeight = 36.sp, fontFeatureSettings = "tnum"))
}

@Composable
private fun MetricCard(label: String, value: String?, unit: String, modifier: Modifier, battery: Boolean = false) {
    val colors = if (battery) listOf(PaleBlue, CardColor) else listOf(CardColor, CardColor)
    val shape = RoundedCornerShape(20.dp)
    val background = if (IsDarkTheme) Modifier.darkGlassBackground(shape)
        else Modifier.background(Brush.linearGradient(colors))
    Row(modifier.softSurfaceShadow(CardShadowElevation, shape).clip(shape).then(background)
        .padding(14.dp)
        .clearAndSetSemantics { contentDescription = value?.let { "$label $it $unit" } ?: "$label 정보 없음" },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, color = Secondary, fontSize = 13.sp, lineHeight = 18.sp)
        BoxWithConstraints(Modifier.fillMaxWidth().heightIn(min = 44.dp), contentAlignment = Alignment.CenterStart) {
            if (maxWidth < 116.dp && unit == "km/kWh") {
                Column {
                    HomeMetricNumber(value ?: "—")
                    Text(unit, color = Secondary, fontSize = 12.sp)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    HomeMetricNumber(value ?: "—", Modifier.alignByBaseline())
                    Text(unit, color = Secondary, fontSize = 12.sp, lineHeight = 18.sp,
                        modifier = Modifier.alignByBaseline(), maxLines = 1, softWrap = false)
                }
            }
        }
      }
      if (battery) HomeBatteryArtwork(value?.toIntOrNull(), Modifier.size(width = 34.dp, height = 46.dp))
    }
}

@Composable
private fun HomeBatteryArtwork(percent: Int?, modifier: Modifier) {
    val blue = Blue
    val line = Secondary.copy(alpha = .65f)
    val face = CardColor
    Canvas(modifier) {
        val u = size.width / 28f
        val top = size.height * .13f
        val h = size.height * .82f
        drawRoundRect(line, Offset(9f * u, 0f), Size(10f * u, top + 2f * u), CornerRadius(2f * u))
        drawRoundRect(face, Offset(2f * u, top), Size(24f * u, h), CornerRadius(4f * u))
        drawRoundRect(line, Offset(2f * u, top), Size(24f * u, h), CornerRadius(4f * u), style = Stroke(1.8f * u))
        val fillHeight = (h - 7f * u) * ((percent ?: 0).coerceIn(0, 100) / 100f)
        if (fillHeight > 0) drawRoundRect(Brush.verticalGradient(listOf(blue.copy(alpha = .78f), blue)),
            Offset(5.5f * u, top + h - 3.5f * u - fillHeight), Size(17f * u, fillHeight), CornerRadius(1.7f * u))
    }
}

@Composable
private fun HomeRoutineCard(enabled: Boolean, onScenario: (RoutineScenario) -> Unit, modifier: Modifier,
    compact: Boolean, fillHeight: Boolean = false) {
    val shape = RoundedCornerShape(18.dp)
    val background = if (IsDarkTheme) Modifier.darkGlassBackground(shape) else Modifier.background(CardColor)
    Column(modifier.softSurfaceShadow(CardShadowElevation, shape)
        .clip(shape).then(background).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("오늘의 루틴", color = Ink, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold)
        if (compact) {
            // Let the shortcuts use the card's spare height, keeping the bottom inset equal to its sides.
            Row(Modifier.fillMaxWidth().then(if (fillHeight) Modifier.weight(1f) else Modifier),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RoutineScenario.entries.forEach { scenario ->
                    RoutineShortcut(scenario, enabled, onScenario,
                        Modifier.weight(1f).then(if (fillHeight) Modifier.fillMaxHeight() else Modifier), compact = true)
                }
            }
        } else {
            RoutineScenario.entries.forEach { scenario ->
                RoutineShortcut(scenario, enabled, onScenario, Modifier.fillMaxWidth(), compact = false)
            }
        }
        if (!enabled) Text("주차 상태와 루틴 제안 설정을 확인해 주세요", color = Secondary, fontSize = 11.sp, lineHeight = 16.sp)
    }
}

@Composable
private fun RoutineShortcut(scenario: RoutineScenario, enabled: Boolean, onScenario: (RoutineScenario) -> Unit,
    modifier: Modifier, compact: Boolean) {
    val accent = when (scenario) {
        RoutineScenario.COMMUTE -> Blue
        RoutineScenario.SCHOOL_PICKUP -> AccentOrange
        RoutineScenario.RETURN_HOME -> AccentGreen
    }
    Surface(onClick = { if (enabled) onScenario(scenario) }, enabled = enabled,
        modifier = modifier.heightIn(min = if (compact) 80.dp else 60.dp),
        shape = RoundedCornerShape(12.dp), color = accent.copy(alpha = if (IsDarkTheme) .15f else .07f)) {
        if (compact) {
            Column(Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically)) {
                Text(String.format(Locale.KOREAN, "%02d:00", scenario.hour), color = if (enabled) accent else Secondary,
                    fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Text(scenario.label, color = if (enabled) Ink else Secondary, fontSize = 13.sp, lineHeight = 18.sp)
            }
        } else {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(String.format(Locale.KOREAN, "%02d:00", scenario.hour), color = if (enabled) accent else Secondary,
                    fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(scenario.label, Modifier.weight(1f), color = if (enabled) Ink else Secondary,
                    fontSize = 14.sp, lineHeight = 20.sp)
                Text("›", color = if (enabled) accent else Secondary, fontSize = 22.sp)
            }
        }
    }
}

@Composable
private fun ServiceShortcuts(enabled: Boolean, onClick: (HomeService) -> Unit, fitAll: Boolean) {
    Surface(Modifier.fillMaxWidth().semantics { contentDescription = "하단 서비스 메뉴" }, color = CardColor, shape = RoundedCornerShape(20.dp),
        shadowElevation = CardShadowElevation) {
        val rowModifier = if (fitAll) Modifier.fillMaxWidth() else Modifier.horizontalScroll(rememberScrollState())
        Row(rowModifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(if (fitAll) 4.dp else 8.dp)) {
                HomeService.entries.filter { it != HomeService.SETTINGS }.forEach { service ->
                    val serviceEnabled = enabled || service == HomeService.DRIVE
                    val itemModifier = if (fitAll) Modifier.weight(1f) else Modifier.width(100.dp)
                    Column(itemModifier.clip(RoundedCornerShape(16.dp)).alpha(if (serviceEnabled) 1f else .4f)
                        .semantics { contentDescription = "${service.label} 열기" }
                        .clickable(enabled = serviceEnabled, role = Role.Button, onClickLabel = "${service.label} 열기") { onClick(service) }
                        .heightIn(min = 94.dp).padding(vertical = 8.dp, horizontal = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
                            HomeMenuIllustration(service.icon, Modifier.size(40.dp))
                        }
                        Text(if (service == HomeService.BATTERY_BUDGET) "예산 드라이브" else service.label,
                            color = Ink, fontSize = 14.sp, lineHeight = 19.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            maxLines = if (service == HomeService.BATTERY_BUDGET) 1 else 2,
                            softWrap = service != HomeService.BATTERY_BUDGET, overflow = TextOverflow.Ellipsis)
                    }
                }
        }
    }
}
