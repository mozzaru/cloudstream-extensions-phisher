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
        val id = "/e/(\\w+)".toRegex().find(url)?.groupValues?.get(1) ?: return
        val response = app.post(
            "$mainUrl/dl", data = mapOf(
                "op" to "embed",
                "file_code" to id,
                "auto" to "1",
                "referer" to "",
            ), referer = referer
        )

        val script = getAndUnpack(response.text) ?: Jsoup.parse(response.text)
            .selectFirst("script:containsData(sources:)")?.data()

        val m3u8 = Regex("file:\\s*\"(.*?\\.m3u8.*?)\"").find(script ?: return)
            ?.groupValues?.getOrNull(1)

        M3u8Helper.generateM3u8(
            source = name,
            streamUrl = m3u8 ?: return,
            referer = mainUrl
        ).forEach(callback)
    }
}

class StreamRuby1 : StreamRuby() {
    override var mainUrl: String = "https://streamruby.net"
    override var name = "StreamRuby1"
}

class StreamRuby2 : StreamRuby() {
    override var mainUrl: String = "https://streamruby.com"
    override var name = "StreamRuby2"
}
