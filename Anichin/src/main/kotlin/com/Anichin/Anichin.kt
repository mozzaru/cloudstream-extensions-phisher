package com.Anichin

import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup

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
        val document = app.get("$mainUrl/${request.data}&page=$page").document
        val home = document.select("div.listupd > article").mapNotNull { it.toSearchResult() }

        val hasNext = document.select("a.page-numbers").lastOrNull()?.text()?.toIntOrNull()?.let { it > page } ?: false

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = false
            ),
            hasNext = hasNext
        )
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val aTag = selectFirst("div.bsx > a") ?: return null
        val title = aTag.attr("title").ifBlank { aTag.text() }
        val href = fixUrl(aTag.attr("href"))
        val img = aTag.selectFirst("img")
        val posterUrl = fixUrlNull(img?.attr("src") ?: img?.attr("data-src"))

        // Default: use Anime, can adjust in load()
        val tvType = TvType.Anime

        return newAnimeSearchResponse(title, href, tvType) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()
        for (i in 1..3) {
            val document = app.get("$mainUrl/page/$i/?s=$query").document
            val results = document.select("div.listupd > article").mapNotNull { it.toSearchResult() }
            if (results.isEmpty()) break
            searchResponse.addAll(results)
        }
        return searchResponse
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
        val isSeries = episodeElements.size > 1 || episodeElements.any { it.text().contains("Episode", true) }

        return if (isSeries) {
            val episodes = episodeElements.map {
                val epHref = it.selectFirst("a")?.attr("href").orEmpty()
                val epName = it.select("a span")?.text()?.substringAfter("-")?.substringBeforeLast("-")?.trim().orEmpty()
                val epPoster = it.selectFirst("a img")?.attr("src").orEmpty()

                newEpisode(epHref) {
                    this.name = if (epName.isNotBlank()) epName else "Episode"
                    this.posterUrl = epPoster
                }
            }

            newTvSeriesLoadResponse(title, url, TvType.Anime, episodes.reversed()) {
                this.posterUrl = poster
                this.plot = description
            }
        } else {
            val watchHref = document.selectFirst("iframe")?.attr("src") ?: url
            newMovieLoadResponse(title, url, TvType.Movie, watchHref) {
                this.posterUrl = poster
                this.plot = description
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document

        document.select(".mobius option").forEach { server ->
            val base64 = server.attr("value").trim()
            val decoded = base64Decode(base64)
            val doc = Jsoup.parse(decoded)
            val rawHref = doc.select("iframe").attr("src").trim()

            if (rawHref.isNotBlank()) {
                val href = if (rawHref.startsWith("http")) rawHref else "https:$rawHref"
                try {
                    loadExtractor(href, subtitleCallback, callback)
                } catch (e: Exception) {
                    println("❌ Gagal load URL: $href -> ${e.message}")
                }
            }
        }
        return true
    }
}
