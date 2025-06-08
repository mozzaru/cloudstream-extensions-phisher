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

class Okru : ExtractorApi() {
    override val name = "Okru"
    override val mainUrl = "https://ok.ru"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink> {
        val response = app.get(url, headers = mapOf("Referer" to (referer ?: mainUrl)))
        val body = response.text

        // Perbaiki regex agar tidak error
        val videoListRegex = Regex("\"videos\":\\s*\\{(.*?)\\}")
        val videosJsonPart = videoListRegex.find(body)?.groups?.get(1)?.value ?: return emptyList()

        val videoUrlRegex = Regex("\"name\":\"(.*?)\",\"url\":\"(.*?)\"")
        val results = mutableListOf<ExtractorLink>()

        videoUrlRegex.findAll(videosJsonPart).forEach {
            val qualityName = it.groupValues[1]
            val videoUrl = it.groupValues[2]
                .replace("\\u0026", "&")
                .replace("\\", "") // unescape

            results.add(
                newExtractorLink(
                    name = "Okru",
                    url = videoUrl,
                    source = "Okru"
                    // `type` optional jika FILE tidak tersedia di versi ini
                )
            )
        }

        // Tangkap jika ada m3u8 streaming link
        val m3u8Regex = Regex("\"hlsManifestUrl\":\"(.*?)\"")
        val m3u8Url = m3u8Regex.find(body)?.groupValues?.get(1)
            ?.replace("\\u0026", "&")
            ?.replace("\\", "")

        if (m3u8Url != null) {
            results.add(
                newExtractorLink(
                    name = "Okru",
                    url = if (m3u8Url.startsWith("//")) "https:$m3u8Url" else m3u8Url,
                    source = "Okru",
                    type = ExtractorLinkType.M3U8
                )
            )
        }

        return results
    }
}
