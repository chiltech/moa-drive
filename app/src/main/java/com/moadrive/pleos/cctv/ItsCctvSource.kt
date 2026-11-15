package com.moadrive.pleos.cctv

import com.moadrive.pleos.navigation.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs
import kotlin.math.cos

/** Official ITS highway catalog only. A provisioned key is required; there is no public-test-key fallback. */
class ItsCctvSource(private val apiKey: String) : CctvSource {
    init { require(apiKey.isNotBlank() && apiKey != "test") }

    override suspend fun fetchNearby(origin: GeoPoint): List<CctvCamera> = withContext(Dispatchers.IO) {
        val latitudeDelta = 10_000.0 / 6_371_000.0 * 180.0 / Math.PI
        val longitudeDelta = latitudeDelta / abs(cos(Math.toRadians(origin.latitude))).coerceAtLeast(0.001)
        val parameters = linkedMapOf(
            "apiKey" to apiKey, "type" to "ex", "cctvType" to "4",
            "minX" to (origin.longitude - longitudeDelta).coerceAtLeast(-180.0).toString(),
            "maxX" to (origin.longitude + longitudeDelta).coerceAtMost(180.0).toString(),
            "minY" to (origin.latitude - latitudeDelta).coerceAtLeast(-90.0).toString(),
            "maxY" to (origin.latitude + latitudeDelta).coerceAtMost(90.0).toString(),
            "getType" to "xml",
        )
        val query = parameters.entries.joinToString("&") { (key, value) ->
            "$key=${URLEncoder.encode(value, "UTF-8")}" }
        // Never include this URL or a returned signed stream URL in logs or user-visible errors.
        val connection = URL("https://openapi.its.go.kr:9443/cctvInfo?$query").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/xml, text/xml")
            val status = connection.responseCode
            val response = if (status == HttpURLConnection.HTTP_OK) connection.inputStream
                else connection.errorStream ?: throw CctvFailure()
            val output = ByteArrayOutputStream()
            response.use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > 2_000_000) throw CctvFailure()
                    output.write(buffer, 0, count)
                }
            }
            CctvCatalog.parseXml(output.toString("UTF-8"), origin, httpStatus = status)
        } finally { connection.disconnect() }
    }
}
