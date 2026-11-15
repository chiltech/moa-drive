package com.moadrive.pleos.navigation

data class SuggestedDestinationRequest internal constructor(
    val generation: Long,
    val query: String,
    val startedAtMs: Long,
)

/** Main-thread token and monotonic deadline guard for responses from a suggested destination. */
class SuggestedDestinationRequests(val timeoutMs: Long = 30_000) {
    private var generation = 0L
    var active: SuggestedDestinationRequest? = null
        private set

    fun begin(query: String, nowMs: Long): SuggestedDestinationRequest {
        require(query.isNotBlank())
        return SuggestedDestinationRequest(++generation, query.trim().take(100), nowMs).also { active = it }
    }

    fun isCurrent(request: SuggestedDestinationRequest) = active?.generation == request.generation
    fun accepts(request: SuggestedDestinationRequest, nowMs: Long) =
        isCurrent(request) && nowMs - request.startedAtMs in 0 until timeoutMs

    fun finish(request: SuggestedDestinationRequest) { if (isCurrent(request)) cancel() }
    fun cancel() { ++generation; active = null }
}

/** Return the provider-owned result so native entrance coordinates/IDs stay intact. */
fun chooseSuggestedDestination(query: String, results: List<NavigationDestination>): NavigationDestination? =
    results.firstOrNull { it.name.trim().equals(query.trim(), ignoreCase = true) } ?: results.firstOrNull()
