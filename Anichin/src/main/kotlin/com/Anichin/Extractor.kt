package com.Anichin

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

open class StreamRuby : ExtractorApi() {
    override val name = "StreamRuby"
    override val mainUrl = "https://rubyvidhub.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val id = "/e/(\\w+)".toRegex().find(url)?.groupValues?.get(1) ?: return
        val response = app.post(
            "$mainUrl/dl", data = mapOf(
                "op" to "embed",
                "file_code" to id,
                "auto" to "1",
                "referer" to "",
            ), referer = referer
        )
        val script = if (!getPacked(response.text).isNullOrEmpty()) {
            getAndUnpack(response.text)
        } else {
            response.document.selectFirst("script:containsData(sources:)")?.data()
        }
        val m3u8 = Regex("file:\\s*\"(.*?m3u8.*?)\"").find(script ?: return)?.groupValues?.getOrNull(1)
        M3u8Helper.generateM3u8(
            name,
            m3u8 ?: return,
            mainUrl
        ).forEach(callback)
    }
}

class VidGuard : ExtractorApi() {
    override var name = "VidGuard"
    override var mainUrl = "https://vidguard.to"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val response = app.get(url, referer = mainUrl).document
        val extractedPack = response.selectFirst("script:containsData(function(p,a,c,k,e,d))")?.data() ?: return null

        JsUnpacker(extractedPack).unpack()?.let { unPacked ->
            Regex("file\\s*:\\s*\"(.*?)\"").find(unPacked)?.groupValues?.get(1)?.let { link ->
                return listOf(
                    newExtractorLink(
                        this.name,
                        this.name,
                        url = link,
                        ExtractorLinkType.M3U8
                    ) {
                        this.referer = referer ?: ""
                        this.quality = Qualities.Unknown.value
                    }
                )
            }
        }
        return null
    }
}

class StreamRuby1 : StreamRuby() {
    override var mainUrl: String = "https://streamruby.net"
    override var name = "StreamRuby"
}

class StreamRuby2 : StreamRuby() {
    override var mainUrl: String = "https://streamruby.com"
    override var name = "StreamRuby"
}

fun Http(url: String): String {
    return if (url.startsWith("//")) {
        "https:$url"
    } else {
        url
    }
}
