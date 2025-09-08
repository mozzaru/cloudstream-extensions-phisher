package com.Anichin

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.network.CloudflareKiller
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class Anichin : MainAPI() {
    override var mainUrl              = "https://anichin.moe"
    override var name                 = "Anichin"
    override val hasMainPage          = true
    override var lang                 = "id"
    override val hasDownloadSupport   = true
    override val supportedTypes       = setOf(TvType.Movie, TvType.Anime)

    private val cloudflareClient: OkHttpClient by lazy {
        app.baseClient.newBuilder()
            .addInterceptor(CloudflareKiller())
            .build()
    }

    override val mainPage = mainPageOf(
        "anime/?order=update" to "Rilisan Terbaru",
        "anime/?status=ongoing&order=update" to "Series Ongoing",
        "anime/?status=completed&order=update" to "Series Completed",
        "anime/?status=hiatus&order=update" to "Series Drop/Hiatus",
        "anime/?type=movie&order=update" to "Movie"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = cloudflareClient.get("$mainUrl/${request.data}&page=$page").document
        val home = document.select("div.listupd > article").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = false
            ),
            hasNext = true
        )
    }

    private fun Element.toSearchResult(): SearchResponse {
        val title = this.select("div.bsx > a").attr("title")
        val href = fixUrl(this.select("div.bsx > a").attr("href"))
        val posterUrl = fixUrlNull(this.select("div.bsx > a img").attr("src"))
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()

        for (i in 1..3) {
            val document = cloudflareClient.get("$mainUrl/page/$i/?s=$query").document

            val results = document.select("div.listupd > article").mapNotNull { it.toSearchResult() }

            if (!searchResponse.containsAll(results)) {
                searchResponse.addAll(results)
            } else {
                break
            }

            if (results.isEmpty()) break
        }

        return searchResponse
    }

    override suspend fun load(url: String): LoadResponse {
        val document = cloudflareClient.get(url).document
        val title = document.selectFirst("h1.entry-title")?.text()?.trim().orEmpty()
        val href = document.selectFirst(".eplister li > a")?.attr("href").orEmpty()
        var poster = document.select("div.ime > img").attr("src")
        val description = document.selectFirst("div.entry-content")?.text()?.trim()
        val type = document.selectFirst(".spe")?.text().orEmpty()
        val tvtag = if (type.contains("Movie", ignoreCase = true)) TvType.Movie else TvType.TvSeries

        return if (tvtag == TvType.TvSeries) {
            val Eppage = document.selectFirst(".eplister li > a")?.attr("href").orEmpty()
            val doc = cloudflareClient.get(Eppage).document
            val episodes = doc.select("div.episodelist > ul > li").map { info ->
                val href1 = info.select("a").attr("href")
                val episode = info.select("a span").text().substringAfter("-").substringBeforeLast("-")
                val posterr = info.selectFirst("a img")?.attr("src").orEmpty()
                newEpisode(href1) {
                    this.name = episode
                    this.posterUrl = posterr
                }
            }

            if (poster.isEmpty()) {
                poster = document.selectFirst("meta[property=og:image]")?.attr("content").orEmpty()
            }

            newTvSeriesLoadResponse(title, url, TvType.Anime, episodes.reversed()) {
                this.posterUrl = poster
                this.plot = description
            }
        } else {
            if (poster.isEmpty()) {
                poster = document.selectFirst("meta[property=og:image]")?.attr("content").orEmpty()
            }

            newMovieLoadResponse(title, url, TvType.Movie, href) {
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
        val document = cloudflareClient.get(data).document
        document.select(".mobius option").forEach { server ->
            val base64 = server.attr("value")
            val decoded = base64Decode(base64)
            val doc = Jsoup.parse(decoded)
            val href = doc.select("iframe").attr("src")
            val url = fixUrl(href)
            loadExtractor(url, subtitleCallback, callback)
        }
        return true
    }
}
