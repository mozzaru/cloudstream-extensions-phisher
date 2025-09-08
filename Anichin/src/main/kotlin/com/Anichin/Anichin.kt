package com.Anichin

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.utils.*
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Document

class Anichin : MainAPI() {
    override var mainUrl = "https://anichin.moe"
    override var name = "Anichin"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.Anime)

    // Fungsi untuk generate random string
    private fun randomString(length: Int): String {
        val charPool = ('a'..'z') + ('A'..'Z') + ('.')
        return List(length) { charPool.random() }.joinToString("")
    }

    // Override headersBuilder untuk menambahkan header kustom termasuk X-Requested-With acak
    override fun headersBuilder() = super.headersBuilder().apply {
        add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0 Safari/537.36")
        add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
        add("Accept-Language", "en-US,en;q=0.5")
        add("Connection", "keep-alive")
        add("Sec-Fetch-Dest", "document")
        add("Sec-Fetch-Mode", "navigate")
        add("Sec-Fetch-Site", "same-origin")
        add("Upgrade-Insecure-Requests", "1")
        add("X-Requested-With", randomString((1..20).random()))
    }

    // OkHttpClient dengan interceptor hapus header X-Requested-With lama, pasang header dari headersBuilder()
    override val cloudflareClient: OkHttpClient = app.baseClient.newBuilder()
        .addInterceptor(CloudflareKiller())
        .addInterceptor { chain ->
            val request = chain.request()
            // Buat headers baru dari headersBuilder (hapus dulu header X-Requested-With dari request awal)
            val newHeaders = headersBuilder().apply {
                removeAll("X-Requested-With")
            }.build()

            val newRequest = request.newBuilder()
                .headers(newHeaders)
                .build()
            chain.proceed(newRequest)
        }
        .rateLimit(9, 2)
        .build()

    override val mainPage = mainPageOf(
        "anime/?order=update" to "Rilisan Terbaru",
        "anime/?status=ongoing&order=update" to "Series Ongoing",
        "anime/?status=completed&order=update" to "Series Completed",
        "anime/?status=hiatus&order=update" to "Series Drop/Hiatus",
        "anime/?type=movie&order=update" to "Movie"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = cloudflareClient.fetchDocument("$mainUrl/${request.data}&page=$page")
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

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()
        for (i in 1..3) {
            val document = cloudflareClient.fetchDocument("$mainUrl/page/$i/?s=$query")
            val results = document.select("div.listupd > article").mapNotNull { it.toSearchResult() }
            if (results.isEmpty()) break
            if (!searchResponse.containsAll(results)) {
                searchResponse.addAll(results)
            } else {
                break
            }
        }
        return searchResponse
    }

    override suspend fun load(url: String): LoadResponse {
        val document = cloudflareClient.fetchDocument(url)
        val title = document.selectFirst("h1.entry-title")?.text()?.trim().orEmpty()
        var poster = document.select("div.ime > img").attr("src")
        val description = document.selectFirst("div.entry-content")?.text()?.trim()
        val type = document.selectFirst(".spe")?.text().orEmpty()
        val isMovie = type.contains("Movie", ignoreCase = true)

        if (isMovie) {
            val href = document.selectFirst(".eplister li > a")?.attr("href").orEmpty()
            if (poster.isEmpty()) {
                poster = document.selectFirst("meta[property=og:image]")?.attr("content").orEmpty()
            }
            return newMovieLoadResponse(title, url, TvType.Movie, href) {
                this.posterUrl = poster
                this.plot = description
            }
        } else {
            val epPage = document.selectFirst(".eplister li > a")?.attr("href").orEmpty()
            val doc = cloudflareClient.fetchDocument(epPage)
            val episodes = doc.select("div.episodelist > ul > li").map { info ->
                val epUrl = info.select("a").attr("href")
                val episodeName = info.select("a span").text().substringAfter("-").substringBeforeLast("-")
                val epPoster = info.selectFirst("a img")?.attr("src").orEmpty()
                newEpisode(epUrl) {
                    name = episodeName
                    posterUrl = epPoster
                }
            }.reversed()

            if (poster.isEmpty()) {
                poster = document.selectFirst("meta[property=og:image]")?.attr("content").orEmpty()
            }

            return newTvSeriesLoadResponse(title, url, TvType.Anime, episodes) {
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
        val document = cloudflareClient.fetchDocument(data)
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

    private fun Element.toSearchResult(): SearchResponse {
        val title = this.select("div.bsx > a").attr("title")
        val href = fixUrl(this.select("div.bsx > a").attr("href"))
        val posterUrl = fixUrlNull(this.select("div.bsx > a img").attr("src"))
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }
}

// Extension function fetchDocument
suspend fun OkHttpClient.fetchDocument(url: String): Document {
    val request = Request.Builder()
        .url(url)
        .build()
    val response = this.newCall(request).execute()
    val body = response.body?.string() ?: throw Exception("Empty body")
    return Jsoup.parse(body)
}
