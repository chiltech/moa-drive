package com.moadrive.pleos.ui.assist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import com.moadrive.pleos.ui.common.SoftSurface as Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moadrive.pleos.ui.*

/** The approved briefing composition shared by weather, routine and incident dialogs.
 * The caller owns the dimmed window, visibility and every action. */
@Composable
internal fun AiCareDialog(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    speaking: Boolean = false,
    aiEnabled: Boolean = true,
    footer: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val card = CardColor
    Surface(modifier.widthIn(max = 640.dp).fillMaxWidth().heightIn(max = 660.dp)
        .semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(30.dp), color = card, shadowElevation = FloatingShadowElevation) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val stackedHeader = maxWidth < 420.dp || LocalDensity.current.fontScale > 1.2f
            val scrollWholeCard = stackedHeader || maxHeight < 520.dp
            val inset = if (stackedHeader) 20.dp else 28.dp
            val scroll = rememberScrollState()
            val avatar: @Composable () -> Unit = {
                if (aiEnabled) BriefingAvatar(Modifier.size(if (stackedHeader) 54.dp else 64.dp), speaking)
                else MoaIcon(MoaIconKind.SAFETY, Modifier.size(54.dp), Blue)
            }
            val heading: @Composable (Modifier) -> Unit = { headingModifier ->
                Column(headingModifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (aiEnabled) "모아 AI" else "모아 · 안전 안내", color = Blue,
                        fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(title.removeSuffix("."), color = Ink, fontSize = 27.sp, lineHeight = 36.sp,
                        fontWeight = FontWeight.SemiBold)
                }
            }
            Column(Modifier.then(if (scrollWholeCard) Modifier.verticalScroll(scroll) else Modifier)
                .padding(inset), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                if (stackedHeader) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            avatar()
                            Spacer(Modifier.size(48.dp))
                        }
                        heading(Modifier.fillMaxWidth())
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        avatar()
                        heading(Modifier.weight(1f))
                        if (scrollWholeCard) Spacer(Modifier.size(48.dp))
                        else AiCareCloseButton(onDismiss)
                    }
                }
                Column(if (scrollWholeCard) Modifier else Modifier.weight(1f, fill = false).verticalScroll(scroll),
                    verticalArrangement = Arrangement.spacedBy(22.dp), content = content)
                footer()
            }
            // Remains reachable even when a small split window scrolls the whole dialog.
            if (scrollWholeCard) AiCareCloseButton(onDismiss,
                Modifier.align(Alignment.TopEnd).padding(inset).background(card, RoundedCornerShape(50)))
        }
    }
}

@Composable
internal fun AiCareNotice(content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), color = if (IsDarkTheme) SurfaceColor else Color(0xFFF7F8FA),
        shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
internal fun AiCareCloseButton(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onDismiss, modifier = modifier.size(48.dp)
        .semantics { contentDescription = "AI 안내 닫기" }, contentPadding = PaddingValues(0.dp)) {
        MoaIcon(MoaIconKind.CLOSE, Modifier.size(24.dp), Secondary, outline = true)
    }
}

@Composable
internal fun AiCareConfirmFooter(onDismiss: () -> Unit, speaking: Boolean, caption: String = "오늘도 안전운전하세요") {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Box(Modifier.fillMaxWidth().height(.7.dp).background(Line))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val stacked = maxWidth < 330.dp || LocalDensity.current.fontScale > 1.2f
            val label = if (speaking) "모아가 안내하고 있어요" else caption
            val confirm: @Composable (Modifier) -> Unit = { buttonModifier ->
                Button(onClick = onDismiss, modifier = buttonModifier.heightIn(min = 48.dp),
                    shape = RoundedCornerShape(50), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Blue, contentColor = OnBlue)) {
                    Text("확인했어요", fontSize = 16.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            if (stacked) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(label, color = Secondary, fontSize = 13.sp, lineHeight = 20.sp)
                confirm(Modifier.fillMaxWidth())
            } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Text(label, Modifier.weight(1f), color = Secondary, fontSize = 13.sp, lineHeight = 20.sp)
                confirm(Modifier.widthIn(min = 132.dp))
            }
        }
    }
}
