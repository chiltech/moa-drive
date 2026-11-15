package com.moadrive.pleos.insurance

import java.net.URI
import java.util.Locale

/** Navigation boundary for the insurer's embedded web page. */
object InsuranceWebPolicy {
    fun isAllowedWebUrl(url: String): Boolean = parsedHttps(url) != null

    // Never put authentication tokens, form parameters, or fragments in the app header.
    fun displayHost(url: String): String? = parsedHttps(url)?.host?.lowercase(Locale.ROOT)

    private fun parsedHttps(url: String): URI? = runCatching {
        if (url.any { it.isWhitespace() || it.code < 32 }) return null
        URI(url).takeIf {
            it.scheme.equals("https", ignoreCase = true) && !it.host.isNullOrBlank() &&
                it.rawUserInfo == null && (it.port == -1 || it.port in 1..65535)
        }
    }.getOrNull()
}
