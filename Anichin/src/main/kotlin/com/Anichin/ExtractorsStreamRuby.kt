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
        val id = Regex("/(?:e|embed)/([a-zA-Z0-9]+)").find(url)?.groupValues?.getOrNull(1) ?: return

        val response = app.post(
            "$mainUrl/dl",
            data = mapOf(
                "op" to "embed",
                "file_code" to id,
                "auto" to "1",
                "referer" to ""
            ),
            referer = referer,
            headers = mapOf("User-Agent" to USER_AGENT)
        )

        val unpackedScript = getAndUnpack(response.text).takeIf { !it.isNullOrBlank() }
        val rawScript = unpackedScript ?: response.document.selectFirst("script:containsData(sources:)")?.data()

        val m3u8Url = Regex("file\\s*:\\s*\"(.*?\\.m3u8.*?)\"").find(rawScript ?: return)
            ?.groupValues?.getOrNull(1)?.replace("\\", "") ?: return

        M3u8Helper.generateM3u8(
            name = name,
            url = m3u8Url,
            referer = mainUrl
        ).forEach(callback)
    }

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
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
