package com.moadrive.pleos.care.news

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate

enum class CareNewsKind { CHILD, PET }

data class CareNewsItem(
    val title: String,
    val url: String,
    val source: String,
    val publishedDate: String,
    val category: String,
)

class CareNewsException : Exception("공식 소식을 불러오지 못했어요. 잠시 후 다시 확인해 주세요.")

fun careNewsPortal(kind: CareNewsKind): String = when (kind) {
    CareNewsKind.CHILD -> "https://www.moe.go.kr/boardCnts/listRenew.do?boardID=294&m=020402&s=moe"
    CareNewsKind.PET -> "https://www.mafra.go.kr/home/5109/subview.do"
}

/** One on-demand request to the publisher's public title-search form. No articles or images are copied. */
suspend fun fetchCareNews(kind: CareNewsKind): List<CareNewsItem> = withContext(Dispatchers.IO) {
    try {
        withTimeout(20_000L) {
            val endpoint: String
            val fields: Map<String, String>
            when (kind) {
                CareNewsKind.CHILD -> {
                    endpoint = "https://www.moe.go.kr/boardCnts/listRenew.do"
                    fields = mapOf("boardID" to "294", "m" to "020402", "s" to "moe",
                        "searchType" to "S", "searchStr" to "유아", "page" to "1")
                }
                CareNewsKind.PET -> {
                    endpoint = "https://www.mafra.go.kr/bbs/home/792/artclList.do"
                    fields = mapOf("srchColumn" to "sj", "srchWrd" to "반려", "row" to "10", "page" to "1")
                }
            }
            val body = fields.entries.joinToString("&") { (name, value) ->
                "$name=${URLEncoder.encode(value, "UTF-8")}" }.toByteArray(Charsets.UTF_8)
            val connection = URL(endpoint).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 7_000
                connection.readTimeout = 7_000
                connection.instanceFollowRedirects = false
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.useCaches = false
                connection.setFixedLengthStreamingMode(body.size)
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                connection.setRequestProperty("Accept", "text/html")
                connection.outputStream.use { it.write(body) }
                currentCoroutineContext().ensureActive()
                if (connection.responseCode != HttpURLConnection.HTTP_OK ||
                    connection.contentLengthLong > MAX_NEWS_BYTES) throw CareNewsException()
                val output = ByteArrayOutputStream()
                connection.inputStream.use { input ->
                    val buffer = ByteArray(8_192)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (output.size() + count > MAX_NEWS_BYTES) throw CareNewsException()
                        output.write(buffer, 0, count)
                    }
                }
                currentCoroutineContext().ensureActive()
                CareNewsParser.parse(kind, output.toString("UTF-8")).ifEmpty { throw CareNewsException() }
            } finally { connection.disconnect() }
        }
    } catch (_: TimeoutCancellationException) { throw CareNewsException() }
    catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { throw CareNewsException() }
}

private const val MAX_NEWS_BYTES = 2_000_000

/** Strict adapters for the two observed official table layouts, checked on 2026-11-13. */
internal object CareNewsParser {
    private val options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    private val anchor = Regex("<a\\b([^>]*)>(.*?)</a\\s*>", options)
    private val unsafeMarkup = Regex("<\\s*(script|style|iframe|object)\\b", RegexOption.IGNORE_CASE)
    private val educationId = Regex("(?:javascript:)?goView\\(\\s*'294'\\s*,\\s*'([0-9]{1,12})'")
    private val petPath = Regex("/bbs/home/792/[0-9]{1,12}/artclView\\.do")
    private val date = Regex("(?<![0-9])(20[0-9]{2})[-.]([0-9]{2})[-.]([0-9]{2})(?![0-9])")
    private val badge = Regex("<span\\b[^>]*class\\s*=\\s*[\"']new[\"'][^>]*>.*?</span\\s*>", options)
    private val tags = Regex("<[^>]*>")
    private val entities = Regex("&(#x[0-9a-fA-F]{1,6}|#[0-9]{1,7}|[A-Za-z]{2,8});")

    fun parse(kind: CareNewsKind, html: String): List<CareNewsItem> {
        if (html.length > MAX_NEWS_BYTES) throw CareNewsException()
        val keyword = if (kind == CareNewsKind.CHILD) "유아" else "반려"
        return rows(html).mapNotNull { row ->
            if (unsafeMarkup.containsMatchIn(row)) return@mapNotNull null
            val link = anchor.findAll(row).take(20).firstOrNull { match ->
                when (kind) {
                    CareNewsKind.CHILD -> educationId.containsMatchIn(attribute(match.groupValues[1], "onclick") ?: "")
                    CareNewsKind.PET -> safePetLink(attribute(match.groupValues[1], "href")) != null
                }
            } ?: return@mapNotNull null
            val title = plainText(link.groupValues[2]).takeIf { it.length in 3..240 && keyword in it }
                ?: return@mapNotNull null
            // Dates come from the row's date cell, never numbers embedded in its headline.
            val metadata = row.removeRange(link.range)
            val published = date.find(metadata)?.groupValues?.let { parts ->
                runCatching { LocalDate.parse("${parts[1]}-${parts[2]}-${parts[3]}").toString() }.getOrNull()
            } ?: return@mapNotNull null
            when (kind) {
                CareNewsKind.CHILD -> {
                    val id = educationId.find(attribute(link.groupValues[1], "onclick") ?: "")?.groupValues?.get(1)
                        ?: return@mapNotNull null
                    CareNewsItem(title, "https://www.moe.go.kr/boardCnts/viewRenew.do?boardID=294&boardSeq=$id&lev=0&searchType=null&statusYN=W&page=1&s=moe&m=020402&opType=N",
                        "교육부", published, "교육·육아")
                }
                CareNewsKind.PET -> CareNewsItem(title,
                    safePetLink(attribute(link.groupValues[1], "href")) ?: return@mapNotNull null,
                    "농림축산식품부", published, "반려동물")
            }
        }.distinctBy { it.url }.sortedByDescending { it.publishedDate }.take(10).toList()
    }

    private fun rows(html: String): Sequence<String> = sequence {
        var cursor = 0
        var seen = 0
        while (cursor < html.length && seen < 200) {
            val start = html.indexOf("<tr", cursor, ignoreCase = true)
            if (start < 0) break
            cursor = start + 3
            if (cursor >= html.length || (!html[cursor].isWhitespace() && html[cursor] != '>')) continue
            val end = html.indexOf("</tr>", cursor, ignoreCase = true)
            if (end < 0) break
            cursor = end + 5
            seen++
            if (cursor - start <= 65_536) yield(html.substring(start, cursor))
        }
    }

    private fun attribute(attributes: String, name: String): String? =
        Regex("(?:^|\\s)$name\\s*=\\s*([\"'])(.*?)\\1", options).find(attributes)?.groupValues?.get(2)

    private fun safePetLink(value: String?): String? {
        if (value == null || value.any { it.isWhitespace() || it.code < 32 }) return null
        return try {
            val uri = URI(value)
            if (uri.isAbsolute && (uri.scheme != "https" || uri.host != "www.mafra.go.kr" || uri.port != -1)) return null
            if (!uri.isAbsolute && uri.rawAuthority != null) return null
            if (uri.rawUserInfo != null || uri.rawQuery != null || uri.rawFragment != null || !petPath.matches(uri.path ?: "")) return null
            "https://www.mafra.go.kr${uri.path}"
        } catch (_: Exception) { null }
    }

    private fun plainText(value: String): String {
        val text = tags.replace(badge.replace(value, ""), " ")
        return entities.replace(text) { match ->
            when (val entity = match.groupValues[1]) {
                "amp" -> "&"; "lt" -> "<"; "gt" -> ">"; "quot" -> "\""; "apos" -> "'"; "nbsp" -> " "
                "middot" -> "·"; "ndash" -> "–"; "mdash" -> "—"
                "lsquo" -> "‘"; "rsquo" -> "’"; "ldquo" -> "“"; "rdquo" -> "”"
                else -> {
                    val code = if (entity.startsWith("#x")) entity.drop(2).toIntOrNull(16)
                        else entity.removePrefix("#").toIntOrNull()
                    if (code != null && code in 32..0x10FFFF && code !in 0xD800..0xDFFF)
                        String(Character.toChars(code)) else match.value
                }
            }
        }.replace(Regex("[\\s\\u00a0]+"), " ").filterNot { it.isISOControl() }.trim()
    }
}
