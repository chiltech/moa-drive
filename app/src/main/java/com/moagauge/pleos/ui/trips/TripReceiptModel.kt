package com.moagauge.pleos.ui.trips

import com.moagauge.pleos.trips.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

internal data class ReceiptRow(val label: String, val value: String)

/** A snapshot freezes the selected records and assumptions while the save picker is open. */
internal data class TripReceiptSnapshot(
    val title: String,
    val selectionLabel: String,
    val fileName: String,
    val rows: List<ReceiptRow>,
    val notes: List<String>,
)

/** Animate the frozen, rounded display amount without converting its digits to Float or Long. */
internal class ReceiptCostCounter(val finalText: String) {
    private val amount = finalText.takeIf { WonAmount.matches(it) }
        ?.dropLast(1)?.replace(",", "")?.toBigDecimalOrNull()
    private val formatter = NumberFormat.getIntegerInstance(Locale.KOREAN)
    val canAnimate: Boolean = amount != null

    fun textAt(progress: Float): String {
        val target = amount ?: return finalText
        if (!progress.isFinite() || progress >= 1f) return finalText
        if (progress <= 0f) return "0원"
        val value = target.multiply(BigDecimal.valueOf(progress.toDouble())).setScale(0, RoundingMode.DOWN)
        return formatter.format(value) + "원"
    }

    private companion object {
        val WonAmount = Regex("(?:[1-9][0-9]*|[1-9][0-9]{0,2}(?:,[0-9]{3})+)원")
    }
}

internal fun TripReceiptSnapshot.savedState(): List<String> = buildList {
    addAll(listOf("1", title, selectionLabel, fileName, rows.size.toString(), notes.size.toString()))
    rows.forEach { add(it.label); add(it.value) }
    addAll(notes)
}

internal fun restoreReceiptSnapshot(values: List<String>): TripReceiptSnapshot? {
    if (values.size < 6 || values[0] != "1") return null
    val rowCount = values[4].toIntOrNull()?.takeIf { it in 0..10_000 } ?: return null
    val noteCount = values[5].toIntOrNull()?.takeIf { it in 0..100 } ?: return null
    if (values.size != 6 + rowCount * 2 + noteCount) return null
    return TripReceiptSnapshot(values[1], values[2], values[3],
        List(rowCount) { index -> ReceiptRow(values[6 + index * 2], values[7 + index * 2]) },
        values.takeLast(noteCount))
}

internal fun tripReceipt(records: List<TripRecord>, basis: TripCostBasis, selectionLabel: String): TripReceiptSnapshot {
    val summary = TripCostCalculator.calculate(records, basis)
    val duration = records.sumOf { it.durationMs.coerceAtLeast(0) } / 1000
    val energy = records.mapNotNull { it.consumedWh?.takeIf { wh -> wh.isFinite() && wh >= 0.0 } }
    val rows = buildList {
        add(ReceiptRow("저장된 주행", "${records.size}회"))
        add(ReceiptRow("총 기록 거리 · 추정", "${TripCostFormat.decimal(summary.totalDistanceKm)} km"))
        add(ReceiptRow("기록 시간", "${duration / 3600}시간 ${(duration / 60) % 60}분 ${duration % 60}초"))
        add(ReceiptRow("배터리 사용 · ${energy.size}/${records.size}회 측정", "${TripCostFormat.decimal(energy.takeIf { it.isNotEmpty() }?.sum()?.div(1000.0), 2)} kWh"))
        add(ReceiptRow("비교 포함 구간", "${summary.measuredCount}/${summary.recordCount}회 · ${TripCostFormat.decimal(summary.measuredDistanceKm)} km"))
        add(ReceiptRow("비교 구간 전력량", "${TripCostFormat.decimal(summary.measuredEnergyKwh, 2)} kWh"))
        add(ReceiptRow("비교 구간 평균 전비 · 추정", "${TripCostFormat.decimal(summary.efficiencyKmPerKwh)} km/kWh"))
        add(ReceiptRow("기준 유류비", TripCostFormat.won(summary.gasolineCostWon)))
        add(ReceiptRow("예상 전력 비용", TripCostFormat.won(summary.electricityCostWon)))
        add(ReceiptRow(if ((summary.savingsWon ?: 0.0) < 0) "기준 유류비 대비 추가 비용" else "기준 유류비 대비 예상 절감",
            TripCostFormat.won(summary.savingsWon?.let(::abs))))
        records.sortedBy { it.startedAtMs }.forEachIndexed { index, record ->
            val status = when {
                record.status == TripStatus.INTERRUPTED -> "중단"
                record.hasDataGap -> "일부 구간 누락"
                record.isActive -> "진행 중"
                else -> "저장 완료"
            }
            add(ReceiptRow("주행 ${index + 1} · $status", "${receiptTime(record.startedAtMs, record.zoneId)} → " +
                (record.endedAtMs?.let { receiptTime(it, record.zoneId) } ?: "종료 미확인")))
            val distance = record.distanceMeters.takeIf { it.isFinite() && it >= 0.0 }?.div(1000.0)
            val kwh = record.consumedWh?.takeIf { it.isFinite() && it >= 0.0 }?.div(1000.0)
            add(ReceiptRow("거리 / 배터리 사용", "${TripCostFormat.decimal(distance)} km / ${TripCostFormat.decimal(kwh, 2)} kWh" +
                if (TripCostCalculator.isMeasured(record)) "" else " · 비용 비교 제외"))
        }
    }
    return TripReceiptSnapshot(
        title = if (records.size == 1) "주행 영수증" else "하루 주행 영수증",
        selectionLabel = selectionLabel,
        fileName = "moa-trip-${records.firstOrNull()?.startDate ?: "record"}.pdf",
        rows = rows,
        notes = listOf(
            TripCostFormat.basisLabel(basis),
            "직접 설정한 비교값이며 실시간·지역 평균 가격이 아닙니다.",
            "유류비 = 비교 거리 ÷ 기준 연비 × 휘발유 가격. 전력 비용 = 비교 전력량 × 전력 단가. 원 단위 반올림.",
            "누락·중단·진행 중인 기록, 거리 100 m 미만 또는 사용 전력량 50 Wh 미만인 기록은 비교에서 제외합니다.",
            "차량 신호 기반 추정값입니다. 충전 손실·할인·주차료·통행료는 포함하지 않습니다. 출발 날짜 기준으로 모았습니다.",
            "개인 주행 요약이며 결제·세금 증빙용 영수증이 아닙니다.",
        ),
    )
}

private fun receiptTime(atMs: Long, zoneId: String): String = Instant.ofEpochMilli(atMs)
    .atZone(runCatching { ZoneId.of(zoneId) }.getOrDefault(ZoneId.systemDefault()))
    .format(DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm", Locale.KOREAN))
