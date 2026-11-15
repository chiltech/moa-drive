package com.moadrive.pleos.passport

import com.moadrive.pleos.navigation.GeoPoint
import com.moadrive.pleos.navigation.NavigationDestination
import com.moadrive.pleos.navigation.NavigationProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

/** Labels describe the search category, not an unverified popularity or quality ranking. */
enum class PassportPlaceKind(val label: String, val keyword: String) {
    ATTRACTION("관광·문화", "관광지"),
    RESTAURANT("음식점", "맛집"),
    PARK("공원·산책", "공원"),
}

data class PassportPlaceSuggestion(val destination: NavigationDestination, val kind: PassportPlaceKind)
data class PassportCityPlaces(val places: List<PassportPlaceSuggestion>, val message: String? = null)

/** Never substitute a civic building or the city centroid for a real recommended place. */
object PassportPlaceRules {
    fun select(city: PassportCity, results: Map<PassportPlaceKind, List<NavigationDestination>>, limit: Int = 3): List<PassportPlaceSuggestion> {
        if (limit <= 0) return emptyList()
        val candidates = results.mapValues { (_, places) -> places.filter { destination ->
            destination.name.isNotBlank() &&
                !Regex("시청|군청|구청|주민센터|행정복지센터").containsMatchIn(destination.name) &&
                city.polygons.any { it.contains(CityBoundaryPoint(destination.point.latitude, destination.point.longitude)) }
        } }
        val selected = mutableListOf<PassportPlaceSuggestion>()
        val usedIds = mutableSetOf<String>()
        val usedNames = mutableSetOf<String>()
        // Interleave categories to offer both a place to explore and somewhere to eat.
        for (index in 0 until (candidates.values.maxOfOrNull { it.size } ?: 0)) {
            PassportPlaceKind.entries.forEach { kind ->
                val destination = candidates[kind]?.getOrNull(index) ?: return@forEach
                val name = destination.name.lowercase(Locale.ROOT).replace(Regex("\\s+"), "")
                if (selected.size < limit && destination.id !in usedIds && name !in usedNames) {
                    usedIds += destination.id; usedNames += name
                    selected += PassportPlaceSuggestion(destination, kind)
                }
            }
            if (selected.size >= limit) break
        }
        return selected
    }
}

/** Bounded read-only POI queries using the same Kakao provider as the app's destination search. */
class PassportPlaceRecommender(private val provider: NavigationProvider) {
    suspend fun load(city: PassportCity, refresh: Boolean = false): PassportCityPlaces {
        val now = System.nanoTime() / 1_000_000
        val key = city.id + "|" + city.title
        if (!refresh) synchronized(cache) {
            cache[key]?.takeIf { now - it.atMs < if (it.result.places.isEmpty()) EMPTY_TTL_MS else TTL_MS }?.result
        }?.let { return it }
        val found = linkedMapOf<PassportPlaceKind, List<NavigationDestination>>()
        var unavailable = false
        val completed = withTimeoutOrNull(50_000) {
            try {
                // Do not call foreground/stop: this query-only owner must not alter native guidance.
                provider.initialize()
                val center = GeoPoint(city.center.latitude, city.center.longitude)
                for (kind in PassportPlaceKind.entries) {
                    if (kind == PassportPlaceKind.PARK && PassportPlaceRules.select(city, found).size >= 3) break
                    try {
                        found[kind] = provider.search("${city.title} ${kind.keyword}", center)
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { unavailable = true }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { unavailable = true }
            true
        } ?: false
        val places = PassportPlaceRules.select(city, found)
        val message = when {
            places.isNotEmpty() && (!completed || unavailable) -> "일부 장소만 확인했어요. 다시 찾을 수 있어요."
            places.size == 1 -> "지금 확인된 장소는 1곳이에요. 더 찾아볼 수 있어요."
            places.isNotEmpty() -> null
            !completed || unavailable -> "장소를 불러오지 못했어요. 연결을 확인하고 다시 찾아주세요."
            else -> "이 도시에서 확인된 추천 장소가 없어요. 다시 찾아주세요."
        }
        return PassportCityPlaces(places, message).also { result ->
            synchronized(cache) {
                cache[key] = Cached(now, result)
                while (cache.size > 24) cache.remove(cache.keys.first())
            }
        }
    }

    private data class Cached(val atMs: Long, val result: PassportCityPlaces)
    private companion object {
        const val TTL_MS = 10 * 60_000L
        const val EMPTY_TTL_MS = 30_000L
        val cache = linkedMapOf<String, Cached>()
    }
}
