package com.moadrive.pleos.navigation.kakao

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NativeInitializationFlightTest {
    @Test fun cancelledAwaiterLeavesNativeRequestForNextAwaiter() = runTest {
        val flight = NativeInitializationFlight()
        val callbacks = mutableListOf<(Result<Unit>) -> Unit>()
        val first = backgroundScope.launch { flight.await { callbacks += it } }
        runCurrent()
        assertEquals(1, callbacks.size)
        first.cancel(); runCurrent()
        var secondReady = false
        backgroundScope.launch { flight.await { callbacks += it }; secondReady = true }
        runCurrent()
        assertEquals(1, callbacks.size)
        callbacks[0](Result.success(Unit)); runCurrent()
        assertTrue(secondReady)
    }

    @Test fun timeoutBoundsWaiterWithoutDiscardingUnfinishedNativeRequest() = runTest {
        val flight = NativeInitializationFlight()
        val callbacks = mutableListOf<(Result<Unit>) -> Unit>()
        val first = backgroundScope.async { withTimeoutOrNull(60_000) { flight.await { callbacks += it } } }
        runCurrent()
        advanceTimeBy(60_000); runCurrent()
        assertNull(first.await())
        var ready = false
        backgroundScope.launch { flight.await { callbacks += it }; ready = true }
        runCurrent()
        assertEquals(1, callbacks.size)
        callbacks[0](Result.success(Unit)); runCurrent()
        assertTrue(ready)
    }

    @Test fun concurrentAwaitersShareNativeCompletion() = runTest {
        val flight = NativeInitializationFlight()
        val callbacks = mutableListOf<(Result<Unit>) -> Unit>()
        var readyCount = 0
        repeat(3) { backgroundScope.launch { flight.await { callbacks += it }; readyCount++ } }
        runCurrent()
        assertEquals(1, callbacks.size)
        callbacks[0](Result.success(Unit)); runCurrent()
        assertEquals(3, readyCount)
    }

    @Test fun failureAllowsNewFlightAndDuplicateOldCallbackCannotRetireIt() = runTest {
        val flight = NativeInitializationFlight()
        val callbacks = mutableListOf<(Result<Unit>) -> Unit>()
        val first = backgroundScope.async { runCatching { flight.await { callbacks += it } } }
        runCurrent()
        val failure = IllegalStateException("authentication temporarily unavailable")
        callbacks[0](Result.failure(failure)); runCurrent()
        val actualFailure = first.await().exceptionOrNull()
        assertEquals(IllegalStateException::class.java, actualFailure?.javaClass)
        assertEquals("authentication temporarily unavailable", actualFailure?.message)

        var readyCount = 0
        backgroundScope.launch { flight.await { callbacks += it }; readyCount++ }
        runCurrent()
        assertEquals(2, callbacks.size)
        callbacks[0](Result.success(Unit)); runCurrent()
        backgroundScope.launch { flight.await { callbacks += it }; readyCount++ }
        runCurrent()
        assertEquals(2, callbacks.size)
        assertEquals(0, readyCount)
        callbacks[1](Result.success(Unit)); runCurrent()
        assertEquals(2, readyCount)
    }

    @Test fun synchronousStartFailureDoesNotLeaveAnUnfinishableFlight() = runTest {
        val flight = NativeInitializationFlight()
        val failure = IllegalStateException("could not start native initialization")
        val first = runCatching { flight.await { throw failure } }
        assertEquals(IllegalStateException::class.java, first.exceptionOrNull()?.javaClass)
        assertEquals("could not start native initialization", first.exceptionOrNull()?.message)
        flight.await { it(Result.success(Unit)) }
    }
}
