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
        val response = app.get(
            url, referer = referer ?: "$mainUrl/"
        )

        val playerScript = response.document
            .selectFirst("script:containsData(mp4)")?.data()
            ?.substringAfter("{\"mp4")?.substringBefore("\"evt\":{") ?: ""

        val regex = """"url":"(.*?)"|h":(.*?)\}""".toRegex()
        val matches = regex.findAll(playerScript)

        for (match in matches) {
            val href = match.groupValues[1].replace("\\/", "/")
            val qualityValue = Regex("(\\d{3,4})").find(href)?.value?.toIntOrNull()

            // ✅ Hanya tampilkan kualitas yang wajar (contoh: 240p, 360p, 480p, 720p, 1080p)
            if (qualityValue != null && qualityValue in listOf(240, 360, 480, 720, 1080)) {
                val quality = "${qualityValue}p"

                callback.invoke(
                    newExtractorLink(
                        source = this.name,
                        name = quality, // Tampilkan kualitas saja tanpa 'Rumble'
                        url = href,
                        type = INFER_TYPE
                    ) {
                        this.referer = "$mainUrl/"
                        this.quality = getQualityFromName(quality)
                        this.headers = headers
                    }
                )
            }
        }
    }
}