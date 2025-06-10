package com.Anichin.extractors

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.JsUnpacker
//import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.ErrorLoadingException
import kotlin.text.Regex
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.util.Base64

class OkruExtractor : ExtractorApi() {
    override val name = "OK.ru Extractor"
    override val mainUrl = "https://ok.ru"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val response = app.get(url, referer = referer ?: "$mainUrl/")
        val document = response.document

        // Ambil iframe langsung di halaman
        document.select("iframe").forEach { iframe ->
            val srcRaw = iframe.attr("src").trim()
            if (srcRaw.isNotBlank()) {
                val videoId = srcRaw.substringAfterLast("/").substringBefore("?")
                val playUrl = "https://ok.ru/videoembed/$videoId"
                callback.invoke(
                    newExtractorLink(
                        name,
                        "$name - Embed",
                        url = playUrl,
                        INFER_TYPE
                    ) {
                        this.referer = ""
                        this.quality = getQualityFromName("")
                    }                    
                )
            }
        }

        // Ambil iframe dari base64 di option.mobius
        document.select(".mobius option").forEach { option ->
            val base64 = option.attr("value").trim()
            if (base64.isNotBlank()) {
                try {
                    val decodedHtml = base64Decode(base64)
                    val iframe = Jsoup.parse(decodedHtml).selectFirst("iframe")
                    val iframeSrcRaw = iframe?.attr("src")?.ifBlank { iframe.attr("data-src") }?.trim()
                    if (!iframeSrcRaw.isNullOrBlank()) {
                        val finalUrl = if (iframeSrcRaw.startsWith("http")) iframeSrcRaw else "https:$iframeSrcRaw"
                        callback.invoke(
                            newExtractorLink(
                                name,
                                "$name - Base64",
                                url = finalUrl,
                                INFER_TYPE
                            ) {
                                this.referer = ""
                                this.quality = getQualityFromName("")
                            }
                        )
                    }
                } catch (e: Exception) {
                    println("❌ [OK.ru Extractor] Error decoding Base64: ${e.message}")
                }
            }
        }
    }

    private fun base64Decode(encoded: String): String {
        val decodedBytes = Base64.getDecoder().decode(encoded)
        return String(decodedBytes, Charsets.UTF_8)
    }
}
