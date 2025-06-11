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
                ?.substringAfter("{\"mp4")?.substringBefore("\"evt\":{") ?: ""
        val regex = """"url":"(.*?)"|h":(.*?)\}""".toRegex()
        val matches = regex.findAll(playerScript)

        for (match in matches) {
            val href = match.groupValues[1].replace("\\/", "/")
            
            // LOGCAT: Tambahkan log ini
            Log.d("RumbleExtractor", "Playing Rumble video with URL: $href")

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