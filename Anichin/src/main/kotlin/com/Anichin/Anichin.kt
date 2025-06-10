package com.Anichin

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import com.Anichin.extractors.OkruExtractor

class Anichin : MainAPI() {
    override var mainUrl = "https://anichin.club"
    override var name = "Anichin"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.Anime)

    override val mainPage = mainPageOf(
        "anime/?order=update" to "Semua Rilisan Terbaru",
        "anime/?status=ongoing&order=update" to "Ongoing",
        "anime/?status=completed&order=update" to "Completed",
        "anime/?type=movie&order=update" to "Movies",
        "anime/?order=popular" to "Populer Hari Ini"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val allItems = mutableListOf<SearchResponse>()
        val maxPages = if (request.name in listOf("Semua Rilisan Terbaru", "Completed")) 3 else 1
        var hasNext = false

        for (i in 1..maxPages) {
            val document = app.get("$mainUrl/${request.data}&page=$i").document
            val items = document.select("div.listupd > article").mapNotNull { it.toSearchResult() }
            allItems.addAll(items)

            val lastPage = document.select("a.page-numbers").lastOrNull()?.text()?.toIntOrNull()
            if (lastPage != null && i < lastPage) {
                hasNext = true
            }
        }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = allItems,
                isHorizontalImages = false
            ),
            hasNext = hasNext
        )
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val aTag = selectFirst("div.bsx > a") ?: return null
        val rawTitle = aTag.attr("title").ifBlank { aTag.text() }
        val href = fixUrl(aTag.attr("href"))
        val img = aTag.selectFirst("img")

        val posterUrlRaw = img?.run {
            attr("data-src").ifBlank {
                attr("src")
            }.ifBlank {
                attr("data-lazy-src")
            }
        }.orEmpty()

        val posterUrlFixed = if (posterUrlRaw.startsWith("//")) {
            "https:$posterUrlRaw"
        } else {
            posterUrlRaw
        }

        val posterUrl = fixUrlNull(posterUrlFixed)

        val type = if (href.contains("/movie/")) TvType.Movie else TvType.Anime
        val statusLabel = this.selectFirst("div.bt span")?.text()?.lowercase().orEmpty()

        val titleWithStatus = if ("complete" in statusLabel) {
            "$rawTitle (Completed)"
        } else {
            rawTitle
        }

        return newMovieSearchResponse(titleWithStatus, href, type) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query").document
        return document.select("div.listupd > article").mapNotNull { it.toSearchResult() }
            .distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val title = document.selectFirst("h1.entry-title")?.text()?.trim().orEmpty()
        var poster = document.selectFirst("div.ime > img")?.attr("src").orEmpty()
        if (poster.isEmpty()) {
            poster = document.selectFirst("meta[property=og:image]")?.attr("content").orEmpty()
        }
        val description = document.selectFirst("div.entry-content")?.text()?.trim()

        val episodeElements = document.select("div.episodelist > ul > li")
        val episodeElementsAlt = document.select("div.eplister > ul > li")
        val episodeList = if (episodeElements.isNotEmpty()) episodeElements else episodeElementsAlt

        val isSeries = episodeList.isNotEmpty()
        val tvType = if (isSeries) TvType.Anime else TvType.Movie

        val episodes = if (isSeries) {
            episodeList.mapIndexed { index, it ->
                val epHref = it.selectFirst("a")?.attr("href").orEmpty()
                val epName = it.select("a span")?.text()?.substringAfter("-")?.substringBeforeLast("-")?.trim()
                val epPoster = it.selectFirst("a img")?.attr("src").orEmpty()

                newEpisode(epHref) {
                    this.name = if (!epName.isNullOrBlank()) epName else "Episode ${index + 1}"
                    this.posterUrl = epPoster
                }
            }.reversed()
        } else {
            val firstOption = document.selectFirst(".mobius option")
            val base64 = firstOption?.attr("value")?.trim()
            var playUrl: String? = null

            if (!base64.isNullOrBlank()) {
                try {
                    val decoded = base64Decode(base64)
                    val iframe = Jsoup.parse(decoded).selectFirst("iframe")
                    val rawSrc = iframe?.attr("src")
                    if (!rawSrc.isNullOrBlank()) {
                        playUrl = if (rawSrc.startsWith("http")) rawSrc else "https:$rawSrc"
                    }
                } catch (_: Exception) {}
            }

            if (playUrl == null) playUrl = url

            listOf(newEpisode(playUrl) {
                name = "Movie"
                posterUrl = poster
            })
        }

        return newTvSeriesLoadResponse(title, url, tvType, episodes) {
            this.posterUrl = poster
            this.plot = description
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document

        // Handle server list base64 first
        document.select(".mobius option").forEach { server ->
            val base64 = server.attr("value").trim()
            if (base64.isBlank()) return@forEach

            try {
                val decoded = base64Decode(base64)
                val iframe = Jsoup.parse(decoded).selectFirst("iframe")
                val iframeSrc = iframe?.attr("src")?.ifBlank { iframe.attr("data-src") }?.trim()
                if (!iframeSrc.isNullOrBlank()) {
                    val finalUrl = if (iframeSrc.startsWith("http")) iframeSrc else "https:$iframeSrc"
                    println("🎯 [Anichin] Trying to extract: $finalUrl")

                    if (finalUrl.contains("ok.ru")) {
                        extractOkruIframe(finalUrl, data, callback)
                    } else {
                        loadExtractor(finalUrl, data, subtitleCallback, callback)
                    }
                }
            } catch (e: Exception) {
                println("❌ [Anichin] Error decoding Base64 or extracting: ${e.message}")
            }
        }

        // Check direct iframe (for some releases)
        extractOkruIframe(document, data, callback)

        return true
    }

    private suspend fun extractOkruIframe(
        document: org.jsoup.nodes.Document,
        referer: String?,
        callback: (ExtractorLink) -> Unit
    ) {
        val iframe = document.selectFirst("iframe[src*=ok.ru], iframe[data-src*=ok.ru]") ?: return
        val iframeSrc = iframe.attr("src").ifBlank { iframe.attr("data-src") }.ifBlank { return }

        val realUrl = if (iframeSrc.startsWith("data:text/plain;base64,")) {
            val base64Part = iframeSrc.removePrefix("data:text/plain;base64,")
            String(android.util.Base64.decode(base64Part, android.util.Base64.DEFAULT))
        } else {
            iframeSrc
        }

        val okruUrl = realUrl.substringBefore("&")

        println("🎬 [Anichin] Extracting OK.ru iframe: $okruUrl")

        val videos = OkruExtractor().getUrl(okruUrl, referer)

        videos.forEach { video ->
            callback.invoke(video)
        }
    }

    private suspend fun extractOkruIframe(
        url: String,
        referer: String?,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val document = app.get(url).document
            extractOkruIframe(document, referer, callback)
        } catch (e: Exception) {
            println("❌ [Anichin] Error extracting OK.ru iframe page: ${e.message}")
        }
    }
}
