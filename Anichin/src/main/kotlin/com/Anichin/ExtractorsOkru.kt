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
import com.lagradost.cloudstream3.ErrorLoadingException
//import com.lagradost.cloudstream3.extractors.Okrulink
import com.lagradost.cloudstream3.utils.*
import kotlin.text.Regex
import org.jsoup.Jsoup

open class Okrunew : ExtractorApi() {
    override val name = "Okrunew"
    override val mainUrl = "https://ok.ru"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink> {
    val headers = mapOf(
        "Referer" to (referer ?: mainUrl),
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36"
    )

    // Convert embed URL to video URL
    val realUrl = when {
        url.contains("/videoembed/") -> url.replace("/videoembed/", "/video/")
        url.contains("/embed/") -> url.replace("/embed/", "/video/")
        else -> url
    }

    val response = app.get(realUrl, headers = headers)
    val body = response.text
    val results = mutableListOf<ExtractorLink>()

    // Extract MP4 links
    val videoListRegex = Regex("\"videos\"\\s*:\\s*\\{(.*?)\\}")
    val videosJsonPart = videoListRegex.find(body)?.groups?.get(1)?.value

    if (videosJsonPart != null) {
        val videoUrlRegex = Regex("\"name\"\\s*:\\s*\"(.*?)\"\\s*,\\s*\"url\"\\s*:\\s*\"(.*?)\"")
        videoUrlRegex.findAll(videosJsonPart).forEach {
            val qualityName = it.groupValues[1]
            val videoUrl = it.groupValues[2]
                .replace("\\u0026", "&")
                .replace("\\", "")

            val qualityInt = qualityName.filter { c -> c.isDigit() }.toIntOrNull() ?: Qualities.Unknown.value

            results.add(
                newExtractorLink(
                    source = "Okru",
                    name = "Okru",
                    url = videoUrl,
                    type = when {
                        videoUrl.contains(".mpd", ignoreCase = true) -> ExtractorLinkType.DASH
                        videoUrl.contains(".m3u8", ignoreCase = true) -> ExtractorLinkType.M3U8
                        else -> ExtractorLinkType.VIDEO
                    },
                    initializer = {
                        this.quality = qualityInt
                        this.referer = mainUrl
                    }
                )
            )
        }
    }

        // Extract HLS (m3u8) link
        val m3u8Regex = Regex("\"hlsManifestUrl\"\\s*:\\s*\"(.*?)\"")
        val m3u8Url = m3u8Regex.find(body)?.groupValues?.get(1)
            ?.replace("\\u0026", "&")
            ?.replace("\\", "")
    
        if (m3u8Url != null) {
            results.add(
                newExtractorLink(
                    source = "Okru",
                    name = "Okru",
                    url = if (m3u8Url.startsWith("//")) "https:$m3u8Url" else m3u8Url,
                    type = when {
                        m3u8Url.contains(".mpd", ignoreCase = true) -> ExtractorLinkType.DASH
                        m3u8Url.contains(".m3u8", ignoreCase = true) -> ExtractorLinkType.M3U8
                        else -> ExtractorLinkType.VIDEO
                    },
                    initializer = {
                        this.quality = Qualities.Unknown.value
                        this.referer = mainUrl
                    }
                )
            )
        }
    
        return results
    }
}

class OkRuSSL : Okrunew() {
    override var name = "OkRuSSL"
    override var mainUrl = "https://ok.ru"
}

class OkRuHTTP : Okrunew() {
    override var name = "OkRuHTTP"
    override var mainUrl = "http://ok.ru"
}

class okrunew1 : Okrunew() {
    override var name    = "okrunew1"
    override var mainUrl = "https://st.okcdn.ru"
}

class okrunew2 : Okrunew() {
    override var name    = "okrunew2"
    override var mainUrl = "https://i.okcdn.ru"
}

class okrunew3 : Okrunew() {
    override var name    = "okrunew3"
    override var mainUrl = "https://vd380.okcdn.ru"
}

class okrunew4 : Okrunew() {
    override var name    = "okrunew4"
    override var mainUrl = "https://api./mycdn.me"
}

class okrunew5 : Okrunew() {
    override var name    = "okrunew5"
    override var mainUrl = "https://videotestapi.ok.ru"
}

class okrunew6 : Okrunew() {
    override var name    = "okrunew6"
    override var mainUrl = "https://api.okcdn.ru"
}
