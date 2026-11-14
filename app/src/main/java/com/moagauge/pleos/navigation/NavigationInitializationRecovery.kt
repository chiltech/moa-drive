package com.moagauge.pleos.navigation

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal data class NavigationInitializationState(
    val online: Boolean = false,
    val ready: Boolean = false,
    val initializing: Boolean = false,
    val failure: Exception? = null,
)

internal class NavigationInitializationRecovery(
    private val scope: CoroutineScope,
    private val initialize: suspend () -> Unit,
    initialOnline: Boolean = false,
    private val retryDelaysMs: List<Long> = listOf(1_000L, 3_000L, 7_000L),
) {
    private val mutableState = MutableStateFlow(NavigationInitializationState(online = initialOnline))
    val state = mutableState.asStateFlow()
    private var eligible = false
    private var generation = 0L
    private var operation: Job? = null

    fun updateAvailability(visible: Boolean, preciseLocationGranted: Boolean) {
        val next = visible && preciseLocationGranted
        if (eligible == next) return
        eligible = next
        invalidateAttempt()
        retry()
    }

    fun setOnline(online: Boolean) {
        if (state.value.online == online) return
        mutableState.update { it.copy(online = online) }
        invalidateAttempt()
        retry()
    }

    /** Manual retry and genuine availability transitions each receive one bounded budget. */
    fun retry() {
        if (!canInitialize() || operation?.isActive == true) return
        val token = generation
        // Assign before starting: Main.immediate may finish an attempt synchronously.
        val next = scope.launch(start = CoroutineStart.LAZY) {
            try {
                for (attempt in 0..retryDelaysMs.size) {
                    if (!accepts(token) || !currentCoroutineContext().isActive) return@launch
                    mutableState.update { it.copy(initializing = true, failure = null) }
                    try {
                        initialize()
                        if (accepts(token) && currentCoroutineContext().isActive) {
                            mutableState.update { it.copy(ready = true, initializing = false, failure = null) }
                        }
                        return@launch
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        if (!accepts(token) || !currentCoroutineContext().isActive) return@launch
                        val retrying = attempt < retryDelaysMs.size
                        mutableState.update { it.copy(initializing = retrying, failure = error) }
                        if (!retrying) return@launch
                        delay(retryDelaysMs[attempt])
                    }
                }
            } finally {
                if (generation == token) mutableState.update { it.copy(initializing = false) }
            }
        }
        operation = next
        next.start()
    }

    /** Only an explicit provider change retires a ready SDK; connectivity never does. */
    fun resetProvider() {
        invalidateAttempt()
        mutableState.update { it.copy(ready = false, failure = null) }
        retry()
    }

    private fun canInitialize() = eligible && state.value.online && !state.value.ready
    private fun accepts(token: Long) = token == generation && canInitialize()

    private fun invalidateAttempt() {
        generation++
        operation?.cancel()
        operation = null
        mutableState.update { it.copy(initializing = false) }
    }
}
