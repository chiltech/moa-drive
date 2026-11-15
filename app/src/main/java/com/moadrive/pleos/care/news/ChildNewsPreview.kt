package com.moadrive.pleos.care.news

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/** Resolve the publisher's first document preview only when the reader is opened. */
suspend fun resolveChildNewsPreviewUrl(articleUrl: String): String {
    if (!ChildNewsPreviewParser.isOfficialArticleUrl(articleUrl)) return articleUrl
    return withContext(Dispatchers.IO) {
        try {
            withTimeoutOrNull(12_000L) {
                val connection = URL(articleUrl).openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = 5_000
                    connection.readTimeout = 5_000
                    connection.instanceFollowRedirects = false
                    connection.useCaches = false
                    connection.setRequestProperty("Accept", "text/html")
                    currentCoroutineContext().ensureActive()
                    if (connection.responseCode != HttpURLConnection.HTTP_OK ||
                        connection.contentLengthLong > MAX_CHILD_ARTICLE_BYTES) return@withTimeoutOrNull articleUrl
                    val output = ByteArrayOutputStream()
                    connection.inputStream.use { input ->
                        val buffer = ByteArray(8_192)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            if (output.size() + count > MAX_CHILD_ARTICLE_BYTES) return@withTimeoutOrNull articleUrl
                            output.write(buffer, 0, count)
                        }
                    }
                    currentCoroutineContext().ensureActive()
                    ChildNewsPreviewParser.previewUrl(articleUrl, output.toString("UTF-8")) ?: articleUrl
                } finally { connection.disconnect() }
            } ?: articleUrl
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            currentCoroutineContext().ensureActive()
            articleUrl
        }
    }
}

private const val MAX_CHILD_ARTICLE_BYTES = 2_000_000

internal object ChildNewsPreviewParser {
    private val htmlOptions = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    private val comments = Regex("<!--.*?(?:-->|$)", htmlOptions)
    private val scriptOrStyle = Regex("<(script|style)\\b[^>]*>.*?(?:</\\1\\s*>|$)", htmlOptions)
    private val anchor = Regex("<a\\b([^>]*)>.*?</a\\s*>", htmlOptions)
    private val href = Regex("(?:^|\\s)href\\s*=\\s*([\"'])(.*?)\\1", htmlOptions)
    private val previewCall = Regex("\\s*javascript:\\s*fncFilePreView\\s*\\(\\s*([\"'])([0-9a-fA-F]{32})\\1\\s*\\)\\s*;?\\s*")

    fun isOfficialArticleUrl(url: String): Boolean = runCatching {
        if (url.any { it.isWhitespace() || it.code < 32 }) return false
        val uri = URI(url)
        if (!uri.scheme.equals("https", ignoreCase = true) ||
            !uri.host.equals("www.moe.go.kr", ignoreCase = true) || uri.port != -1 ||
            uri.rawUserInfo != null || uri.rawPath != "/boardCnts/viewRenew.do") return false
        val fields = uri.rawQuery.orEmpty().split('&').map { it.split('=', limit = 2) }
        val boardIds = fields.filter { it.first() == "boardID" }.map { it.getOrNull(1) }
        val articleIds = fields.filter { it.first() == "boardSeq" }.map { it.getOrNull(1) }
        boardIds == listOf("294") && articleIds.size == 1 &&
            articleIds.single()?.matches(Regex("[0-9]{1,12}")) == true
    }.getOrDefault(false)

    /** Parse the observed link syntax; never execute publisher JavaScript. */
    fun previewUrl(articleUrl: String, html: String): String? {
        if (!isOfficialArticleUrl(articleUrl) || html.length > MAX_CHILD_ARTICLE_BYTES) return null
        val markup = scriptOrStyle.replace(comments.replace(html, ""), "")
        return anchor.findAll(markup).mapNotNull { match ->
            val value = href.find(match.groupValues[1])?.groupValues?.get(2) ?: return@mapNotNull null
            val id = previewCall.matchEntire(value)?.groupValues?.get(2) ?: return@mapNotNull null
            "https://www.moe.go.kr/boardCnts/filePreView.do?seq=$id"
        }.firstOrNull()
    }
}
