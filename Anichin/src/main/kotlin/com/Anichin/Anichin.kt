package com.Anichin

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

class Anichin : MainAPI() {
    override var mainUrl = "https://anichin.moe"
    override var name = "Anichin"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.Anime)

    // simple in-memory cache: url -> (timestamp, document)
    private val pageCache = ConcurrentHashMap<String, Pair<Long, Document>>()
    private val CACHE_TTL_MS = 5 * 60 * 1000L // 5 minutes

    private fun randomString(length: Int): String {
        val charPool = ('a'..'z') + ('A'..'Z') + ('.')
        return List(length) { charPool.random() }.joinToString("")
    }

    // Single cloudflare client reused across requests (so cookie cf_clearance can persist)
    private val cloudflareClient: OkHttpClient by lazy {
        app.baseClient.newBuilder()
            .addInterceptor(CloudflareKiller())
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0 Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.5")
                    .header("Connection", "keep-alive")
                    .header("Sec-Fetch-Dest", "document")
                    .header("Sec-Fetch-Mode", "navigate")
                    .header("Sec-Fetch-Site", "same-origin")
                    .header("Upgrade-Insecure-Requests", "1")
                    .header("X-Requested-With", randomString((1..20).random()))
                    .build()
                chain.proceed(request)
            }
            // Optionally attach a CookieJar that persists cookies across runs if you implemented one.
            .build()
    }

    // === Helpers ===

    // Convert anichin/wp-content image -> i0.wp.com proxy to avoid requesting origin domain directly
    private fun toCdnUrlIfPossible(url: String?): String? {
        if (url.isNullOrBlank()) return url
        val trimmed = url.trim()
        // If it's already i0/i2 etc, keep it
        if (trimmed.contains("i0.wp.com") || trimmed.contains("i2.wp.com") || trimmed.contains("i1.wp.com")) return trimmed
        return if (trimmed.contains("/wp-content/") && (trimmed.startsWith("http://") || trimmed.startsWith("https://"))) {
            // Build i0.wp.com/{original-host}/{path} -> reliable WP proxy format
            val withoutScheme = trimmed.replace(Regex("^https?://"), "")
            "https://i0.wp.com/$withoutScheme"
        } else {
            trimmed
        }
    }

    // Robust poster extraction from element
    private fun Element.extractPoster(): String? {
        // 1) <img src=...> or <img data-src=...> or data-lazy
        val img = this.selectFirst("div.bsx > a img") ?: this.selectFirst("img")
        var poster = img?.attr("src")?.takeIf { it.isNotBlank() }
            ?: img?.attr("data-src")?.takeIf { it.isNotBlank() }
            ?: img?.attr("data-lazy")?.takeIf { it.isNotBlank() }
            ?: img?.attr("data-srcset")?.takeIf { it.isNotBlank() }?.split("\\s+".toRegex())?.firstOrNull()

        // 2) .backdrop style background-image: url('...') (used in slider)
        if (poster.isNullOrBlank()) {
            val style = this.selectFirst(".backdrop")?.attr("style").orEmpty()
            val regex = Regex("url\\(['\"]?(.*?)['\"]?\\)")
            poster = regex.find(style)?.groupValues?.get(1)
        }

        // 3) fallback: meta og:image on document (caller can call fallback again)
        return toCdnUrlIfPossible(fixUrlNull(poster))
    }

    // Cached fetchDocument (suspend) - fixed: capture receiver before withContext
    private suspend fun OkHttpClient.fetchDocumentCached(url: String, useCache: Boolean = true): Document {
        val now = System.currentTimeMillis()
        if (useCache) {
            pageCache[url]?.let { (ts, doc) ->
                if (now - ts < CACHE_TTL_MS) return doc
                else pageCache.remove(url)
            }
        }

        // capture receiver client (this) before entering lambda
        val client = this
        return withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: throw Exception("Empty body")
            val doc = Jsoup.parse(body)
            pageCache[url] = Pair(now, doc)
            doc
        }
    }

    // === Main page config ===
    override val mainPage = mainPageOf(
        "anime/?order=update" to "Rilisan Terbaru",
        "anime/?status=ongoing&order=update" to "Series Ongoing",
        "anime/?status=completed&order=update" to "Series Completed",
        "anime/?status=hiatus&order=update" to "Series Drop/Hiatus",
        "anime/?type=movie&order=update" to "Movie"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (request.data.contains("?")) "$mainUrl/${request.data}&page=$page" else "$mainUrl/${request.data}?page=$page"
        val document = cloudflareClient.fetchDocumentCached(url)
        val home = document.select("div.listupd > article").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = false
            ),
            hasNext = document.selectFirst(".pagination a.next, .pagination .next") != null
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()
        val qEncoded = URLEncoder.encode(query, "UTF-8") // <-- encode query
        for (i in 1..3) {
            val document = cloudflareClient.fetchDocumentCached("$mainUrl/page/$i/?s=$qEncoded")
            val results = document.select("div.listupd > article").mapNotNull { it.toSearchResult() }
            if (results.isEmpty()) break
            if (!searchResponse.containsAll(results)) {
                searchResponse.addAll(results)
            } else break
        }
        return searchResponse
    }

    override suspend fun load(url: String): LoadResponse {
        val fetchUrl = fixUrl(url) // <-- ensure absolute URL before fetch
        val document = cloudflareClient.fetchDocumentCached(fetchUrl)
        val title = document.selectFirst("h1.entry-title")?.text()?.trim().orEmpty()
        var poster = document.select("div.ime > img").attr("src")
        val description = document.selectFirst("div.entry-content")?.text()?.trim()
        val typeText = document.selectFirst(".spe")?.text().orEmpty()
        val isMovie = typeText.contains("Movie", ignoreCase = true)

        if (isMovie) {
            val href = document.selectFirst(".eplister li > a")?.attr("href").orEmpty()
            if (poster.isEmpty()) {
                poster = document.selectFirst("meta[property=og:image]")?.attr("content").orEmpty()
            }
            // use CDN for poster if possible
            poster = toCdnUrlIfPossible(fixUrlNull(poster)).orEmpty()
            return newMovieLoadResponse(title, url, TvType.Movie, href) {
                this.posterUrl = poster
                this.plot = description
            }
        } else {
            val epPage = document.selectFirst(".eplister li > a")?.attr("href").orEmpty()
            val doc = if (epPage.isNotBlank()) cloudflareClient.fetchDocumentCached(fixUrl(epPage)) else document
            val episodes = doc.select("div.episodelist > ul > li").map { info ->
                val epUrl = info.select("a").attr("href")
                val rawName = info.select("a span").text()
                val episodeName = rawName.substringAfter("-").substringBeforeLast("-").ifEmpty { rawName }
                val epPoster = info.selectFirst("a img")?.attr("src").orEmpty()
                newEpisode(epUrl) {
                    name = episodeName
                    posterUrl = toCdnUrlIfPossible(fixUrlNull(epPoster)).orEmpty()
                }
            }.reversed()

            if (poster.isEmpty()) {
                poster = document.selectFirst("meta[property=og:image]")?.attr("content").orEmpty()
            }
            poster = toCdnUrlIfPossible(fixUrlNull(poster)).orEmpty()

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
        val document = cloudflareClient.fetchDocumentCached(fixUrl(data))
        // mobius option approach as in your original
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

    // Robust conversion for element -> SearchResponse based on site HTML
    private fun Element.toSearchResult(): SearchResponse? {
        // Use .bsx block as container
        val anchor = this.selectFirst("div.bsx > a") ?: this.selectFirst("a")
        val title = anchor?.attr("title")?.ifEmpty { anchor?.text() } ?: this.selectFirst("h2")?.text().orEmpty()
        val href = anchor?.attr("href") ?: return null
        val fixedHref = fixUrl(href)

        val posterUrl = this.extractPoster().ifNullOrEmpty {
            // fallback to document-level og:image if element-based not found
            val docOg = this.ownerDocument()?.selectFirst("meta[property=og:image]")?.attr("content")
            toCdnUrlIfPossible(fixUrlNull(docOg))
        }

        return newMovieSearchResponse(title, fixedHref, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }
}
