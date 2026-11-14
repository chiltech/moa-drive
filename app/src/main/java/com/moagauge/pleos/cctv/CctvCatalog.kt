package com.moagauge.pleos.cctv

import com.moagauge.pleos.assist.distanceMeters
import com.moagauge.pleos.navigation.GeoPoint
import org.w3c.dom.Element
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.SAXParseException
import org.xml.sax.helpers.DefaultHandler
import java.io.StringReader
import java.net.URI
import java.security.MessageDigest
import javax.xml.parsers.DocumentBuilderFactory

object CctvCatalog {
    private const val MAX_XML_CHARS = 2_000_000
    private val declarations = Regex("<!DOCTYPE|<!ENTITY", RegexOption.IGNORE_CASE)

    fun isSafeStreamUrl(value: String): Boolean = try {
        val uri = URI(value)
        uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank() &&
            uri.rawUserInfo == null && (uri.port == -1 || uri.port in 1..65535) &&
            value.none { it.isWhitespace() || it.code < 32 }
    } catch (_: Exception) { false }

    fun nearby(cameras: List<CctvCamera>, origin: GeoPoint): List<CctvCamera> = cameras
        .filter { it.id.isNotBlank() && it.name.isNotBlank() }
        .distinctBy { it.id }
        .map { camera -> camera.copy(streamUrl = camera.streamUrl?.takeIf(::isSafeStreamUrl)) }
        .map { it to distanceMeters(origin, it.point) }
        .filter { it.second <= 10_000.0 + 0.000001 }
        .sortedWith(compareBy<Pair<CctvCamera, Double>> { it.second }.thenBy { it.first.id })
        .take(100).map { it.first }

    /** ITS' documented XML response. No DTD/entities, network entity resolution or unbounded documents. */
    fun parseXml(xml: String, origin: GeoPoint, httpStatus: Int = 200): List<CctvCamera> {
        if (xml.length > MAX_XML_CHARS || declarations.containsMatchIn(xml)) throw CctvFailure()
        try {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = false
                isExpandEntityReferences = false
                // Android and desktop parsers expose different feature sets; declaration preflight and the
                // entity resolver below remain mandatory even when a parser does not implement a feature.
                listOf("http://apache.org/xml/features/disallow-doctype-decl" to true,
                    "http://xml.org/sax/features/external-general-entities" to false,
                    "http://xml.org/sax/features/external-parameter-entities" to false,
                    "http://apache.org/xml/features/nonvalidating/load-external-dtd" to false).forEach { (feature, enabled) ->
                    try { setFeature(feature, enabled) } catch (_: Exception) { }
                }
            }
            val builder = factory.newDocumentBuilder()
            builder.setEntityResolver { _, _ -> throw SAXException("External entities are disabled") }
            builder.setErrorHandler(object : DefaultHandler() {
                override fun error(e: SAXParseException) { throw e }
                override fun fatalError(e: SAXParseException) { throw e }
            })
            val document = builder.parse(InputSource(StringReader(xml.removePrefix("\uFEFF"))))
            if (document.documentElement.tagName != "response") throw CctvFailure()
            val header = document.documentElement.getElementsByTagName("header").item(0) as? Element
            if (header?.text("resultCode") == "4001") {
                throw CctvFailure(CctvFailureKind.MONTHLY_QUOTA_EXCEEDED)
            }
            // ITS returns its quota envelope with HTTP 401. Parse that safe code before
            // rejecting HTTP failures; never show the remote resultMsg or request URL.
            if (httpStatus != 200) throw CctvFailure()
            val count = document.getElementsByTagName("datacount").item(0)?.textContent?.trim()?.toIntOrNull()
                ?: throw CctvFailure()
            if (count !in 0..5000) throw CctvFailure()
            val nodes = document.getElementsByTagName("data")
            if (nodes.length != count) throw CctvFailure()
            val cameras = (0 until nodes.length).mapNotNull { index ->
                val element = nodes.item(index) as? Element ?: return@mapNotNull null
                val name = element.text("cctvname").take(120)
                val latitude = element.text("coordy").toDoubleOrNull()
                val longitude = element.text("coordx").toDoubleOrNull()
                if (name.isBlank() || latitude == null || longitude == null || !latitude.isFinite() || !longitude.isFinite()
                    || latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return@mapNotNull null
                val identity = "${element.text("roadsectionid")}|$name|$latitude|$longitude"
                val id = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
                    .take(12).joinToString("") { "%02x".format(it.toInt() and 0xff) }
                CctvCamera("its-$id", name, GeoPoint(latitude, longitude),
                    element.text("cctvurl").takeIf { it.isNotBlank() && isSafeStreamUrl(it) })
            }
            return nearby(cameras, origin)
        } catch (error: CctvFailure) { throw error }
        catch (_: Exception) { throw CctvFailure() }
    }

    private fun Element.text(name: String) = getElementsByTagName(name).item(0)?.textContent?.trim().orEmpty()
}
