package com.moadrive.pleos.cctv

import com.moadrive.pleos.navigation.GeoPoint
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI

class CctvCatalogTest {
    private val origin = GeoPoint(0.0, 0.0)
    private fun north(meters: Double) = meters / 6_371_000.0 * 180.0 / PI
    private fun item(name: String, lat: String, lon: String = "0", url: String = "https://cctv.example.org/live.m3u8") =
        "<data><cctvname>$name</cctvname><coordy>$lat</coordy><coordx>$lon</coordx><cctvurl><![CDATA[$url]]></cctvurl></data>"
    private fun xml(vararg items: String) = "<response><datacount>${items.size}</datacount>${items.joinToString("")}</response>"

    @Test fun parsesFiltersDeduplicatesAndSortsActualNearbyCoordinates() {
        val result = CctvCatalog.parseXml(xml(item("Far", north(9_000.0).toString()),
            item("Near", north(100.0).toString()), item("Near", north(100.0).toString()),
            item("Outside", north(10_001.0).toString()), item("Invalid", "NaN"), item("Bad lon", "0", "181")), origin)
        assertEquals(listOf("Near", "Far"), result.map { it.name })
        assertTrue(result.all { !it.isDemo && it.streamUrl?.startsWith("https://") == true })
    }

    @Test fun acceptsExactTenKilometerBoundary() {
        assertEquals(1, CctvCatalog.parseXml(xml(item("Boundary", north(10_000.0).toString())), origin).size)
    }

    @Test fun allowsOnlyHttpsWithoutEmbeddedCredentialsOrInvalidHost() {
        assertTrue(CctvCatalog.isSafeStreamUrl("https://cctv.example.org:9443/live.m3u8?token=a%2Bb"))
        listOf("http://cctv.example.org/live.m3u8", "file:///sdcard/movie.mp4", "javascript:alert(1)",
            "https://user:pass@cctv.example.org/live", "https:///missing-host", "https://bad host/video").forEach {
            assertFalse(it, CctvCatalog.isSafeStreamUrl(it))
        }
        val result = CctvCatalog.parseXml(xml(item("HTTP camera", "0", url = "http://cctv.example.org/live")), origin)
        assertNull(result.single().streamUrl)
    }

    @Test fun rejectsDoctypeEntityAndUnrecognizedResponses() {
        listOf("<!DOCTYPE response [<!ENTITY xxe SYSTEM 'file:///etc/passwd'>]><response><datacount>0</datacount></response>",
            "<response><errorCode>4001</errorCode></response>", "<html>Gateway unavailable</html>").forEach { document ->
            try { CctvCatalog.parseXml(document, origin); fail("Unexpected accepted document") }
            catch (_: CctvFailure) { }
        }
    }

    @Test fun emptyCatalogIsValidAndIdsDoNotDependOnExpiringStreamTokens() {
        assertTrue(CctvCatalog.parseXml(xml(), origin).isEmpty())
        val first = CctvCatalog.parseXml(xml(item("Camera", "0", url = "https://cctv.example.org/live?token=one")), origin).single()
        val second = CctvCatalog.parseXml(xml(item("Camera", "0", url = "https://cctv.example.org/live?token=two")), origin).single()
        assertEquals(first.id, second.id)
    }

    @Test fun recognizesMonthlyQuotaEnvelopeOnUnauthorizedResponse() {
        val response = "<response><header><resultCode>4001</resultCode><resultMsg>Monthly API limit exceeded</resultMsg></header><body/></response>"
        val failure = assertThrows(CctvFailure::class.java) {
            CctvCatalog.parseXml(response, origin, httpStatus = 401)
        }
        assertEquals(CctvFailureKind.MONTHLY_QUOTA_EXCEEDED, failure.kind)
    }

    @Test fun unknownApiErrorAndNonSuccessCatalogDoNotBecomeQuotaOrCameraData() {
        val response = "<response><header><resultCode>9999</resultCode><resultMsg>Authentication failed</resultMsg></header><body/></response>"
        val unknown = assertThrows(CctvFailure::class.java) {
            CctvCatalog.parseXml(response, origin, httpStatus = 401)
        }
        assertEquals(CctvFailureKind.GENERAL, unknown.kind)
        val malformedSuccess = assertThrows(CctvFailure::class.java) {
            CctvCatalog.parseXml(xml(item("Camera", "0")), origin, httpStatus = 503)
        }
        assertEquals(CctvFailureKind.GENERAL, malformedSuccess.kind)
    }
}
