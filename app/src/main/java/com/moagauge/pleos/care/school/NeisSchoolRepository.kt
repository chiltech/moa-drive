package com.moagauge.pleos.care.school

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Public school data only. Requests contain a school query or official codes/date, never child identity. */
class NeisSchoolRepository(apiKey: String = "") {
    private val key = apiKey.trim()

    suspend fun searchSchools(query: String, page: Int = 1): SchoolSearchResult {
        val normalized = query.trim()
        if (normalized.length !in 2..60 || normalized.any(Char::isISOControl))
            throw SchoolApiException("학교 이름을 2~60자로 입력해 주세요.")
        if (page !in 1..100 || (key.isEmpty() && page != 1))
            throw SchoolApiException("학교 이름을 더 정확히 입력해 다시 검색해 주세요.")
        val pageSize = if (key.isEmpty()) 5 else 20
        val xml = request("schoolInfo", mapOf("SCHUL_NM" to normalized), page, pageSize)
        return withContext(Dispatchers.Default) {
            currentCoroutineContext().ensureActive()
            NeisSchoolParser.schools(xml, page, pageSize, key.isNotEmpty())
        }
    }

    suspend fun fetchSchoolMeals(school: SchoolInfo, date: LocalDate): SchoolMealResult {
        if (!school.isValidSchool()) throw SchoolApiException("학교를 다시 검색해 선택해 주세요.")
        if (date.year !in 2000..2100) throw SchoolApiException("급식 날짜를 확인해 주세요.")
        val xml = request("mealServiceDietInfo", mapOf("ATPT_OFCDC_SC_CODE" to school.officeCode,
            "SD_SCHUL_CODE" to school.schoolCode, "MLSV_YMD" to date.format(DateTimeFormatter.BASIC_ISO_DATE)), 1, 10)
        return withContext(Dispatchers.Default) {
            currentCoroutineContext().ensureActive()
            NeisSchoolParser.meals(xml, school, date, maxRows = if (key.isEmpty()) 5 else 10)
        }
    }

    private suspend fun request(service: String, fields: Map<String, String>, page: Int, pageSize: Int): String = withContext(Dispatchers.IO) {
        if (!key.matches(Regex("[A-Za-z0-9]{0,128}"))) throw SchoolApiException("학교 정보 인증키 설정을 확인해 주세요.")
        try {
            withTimeout(20_000L) {
                val params = linkedMapOf("Type" to "xml", "pIndex" to page.toString(), "pSize" to pageSize.toString()).apply {
                    if (key.isNotEmpty()) put("KEY", key)
                    putAll(fields)
                }
                val query = params.entries.joinToString("&") { (name, value) -> "$name=${URLEncoder.encode(value, "UTF-8")}" }
                val connection = URL("https://open.neis.go.kr/hub/$service?$query").openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = 7_000
                    connection.readTimeout = 7_000
                    connection.instanceFollowRedirects = false
                    connection.useCaches = false
                    // NEIS selects XML via Type=xml. Its server returns HTTP 500 for
                    // XML-specific Accept values; the wildcard is required (verified live).
                    connection.setRequestProperty("Accept", "*/*")
                    if (connection.responseCode != HttpURLConnection.HTTP_OK || connection.contentLengthLong > MAX_SCHOOL_RESPONSE_BYTES) throw SchoolApiException()
                    val output = ByteArrayOutputStream()
                    connection.inputStream.use { input ->
                        val buffer = ByteArray(8_192)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            if (output.size() + count > MAX_SCHOOL_RESPONSE_BYTES) throw SchoolApiException()
                            output.write(buffer, 0, count)
                        }
                    }
                    currentCoroutineContext().ensureActive()
                    output.toString("UTF-8")
                } finally { connection.disconnect() }
            }
        } catch (_: TimeoutCancellationException) { throw SchoolApiException("학교 정보 조회가 지연되고 있어요. 다시 시도해 주세요.") }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: SchoolApiException) { throw error }
        catch (_: Exception) { throw SchoolApiException() }
    }
}
