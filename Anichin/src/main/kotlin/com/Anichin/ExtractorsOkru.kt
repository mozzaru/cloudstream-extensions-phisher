package com.Anichin.extractors

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.JsUnpacker
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.getQualityFromName
import kotlin.text.Regex
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser
import java.util.Base64

class OkruExtractor : ExtractorApi() {
    override val name = "Okru"
    override val mainUrl = "https://ok.ru"
    override val requiresReferer: Boolean = true

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink> {
        val links = mutableListOf<ExtractorLink>()
        val document = app.get(url, referer = referer).document
    
        val dataOptionsRaw = document.selectFirst("div[data-module=OKVideo]")?.attr("data-options")
            ?: document.select("script").find { it.data().contains("data-options") }
                ?.data()?.substringAfter("data-options=\"")?.substringBefore("\"")
            ?: return emptyList()
    
        val dataOptions = Parser.unescapeEntities(dataOptionsRaw, true)
    
        val flashvarsRegex = "\"flashvars\":(\\{.*?\\})".toRegex()
        val flashvarsMatch = flashvarsRegex.find(dataOptions) ?: return emptyList()
    
        val flashvarsJson = flashvarsMatch.groupValues[1]
        val flashvarsMap = parseJson<LinkedHashMap<String, Any>>(flashvarsJson)
        val metadataUrl = flashvarsMap["metadata"]?.toString() ?: return emptyList()
    
        val metadataDocument = app.get(metadataUrl, referer = referer).parsedSafe<LinkedHashMap<String, Any>>() ?: return emptyList()
        val videos = metadataDocument["videos"] as? List<LinkedHashMap<String, Any>> ?: return emptyList()
    
        videos.forEach { video ->
            val videoUrl = video["url"]?.toString() ?: return@forEach
            val qualityName = video["name"]?.toString()?.lowercase() ?: "default"
    
            val quality = when (qualityName) {
                "mobile" -> 144
                "lowest" -> 240
                "low" -> 360
                "sd" -> 480
                "hd" -> 720
                "full" -> 1080
                "quad" -> 2000
                "ultra" -> 4000
                else -> Qualities.Unknown.value
            }
    
            links.add(
                newExtractorLink {
                    name = "Okru"
                    source = "Okru"
                    url = videoUrl
                    referer = referer ?: ""
                    quality = quality
                    isM3u8 = false
                }
            )
        }
    
        return links
    }
}
