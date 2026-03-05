package org.schabi.newpipe.download

import android.net.Uri
import java.util.Locale

private val URL_REGEX = Regex("""https?://[^\s<>"'`]+""", RegexOption.IGNORE_CASE)

private val TRACKING_KEYS = setOf(
    "fbclid",
    "gclid",
    "dclid",
    "igshid",
    "ig_rid",
    "ig_mid",
    "mibextid",
    "mc_cid",
    "mc_eid",
    "ref",
    "ref_src",
    "si",
    "s",
    "spm",
    "trk",
    "tracking_id"
)

fun extractUrl(text: String): String? {
    val rawUrl = URL_REGEX.find(text)?.value ?: return null
    return sanitizeUrl(rawUrl)
}

private fun sanitizeUrl(url: String): String? {
    val cleanedInput = url
        .trim()
        .trimEnd('.', ',', ';', ':', ')', ']', '}', '>', '"', '\'')

    val uri = runCatching { Uri.parse(cleanedInput) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null
    if (scheme != "http" && scheme != "https") return null
    if (uri.host.isNullOrBlank()) return null

    val builder = uri.buildUpon().clearQuery()
    for (queryName in uri.queryParameterNames) {
        if (isTrackingQuery(queryName)) continue
        for (value in uri.getQueryParameters(queryName)) {
            builder.appendQueryParameter(queryName, value)
        }
    }

    return builder.build().toString()
}

private fun isTrackingQuery(queryName: String): Boolean {
    val normalized = queryName.lowercase(Locale.ROOT)
    return normalized.startsWith("utm_") || normalized in TRACKING_KEYS
}

fun isCarouselUrl(url: String): Boolean {
    val lowerUrl = url.lowercase(Locale.ROOT)
    val host = runCatching { Uri.parse(url).host.orEmpty().lowercase(Locale.ROOT) }
        .getOrDefault("")

    if (host.contains("instagram.com") || host.contains("tiktok.com")) {
        return true
    }

    return lowerUrl.contains("img_index=") ||
        lowerUrl.contains("carousel") ||
        lowerUrl.contains("sidecar") ||
        lowerUrl.contains("/album/") ||
        lowerUrl.contains("multi")
}

fun isNoVideoInPostError(error: String?): Boolean {
    return error?.contains("There is no video in this post", ignoreCase = true) == true
}

fun parseYtDlpError(error: String?): String {
    val message = error.orEmpty()
    return when {
        message.contains("There is no video in this post", ignoreCase = true) ->
            "Este post no contiene video descargable"

        message.contains("Unsupported URL", ignoreCase = true) ->
            "Este enlace no es compatible"

        message.contains("HTTP Error 403", ignoreCase = true) ->
            "Acceso denegado por el servidor"

        else ->
            "No se pudo descargar. Intenta de nuevo."
    }
}
