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
        val links = mutableListOf<Pair<String, Int>>()

        for (match in matches) {
            val rawUrl = match.groupValues[1]
            if (rawUrl.isBlank()) continue

            val cleanedUrl = rawUrl.replace("\\/", "/")

            if (!isValidVideoUrl(cleanedUrl)) continue
            if (!processedUrls.add(cleanedUrl)) continue

            val quality = getQualityFromUrl(cleanedUrl) ?: -1

            Log.d("RumbleExtractor", "Playing Rumble video ($quality) with URL: $cleanedUrl")
            links.add(cleanedUrl to quality)
        }

        links.sortedByDescending { it.second }.forEach { (linkUrl, quality) ->
            callback.invoke(
                newExtractorLink(
                    name = when {
                        linkUrl.contains(".m3u8") -> "HLS"
                        linkUrl.contains(".Faa.mp4") -> "Auto"
                        else -> "$quality"
                    },
                    source = this@Rumble.name,
                    url = linkUrl,
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
            url.contains(".Faa.mp4") -> 720       // Default fallback untuk video utama
            url.contains(".m3u8") -> 720           // Asumsi kualitas jika tidak ditentukan
            else -> null
        }
    }
}