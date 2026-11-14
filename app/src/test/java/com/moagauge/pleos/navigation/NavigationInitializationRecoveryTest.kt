package com.moagauge.pleos.navigation

import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NavigationInitializationRecoveryTest {
    @Test fun offlineLaunchAuthenticatesOnlyWhenInternetReturns() = runTest {
        var attempts = 0
        val recovery = NavigationInitializationRecovery(backgroundScope, { attempts++ })
        recovery.updateAvailability(visible = true, preciseLocationGranted = true)
        recovery.retry(); runCurrent()
        assertEquals(0, attempts)
        assertFalse(recovery.state.value.initializing)

        recovery.setOnline(true); runCurrent()
        assertEquals(1, attempts)
        assertTrue(recovery.state.value.ready)
        assertTrue(recovery.state.value.online)
    }

    @Test fun transientFailureRetriesWithinBoundedBackoffAndStopsAfterSuccess() = runTest {
        var attempts = 0
        val recovery = NavigationInitializationRecovery(backgroundScope, {
            attempts++
            if (attempts < 3) throw IllegalStateException("network unavailable")
        }, initialOnline = true)
        recovery.updateAvailability(true, true); runCurrent()
        assertEquals(1, attempts)
        advanceTimeBy(999); runCurrent()
        assertEquals(1, attempts)
        advanceTimeBy(1); runCurrent()
        assertEquals(2, attempts)
        advanceTimeBy(3_000); runCurrent()
        assertEquals(3, attempts)
        assertTrue(recovery.state.value.ready)
        assertNull(recovery.state.value.failure)
        advanceTimeBy(60_000); runCurrent()
        assertEquals(3, attempts)
    }

    @Test fun exhaustedRetriesStayStoppedUntilExplicitRetryOrNewConnection() = runTest {
        var attempts = 0
        val recovery = NavigationInitializationRecovery(backgroundScope, {
            attempts++
            throw IllegalStateException("temporary service failure")
        }, initialOnline = true)
        recovery.updateAvailability(true, true); runCurrent()
        advanceTimeBy(60_000); runCurrent()
        assertEquals(4, attempts)
        assertFalse(recovery.state.value.initializing)
        assertNotNull(recovery.state.value.failure)
        repeat(5) { recovery.setOnline(true); recovery.updateAvailability(true, true) }
        advanceTimeBy(60_000); runCurrent()
        assertEquals(4, attempts)

        recovery.retry(); runCurrent()
        assertEquals(5, attempts)
        recovery.setOnline(false)
        recovery.setOnline(true); runCurrent()
        assertEquals(6, attempts)
    }

    @Test fun duplicateSignalsAndRetryTapsCannotStartParallelInitialization() = runTest {
        var attempts = 0
        lateinit var finish: Continuation<Unit>
        val recovery = NavigationInitializationRecovery(backgroundScope, {
            attempts++
            suspendCoroutine { finish = it }
        }, initialOnline = true)
        recovery.updateAvailability(true, true); runCurrent()
        repeat(10) { recovery.setOnline(true); recovery.updateAvailability(true, true); recovery.retry() }
        runCurrent()
        assertEquals(1, attempts)
        assertTrue(recovery.state.value.initializing)
        finish.resume(Unit); runCurrent()
        assertTrue(recovery.state.value.ready)
    }

    @Test fun networkLossCancelsBackoffAndRestorationStartsFreshBudget() = runTest {
        var attempts = 0
        val recovery = NavigationInitializationRecovery(backgroundScope, {
            attempts++
            throw IllegalStateException("network unavailable")
        }, initialOnline = true)
        recovery.updateAvailability(true, true); runCurrent()
        assertEquals(1, attempts)
        recovery.setOnline(false)
        advanceTimeBy(30_000); runCurrent()
        assertEquals(1, attempts)
        assertFalse(recovery.state.value.initializing)
        recovery.setOnline(true); runCurrent()
        assertEquals(2, attempts)
        advanceTimeBy(1_000); runCurrent()
        assertEquals(3, attempts)
    }

    @Test fun backgroundOrMissingPermissionBlocksAutomaticAttempts() = runTest {
        var attempts = 0
        val recovery = NavigationInitializationRecovery(backgroundScope, {
            attempts++
            throw IllegalStateException("service unavailable")
        }, initialOnline = true)
        recovery.updateAvailability(false, true); recovery.retry(); runCurrent()
        recovery.updateAvailability(true, false); recovery.retry(); runCurrent()
        assertEquals(0, attempts)
        recovery.updateAvailability(true, true); runCurrent()
        assertEquals(1, attempts)
        recovery.updateAvailability(false, true)
        recovery.setOnline(false); recovery.setOnline(true)
        advanceTimeBy(30_000); runCurrent()
        assertEquals(1, attempts)
        recovery.updateAvailability(true, true); runCurrent()
        assertEquals(2, attempts)
        recovery.updateAvailability(true, false)
        advanceTimeBy(30_000); runCurrent()
        assertEquals(2, attempts)
    }

    @Test fun lateSuccessAfterNetworkLossCannotMarkDisconnectedSdkReady() = runTest {
        lateinit var finish: Continuation<Unit>
        val recovery = NavigationInitializationRecovery(backgroundScope, {
            suspendCoroutine { finish = it }
        }, initialOnline = true)
        recovery.updateAvailability(true, true); runCurrent()
        assertTrue(recovery.state.value.initializing)
        recovery.setOnline(false)
        finish.resume(Unit); runCurrent()
        assertFalse(recovery.state.value.ready)
        assertFalse(recovery.state.value.initializing)
    }

    @Test fun lateSuccessAfterLeavingPageIsIgnoredAndReturnCanInitialize() = runTest {
        lateinit var finishOld: Continuation<Unit>
        var attempts = 0
        val recovery = NavigationInitializationRecovery(backgroundScope, {
            attempts++
            if (attempts == 1) suspendCoroutine { finishOld = it }
        }, initialOnline = true)
        recovery.updateAvailability(true, true); runCurrent()
        assertEquals(1, attempts)
        recovery.updateAvailability(false, true)
        finishOld.resume(Unit); runCurrent()
        assertFalse(recovery.state.value.ready)
        assertFalse(recovery.state.value.initializing)
        recovery.updateAvailability(true, true); runCurrent()
        assertEquals(2, attempts)
        assertTrue(recovery.state.value.ready)
    }

    @Test fun cancelledOwnerRejectsLateInitializationResult() = runTest {
        val owner = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        lateinit var finish: Continuation<Unit>
        val recovery = NavigationInitializationRecovery(owner, {
            suspendCoroutine { finish = it }
        }, initialOnline = true)
        recovery.updateAvailability(true, true); runCurrent()
        assertTrue(recovery.state.value.initializing)
        owner.cancel()
        finish.resume(Unit); runCurrent()
        assertFalse(recovery.state.value.ready)
        assertFalse(recovery.state.value.initializing)
    }

    @Test fun lateFailureFromPreviousProviderCannotOverwriteSuccessfulReplacement() = runTest {
        lateinit var finishOld: Continuation<Unit>
        var attempts = 0
        val recovery = NavigationInitializationRecovery(backgroundScope, {
            attempts++
            if (attempts == 1) suspendCoroutine { finishOld = it }
        }, initialOnline = true)
        recovery.updateAvailability(true, true); runCurrent()
        recovery.resetProvider(); runCurrent()
        assertEquals(2, attempts)
        assertTrue(recovery.state.value.ready)
        finishOld.resumeWith(Result.failure(IllegalStateException("old provider failed"))); runCurrent()
        assertTrue(recovery.state.value.ready)
        assertNull(recovery.state.value.failure)
    }

    @Test fun readySdkRemainsReadyAcrossNetworkLossAndNeverReinitializes() = runTest {
        var attempts = 0
        val recovery = NavigationInitializationRecovery(backgroundScope, { attempts++ }, initialOnline = true)
        recovery.updateAvailability(true, true); runCurrent()
        assertTrue(recovery.state.value.ready)
        recovery.setOnline(false); runCurrent()
        assertTrue(recovery.state.value.ready)
        recovery.setOnline(true); recovery.retry(); runCurrent()
        recovery.updateAvailability(false, true)
        recovery.updateAvailability(true, true)
        advanceTimeBy(30_000); runCurrent()
        assertTrue(recovery.state.value.ready)
        assertEquals(1, attempts)
    }
}
