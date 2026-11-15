package com.moadrive.pleos.ui.assist

import com.moadrive.pleos.ui.FloatingShadowElevation
import com.moadrive.pleos.ui.SurfaceColor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import com.moadrive.pleos.ui.common.SoftSurface as Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moadrive.pleos.assist.AiGuideKind
import com.moadrive.pleos.assist.AiGuideMessage
import com.moadrive.pleos.assist.AiBriefingContent
import com.moadrive.pleos.assist.AiBriefingStatus
import com.moadrive.pleos.ui.AccentGreen
import com.moadrive.pleos.ui.AccentOrange
import com.moadrive.pleos.ui.Blue
import com.moadrive.pleos.ui.CardColor
import com.moadrive.pleos.ui.Ink
import com.moadrive.pleos.ui.IsDarkTheme
import com.moadrive.pleos.ui.OrangeInk
import com.moadrive.pleos.ui.OrangeSurface
import com.moadrive.pleos.ui.PaleBlue
import com.moadrive.pleos.ui.Secondary
import com.moadrive.pleos.ui.MoaIcon
import com.moadrive.pleos.ui.MoaIconKind

/** A restrained, face-like companion that stays legible at dashboard icon sizes. */
@Composable
fun AiGuideAvatar(modifier: Modifier = Modifier, alert: Boolean = false, speaking: Boolean = false) {
    val blue = Blue
    val accent = if (alert) AccentOrange else AccentGreen
    val background = if (alert) OrangeSurface else PaleBlue
    Canvas(modifier.semantics { contentDescription = "모아 AI 가이드" }) {
        val u = size.minDimension
        val origin = Offset((size.width - u) / 2f, (size.height - u) / 2f)
        fun at(x: Float, y: Float) = origin + Offset(u * x, u * y)
        drawCircle(background, u * .49f, at(.5f, .5f))
        drawRoundRect(Color.White, at(.14f, .2f), Size(u * .72f, u * .61f), CornerRadius(u * .19f))
        drawRoundRect(blue.copy(alpha = .2f), at(.14f, .2f), Size(u * .72f, u * .61f),
            CornerRadius(u * .19f), style = Stroke(u * .018f))
        drawRoundRect(blue, at(.24f, .34f), Size(u * .52f, u * .25f), CornerRadius(u * .1f))
        drawLine(Color.White, at(.37f, .42f), at(.37f, .49f), u * .045f, StrokeCap.Round)
        drawLine(Color.White, at(.63f, .42f), at(.63f, .49f), u * .045f, StrokeCap.Round)
        if (speaking) {
            drawRoundRect(blue, at(.43f, .65f), Size(u * .14f, u * .08f), CornerRadius(u * .035f))
        } else {
            drawLine(blue, at(.43f, .68f), at(.57f, .68f), u * .035f, StrokeCap.Round)
        }
        drawCircle(accent, u * .09f, at(.83f, .2f))
        drawCircle(Color.White, u * .027f, at(.83f, .2f))
    }
}

/** Guidance content, hosted in a dimmed dialog or the navigation screen's reserved inline strip. */
@Composable
fun AiGuideCard(
    message: AiGuideMessage,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    voiceUnavailable: Boolean = false,
    compact: Boolean = false,
    speaking: Boolean = false,
    inline: Boolean = false,
) {
    val briefing = message.briefing
    if (!compact && !inline && briefing != null) {
        AiBriefingCard(briefing, onDismiss, modifier, voiceUnavailable, speaking)
        return
    }
    val attention = message.kind == AiGuideKind.OVERSPEED || message.kind == AiGuideKind.INCIDENT
    val accent = if (attention) OrangeInk else Blue
    if (!compact && !inline) {
        AiCareDialog(message.title, onDismiss, modifier, speaking,
            footer = { AiCareConfirmFooter(onDismiss, speaking) }) {
            AiCareNotice {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MoaIcon(if (attention) MoaIconKind.SAFETY else MoaIconKind.DRIVE,
                        Modifier.size(42.dp), if (attention) AccentOrange else Blue)
                    Text(when (message.kind) {
                        AiGuideKind.INCIDENT -> "주변 도로 · 사고 주의"
                        AiGuideKind.OVERSPEED -> "주행 안전 · 속도 확인"
                        AiGuideKind.CAMERA, AiGuideKind.SECTION -> "주행 안전 · 단속 안내"
                        else -> "모아의 일상 케어"
                    }, color = accent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
                Text(message.body, color = Ink, fontSize = 21.sp, lineHeight = 32.sp)
            }
            if (attention) Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BriefingCheckIcon(2, Modifier.size(36.dp))
                Text("전방을 살피고 충분한 안전거리를 유지해 주세요.", color = Secondary,
                    fontSize = 16.sp, lineHeight = 25.sp)
            }
            if (voiceUnavailable) Text("음성을 사용할 수 없어 화면으로 안내해요.",
                color = Secondary, fontSize = 12.sp, lineHeight = 18.sp)
        }
        return
    }
    Surface(
        modifier = modifier.widthIn(max = 600.dp).fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        color = CardColor,
        shape = RoundedCornerShape(if (compact) 16.dp else 22.dp),
        shadowElevation = FloatingShadowElevation,
    ) {
        Column(Modifier.padding(horizontal = if (compact) 12.dp else 18.dp, vertical = if (compact) 8.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 7.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AiGuideAvatar(Modifier.size(if (compact) 40.dp else 50.dp), attention, speaking)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("모아 · 일상 케어", color = accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Text(message.title, color = Ink, fontSize = if (compact) 16.sp else 18.sp,
                        lineHeight = if (compact) 21.sp else 24.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                TextButton(onClick = onDismiss, modifier = Modifier.size(48.dp).semantics { contentDescription = "AI 안내 닫기" },
                    contentPadding = PaddingValues(0.dp)) {
                    Text("×", color = Secondary, fontSize = 27.sp)
                }
            }
            Text(message.body, color = Ink, fontSize = if (compact) 14.sp else 16.sp,
                lineHeight = if (compact) 19.sp else 22.sp, maxLines = if (compact) 2 else 6,
                overflow = TextOverflow.Ellipsis)
            if (voiceUnavailable && !compact) Text("음성을 사용할 수 없어 화면으로 안내해요.",
                color = Secondary, fontSize = 12.sp, lineHeight = 17.sp)
        }
    }
}

@Composable
private fun AiBriefingCard(
    briefing: AiBriefingContent,
    onDismiss: () -> Unit,
    modifier: Modifier,
    voiceUnavailable: Boolean,
    speaking: Boolean,
) {
    AiCareDialog(briefing.greeting, onDismiss, modifier, speaking,
        footer = { AiCareConfirmFooter(onDismiss, speaking) }) {
        BriefingWeather(briefing)
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("출발 전 체크", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val stacked = maxWidth < 470.dp || LocalDensity.current.fontScale > 1.2f
                if (stacked) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    briefing.checks.forEachIndexed { index, text ->
                        BriefingCheck(index, text, Modifier.fillMaxWidth())
                    }
                } else Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    briefing.checks.forEachIndexed { index, text ->
                        BriefingCheck(index, text, Modifier.weight(1f))
                    }
                }
            }
        }
        if (voiceUnavailable) Text("음성을 사용할 수 없어 화면으로 안내해요.",
            color = Secondary, fontSize = 12.sp, lineHeight = 18.sp)
    }
}

@Composable
private fun BriefingWeather(briefing: AiBriefingContent) {
    val dark = IsDarkTheme
    val symbol = briefingWeatherSymbol(briefing)
    val accent = if (briefing.status == AiBriefingStatus.CAUTION) AccentOrange else Blue
    val statusLabel = when (briefing.status) {
        AiBriefingStatus.CAUTION -> "주변 날씨 · 주의 예보"
        AiBriefingStatus.ROUTINE -> "주변 날씨 · 예보 확인"
        AiBriefingStatus.UNKNOWN -> "주변 날씨 · 확인 필요"
        AiBriefingStatus.DISABLED -> "출발 전 확인 · 날씨 안내 꺼짐"
    }
    // Weather is never represented by a warm-tinted surface: warning color belongs to the icon.
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp),
        color = if (dark) SurfaceColor else Color(0xFFF7F8FA)) {
        BoxWithConstraints(Modifier.padding(22.dp)) {
            val stacked = maxWidth < 330.dp || LocalDensity.current.fontScale > 1.2f
            val details: @Composable () -> Unit = {
                Text(statusLabel, color = Secondary, fontSize = 13.sp, lineHeight = 20.sp)
                Text(briefing.weatherTitle, color = Ink, fontSize = 24.sp, lineHeight = 33.sp,
                    fontWeight = FontWeight.SemiBold)
                Text(briefing.weatherDetail, color = Secondary, fontSize = 15.sp, lineHeight = 24.sp)
            }
            if (stacked) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    BriefingWeatherIcon(symbol, Modifier.size(58.dp), accent)
                    Column(verticalArrangement = Arrangement.spacedBy(9.dp), content = { details() })
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                    BriefingWeatherIcon(symbol, Modifier.size(68.dp), accent)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp), content = { details() })
                }
            }
        }
    }
}

/** Symbol selection only decorates the already validated structured notice, never speech or sample weather. */
private fun briefingWeatherSymbol(briefing: AiBriefingContent): BriefingWeatherSymbol = when (briefing.status) {
    AiBriefingStatus.UNKNOWN -> BriefingWeatherSymbol.UNKNOWN
    AiBriefingStatus.DISABLED -> BriefingWeatherSymbol.DISABLED
    AiBriefingStatus.ROUTINE -> BriefingWeatherSymbol.REVIEWED
    AiBriefingStatus.CAUTION -> when (briefing.weatherTitle) {
        "뇌우 예보" -> BriefingWeatherSymbol.THUNDER
        "눈·미끄럼 주의" -> BriefingWeatherSymbol.SNOW
        "강수·젖은 노면 주의" -> BriefingWeatherSymbol.RAIN
        "시야 저하 주의" -> BriefingWeatherSymbol.FOG
        "강한 바람 주의" -> BriefingWeatherSymbol.WIND
        "결빙 가능성" -> BriefingWeatherSymbol.ICE
        else -> BriefingWeatherSymbol.CAUTION
    }
}

@Composable
private fun BriefingCheck(index: Int, label: String, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        BriefingCheckIcon(index, Modifier.size(36.dp))
        Text(label, Modifier.weight(1f), color = Ink, fontSize = 14.sp, lineHeight = 22.sp,
            fontWeight = FontWeight.Medium)
    }
}

/** Persistent home entry; its briefing is requested explicitly, never spoken on recomposition. */
@Composable
fun AiGuideHomeCard(
    summary: String?,
    enabled: Boolean,
    browsingAllowed: Boolean,
    onBriefing: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!enabled) return
    Surface(modifier.fillMaxWidth(), color = CardColor, shape = RoundedCornerShape(20.dp),
        shadowElevation = com.moadrive.pleos.ui.CardShadowElevation) {
        BoxWithConstraints(Modifier.padding(14.dp)) {
            val narrow = maxWidth < 430.dp
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AiGuideAvatar(Modifier.size(50.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("오늘도 모아와 함께", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Text("일상 케어 · 탑승 인사와 루틴 안내", color = Blue, fontSize = 12.sp)
                    }
                    if (!narrow) BriefingButton(browsingAllowed, onBriefing)
                }
                Text(summary ?: "출근부터 귀가까지, 필요한 순간에 모아가 챙겨드릴게요.", color = Secondary,
                    fontSize = 14.sp, lineHeight = 21.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (narrow) BriefingButton(browsingAllowed, onBriefing, Modifier.align(Alignment.End))
            }
        }
    }
}

@Composable
private fun BriefingButton(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick, modifier.heightIn(min = 48.dp), enabled = enabled) {
        Text("브리핑 보기", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}
