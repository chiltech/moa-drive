package com.moagauge.pleos.ui.common

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.moagauge.pleos.ui.common.SoftSurface as Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moagauge.pleos.ui.*
import com.moagauge.pleos.ui.drive.ServiceRail
import com.moagauge.pleos.ui.drive.serviceRailWidth

/** MoaGauge's tablet Explore layout: original art left, service details right. */
@Composable
fun DetailPage(
    service: HomeService,
    browsingAllowed: Boolean,
    onHome: () -> Unit,
    onService: (HomeService) -> Unit,
    left: @Composable ColumnScope.() -> Unit,
    right: @Composable ColumnScope.() -> Unit,
    compactLeft: (@Composable ColumnScope.() -> Unit)? = null,
    revealState: InfoRevealState = rememberInfoRevealState(),
) {
    BackHandler(onBack = onHome)
    // Keep readers, selected dates and child card state when Pleos moves between panes.
    val rightPane = rememberDetailPane { modifier ->
        Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CompositionLocalProvider(LocalInfoRevealState provides revealState) { right() }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(BackgroundColor).windowInsetsPadding(WindowInsets.safeDrawing)) {
        val railWidth = serviceRailWidth(maxWidth)
        val expanded = DetailPaneLayout.isExpanded(maxWidth, LocalDensity.current.fontScale)
        val inset = DetailPaneLayout.pagePadding(expanded)
        CompositionLocalProvider(LocalDetailExpanded provides expanded) {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxHeight().padding(inset)) {
                    if (expanded) {
                        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(DetailPaneLayout.Gap)) {
                            Column(Modifier.weight(DetailPaneLayout.LeftWeight).fillMaxHeight().verticalScroll(rememberScrollState(), enabled = browsingAllowed),
                                verticalArrangement = Arrangement.spacedBy(16.dp), content = left)
                            rightPane(Modifier.weight(DetailPaneLayout.RightWeight).fillMaxHeight()
                                .verticalScroll(rememberScrollState(), enabled = browsingAllowed))
                        }
                    } else {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState(), enabled = browsingAllowed), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                            (compactLeft ?: left)()
                            rightPane(Modifier.fillMaxWidth())
                        }
                    }
                }
                ServiceRail(railWidth, browsingAllowed, onHome, onService, selectedService = service)
            }
        }
    }
}

/** Compact heroes share one size while preserving each wide-screen composition. */
@Composable
fun DetailPageArtwork(art: JourneyArt, wideRatio: Float, wideMaxHeight: androidx.compose.ui.unit.Dp) {
    val expanded = LocalDetailExpanded.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val artHeight = if (expanded) (maxWidth * wideRatio).coerceAtMost(wideMaxHeight) else 160.dp
        JourneyArtwork(art, Modifier.fillMaxWidth().height(artHeight))
    }
}

/** Move a pane between one- and two-column layouts without recreating its scroll/native map state. */
@Composable
fun rememberDetailPane(content: @Composable (Modifier) -> Unit): @Composable (Modifier) -> Unit {
    val latestContent by rememberUpdatedState(content)
    return remember { movableContentOf { modifier: Modifier -> latestContent(modifier) } }
}

@Composable
fun DetailHero(eyebrow: String, title: String, art: Int, summary: String, value: String? = null) {
    Surface(shape = RoundedCornerShape(28.dp), color = SurfaceColor, shadowElevation = CardShadowElevation) {
        Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(PaleBlue, SurfaceColor))).padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(eyebrow, color = Blue, fontSize = 11.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.SemiBold)
            Image(painterResource(art), contentDescription = null, modifier = Modifier.fillMaxWidth().height(150.dp), contentScale = ContentScale.Fit)
            Text(title, color = Ink, fontSize = 30.sp, fontWeight = FontWeight.Medium)
            if (value != null) Text(value, color = Blue, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text(summary, color = Secondary, fontSize = 14.sp, lineHeight = 21.sp)
        }
    }
}

@Composable
fun DetailCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = CardColor, shadowElevation = CardShadowElevation, border = null) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

/** Unboxed summary content aligned with the artwork and heading in a detail pane. */
@Composable
fun DetailSummary(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

@Composable
fun DetailHeading(title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = Ink, fontSize = 30.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold)
        Text(description, color = Secondary, fontSize = 15.sp, lineHeight = 22.sp)
    }
}
