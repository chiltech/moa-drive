package com.moadrive.pleos.ui.safety

import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moadrive.pleos.navigation.GeoPoint
import com.moadrive.pleos.safety.*
import com.moadrive.pleos.safety.score.DrivingScorePreview
import com.moadrive.pleos.ui.*
import com.moadrive.pleos.ui.common.DetailCard

/** Presentation-only weather. It is never submitted to the weather owner or alert pipeline. */
internal fun sampleSafetyState(nowMs: Long): SafetyState {
    val center = GeoPoint(37.5665, 126.9780)
    val hourMs = 3_600_000L
    val currentHour = nowMs / hourMs * hourMs
    fun reading(timeMs: Long, hour: Int = 0) = WeatherReading(
        timeMs = timeMs,
        weatherCode = if (hour < 3) 61 else 3,
        temperatureC = 16.0 + hour * .4,
        precipitationMm = if (hour < 3) 1.2 else 0.0,
        snowfallCm = 0.0,
        windMps = 3.2,
        gustMps = 5.1,
        visibilityMeters = 8_000.0,
        precipitationProbability = if (hour < 3) 80.0 else 30.0,
    )
    val samples = weatherSamplePoints(center).map { point ->
        WeatherSample(point.label, point.point, reading(nowMs),
            (0 until 6).map { hour -> reading(currentHour + hour * hourMs, hour) })
    }
    return SafetyState(locationReady = true, forecast = AreaForecast(center, nowMs, samples),
        message = "샘플 기상정보예요. 비가 그친 뒤에도 젖은 노면에 주의해 주세요.", nowMs = nowMs)
}

/** No model, stored record identifier or editing controls are available in this sample card. */
@Composable
internal fun SampleSafetyScoreCard() {
    val score = DrivingScorePreview.READY
    DetailCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("내 안전운전 점수", Modifier.weight(1f), color = Ink, fontSize = 21.sp,
                fontWeight = FontWeight.SemiBold)
            Text("샘플", color = OrangeInk, fontSize = 12.sp)
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AnimatedDrivingScoreValue(score.score)
            Text("/ 100점", Modifier.padding(bottom = 9.dp), color = Secondary, fontSize = 17.sp)
        }
        LinearProgressIndicator(progress = score.score / 100f, modifier = Modifier.fillMaxWidth().height(8.dp),
            color = Blue, trackColor = PaleBlue, strokeCap = StrokeCap.Round)
        Text("꾸준한 안전운전 습관을 이어가고 있어요", color = Ink, fontSize = 16.sp,
            fontWeight = FontWeight.Medium)
        Text("샘플 누적 관측 거리 ${score.distanceKm.toInt()} km", color = Secondary, fontSize = 14.sp)
        Text("급가속과 급감속을 줄이고 충분한 차간 거리를 유지해 보세요.",
            color = Secondary, fontSize = 14.sp, lineHeight = 21.sp)
        Text("화면 체험용 점수로 실제 기록이나 보험 할인 판단에 반영되지 않아요.",
            color = Secondary, fontSize = 12.sp, lineHeight = 19.sp)
    }
}
