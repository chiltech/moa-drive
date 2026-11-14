package com.moagauge.pleos.care.news

import java.net.URI
import java.util.Locale

/** Keep originals and publisher links in the reader, without launching external applications. */
internal object CareNewsWebPolicy {
    fun isAllowedWebUrl(url: String): Boolean = parsedHttps(url) != null

    fun displayHost(url: String): String? = parsedHttps(url)?.host?.lowercase(Locale.ROOT)

    private fun parsedHttps(url: String): URI? = runCatching {
        if (url.any { it.isWhitespace() || it.code < 32 }) return null
        URI(url).takeIf {
            it.scheme.equals("https", ignoreCase = true) && !it.host.isNullOrBlank() &&
                it.rawUserInfo == null && (it.port == -1 || it.port in 1..65535)
        }
    }.getOrNull()
}
