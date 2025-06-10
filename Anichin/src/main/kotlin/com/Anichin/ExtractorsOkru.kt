package com.Anichin.extractors

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.*
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.JsUnpacker
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import com.lagradost.cloudstream3.SubtitleFile
import kotlin.text.Regex
import org.jsoup.Jsoup

class OkruExtractor : ExtractorApi() {
    override val name = "Okru"
    override val mainUrl = "https://ok.ru"
    override val requiresReferer = false

    private fun fixQuality(quality: String): Int {
        return when (quality.lowercase()) {
            "ultra" -> 2160
            "quad" -> 1440
            "full" -> 1080
            "hd" -> 720
            "sd" -> 480
            "low" -> 360
            "lowest" -> 240
            "mobile" -> 144
            else -> Qualities.Unknown.value
        }
    }

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val response = app.get(url)
        val doc = Jsoup.parse(response.body.string())
        val dataOptions = doc.selectFirst("div[data-options]")?.attr("data-options") ?: return

        // 1. Try to parse as m3u8 or dash
        val hlsUrl = dataOptions.substringAfter("ondemandHls\":\"", "")
            .substringBefore("\"")
            .replace("\\u0026", "&")
        if (hlsUrl.isNotBlank()) {
            M3u8Helper.generateM3u8(name, hlsUrl, url).forEach(callback)
            return
        }

        // 2. Fallback to direct mp4 list
        val videoListRaw = dataOptions.substringAfter("\"videos\":[{\"name\":\"", "")
            .substringBefore("]")

        videoListRaw.split("{\"name\":\"").reversed().forEach {
            val videoUrl = it.substringAfter("url\":\"").substringBefore("\"").replace("\\u0026", "&")
            val qualityStr = it.substringBefore("\"")
            val quality = fixQuality(qualityStr)

            if (videoUrl.startsWith("https://")) {
                callback(
                    newExtractorLink(
                        source = name,
                        name = "$name - $quality",
                        url = videoUrl,
                        type = ExtractorLinkType.VIDEO
                    ) {
                        this.referer = url
                        this.quality = quality
                        this.headers = mapOf("Referer" to url)
                    }
                )
            }
        }
    }
}
