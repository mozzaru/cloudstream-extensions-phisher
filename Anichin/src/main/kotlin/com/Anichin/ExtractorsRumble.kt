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
import com.lagradost.cloudstream3.utils.Qualities
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
        Log.d("RumbleExtractor", "Mulai ekstraksi dari url: $url")
        val response = app.get(url, referer = referer ?: "$mainUrl/")
        val scriptData = response.document.selectFirst("script:containsData(mp4)")?.data()
            ?.substringAfter("{\"mp4")?.substringBefore("\"evt\":{")
        if (scriptData == null) {
            Log.d("RumbleExtractor", "Script mp4 tidak ditemukan.")
            return
        }

        val regex = """"url":"(.*?)"|h":(.*?)\}""".toRegex()
        val matches = regex.findAll(scriptData)

        val processedUrls = mutableSetOf<String>()
        var foundMaster = false

        for (match in matches) {
            val rawUrl = match.groupValues[1]
            if (rawUrl.isBlank()) continue

            val cleanedUrl = rawUrl.replace("\\/", "/")
            Log.d("RumbleExtractor", "Cek url: $cleanedUrl")
            if (!cleanedUrl.contains("rumble.com")) {
                Log.d("RumbleExtractor", "Lewatkan url bukan rumble.com: $cleanedUrl")
                continue
            }
            if (!cleanedUrl.endsWith(".m3u8")) {
                Log.d("RumbleExtractor", "Lewatkan url non m3u8: $cleanedUrl")
                continue
            }
            if (!processedUrls.add(cleanedUrl)) {
                Log.d("RumbleExtractor", "Lewatkan url duplikat: $cleanedUrl")
                continue
            }

            // Cek apakah ini master playlist
            val m3u8Response = app.get(cleanedUrl)
            val variantCount = "#EXT-X-STREAM-INF".toRegex().findAll(m3u8Response.text).count()
            Log.d("RumbleExtractor", "Jumlah variant EXT-X-STREAM-INF pada $cleanedUrl: $variantCount")

            if (variantCount > 1) {
                // Ini master playlist multi kualitas, callback satu saja
                Log.d("RumbleExtractor", "MASTER playlist ditemukan, callback: $cleanedUrl")
                callback.invoke(
                    ExtractorLink(
                        name = "Rumble HLS",
                        source = this@Rumble.name,
                        url = cleanedUrl,
                        type = ExtractorLinkType.M3U8,
                        quality = -1, // Unknown, biar multi quality otomatis
                        referer = ""
                    )
                )
                foundMaster = true
                break // Cukup satu, langsung break
            } else {
                Log.d("RumbleExtractor", "Bukan master playlist, dilewati: $cleanedUrl")
            }
        }
        if (!foundMaster) {
            Log.d("RumbleExtractor", "Tidak ada master playlist .m3u8 dari rumble.com ditemukan.")
        }
    }
}