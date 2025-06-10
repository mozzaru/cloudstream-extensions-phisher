package com.Anichin

import com.lagradost.cloudstream3.app 
import com.lagradost.cloudstream3.utils.ExtractorApi 
import com.lagradost.cloudstream3.utils.ExtractorLink 
import com.lagradost.cloudstream3.utils.ExtractorLinkType 
import com.lagradost.cloudstream3.utils.JsUnpacker 
import com.lagradost.cloudstream3.utils.Qualities 
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlin.text.Regex

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
