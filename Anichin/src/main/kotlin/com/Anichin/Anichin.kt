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

    override val mainPage = mainPageOf( // hanya satu: homepage rilisan terbaru
        "" to "Rilisan Terbaru"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        if (page != 1) throw ErrorLoadingException("Tidak ada halaman berikutnya")
        val document = app.get(mainUrl).document
        val home = document.select("div.post-show > article").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = false
            ),
            hasNext = false
        )
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val aTag = selectFirst("a") ?: return null
        val titleRaw = aTag.attr("title").ifBlank { aTag.text() }.trim()
        val epInfo = selectFirst(".epx")?.text()?.trim()
        val subInfo = selectFirst(".bt > span")?.text()?.trim()

        val fullTitle = listOfNotNull(titleRaw, epInfo, subInfo).joinToString(" • ")

        val href = fixUrl(aTag.attr("href"))
        val posterUrl = fixUrlNull(selectFirst("img")?.attr("src"))

        return newMovieSearchResponse(fullTitle, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()
        for (i in 1..3) {
            val document = app.get("${mainUrl}/page/$i/?s=$query").document
            val results = document.select("div.listupd > article").mapNotNull {
                val title = it.select("div.bsx > a").attr("title") ?: return@mapNotNull null
                val href = fixUrl(it.select("div.bsx > a").attr("href"))
                val posterUrl = fixUrlNull(it.select("div.bsx > a img").attr("src"))
                newMovieSearchResponse(title, href, TvType.Movie) {
                    this.posterUrl = posterUrl
                }
            }
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

        val isSeries = document.select("div.episodelist").isNotEmpty()
        return if (isSeries) {
            val episodes = document.select("div.episodelist > ul > li").map {
                val epHref = it.selectFirst("a")?.attr("href").orEmpty()
                val epName = it.select("a span").text().substringAfter("-").substringBeforeLast("-").trim()
                val epPoster = it.selectFirst("a img")?.attr("src").orEmpty()

                newEpisode(epHref) {
                    this.name = epName
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
