package com.Anichin

import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup
import com.lagradost.cloudstream3.network.CloudflareKiller
import okhttp3.Interceptor
import okhttp3.Response

class Anichin : MainAPI() {
    override var mainUrl = "https://anichin.moe"
    override var name = "Anichin"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Anime, TvType.Movie)

    // Cloudflare bypass
    private val cfKiller = CloudflareKiller()

    override val mainPage = mainPageOf(
        "" to "Rilisan Terbaru",
        "anime/?status=ongoing" to "Series Ongoing",
        "anime/?status=completed" to "Series Completed",
        "anime/?status=hiatus" to "Series Drop/Hiatus",
        "anime/?type=movie" to "Movie"
    )

    // Fungsi untuk mendapatkan document dengan Cloudflare bypass
    private suspend fun getProtectedDocument(url: String): org.jsoup.nodes.Document {
        return try {
            // Gunakan app.get dengan interceptor CloudflareKiller
            app.get(url, interceptor = cfKiller).document
        } catch (e: Exception) {
            // Fallback ke app.get biasa jika gagal
            app.get(url).document
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page > 1) "${mainUrl}/${request.data}&page=$page" else "${mainUrl}/${request.data}"
        
        val document = getProtectedDocument(url)
        
        val home = document.select("article.bs").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = false
            ),
            hasNext = home.isNotEmpty()
        )
    }

    private fun Element.toSearchResult(): SearchResponse {
        val title = this.selectFirst("div.tt h2")?.text()?.trim() ?: this.selectFirst("div.tt")?.text()?.trim()
            ?: "No Title"
        
        val href = fixUrl(this.selectFirst("a")?.attr("href") ?: "")
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))
        
        val type = this.selectFirst("div.typez")?.text()?.trim() ?: "Anime"
        
        return if (type.contains("Movie", true)) {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
            }
        } else {
            newAnimeSearchResponse(title, href, TvType.Anime) {
                this.posterUrl = posterUrl
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()
        
        val searchUrl = "$mainUrl/?s=${query.replace(" ", "+")}"
        val document = getProtectedDocument(searchUrl)
        
        val results = document.select("article.bs").mapNotNull { it.toSearchResult() }
        searchResponse.addAll(results)

        return searchResponse
    }

    override suspend fun load(url: String): LoadResponse {
        val document = getProtectedDocument(url)
        
        val title = document.selectFirst("h1.entry-title")?.text()?.trim() ?: "No Title"
        val poster = document.selectFirst("div.thumb img")?.attr("src") 
            ?: document.selectFirst("meta[property=og:image]")?.attr("content") 
            ?: ""
        
        val description = document.selectFirst("div.entry-content")?.text()?.trim()
            ?: document.selectFirst("meta[property=og:description]")?.attr("content") 
            ?: ""
        
        // Determine type
        val typeText = document.selectFirst("div.spe")?.text()?.lowercase() ?: ""
        val isMovie = typeText.contains("movie") || document.selectFirst("div.typez.Movie") != null
        
        return if (isMovie) {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = description
            }
        } else {
            val episodes = document.select("div.eplister li").mapNotNull { ep ->
                val epNum = ep.selectFirst("div.epl-num")?.text()?.trim() ?: "1"
                val epUrl = fixUrl(ep.selectFirst("a")?.attr("href") ?: "")
                val epTitle = ep.selectFirst("div.epl-title")?.text()?.trim() ?: "Episode $epNum"
                val epPoster = ep.selectFirst("img")?.attr("src") ?: poster
                
                newEpisode(epUrl) {
                    this.name = epTitle
                    this.posterUrl = epPoster
                    this.episode = epNum.toIntOrNull()
                }
            }
            
            newTvSeriesLoadResponse(title, url, TvType.Anime, episodes) {
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
        val document = getProtectedDocument(data)
        
        // Extract video iframes/players
        document.select("div.mobius option, iframe").forEach { element ->
            var videoUrl = when {
                element.`is`("option") -> {
                    val base64 = element.attr("value")
                    if (base64.isNotBlank()) {
                        try {
                            val decoded = base64Decode(base64)
                            Jsoup.parse(decoded).selectFirst("iframe")?.attr("src")
                        } catch (e: Exception) {
                            null
                        }
                    } else null
                }
                element.`is`("iframe") -> element.attr("src")
                else -> null
            }
            
            videoUrl?.let { url ->
                if (url.isNotBlank()) {
                    loadExtractor(fixUrl(url), subtitleCallback, callback)
                }
            }
        }
        
        return true
    }

    // Tambahkan interceptor untuk video streams
    override fun getVideoInterceptor(extractorLink: ExtractorLink): Interceptor {
        return object : Interceptor {
            override fun intercept(chain: Interceptor.Chain): Response {
                val response = cfKiller.intercept(chain)
                return response
            }
        }
    }
}
