package com.moadrive.pleos.navigation.kakao

import kotlinx.coroutines.CompletableDeferred

/** A native callback owns completion; cancelling a UI waiter cannot cancel the SDK request. */
internal class NativeInitializationFlight {
    private val lock = Any()
    private var pending: CompletableDeferred<Unit>? = null

    suspend fun await(start: (complete: (Result<Unit>) -> Unit) -> Unit) {
        val (request, shouldStart) = synchronized(lock) {
            pending?.let { it to false }
                // No parent Job: the native request outlives cancellation or timeout of await().
                ?: (CompletableDeferred<Unit>().also { pending = it } to true)
        }
        if (shouldStart) {
            try {
                start { result -> settle(request, result) }
            } catch (error: Exception) {
                settle(request, Result.failure(error))
            }
        }
        request.await()
    }

    private fun settle(request: CompletableDeferred<Unit>, result: Result<Unit>) {
        val accepted = synchronized(lock) {
            // A repeated callback from an old request must not retire its replacement.
            if (pending !== request) false else {
                pending = null
                true
            }
        }
        if (accepted) result.fold(request::complete, request::completeExceptionally)
    }
}
