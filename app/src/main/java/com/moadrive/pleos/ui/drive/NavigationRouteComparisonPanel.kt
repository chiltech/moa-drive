package com.moadrive.pleos.ui.drive

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import com.moadrive.pleos.ui.common.SoftSurface as Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moadrive.pleos.navigation.NavigationDestination
import com.moadrive.pleos.navigation.NavigationRouteAlternative
import com.moadrive.pleos.navigation.NavigationRoutePreference
import com.moadrive.pleos.ui.*

/** Each calculated choice is immediately selectable while the remaining choices load. */
@Composable
internal fun NavigationRouteComparisonPanel(
    destination: NavigationDestination,
    alternatives: List<NavigationRouteAlternative>,
    selected: NavigationRoutePreference,
    actionsAllowed: Boolean,
    retryAllowed: Boolean,
    message: String?,
    onSelect: (NavigationRoutePreference) -> Unit,
    onStart: (NavigationRoutePreference) -> Unit,
    onRetry: (NavigationRoutePreference) -> Unit,
    onChangeDestination: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier, shape = RoundedCornerShape(24.dp), color = SurfaceColor,
        border = null, shadowElevation = FloatingShadowElevation) {
        BoxWithConstraints(Modifier.padding(14.dp)) {
            val sideBySide = maxWidth >= 580.dp && LocalDensity.current.fontScale <= 1.45f
            val compactHeader = maxWidth < 520.dp || LocalDensity.current.fontScale > 1.3f
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(destination.name, color = Ink, fontSize = if (compactHeader) 18.sp else 20.sp,
                            fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (!compactHeader) Text("경로를 눌러 지도에서 비교해 보세요", color = Secondary, fontSize = 12.sp)
                    }
                    if (compactHeader) IconButton(onClick = onChangeDestination, enabled = actionsAllowed,
                        modifier = Modifier.size(48.dp).semantics { contentDescription = "목적지 변경" }) {
                        MoaIcon(MoaIconKind.SEARCH, Modifier.size(22.dp), Blue)
                    } else TextButton(onClick = onChangeDestination, enabled = actionsAllowed,
                        modifier = Modifier.heightIn(min = 48.dp)) { Text("목적지 변경", fontSize = 14.sp) }
                    IconButton(onClick = onCancel, modifier = Modifier.size(48.dp)
                        .semantics { contentDescription = "경로 비교 취소" }) {
                        MoaIcon(MoaIconKind.CLOSE, Modifier.size(22.dp), Secondary)
                    }
                }
                Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val candidate: @Composable (NavigationRoutePreference, Modifier) -> Unit = { preference, candidateModifier ->
                        RouteAlternativeCard(
                            alternative = alternatives.firstOrNull { it.preference == preference }
                                ?: NavigationRouteAlternative(preference),
                            destination = destination, selected = selected == preference,
                            actionsAllowed = actionsAllowed, retryAllowed = retryAllowed, compact = !sideBySide,
                            onSelect = { if (actionsAllowed) onSelect(preference) },
                            onStart = { if (actionsAllowed) onStart(preference) },
                            onRetry = { if (retryAllowed) onRetry(preference) },
                            modifier = candidateModifier,
                        )
                    }
                    if (sideBySide) Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).selectableGroup(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NavigationRoutePreference.entries.forEach { preference ->
                            candidate(preference, Modifier.weight(1f).fillMaxHeight())
                        }
                    } else Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        NavigationRoutePreference.entries.forEach { preference -> candidate(preference, Modifier.fillMaxWidth()) }
                    }
                    message?.let { Text(it, color = Secondary, fontSize = 13.sp, lineHeight = 19.sp) }
                    Text("교통 상황에 따라 달라질 수 있어요. 무료도로 우선도 통행료가 발생할 수 있어요.",
                        color = Secondary, fontSize = 11.sp, lineHeight = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun RouteAlternativeCard(
    alternative: NavigationRouteAlternative,
    destination: NavigationDestination,
    selected: Boolean,
    actionsAllowed: Boolean,
    retryAllowed: Boolean,
    compact: Boolean,
    onSelect: () -> Unit,
    onStart: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier,
) {
    val route = alternative.route?.takeIf {
        !alternative.loading && it.preference == alternative.preference && it.destination == destination
    }
    Surface(modifier.heightIn(min = if (compact) 76.dp else 132.dp)
        .selectable(selected = selected, enabled = actionsAllowed, role = Role.RadioButton, onClick = onSelect),
        shape = RoundedCornerShape(16.dp), color = if (selected) PaleBlue else CardColor,
        border = BorderStroke(if (selected) 2.dp else .5.dp, if (selected) Blue else Line)) {
        if (compact) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(alternative.preference.label, color = if (selected) Blue else Ink,
                        fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    when {
                        route != null -> {
                            Text("${(route.durationSeconds + 59) / 60}분 · ${"%.1f".format(route.distanceMeters / 1000.0)} km",
                                color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                            Text(route.tollWon?.let { "통행료 ${"%,d".format(it)}원" } ?: "통행료 정보 없음",
                                color = Secondary, fontSize = 12.sp)
                        }
                        alternative.loading -> Text("경로 계산 중", color = Secondary, fontSize = 14.sp)
                        else -> Text(alternative.message ?: "이 경로를 준비하지 못했어요.",
                            color = Secondary, fontSize = 13.sp, lineHeight = 18.sp)
                    }
                }
                when {
                    route != null -> Button(onClick = onStart, enabled = actionsAllowed,
                        modifier = Modifier.heightIn(min = 48.dp)
                            .semantics { contentDescription = "${alternative.preference.label} 경로 안내 시작" },
                        contentPadding = PaddingValues(horizontal = 14.dp), shape = RoundedCornerShape(12.dp)) {
                        Text("안내", fontSize = 16.sp)
                    }
                    alternative.loading -> CircularProgressIndicator(Modifier.size(20.dp), color = Blue, strokeWidth = 2.dp)
                    else -> TextButton(onClick = onRetry, enabled = retryAllowed,
                        modifier = Modifier.heightIn(min = 48.dp)
                            .semantics { contentDescription = "${alternative.preference.label} 경로 다시 시도" }) {
                        Text("재시도", fontSize = 14.sp)
                    }
                }
            }
        } else {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(alternative.preference.label, Modifier.weight(1f), color = Ink,
                        fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    RadioButton(selected = selected, onClick = null, enabled = actionsAllowed, modifier = Modifier.size(20.dp),
                        colors = RadioButtonDefaults.colors(selectedColor = Blue, unselectedColor = Secondary))
                }
                when {
                    route != null -> {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("${(route.durationSeconds + 59) / 60}분", Modifier.weight(1f), color = Ink,
                                fontSize = 28.sp, fontWeight = FontWeight.Bold)
                            Button(onClick = onStart, enabled = actionsAllowed,
                                modifier = Modifier.widthIn(min = 60.dp).heightIn(min = 48.dp)
                                    .semantics { contentDescription = "${alternative.preference.label} 경로 안내 시작" },
                                shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                                Text("안내", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Text("${"%.1f".format(route.distanceMeters / 1000.0)} km · ${route.tollWon?.let { "통행료 ${"%,d".format(it)}원" } ?: "통행료 정보 없음"}",
                            color = Secondary, fontSize = 13.sp, lineHeight = 19.sp)
                    }
                    alternative.loading -> {
                        Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(Modifier.size(18.dp), color = Blue, strokeWidth = 2.dp)
                            Text("경로 계산 중", color = Secondary, fontSize = 15.sp)
                        }
                        Text("시간·거리·통행료를 확인하고 있어요", color = Secondary, fontSize = 13.sp, lineHeight = 19.sp)
                    }
                    else -> {
                        Text(alternative.message ?: "이 경로를 준비하지 못했어요.", color = Secondary,
                            fontSize = 13.sp, lineHeight = 19.sp)
                        OutlinedButton(onClick = onRetry, enabled = retryAllowed,
                            modifier = Modifier.heightIn(min = 48.dp)
                                .semantics { contentDescription = "${alternative.preference.label} 경로 다시 시도" }) {
                            Text("다시 시도", fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}
