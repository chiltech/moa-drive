package com.moagauge.pleos.insurance

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class InsuranceSessionTest {
    private fun contract(provider: InsuranceProvider) = InsuranceContract(provider, "테스트 자동차보험",
        LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31"), listOf("긴급출동 예시"), ContractSource.DEMO, 1L)
    private class Source : InsuranceContractSource {
        var submissions = 0
        var failure = false
        override suspend fun fetchContract(provider: InsuranceProvider): InsuranceContract {
            if (failure) error("offline")
            return InsuranceContract(provider, "예시", LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31"),
                listOf("긴급출동"), ContractSource.DEMO, 1)
        }
        override suspend fun requestService(provider: InsuranceProvider, service: RoadsideService): InsuranceServiceReceipt {
            submissions++
            return InsuranceServiceReceipt(provider, service, "DEMO-TEST", 2, ContractSource.DEMO)
        }
    }
    @Test fun firstSelectionFetchesContractAndSwitchingDoesNotDisplayOldPolicy() = runTest {
        val saved = mutableListOf<InsuranceProvider>()
        val session = InsuranceSession(Source(), backgroundScope, persistProvider = { saved.add(it) })
        assertTrue(session.state.value.selectingProvider)
        session.connect(InsuranceProvider.DB)
        assertEquals(InsuranceConnectionStatus.CONNECTING, session.state.value.connectionStatus)
        runCurrent()
        assertEquals(InsuranceProvider.DB, session.state.value.contract?.provider)
        session.connect(InsuranceProvider.KB)
        assertNull(session.state.value.contract)
        runCurrent()
        assertEquals(InsuranceProvider.KB, session.state.value.contract?.provider)
        assertEquals(listOf(InsuranceProvider.DB, InsuranceProvider.KB), saved)
    }
    @Test fun lateContractFromPreviousProviderIsIgnoredEvenIfSourceIgnoresCancellation() = runTest {
        val first = CompletableDeferred<InsuranceContract>()
        val source = object : InsuranceContractSource {
            override suspend fun fetchContract(provider: InsuranceProvider) = if (provider == InsuranceProvider.DB)
                withContext(NonCancellable) { first.await() } else contract(provider)
            override suspend fun requestService(provider: InsuranceProvider, service: RoadsideService) = error("unused")
        }
        val session = InsuranceSession(source, backgroundScope)
        session.connect(InsuranceProvider.DB); runCurrent()
        session.connect(InsuranceProvider.KB); runCurrent()
        first.complete(contract(InsuranceProvider.DB)); runCurrent()
        assertEquals(InsuranceProvider.KB, session.state.value.contract?.provider)
    }
    @Test fun mockRequestRequiresConfirmationAndDuplicateSubmitsAreIgnored() = runTest {
        val source = Source(); val session = InsuranceSession(source, backgroundScope)
        session.openRequest(RoadsideService.TOW)
        assertNull(session.state.value.request)
        session.connect(InsuranceProvider.DB); runCurrent()
        session.openRequest(RoadsideService.TOW)
        assertEquals(InsuranceRequestPhase.READY, session.state.value.request?.phase)
        assertEquals(0, source.submissions)
        session.submitRequest(); session.submitRequest(); runCurrent()
        assertEquals(1, source.submissions)
        assertEquals(InsuranceRequestPhase.COMPLETED, session.state.value.request?.phase)
        assertEquals(ContractSource.DEMO, session.state.value.request?.result?.source)
        assertEquals("DEMO-TEST", session.state.value.request?.result?.requestId)
        session.submitRequest(); runCurrent()
        assertEquals(1, source.submissions)
    }
    @Test fun dismissedLateRequestCannotReopenOrLeakIntoAnotherInsurer() = runTest {
        val receipt = CompletableDeferred<InsuranceServiceReceipt>()
        val source = object : InsuranceContractSource {
            override suspend fun fetchContract(provider: InsuranceProvider) = contract(provider)
            override suspend fun requestService(provider: InsuranceProvider, service: RoadsideService) =
                withContext(NonCancellable) { receipt.await() }
        }
        val session = InsuranceSession(source, backgroundScope)
        session.connect(InsuranceProvider.DB); runCurrent()
        session.openRequest(RoadsideService.BATTERY); session.submitRequest(); runCurrent()
        session.dismissRequest(); session.connect(InsuranceProvider.KB); runCurrent()
        receipt.complete(InsuranceServiceReceipt(InsuranceProvider.DB, RoadsideService.BATTERY, "DEMO-LATE", 3, ContractSource.DEMO)); runCurrent()
        assertNull(session.state.value.request)
        assertEquals(InsuranceProvider.KB, session.state.value.provider)
    }
    @Test fun connectionFailureHasRetryAndPickerCancelKeepsSelectedProvider() = runTest {
        val source = Source().apply { failure = true }; val session = InsuranceSession(source, backgroundScope)
        session.connect(InsuranceProvider.DB); runCurrent()
        assertEquals(InsuranceConnectionStatus.FAILED, session.state.value.connectionStatus)
        assertNull(session.state.value.contract)
        source.failure = false; session.retryConnection(); runCurrent()
        assertEquals(InsuranceConnectionStatus.CONNECTED, session.state.value.connectionStatus)
        session.showProviderPicker(); assertTrue(session.state.value.selectingProvider)
        session.cancelProviderPicker(); assertFalse(session.state.value.selectingProvider)
        assertEquals(InsuranceProvider.DB, session.state.value.provider)
    }
    @Test fun demoSourceReturnsOnlyExampleContractsAndIdentifiableMockReceipts() = runTest {
        val source = DemoInsuranceContractSource(today = { LocalDate.parse("2026-11-04") }, nowMs = { 1234L })
        InsuranceProvider.entries.forEach { provider ->
            val result = source.fetchContract(provider)
            assertEquals(provider, result.provider)
            assertEquals(ContractSource.DEMO, result.source)
            assertTrue(result.startDate.isBefore(result.endDate))
            assertFalse(result.endorsements.isEmpty())
            val receipt = source.requestService(provider, RoadsideService.TOW)
            assertTrue(receipt.requestId.startsWith("DEMO-"))
            assertEquals(ContractSource.DEMO, receipt.source)
            assertEquals(provider, receipt.provider)
            assertEquals(RoadsideService.TOW, receipt.service)
        }
    }

    @Test fun restoredSelectionFetchesFreshContractAndRejectsWrongProviderOrPeriod() = runTest {
        var response = contract(InsuranceProvider.KB)
        val source = object : InsuranceContractSource {
            override suspend fun fetchContract(provider: InsuranceProvider) = response
            override suspend fun requestService(provider: InsuranceProvider, service: RoadsideService) = error("unused")
        }
        val session = InsuranceSession(source, backgroundScope, initialProvider = InsuranceProvider.DB)
        assertEquals(InsuranceConnectionStatus.CONNECTING, session.state.value.connectionStatus)
        assertNull(session.state.value.contract)
        runCurrent()
        assertEquals(InsuranceConnectionStatus.FAILED, session.state.value.connectionStatus)
        response = contract(InsuranceProvider.DB).copy(endDate = LocalDate.parse("2025-01-01"))
        session.retryConnection(); runCurrent()
        assertEquals(InsuranceConnectionStatus.FAILED, session.state.value.connectionStatus)
        assertNull(session.state.value.contract)
        response = contract(InsuranceProvider.DB)
        session.retryConnection(); runCurrent()
        assertEquals(response, session.state.value.contract)
    }

    @Test fun mismatchedReceiptFailsWithoutResultAndCanRetry() = runTest {
        var wrongProvider = true
        val source = object : InsuranceContractSource {
            override suspend fun fetchContract(provider: InsuranceProvider) = contract(provider).copy(roadsideUsage = InsuranceRoadsideUsage(6))
            override suspend fun requestService(provider: InsuranceProvider, service: RoadsideService) =
                InsuranceServiceReceipt(if (wrongProvider) InsuranceProvider.KB else provider, service,
                    "DEMO-RETRY", 2, ContractSource.DEMO)
        }
        val session = InsuranceSession(source, backgroundScope, initialProvider = InsuranceProvider.DB)
        runCurrent(); session.openRequest(RoadsideService.TIRE); session.submitRequest(); runCurrent()
        assertEquals(InsuranceRequestPhase.FAILED, session.state.value.request?.phase)
        assertNull(session.state.value.request?.result)
        assertEquals(0, session.state.value.contract!!.roadsideUsage!!.used)
        wrongProvider = false
        session.submitRequest(); runCurrent()
        assertEquals(InsuranceRequestPhase.COMPLETED, session.state.value.request?.phase)
        assertEquals(InsuranceProvider.DB, session.state.value.request?.result?.provider)
        assertEquals(1, session.state.value.contract!!.roadsideUsage!!.used)
    }

    @Test fun completedReceiptConsumesOneSharedUseAndRestoresByContractPeriod() = runTest {
        var year = 2026
        val saved = mutableMapOf<String, InsuranceRoadsideUsage>()
        val source = object : InsuranceContractSource {
            override suspend fun fetchContract(provider: InsuranceProvider) = contract(provider).copy(
                startDate = LocalDate.of(year, 1, 1), endDate = LocalDate.of(year, 12, 31),
                roadsideUsage = InsuranceRoadsideUsage(6, mapOf(RoadsideService.ROADSIDE to 1, RoadsideService.BATTERY to 1)))
            override suspend fun requestService(provider: InsuranceProvider, service: RoadsideService) =
                InsuranceServiceReceipt(provider, service, "DEMO-ONCE", 2, ContractSource.DEMO)
        }
        fun session() = InsuranceSession(source, backgroundScope, initialProvider = InsuranceProvider.DB,
            restoreUsage = { saved[it.usageStorageKey()] },
            persistUsage = { saved[it.usageStorageKey()] = it.roadsideUsage!! })
        val first = session(); runCurrent()
        first.openRequest(RoadsideService.BATTERY)
        assertEquals(2, first.state.value.contract!!.roadsideUsage!!.used)
        first.submitRequest(); first.submitRequest(); runCurrent()
        assertEquals(3, first.state.value.contract!!.roadsideUsage!!.used)
        assertEquals(2, first.state.value.contract!!.roadsideUsage!!.usedFor(RoadsideService.BATTERY))
        first.dismissRequest(); first.openRequest(RoadsideService.BATTERY); first.submitRequest(); runCurrent()
        assertEquals(3, first.state.value.contract!!.roadsideUsage!!.used)
        val restored = session(); runCurrent()
        assertEquals(3, restored.state.value.contract!!.roadsideUsage!!.remaining)
        restored.connect(InsuranceProvider.KB); runCurrent()
        assertEquals(4, restored.state.value.contract!!.roadsideUsage!!.remaining)
        year = 2027; restored.connect(InsuranceProvider.DB); runCurrent()
        assertEquals(4, restored.state.value.contract!!.roadsideUsage!!.remaining)
    }

    @Test fun exhaustedQuotaCannotOpenOrSendAnotherServiceRequest() = runTest {
        var submissions = 0
        val source = object : InsuranceContractSource {
            override suspend fun fetchContract(provider: InsuranceProvider) = contract(provider).copy(
                roadsideUsage = InsuranceRoadsideUsage(1))
            override suspend fun requestService(provider: InsuranceProvider, service: RoadsideService): InsuranceServiceReceipt {
                submissions++
                return InsuranceServiceReceipt(provider, service, "DEMO-LAST", 2, ContractSource.DEMO)
            }
        }
        val session = InsuranceSession(source, backgroundScope, initialProvider = InsuranceProvider.DB)
        runCurrent(); session.openRequest(RoadsideService.TIRE); session.submitRequest(); runCurrent()
        assertEquals(0, session.state.value.contract!!.roadsideUsage!!.remaining)
        session.dismissRequest(); session.openRequest(RoadsideService.BATTERY); session.submitRequest(); runCurrent()
        assertNull(session.state.value.request)
        assertEquals(1, submissions)
    }

    @Test fun cancelledLateReceiptNeverConsumesQuota() = runTest {
        val receipt = CompletableDeferred<InsuranceServiceReceipt>()
        val source = object : InsuranceContractSource {
            override suspend fun fetchContract(provider: InsuranceProvider) = contract(provider).copy(roadsideUsage = InsuranceRoadsideUsage(6))
            override suspend fun requestService(provider: InsuranceProvider, service: RoadsideService) =
                withContext(NonCancellable) { receipt.await() }
        }
        val saved = mutableListOf<InsuranceContract>()
        val session = InsuranceSession(source, backgroundScope, initialProvider = InsuranceProvider.DB, persistUsage = { saved.add(it) })
        runCurrent(); session.openRequest(RoadsideService.TOW); session.submitRequest(); runCurrent()
        session.dismissRequest()
        receipt.complete(InsuranceServiceReceipt(InsuranceProvider.DB, RoadsideService.TOW, "DEMO-LATE", 2, ContractSource.DEMO)); runCurrent()
        assertEquals(0, session.state.value.contract!!.roadsideUsage!!.used)
        assertTrue(saved.isEmpty())
    }

    @Test fun insurerCountsRemainAuthoritativeAndStaleLocalCountsAreIgnored() = runTest {
        val source = object : InsuranceContractSource {
            override suspend fun fetchContract(provider: InsuranceProvider) = contract(provider).copy(
                source = ContractSource.INSURER, roadsideUsage = InsuranceRoadsideUsage(6, mapOf(RoadsideService.TIRE to 1)))
            override suspend fun requestService(provider: InsuranceProvider, service: RoadsideService) = error("unused")
        }
        val session = InsuranceSession(source, backgroundScope, initialProvider = InsuranceProvider.DB,
            restoreUsage = { InsuranceRoadsideUsage(6, mapOf(RoadsideService.TOW to 6)) })
        runCurrent()
        assertEquals(5, session.state.value.contract!!.roadsideUsage!!.remaining)
    }

    @Test fun demoContractPeriodStaysStableAcrossDaysAndHasSeparateMileageAndQuota() = runTest {
        val today = LocalDate.parse("2026-11-05")
        val first = DemoInsuranceContractSource(today = { today }).fetchContract(InsuranceProvider.DB)
        val next = DemoInsuranceContractSource(today = { today.plusDays(1) }).fetchContract(InsuranceProvider.DB)
        assertEquals(first.usageStorageKey(), next.usageStorageKey())
        assertEquals(7420.0, first.annualMileageKm!!, 0.0)
        assertEquals(6, first.roadsideUsage!!.totalAllowed)
        assertEquals(2, first.roadsideUsage!!.used)
    }
}
