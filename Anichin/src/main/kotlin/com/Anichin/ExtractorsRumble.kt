package com.Anichin

import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup

class Rumble : ExtractorApi() {
    override var name = "Rumble"
    override var mainUrl = "https://rumble.com"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val response = app.get(url, referer = referer ?: "$mainUrl/")
        val scriptData = response.document.selectFirst("script:containsData(mp4)")?.data()
            ?.substringAfter("{\"mp4")?.substringBefore("\"evt\":{") ?: return

        val regex = """"url":"(.*?)"|h":(.*?)\}""".toRegex()
        val matches = regex.findAll(scriptData)

        val processedUrls = mutableSetOf<String>()

        for (match in matches) {
            val rawUrl = match.groupValues[1]
            if (rawUrl.isBlank()) continue

            val cleanedUrl = rawUrl.replace("\\/", "/")
            if (!isValidVideoUrl(cleanedUrl)) continue
            if (!processedUrls.add(cleanedUrl)) continue

            // Jika .m3u8, parse dan tampilkan semua kualitas variant di dalamnya
            if (cleanedUrl.contains(".m3u8")) {
                val m3u8Response = app.get(cleanedUrl)
                val variantRegex = Regex("#EXT-X-STREAM-INF:.*RESOLUTION=\\d+x(\\d+).*\\n(.+)")
                val qualities = variantRegex.findAll(m3u8Response.text)
                    .map { it.groupValues[1] }
                    .toSet()
                    .mapNotNull { it.toIntOrNull() }
                    .sorted()
                Log.d("RumbleExtractor", "Link: $cleanedUrl\nKualitas: ${qualities.joinToString(", ")}")
            } else {
                val quality = getQualityFromUrl(cleanedUrl) ?: -1
                Log.d("RumbleExtractor", "Link: $cleanedUrl\nKualitas: $quality")
            }

            // Tetap callback seperti biasa
            val quality = getQualityFromUrl(cleanedUrl) ?: -1
            callback.invoke(
                newExtractorLink(
                    name = when {
                        cleanedUrl.contains(".m3u8") -> "HLS"
                        cleanedUrl.contains(".Faa.mp4") -> "Auto"
                        else -> "$quality"
                    },
                    source = this@Rumble.name,
                    url = cleanedUrl,
                    type = INFER_TYPE
                ) {
                    this.referer = ""
                    this.quality = quality
                }
            )
        }
    }

    private fun isValidVideoUrl(url: String): Boolean {
        return url.endsWith(".mp4") || url.contains(".m3u8")
    }

    private fun getQualityFromUrl(url: String): Int? {
        return when {
            url.contains("1080") -> 1080
            url.contains("720") -> 720
            url.contains("480") -> 480
            url.contains("360") -> 360
            url.contains("240") -> 240
            url.contains("144") -> 144
            url.contains(".Faa.mp4") -> 720       // Fallback utama
            url.contains(".m3u8") -> 720           // Fallback jika .m3u8
            else -> null
        }
    }
}