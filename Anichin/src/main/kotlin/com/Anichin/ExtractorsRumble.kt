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
import com.lagradost.cloudstream3.utils.*
import kotlin.text.Regex
import org.jsoup.Jsoup

class Rumble : ExtractorApi() {
    override var name = "Rumble"
    override var mainUrl = "https://rumble.com"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val response = app.get(url, referer = referer ?: "$mainUrl/")
        val results = mutableListOf<ExtractorLink>()
        val seenQualities = mutableSetOf<String>()

        val playerScript = response.document
            .selectFirst("script:containsData(mp4)")
            ?.data()
            ?.substringAfter("{\"mp4")
            ?.substringBefore("\"evt\":{")
            ?: return null

        val regex = """"url":"(.*?)"|h":(.*?)\}""".toRegex()
        val matches = regex.findAll(playerScript)

        for (match in matches) {
            val rawHref = match.groupValues[1].ifBlank { match.groupValues[2] }
            val href = rawHref.replace("\\/", "/").trim()

            val qualityStr = when {
                href.contains("1080") -> "1080p"
                href.contains("720") -> "720p"
                href.contains("480") -> "480p"
                else -> continue
            }

            if (qualityStr in seenQualities) continue
            seenQualities.add(qualityStr)

            val qualityInt = when (qualityStr) {
                "1080p" -> Qualities.P1080.value
                "720p" -> Qualities.P720.value
                "480p" -> Qualities.P480.value
                else -> Qualities.Unknown.value
            }

            results.add(
                newExtractorLink(
                    "$name $qualityStr", // e.g., "Rumble 1080p"
                    name,
                    href,
                    ExtractorLinkType.M3U8
                ) {
                    this.referer = referer ?: ""
                    this.quality = qualityInt
                }
            )
        }

        return results
    }
}
