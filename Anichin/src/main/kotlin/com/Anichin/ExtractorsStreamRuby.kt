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

open class Streamruby : ExtractorApi() {
    override var name = "Streamruby"
    override var mainUrl = "https://streamruby.com"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val newUrl = if (url.contains("/e/")) url.replace("/e", "") else url
        val txt = app.get(newUrl).text
        val m3u8 = Regex("file:\\s*\"(.*?m3u8.*?)\"").find(txt)?.groupValues?.getOrNull(1)

        return m3u8?.takeIf { it.isNotEmpty() }?.let {
            listOf(
                newExtractorLink(
                    name = this.name,
                    source = this.name,
                    url = it,
                    type = INFER_TYPE
                ) {
                    this.referer = mainUrl
                    this.quality = Qualities.Unknown.value
                }
            )
        }
    }
}

class Streamruby1 : Streamruby() {
    override var mainUrl: String = "https://streamruby.net"
    override var name = "Streamruby1"
}

class Streamruby2 : Streamruby() {
    override var mainUrl: String = "https://rubyvidhub.com"
    override var name = "Streamruby2"
}
