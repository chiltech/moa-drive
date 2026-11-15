package com.moadrive.pleos.insurance

import java.util.Base64

enum class MileageDiscountRange { UNKNOWN, WITHIN_LIMIT, OVER_LIMIT }

/** Contract-period aggregate supplied by the contract source, never inferred from the odometer. */
data class InsuranceAnnualMileage private constructor(val distanceKm: Double?) {
    val range: MileageDiscountRange get() = when {
        distanceKm == null -> MileageDiscountRange.UNKNOWN
        distanceKm <= THRESHOLD_KM -> MileageDiscountRange.WITHIN_LIMIT
        else -> MileageDiscountRange.OVER_LIMIT
    }
    val progress: Float get() = ((distanceKm ?: 0.0) / THRESHOLD_KM).coerceIn(0.0, 1.0).toFloat()
    val remainingKm: Double? get() = distanceKm?.let { (THRESHOLD_KM - it).coerceAtLeast(0.0) }
    val excessKm: Double? get() = distanceKm?.let { (it - THRESHOLD_KM).coerceAtLeast(0.0) }

    companion object {
        const val THRESHOLD_KM = 10_000.0
        fun from(distanceKm: Double?) = InsuranceAnnualMileage(distanceKm?.takeIf { it.isFinite() && it >= 0 })
    }
}

/** Every service draws from this one shared allowance; individual counts are a breakdown. */
data class InsuranceRoadsideUsage(
    val totalAllowed: Int,
    val usedByService: Map<RoadsideService, Int> = emptyMap(),
    val appliedReceiptIds: Set<String> = emptySet(),
) {
    init {
        require(totalAllowed >= 0)
        require(usedByService.values.all { it >= 0 })
        require(usedByService.values.sumOf { it.toLong() } <= totalAllowed.toLong())
        require(appliedReceiptIds.all { it.isNotBlank() })
    }

    val used: Int get() = usedByService.values.sum()
    val remaining: Int get() = totalAllowed - used
    fun usedFor(service: RoadsideService): Int = usedByService[service] ?: 0

    /** A repeated receipt is idempotent even if it arrives after the final available use. */
    fun consume(service: RoadsideService, requestId: String): InsuranceRoadsideUsage? = when {
        requestId.isBlank() -> null
        requestId in appliedReceiptIds -> this
        remaining == 0 -> null
        else -> copy(usedByService = usedByService + (service to usedFor(service) + 1),
            appliedReceiptIds = appliedReceiptIds + requestId)
    }
}

fun InsuranceContract.usageStorageKey(): String = "${source.name}:${provider.name}:$startDate:$endDate"

/** Small versioned local snapshot; only the demonstration adapter restores it. */
object InsuranceUsageCodec {
    fun encode(usage: InsuranceRoadsideUsage): String = listOf(
        "1", usage.totalAllowed.toString(),
        RoadsideService.entries.joinToString(",") { usage.usedFor(it).toString() },
        usage.appliedReceiptIds.sorted().joinToString(",") {
            Base64.getUrlEncoder().withoutPadding().encodeToString(it.toByteArray(Charsets.UTF_8))
        },
    ).joinToString("|")

    fun decode(serialized: String?): InsuranceRoadsideUsage? = runCatching {
        require(serialized != null && serialized.length <= 16_384)
        val fields = serialized.split('|')
        require(fields.size == 4 && fields[0] == "1")
        val counts = fields[2].split(',')
        require(counts.size == RoadsideService.entries.size)
        val receipts = if (fields[3].isEmpty()) emptySet() else fields[3].split(',').map {
            String(Base64.getUrlDecoder().decode(it), Charsets.UTF_8)
        }.toSet()
        InsuranceRoadsideUsage(fields[1].toInt(), RoadsideService.entries.mapIndexed { index, service ->
            service to counts[index].toInt()
        }.toMap(), receipts).also { require(receipts.size <= it.used) }
    }.getOrNull()
}
