package com.moagauge.pleos.passport

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Packaged public geometry; loading does not need a map key or rewrite the visit database. */
object PassportCityAssets {
    const val SOURCE_LABEL = "행정경계 2023.07 · V-World / KnellBalm"
    const val SOURCE_URL = "https://github.com/KnellBalm/kr-admin-geojson/tree/3a859f2a45be8acf8237d9eefd9565f99cc603da"
    @Volatile private var cached: PassportCityAtlas? = null

    suspend fun load(context: Context): PassportCityAtlas = withContext(Dispatchers.IO) {
        cached ?: synchronized(this@PassportCityAssets) {
            cached ?: parse(context.assets.open("passport/korea-municipalities-2023.json").bufferedReader().use { it.readText() })
                .also { cached = it }
        }
    }
    private fun parse(text: String): PassportCityAtlas {
        val rows = JSONObject(text).getJSONArray("cities")
        fun point(row: JSONArray) = CityBoundaryPoint(row.getDouble(1), row.getDouble(0))
        fun ring(row: JSONArray) = (0 until row.length()).map { point(row.getJSONArray(it)) }
        return PassportCityAtlas((0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            val shapes = row.getJSONArray("polygons")
            PassportCity(row.getString("id"), row.getString("name"), row.getString("province"), row.getString("kind"),
                point(row.getJSONArray("center")), (0 until shapes.length()).map { shape ->
                    val rings = shapes.getJSONArray(shape)
                    CityBoundaryPolygon(ring(rings.getJSONArray(0)), (1 until rings.length()).map { ring(rings.getJSONArray(it)) })
                })
        })
    }
}
