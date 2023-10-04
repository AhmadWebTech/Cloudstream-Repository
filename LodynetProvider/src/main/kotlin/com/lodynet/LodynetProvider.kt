package com.lodynet

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addMalId
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

class Lodynet : MainAPI() {
    override var lang = "ar"
    override var mainUrl = "https://lodynet.link"
    override var name = "Lodynet"
    override val usesWebView = false
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie)

    private fun Element.toSearchResponse(): SearchResponse? {
        val url = select("a").attr("href")
        val title = select(".SliderItemDescription h2").text()
        val posterUrl = select("img").attr("src")
        return MovieSearchResponse(
            cleanTitle(title),
            url,
            name,
            null,
            posterUrl,
            null,
            null,
            posterHeaders = cfKiller.getCookieHeaders(alternativeUrl).toMap()
        )
    }

    private fun cleanTitle(title: String): String {
        return title.replace("فيلم|مترجم|مسلسل|مشاهدة".toRegex(), "").trim()
    }

    override val mainPage = mainPageOf(
        "$mainUrl/category/افلام-هندية/" to "Indian Movies",
        "$mainUrl/bالمسلسلات-هندية-مترجمة/" to "Indian Series",
        "$mainUrl/dubbed-indian-series-p4/" to "Dubbed Indian Series",
        "$mainUrl/turkish-series-2b/" to "Turkish Series",
        "$mainUrl/dubbed-turkish-series-g/" to "Dubbed Turkish Series",
        "$mainUrl/tag/new-asia/" to "Asia Series",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val doc = app.get(request.data + page).document
        val list = doc.select(".BlocksArea li").mapNotNull { element ->
            element.toSearchResponse()
        }
        return newHomePageResponse(request.name, list)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val q = query.replace(" ", "+")
        return app.get("$mainUrl/search/$q").document.select(".BlocksArea li").mapNotNull {
            it.toSearchResponse()
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val posterUrl = doc.select(".Poster img").attr("src") ?: null
        val title = extractTitle(doc)
        val isMovie = doc.select(".TitleSingle > ul > li:nth-child(1) > a").text().contains("فيلم")

        return if (isMovie) {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = posterUrl
            }
        } else {
            val episodes = ArrayList<Episode>()
            doc.select(".EpisodesList a").forEach { el ->
                episodes.add(
                    Episode(
                        el.attr("href"),
                        el.text(),
                        getSeasonFromString(el.select(".BlockTitle").text()),
                        null
                    )
                )
            }

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = posterUrl
            }
        }
    }

    private fun extractTitle(doc: Document): String {
        val titleElement = doc.select(".TitleSingle > ul > li:nth-child(1) > a")
        return if (titleElement.isNotEmpty()) {
            cleanTitle(titleElement.text())
        } else {
            cleanTitle(doc.select(".TitleInner h2").text())
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document
        val watchList = doc.select("ul.ServersList > li")
        watchList.forEach { li ->
            val iframeUrl = li.attr("data-embed")
            loadExtractor(iframeUrl, data, subtitleCallback, callback)
        }
        return true
    }
}
