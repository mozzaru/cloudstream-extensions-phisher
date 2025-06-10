package com.Anichin.extractors

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.JsUnpacker
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.utils.*
import kotlin.text.Regex
import org.jsoup.Jsoup

private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

data class OkruMetadata(
    @JsonProperty("videos") val videos: List<OkruVideo>?
)

data class OkruVideo(
    @JsonProperty("name") val name: String?,
    @JsonProperty("url") val url: String?
)

open class OkruExtractor : ExtractorApi() {
    override var name = "Okru"
    override var mainUrl = "https://ok.ru"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink> {
        val sources = mutableListOf<ExtractorLink>()

        // Get video ID from any known OK.ru format
        val videoId = when {
            url.contains("okru.link") -> Regex("[?&]t=([0-9]+)").find(url)?.groupValues?.getOrNull(1)
            url.contains("/videoembed/") -> Regex("/videoembed/([0-9]+)").find(url)?.groupValues?.getOrNull(1)
            url.contains("/video/") -> Regex("/video/([0-9]+)").find(url)?.groupValues?.getOrNull(1)
            else -> null
        } ?: return emptyList()

        val okruUrl = "https://ok.ru/video/$videoId"
        val response = app.get(okruUrl, headers = mapOf("User-Agent" to USER_AGENT))
        val document = Jsoup.parse(response.text)

        // Try parsing using data-options first
        val dataOptions = document.selectFirst("div[data-module='OKVideo']")?.attr("data-options")

        if (dataOptions != null && dataOptions.contains("\"metadata\":")) {
            val jsonStart = dataOptions.indexOf("\"metadata\":")
            val jsonEnd = dataOptions.indexOf(",\"jsData\"")
            if (jsonStart != -1 && jsonEnd != -1) {
                val metadataJson = dataOptions.substring(jsonStart + 11, jsonEnd)
                val parsed: OkruMetadata = jacksonObjectMapper().readValue(metadataJson)

                parsed.videos?.forEach { video ->
                    val videoUrl = video.url ?: return@forEach
                    val quality = when {
                        video.name?.contains("1080") == true -> 1080
                        video.name?.contains("720") == true -> 720
                        video.name?.contains("480") == true -> 480
                        video.name?.contains("360") == true -> 360
                        else -> Qualities.Unknown.value
                    }

                    sources.add(
                        newExtractorLink(
                            source = name,
                            name = name,
                            url = videoUrl,
                            type = if (videoUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO,
                            referer = okruUrl,
                            quality = quality,
                            headers = mapOf("User-Agent" to USER_AGENT)
                        )
                    )
                }

                return sources
            }
        }

        // 🔁 Fallback if data-options parsing failed: Try extract m3u8 directly from page
        val fallbackUrls = Regex("\"url\":\"(https:[^\"]+?\\.m3u8)\"").findAll(response.text)
            .mapNotNull { it.groupValues.getOrNull(1)?.replace("\\/", "/") }
            .toList()

        if (fallbackUrls.isNotEmpty()) {
            fallbackUrls.forEach {
                sources.add(
                    newExtractorLink(
                        source = name,
                        name = "$name Fallback",
                        url = it,
                        type = ExtractorLinkType.M3U8,
                        referer = okruUrl,
                        quality = Qualities.Unknown.value,
                        headers = mapOf("User-Agent" to USER_AGENT)
                    )
                )
            }
            return sources
        }

        return emptyList()
    }
}
