package com.moagauge.pleos.insurance

import java.time.LocalDate

enum class InsuranceConnectionStatus { NOT_SELECTED, CONNECTING, PARTNER_REQUIRED, CONNECTED, FAILED }
enum class ContractSource { DEMO, INSURER }

data class InsuranceContract(
    val provider: InsuranceProvider,
    val productName: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val endorsements: List<String>,
    val source: ContractSource,
    val updatedAtMs: Long,
    val annualMileageKm: Double? = null,
    val roadsideUsage: InsuranceRoadsideUsage? = null,
)

enum class InsuranceRequestPhase { READY, SUBMITTING, COMPLETED, FAILED }

data class InsuranceServiceReceipt(
    val provider: InsuranceProvider,
    val service: RoadsideService,
    val requestId: String,
    val requestedAtMs: Long,
    val source: ContractSource,
)

data class InsuranceRequestState(
    val provider: InsuranceProvider,
    val service: RoadsideService,
    val phase: InsuranceRequestPhase,
    val result: InsuranceServiceReceipt? = null,
    val message: String? = null,
)

data class InsuranceUiState(
    val provider: InsuranceProvider? = null,
    val selectingProvider: Boolean = true,
    val connectionStatus: InsuranceConnectionStatus = InsuranceConnectionStatus.NOT_SELECTED,
    val contract: InsuranceContract? = null,
    val message: String? = null,
    val request: InsuranceRequestState? = null,
)
