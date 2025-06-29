// ! Alat ini dibuat oleh @keyiflerolsun | Untuk @KekikAkademi

package com.Anichin

import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.newExtractorLink


class OkRuSSL : Odnoklassniki() {
    override var name    = "OkRuSSL"
    override var mainUrl = "https://ok.ru"
}

class OkRuHTTP : Odnoklassniki() {
    override var name    = "OkRuHTTP"
    override var mainUrl = "http://ok.ru"
}

open class Odnoklassniki : ExtractorApi() {
    override val name            = "Odnoklassniki"
    override val mainUrl         = "https://odnoklassniki.ru"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val headers = mapOf(
            "Accept" to "*/*",
            "Connection" to "keep-alive",
            "Sec-Fetch-Dest" to "empty",
            "Sec-Fetch-Mode" to "cors",
            "Sec-Fetch-Site" to "cross-site",
            "Origin" to mainUrl,
            "User-Agent" to USER_AGENT,
        )
        
        // Mengubah URL video menjadi URL embed
        val embedUrl = url.replace("/video/","/videoembed/")
        
        // Mendapatkan respons dari URL embed
        val videoReq = app.get(embedUrl, headers=headers).text
            .replace("\\&quot;", "\"")
            .replace("\\\\", "\\")
            .replace(Regex("\\\\u([0-9A-Fa-f]{4})")) { matchResult ->
                Integer.parseInt(matchResult.groupValues[1], 16).toChar().toString()
            }

        // Mengekstrak informasi video dari respons
        val videosStr = Regex(""""videos":(\[[^]]*])""").find(videoReq)?.groupValues?.get(1) 
            ?: throw ErrorLoadingException("Video tidak ditemukan")
        
        val videos = AppUtils.tryParseJson<List<OkRuVideo>>(videosStr) 
            ?: throw ErrorLoadingException("Video tidak ditemukan")

        // Memproses setiap video yang ditemukan
        for (video in videos) {
            // Menangani berbagai format URL video
            val videoUrl = when {
                video.url.startsWith("//") -> "https:${video.url}"  // URL tanpa protokol
                video.url.startsWith("/") -> "${mainUrl}${video.url}" // URL relatif
                video.url.startsWith("http", ignoreCase = true) -> video.url // URL lengkap
                else -> "${mainUrl}/${video.url}" // Format lainnya
            }

            // Mengkonversi kualitas video ke format standar
            val quality = video.name.uppercase()
                .replace("MOBILE", "144p")
                .replace("LOWEST", "240p")
                .replace("LOW",    "360p")
                .replace("SD",     "480p")
                .replace("HD",     "720p")
                .replace("FULL",   "1080p")
                .replace("QUAD",   "1440p")
                .replace("ULTRA",  "4k")

            // Memastikan URL video valid sebelum diproses
            if (videoUrl.isNotBlank() && videoUrl.startsWith("http")) {
                callback.invoke(
                    newExtractorLink(
                        source  = this.name,
                        name    = this.name,
                        url     = videoUrl,
                        type    = INFER_TYPE
                    ) {
                        this.referer = "$mainUrl/"
                        this.quality = getQualityFromName(quality)
                        this.headers = headers
                    }
                )
            }
        }
    }

    // Data class untuk menyimpan informasi video
    data class OkRuVideo(
        @JsonProperty("name") val name: String,
        @JsonProperty("url")  val url: String,
    )
}