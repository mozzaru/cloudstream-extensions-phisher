package com.Anichin.extractors

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
//import com.lagradost.cloudstream3.utils.JsUnpacker
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.SubtitleFile
//import com.lagradost.cloudstream3.utils.M3u8Helper
//import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.utils.*
import kotlin.text.Regex
import org.jsoup.Jsoup

private const val USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

data class OkruMetadata(
    @JsonProperty("videos") val videos: List<OkruVideo>?
)

data class OkruVideo(
    @JsonProperty("name") val name: String?,
    @JsonProperty("url") val url: String?
)

open class OkruExtractor : ExtractorApi() {
    override val name = "Okru"
    override val mainUrl = "https://ok.ru"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val videoId = when {
            url.contains("okru.link") -> Regex("[?&]t=(\\d+)").find(url)?.groupValues?.getOrNull(1)
            url.contains("/videoembed/") -> Regex("/videoembed/(\\d+)").find(url)?.groupValues?.getOrNull(1)
            url.contains("/video/") -> Regex("/video/(\\d+)").find(url)?.groupValues?.getOrNull(1)
            else -> null
        } ?: return

        val okruUrl = "https://ok.ru/video/$videoId"
        val response = app.get(okruUrl, headers = mapOf("User-Agent" to USER_AGENT))
        val document = Jsoup.parse(response.text)

        val dataOptions = document.selectFirst("div[data-module='OKVideo']")?.attr("data-options")

        if (dataOptions != null && dataOptions.contains("\"metadata\":")) {
            val jsonStart = dataOptions.indexOf("\"metadata\":")
            val jsonEnd = dataOptions.indexOf(",\"jsData\"")
            if (jsonStart != -1 && jsonEnd != -1) {
                val metadataJson = dataOptions.substring(jsonStart + 11, jsonEnd)
                val parsed: OkruMetadata = jacksonObjectMapper().readValue(metadataJson)

                parsed.videos?.forEach { video ->
                    val link = video.url ?: return@forEach
                    val quality = when {
                        video.name?.contains("1080") == true -> 1080
                        video.name?.contains("720") == true -> 720
                        video.name?.contains("480") == true -> 480
                        video.name?.contains("360") == true -> 360
                        else -> 0
                    }

                    callback.invoke(
                        ExtractorLink(
                            source = name,
                            name = name,
                            url = link,
                            referer = okruUrl,
                            quality = quality,
                            type = if (link.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO,
                            headers = mapOf("User-Agent" to USER_AGENT)
                        )
                    )
                }
                return
            }
        }

        // fallback to m3u8 regex
        val fallbackUrls = Regex("\"url\":\"(https:[^\"]+?\\.m3u8)\"").findAll(response.text)
            .mapNotNull { it.groupValues.getOrNull(1)?.replace("\\/", "/") }
            .toList()

        fallbackUrls.forEach {
            callback.invoke(
                ExtractorLink(
                    source = name,
                    name = name,
                    url = it,
                    referer = okruUrl,
                    quality = 0,
                    type = ExtractorLinkType.M3U8,
                    headers = mapOf("User-Agent" to USER_AGENT)
                )
            )
        }
    }
}
