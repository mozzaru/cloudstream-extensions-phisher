package com.Anichin

import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.*

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
        val response = app.get(url, referer = referer ?: "$mainUrl/")
//        Log.d("rumbleex","url = $url")

        val playerScript = response.document
            .selectFirst("script:containsData(mp4)")?.data()
            ?.substringAfter("{\"mp4")?.substringBefore("\"evt\":{") ?: return

        val regex = """"url":"(.*?)".*?"h":([0-9]+)""".toRegex()
        val matches = regex.findAll(playerScript)

        for (match in matches) {
            val rawHref = match.groupValues[1]
            val rawQuality = match.groupValues[2]
            if (rawHref.isBlank()) continue

            val href = rawHref.replace("\\/", "/")
//            Log.d("rumbleex", "href = $href")
//            Log.d("rumbleex", "rawQuality = $rawQuality")
            callback.invoke(
                newExtractorLink(
                    source = this.name,
                    name = this.name, // Tampil: Rumble - 720p
                    url = href,
                    type = INFER_TYPE
                ) {
                    this.referer = "$mainUrl/"
                    this.quality = getQualityFromName(rawQuality)
                }
            )
        }
    }
}