package com.Anichin

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Headers
import okhttp3.OkHttpClient
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

    // Gunakan CloudflareKiller untuk melewati proteksi
    private val cloudflareKiller by lazy { CloudflareKiller() }

    override val mainPage = mainPageOf(
        "$mainUrl/" to "Terbaru",
        "$mainUrl/page/2/" to "Halaman 2"
    )

    // Fungsi untuk mendapatkan dokumen dengan bypass Cloudflare
    private suspend fun getDocument(url: String): Document {
        return try {
            // Coba akses langsung terlebih dahulu
            val directResponse = app.get(url)
            var document = directResponse.document
            
            // Periksa apakah halaman mengandung Cloudflare challenge
            if (document.select("title").text().contains("Cloudflare") || 
                document.select("div#challenge-error-title").isNotEmpty() ||
                document.select("form#challenge-form").isNotEmpty()) {
                
                // Gunakan CloudflareKiller jika terdeteksi Cloudflare
                document = withContext(Dispatchers.IO) {
                    cloudflareKiller.getDocument(
                        client = app.client,
                        url = url,
                        headers = directResponse.headers,
                        cookies = directResponse.cookies,
                        body = directResponse.body
                    )
                }
            }
            document
        } catch (e: Exception) {
            // Fallback ke request biasa jika terjadi error
            app.get(url).document
        }
    }

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val document = getDocument(request.data)
        val homePageList = mutableListOf<HomePageList>()

        // Ekstraksi daftar anime dari berbagai bagian
        val sections = document.select("div.listupd")
        
        sections.forEach { section ->
            val parentTitle = section.previousElementSibling()?.select("h2, h3")?.text() ?: "Anime Terbaru"
            val animeList = section.select("article.bs").mapNotNull { element ->
                parseAnimeFromElement(element)
            }
            
            if (animeList.isNotEmpty()) {
                homePageList.add(HomePageList(parentTitle, animeList))
            }
        }

        return HomePageResponse(homePageList)
    }

    private fun parseAnimeFromElement(element: Element): AnimeSearchResponse? {
        return try {
            val title = element.select("div.tt h2").text()
            val url = element.select("a").attr("href")
            val image = element.select("img").attr("src")
            val episodeText = element.select("span.epx").text()
            val episode = episodeText.filter { it.isDigit() }.toIntOrNull() ?: 1
            
            AnimeSearchResponse(
                name = title,
                url = url,
                apiName = this.name,
                type = TvType.Anime,
                posterUrl = image,
                episode = episode
            )
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/?s=$query"
        val document = getDocument(searchUrl)
        
        return document.select("article.bs").mapNotNull { element ->
            parseAnimeFromElement(element)
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = getDocument(url)
        
        val title = document.select("h1.entry-title").text()
        val description = document.select("div.entry-content p").text()
        val poster = document.select("div.thumb img").attr("src")
        val episodes = document.select("div.eplister ul li").map { epElement ->
            val episodeNum = epElement.select("div.epl-num").text().toIntOrNull() ?: 0
            val episodeUrl = epElement.select("a").attr("href")
            Episode(episodeUrl, episode = episodeNum)
        }.reversed()

        return AnimeLoadResponse(
            name = title,
            url = url,
            apiName = this.name,
            type = TvType.Anime,
            posterUrl = poster,
            episodes = episodes,
            plot = description
        )
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = getDocument(data)
        val links = document.select("div.download-eps a")
        
        links.forEach { link ->
            val url = link.attr("href")
            val quality = link.text()
            
            if (url.isNotBlank()) {
                callback(
                    ExtractorLink(
                        name = this.name,
                        source = url,
                        url = url,
                        quality = Qualities.Unknown.value,
                        isM3u8 = url.contains("m3u8")
                    )
                )
            }
        }
        
        return true
    }
}
