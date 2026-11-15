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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moadrive.pleos.navigation.NavigationRoutePreference
import com.moadrive.pleos.ui.*
import com.moadrive.pleos.ui.common.DimmedDialog
import com.moadrive.pleos.ui.common.rememberDialogAction

/** The selected route preference is applied and stored by the navigation model. */
@Composable
fun NavigationRouteOptionsDialog(
    selected: NavigationRoutePreference,
    enabled: Boolean,
    onSelect: (NavigationRoutePreference) -> Unit,
    onDismiss: () -> Unit,
) {
    LaunchedEffect(enabled) { if (!enabled) onDismiss() }
    if (!enabled) return

    DimmedDialog(onDismissRequest = onDismiss) {
        val close = rememberDialogAction(onDismiss)
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val compact = maxWidth < 600.dp || maxHeight < 480.dp
            val showSubtitle = maxHeight >= 400.dp
            val inset = if (compact) 12.dp else 24.dp
            Surface(Modifier.padding(inset).widthIn(max = 600.dp).fillMaxWidth()
                .heightIn(max = minOf((maxHeight - inset * 2).coerceAtLeast(1.dp), 680.dp)),
                shape = RoundedCornerShape(24.dp), color = SurfaceColor) {
                Column(Modifier.padding(if (compact) 16.dp else 24.dp), verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("경로 옵션", color = Ink, fontSize = if (compact) 22.sp else 26.sp, fontWeight = FontWeight.Medium)
                            if (showSubtitle) Text("선택한 옵션은 다음 목적지에도 적용돼요.", color = Secondary, fontSize = 14.sp)
                        }
                        IconButton(onClick = close, modifier = Modifier.size(48.dp)
                            .semantics { contentDescription = "경로 옵션 닫기" }) {
                            MoaIcon(MoaIconKind.CLOSE, Modifier.size(22.dp), Secondary)
                        }
                    }
                    Column(Modifier.fillMaxWidth().weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()).selectableGroup(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        NavigationRoutePreference.entries.forEach { preference ->
                            val active = selected == preference
                            Surface(Modifier.fillMaxWidth().heightIn(min = 76.dp)
                                .selectable(selected = active, enabled = enabled, role = Role.RadioButton,
                                    onClick = { if (enabled) onSelect(preference) }),
                                shape = RoundedCornerShape(16.dp),
                                color = if (active) PaleBlue else CardColor,
                                border = BorderStroke(if (active) 1.5.dp else .5.dp, if (active) Blue else Line)) {
                                Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                    RadioButton(selected = active, onClick = null, enabled = enabled,
                                        colors = RadioButtonDefaults.colors(selectedColor = Blue, unselectedColor = Secondary))
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                        Text(preference.label, color = Ink, fontSize = 19.sp, fontWeight = FontWeight.Medium)
                                        Text(preference.description, color = Secondary, fontSize = 14.sp, lineHeight = 21.sp)
                                    }
                                }
                            }
                        }
                    }
                    Button(onClick = close, modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp).widthIn(min = 110.dp),
                        shape = RoundedCornerShape(24.dp)) { Text("닫기", fontSize = 16.sp) }
                }
            }
        }
    }
}
