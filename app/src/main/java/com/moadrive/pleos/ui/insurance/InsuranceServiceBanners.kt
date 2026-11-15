package com.moadrive.pleos.ui.insurance

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import com.moadrive.pleos.ui.common.SoftSurface as Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moadrive.pleos.R
import com.moadrive.pleos.insurance.InsuranceRoadsideUsage
import com.moadrive.pleos.insurance.RoadsideService
import com.moadrive.pleos.ui.*
import com.moadrive.pleos.ui.common.SupportArt
import com.moadrive.pleos.ui.common.SupportArtwork

/** The four assistance banners share the selected policy's existing request and quota flow. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun InsuranceServiceBanners(
    enabled: Boolean,
    usage: InsuranceRoadsideUsage?,
    onTemporaryDriver: () -> Unit,
    temporaryDriverEnabled: Boolean,
    onSelect: (RoadsideService) -> Unit,
) {
    val canRequest = enabled && usage?.remaining != 0
    val darkArt = if (IsDarkTheme) ImageBitmap.imageResource(R.drawable.insurance_service_illustrations) else null
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Column(Modifier.align(Alignment.CenterVertically), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("어떤 도움이 필요하세요?", color = Ink, fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium)
                Text("모의 요청", color = Blue, fontSize = 13.sp, lineHeight = 17.sp)
            }
            OutlinedButton(onClick = onTemporaryDriver, enabled = temporaryDriverEnabled,
                modifier = Modifier.heightIn(min = 48.dp).align(Alignment.CenterVertically),
                shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, Line),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
                Text("임시 운전자 등록  ›", fontSize = 14.sp)
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val fontScale = LocalDensity.current.fontScale
            val columns = if (maxWidth < 420.dp || fontScale > 1.3f) 1 else 2
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                RoadsideService.entries.chunked(columns).forEach { services ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        services.forEach { service ->
                            InsuranceServiceBanner(service, canRequest, usage, darkArt,
                                modifier = Modifier.weight(1f), onClick = { if (canRequest) onSelect(service) })
                        }
                        repeat(columns - services.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        usage?.let { SharedServiceUsageCard(it) }
    }
}

@Composable
private fun InsuranceServiceBanner(
    service: RoadsideService,
    enabled: Boolean,
    usage: InsuranceRoadsideUsage?,
    darkArt: ImageBitmap?,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val title = when (service) {
        RoadsideService.BATTERY -> "배터리 충전"
        RoadsideService.TIRE -> "타이어 펑크"
        else -> service.title
    }
    val artwork = when (service) {
        RoadsideService.ROADSIDE -> SupportArt.ASSISTANCE
        RoadsideService.BATTERY -> SupportArt.BATTERY
        RoadsideService.TOW -> SupportArt.TOWING
        RoadsideService.TIRE -> SupportArt.TIRE
    }
    Surface(onClick = onClick, enabled = enabled, modifier = modifier,
        shape = RoundedCornerShape(20.dp), color = CardColor,
        border = null, shadowElevation = CardShadowElevation) {
        Row(Modifier.fillMaxWidth().heightIn(min = 74.dp).padding(horizontal = 15.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, color = Ink, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium)
                Text(usage?.let { "사용 ${it.usedFor(service)}회" } ?: service.description,
                    color = Secondary, fontSize = 13.sp, lineHeight = 18.sp)
            }
            val artModifier = Modifier.size(52.dp).graphicsLayer { alpha = if (enabled) 1f else .5f }
            if (darkArt == null) {
                SupportArtwork(artwork, artModifier)
            } else {
                DarkServiceArtwork(service, darkArt, artModifier)
            }
            Text("›", color = Secondary, fontSize = 23.sp, modifier = Modifier.clearAndSetSemantics {})
        }
    }
}

/** Transparent originals on dark surfaces, where the light mockup's paper edges would show. */
@Composable
private fun DarkServiceArtwork(service: RoadsideService, sheet: ImageBitmap, modifier: Modifier) {
    val painter = remember(service, sheet) {
        // Alpha bounds measured in each source quadrant at the original 2048px size.
        val bounds = when (service) {
            RoadsideService.ROADSIDE -> IntRect(228, 258, 935, 834)
            RoadsideService.BATTERY -> IntRect(1257, 246, 1714, 855)
            RoadsideService.TOW -> IntRect(289, 1086, 862, 1775)
            RoadsideService.TIRE -> IntRect(1223, 1168, 1801, 1796)
        }
        BitmapPainter(
            sheet,
            srcOffset = IntOffset(bounds.left * sheet.width / 2048, bounds.top * sheet.height / 2048),
            srcSize = IntSize(bounds.width * sheet.width / 2048, bounds.height * sheet.height / 2048),
        )
    }
    Image(painter, contentDescription = null, modifier = modifier, contentScale = ContentScale.Fit)
}

