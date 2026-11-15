package com.moadrive.pleos.ui.common

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moadrive.pleos.ui.Ink
import com.moadrive.pleos.ui.OrangeInk
import com.moadrive.pleos.ui.Secondary

/** The same sample entry pattern as Condition Note, shared by the service detail panes. */
@Composable
fun SampleDataHeader(
    title: String,
    description: String,
    preview: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    previewDescription: String = "샘플 데이터 · 실제 기록은 그대로 유지돼요",
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val narrow = maxWidth < 410.dp || LocalDensity.current.fontScale > 1.15f
        val heading: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, color = Ink, fontSize = 24.sp, lineHeight = 31.sp,
                    fontWeight = FontWeight.SemiBold)
                Text(if (preview) previewDescription else description,
                    color = if (preview) OrangeInk else Secondary, fontSize = 13.sp, lineHeight = 20.sp)
            }
        }
        val action: @Composable () -> Unit = {
            Button(onClick = onToggle, enabled = enabled, shape = CircleShape,
                modifier = Modifier.heightIn(min = 48.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
                Text(if (preview) "실제 데이터 보기" else "샘플 데이터 보기", fontSize = 14.sp)
            }
        }
        if (narrow) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                heading()
                Box(Modifier.align(Alignment.End)) { action() }
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { heading() }
                action()
            }
        }
    }
}
