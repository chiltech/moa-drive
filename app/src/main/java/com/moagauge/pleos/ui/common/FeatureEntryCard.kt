package com.moagauge.pleos.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import com.moagauge.pleos.ui.common.SoftSurface as Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moagauge.pleos.ui.*

@Composable
fun FeatureEntryCard(
    title: String,
    description: String,
    icon: MoaIconKind,
    accent: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(onClick = onClick, enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 86.dp)
            .semantics { contentDescription = title + " 열기" },
        shape = RoundedCornerShape(20.dp), color = CardColor,
        shadowElevation = CardShadowElevation,
        border = null) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MoaIcon(icon, Modifier.size(38.dp), tint = accent, outline = true)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, color = if (enabled) Ink else Secondary, fontSize = 17.sp,
                    lineHeight = 23.sp, fontWeight = FontWeight.SemiBold)
                Text(description, color = Secondary, fontSize = 12.sp, lineHeight = 18.sp)
            }
            Text("›", color = accent, fontSize = 24.sp)
        }
    }
}
