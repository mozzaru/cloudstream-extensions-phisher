package com.Anichin


import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.Filesim
import com.lagradost.cloudstream3.extractors.StreamSB
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.JsUnpacker
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlin.text.Regex

open class Vtbe : ExtractorApi() {
    override var name = "Vtbe"
    override var mainUrl = "https://vtbe.to"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val response = app.get(url,referer=mainUrl).document
        val extractedpack =response.selectFirst("script:containsData(function(p,a,c,k,e,d))")?.data().toString()
        JsUnpacker(extractedpack).unpack()?.let { unPacked ->
            Regex("sources:\\[\\{file:\"(.*?)\"").find(unPacked)?.groupValues?.get(1)?.let { link ->
                val type = getTypeFromUrl(videoUrl)
                    return listOf(
                        newExtractorLink(
                            name,
                            name,
                            videoUrl,
                            type
                        ) {
                            this.quality = 0
                        }
                    )

                )
            }
        }
        return null
    }
}

class Rumble : ExtractorApi() {
    override val name = "Rumble"
    override val mainUrl = "https://rumble.com"
    override val requiresReferer = false
    
    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val doc = app.get(url).document
    
        val scriptData = doc.select("script").mapNotNull { it.data() }
            .find { it.contains("source") && it.contains("video") } ?: return null
    
        val link = Regex("\"source\"\\s*:\\s*\"(https[^\"]+)\"")
            .find(scriptData)?.groupValues?.get(1) ?: return null
    
        return listOf(
            newExtractorLink(
                name,
                name,
                videoUrl,
                type
            ) {
                this.quality = 0 // jika tidak tahu resolusinya
            }
        )
    }
}

class RPMShare : ExtractorApi() {
    override val name = "RPMShare"
    override val mainUrl = "https://anichin.rpmvid.com"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val apiUrl = "https://anichin.rpmvid.com/api/v1/player?t=80b1d68c4e0fd0b1514ef99021f80610e9396557fcc9a2b087fc99374"
        val response = app.get(apiUrl, referer = referer ?: mainUrl).document

        val videoUrl = response.select("video source").attr("src")
                if (videoUrl.isNotEmpty()) {
                    val type = getTypeFromUrl(videoUrl) // Tambahkan ini
                    return listOf(
                        newExtractorLink(
                            name,
                            name,
                            videoUrl,
                            type // Ganti dari ExtractorLinkType.M3U8
                        ) {
                            this.quality = 0
                        }
                    )
                }
           
        }
        return null
    }
}

class StreamRuby : ExtractorApi() {
    override val name = "StreamRuby"
    override val mainUrl = "https://streamruby.net"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val apiUrl = "https://streamruby.com/api/v1/player?t=80b1d68c4e0fd0b1514ef99021f80610e9396557fcc9a2b087fc99374"
        val response = app.get(apiUrl, referer = referer ?: mainUrl).document

        val videoUrl = response.select("video source").attr("src")
                if (videoUrl.isNotEmpty()) {
                    val type = getTypeFromUrl(videoUrl) // Tambahkan ini
                    return listOf(
                        newExtractorLink(
                            name,
                            name,
                            videoUrl,
                            type // Ganti dari ExtractorLinkType.M3U8
                        ) {
                            this.quality = 0
                        }
                    )
                }
          
        }
        return null
    }
}

class NewPlayer : ExtractorApi() {
    override val name = "NewPlayer"
    override val mainUrl = "https://storage.googleapis.com"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        return listOf(
            newExtractorLink(
                name,
                name,
                url,
                type
            ) {
                this.quality = 0 // jika tidak tahu resolusinya
            }
        )
    }
}

class wishfast : StreamWishExtractor() {
    override var mainUrl = "https://wishfast.top"
    override var name = "StreamWish"
}

class waaw : StreamSB() {
    override var mainUrl = "https://waaw.to"
}

class FileMoonSx : Filesim() {
    override val mainUrl = "https://filemoon.sx"
    override val name = "FileMoonSx"
}

fun getTypeFromUrl(videoUrl: String): ExtractorLinkType {
    return when {
        videoUrl.endsWith(".m3u8") -> ExtractorLinkType.M3U8
        videoUrl.endsWith(".mpd") -> ExtractorLinkType.DASH
        else -> ExtractorLinkType.UNKNOWN // fallback untuk .mp4, .webm, dll
    }
}

fun Http(url: String): String {
    return if (url.startsWith("//")) {
        "https:$url"
    } else {
        url
    }
}
