package com.moagauge.pleos.ui.insurance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moagauge.pleos.insurance.InsuranceAnnualMileage
import com.moagauge.pleos.insurance.InsuranceRoadsideUsage
import com.moagauge.pleos.insurance.MileageDiscountRange
import com.moagauge.pleos.ui.*
import com.moagauge.pleos.ui.common.DetailCard
import com.moagauge.pleos.ui.common.RoundedMetricBar
import java.util.Locale

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun AnnualMileageCard(distanceKm: Double?) {
    val mileage = InsuranceAnnualMileage.from(distanceKm)
    val threshold = formatDistance(InsuranceAnnualMileage.THRESHOLD_KM)
    val accent = when (mileage.range) {
        MileageDiscountRange.WITHIN_LIMIT -> Blue
        MileageDiscountRange.OVER_LIMIT -> OrangeInk
        MileageDiscountRange.UNKNOWN -> Secondary
    }
    DetailCard {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("연간 마일리지 할인", Modifier.weight(1f), color = Ink,
                    fontSize = 18.sp, lineHeight = 23.sp, fontWeight = FontWeight.Medium)
                Text(when (mileage.range) {
                    MileageDiscountRange.WITHIN_LIMIT -> "기준 이내"
                    MileageDiscountRange.OVER_LIMIT -> "기준 초과"
                    MileageDiscountRange.UNKNOWN -> "확인 필요"
                }, Modifier.background(accent.copy(alpha = .10f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 9.dp, vertical = 3.dp), color = accent, fontSize = 13.sp,
                    lineHeight = 18.sp, fontWeight = FontWeight.Medium)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(mileage.distanceKm?.let { "${formatDistance(it)} km" } ?: "거리 확인 전",
                    Modifier.align(Alignment.CenterVertically), color = Ink, lineHeight = 37.sp,
                    fontSize = if (mileage.distanceKm == null) 22.sp else 32.sp, fontWeight = FontWeight.SemiBold)
                Text("보험기간 누적 · 기준 $threshold km", Modifier.align(Alignment.CenterVertically),
                    color = Secondary, fontSize = 13.sp, lineHeight = 18.sp)
            }
            RoundedMetricBar(progress = mileage.progress, modifier = Modifier.fillMaxWidth().height(8.dp),
                color = accent, trackColor = Line)
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("최종 할인은 특약 조건에 따라 달라요.", color = Secondary, fontSize = 13.sp, lineHeight = 18.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    mileage.distanceKm?.let {
                        Text(String.format(Locale.KOREAN, "%.1f%%", it / InsuranceAnnualMileage.THRESHOLD_KM * 100),
                            color = Secondary, fontSize = 13.sp, lineHeight = 18.sp)
                    }
                    Text(when (mileage.range) {
                        MileageDiscountRange.WITHIN_LIMIT -> "${formatDistance(mileage.remainingKm!!)} km 남음"
                        MileageDiscountRange.OVER_LIMIT -> "${formatDistance(mileage.excessKm!!)} km 초과"
                        MileageDiscountRange.UNKNOWN -> "계약의 누적 주행거리 정보가 아직 없어요."
                    }, color = accent, fontSize = 13.sp, lineHeight = 18.sp)
                }
            }
        }
    }
}

@Composable
internal fun SharedServiceUsageCard(usage: InsuranceRoadsideUsage) {
    val accent = if (usage.remaining == 0) OrangeInk else Blue
    DetailCard {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("긴급출동 공통 횟수", color = Ink, fontSize = 18.sp, lineHeight = 23.sp, fontWeight = FontWeight.Medium)
                    Text("사용 ${usage.used}회 / 전체 ${usage.totalAllowed}회", color = Secondary,
                        fontSize = 13.sp, lineHeight = 18.sp)
                }
                Text("${usage.remaining}회 남음", color = accent, fontSize = 25.sp, lineHeight = 31.sp, fontWeight = FontWeight.Medium)
            }
            RoundedMetricBar(progress = if (usage.totalAllowed == 0) 0f else usage.remaining.toFloat() / usage.totalAllowed,
                modifier = Modifier.fillMaxWidth().height(7.dp), color = accent, trackColor = Line)
            Text(if (usage.remaining == 0) "이번 보험기간의 공통 횟수를 모두 사용했어요."
                else "출동·배터리·견인·타이어가 같은 횟수에서 차감돼요.", color = Secondary, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

private fun formatDistance(km: Double): String = String.format(Locale.KOREAN, if (km % 1.0 == 0.0) "%,.0f" else "%,.1f", km)
