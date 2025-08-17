package com.donghub

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class ArchiveOrgExtractor : ExtractorApi() {
    override val name = "ArchiveOrg"
    override val mainUrl = "https://archive.org"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val safeUrl = url.replace(" ", "%20")

        // Archive.org direct mp4/m3u8 links
        if (safeUrl.endsWith(".mp4", true) || safeUrl.endsWith(".m3u8", true)) {
            callback.invoke(
                ExtractorLink(
                    source = name,
                    name = name,
                    url = safeUrl,
                    referer = referer ?: mainUrl,
                    quality = Qualities.Unknown.value,  // bisa diganti getQualityFromName("720p") kalau tahu
                    isM3u8 = safeUrl.endsWith(".m3u8", true)
                )
            )
        }
    }
}