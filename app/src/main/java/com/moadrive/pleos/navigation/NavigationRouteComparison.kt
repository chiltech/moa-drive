package com.moadrive.pleos.navigation

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

data class NavigationRouteAlternative(
    val preference: NavigationRoutePreference,
    val route: NavigationRoute? = null,
    val loading: Boolean = true,
    val message: String? = null,
)

fun pendingRouteAlternatives(): List<NavigationRouteAlternative> =
    NavigationRoutePreference.entries.map { NavigationRouteAlternative(preference = it) }

/** Each preference is an independent provider request; completed options can be shown immediately. */
fun loadRouteAlternatives(
    provider: NavigationProvider,
    origin: GeoPoint,
    destination: NavigationDestination,
    firstPreference: NavigationRoutePreference,
): Flow<NavigationRouteAlternative> = flow {
    val preferences = listOf(firstPreference) + NavigationRoutePreference.entries.filter { it != firstPreference }
    for (preference in preferences) {
        currentCoroutineContext().ensureActive()
        val alternative = try {
            val route = provider.prepareRoute(origin, destination, preference)
            if (route.destination != destination || route.preference != preference || route.provider != provider.id) {
                throw NavigationFailure("요청한 조건의 경로를 확인할 수 없어요.")
            }
            NavigationRouteAlternative(preference = preference, route = route, loading = false)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            NavigationRouteAlternative(
                preference = preference,
                loading = false,
                message = (failure as? NavigationFailure)?.userMessage ?: "경로를 불러오지 못했어요. 다시 시도해 주세요.",
            )
        }
        emit(alternative)
    }
}
