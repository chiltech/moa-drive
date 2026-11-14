package com.moagauge.pleos.insurance

import kotlinx.coroutines.delay
import java.time.LocalDate
import java.util.UUID

/** Adapter boundary for a future authenticated insurance partner backend. */
interface InsuranceContractSource {
    suspend fun fetchContract(provider: InsuranceProvider): InsuranceContract
    suspend fun requestService(provider: InsuranceProvider, service: RoadsideService): InsuranceServiceReceipt
}

/** Local demonstration only: never contacts an insurer or dispatches assistance. */
class DemoInsuranceContractSource(
    private val today: () -> LocalDate = LocalDate::now,
    private val nowMs: () -> Long = System::currentTimeMillis,
) : InsuranceContractSource {
    override suspend fun fetchContract(provider: InsuranceProvider): InsuranceContract {
        delay(650)
        // A stable annual renewal date keeps local service usage scoped to the same contract across restarts.
        val current = today()
        val anniversary = LocalDate.of(current.year, 8, 5)
        val start = if (anniversary.isAfter(current)) anniversary.minusYears(1) else anniversary
        return InsuranceContract(
            provider, "${provider.title} 자동차보험", start, start.plusYears(1).minusDays(1),
            listOf("마일리지 할인 특약", "블랙박스 할인 특약", "긴급출동 서비스 특약"),
            ContractSource.DEMO, nowMs(),
            annualMileageKm = 7_420.0,
            roadsideUsage = InsuranceRoadsideUsage(6, mapOf(RoadsideService.ROADSIDE to 1, RoadsideService.BATTERY to 1)),
        )
    }

    override suspend fun requestService(provider: InsuranceProvider, service: RoadsideService): InsuranceServiceReceipt {
        delay(700)
        return InsuranceServiceReceipt(provider, service, "DEMO-${UUID.randomUUID().toString().take(8).uppercase()}",
            nowMs(), ContractSource.DEMO)
    }
}
