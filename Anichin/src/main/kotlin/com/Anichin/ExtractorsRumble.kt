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

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val response = app.get(
            url, referer = referer ?: "$mainUrl/"
        )
        val playerScript =
            response.document.selectFirst("script:containsData(mp4)")?.data()
                ?.substringAfter("{\"mp4")?.substringBefore("\"evt\":{") ?:""
        val regex = """"url":"(.*?)"|h":(.*?)\}""".toRegex()
        val matches = regex.findAll(playerScript)
        for (match in matches) {
            val href = match.groupValues[1].replace("\\/", "/")
            callback.invoke(
                newExtractorLink(
                    name,
                    name,
                    url = href,
                    INFER_TYPE
                ) {
                    this.referer = ""
                    this.quality = getQualityFromName("")
                }
            )
        }
    }
}
