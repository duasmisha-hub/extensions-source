package eu.kanade.tachiyomi.extension.pt.onereader

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response

@Source
abstract class OneReader : HttpSource() {

    override val supportsLatest = true

    override val client = network.client.newBuilder()
        .rateLimit(3)
        .build()

    private val apiUrl = "https://api.onereader.net"

    override fun headersBuilder() = super.headersBuilder()
        .set("Accept", "*/*")
        .set("Origin", baseUrl)
        .set("Referer", "$baseUrl/")
        .set("User-Agent", BROWSER_USER_AGENT)

    override fun popularMangaRequest(page: Int): Request = GET("$apiUrl/api/mangas/trending-weekly", headers)

    override fun popularMangaParse(response: Response): MangasPage = MangasPage(
        response.parseAs<List<MangaDto>>().map(MangaDto::toSManga),
        false,
    )

    override fun latestUpdatesRequest(page: Int): Request = searchRequest(
        page = page,
        query = "",
        order = "recent",
        genre = "",
        type = "",
        status = "",
    )

    override fun latestUpdatesParse(response: Response): MangasPage = searchMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        var order = "recent"
        var genre = ""
        var type = ""
        var status = ""

        filters.forEach { filter ->
            when (filter) {
                is OrderFilter -> order = filter.selected()
                is TagFilter -> genre = filter.selected()
                is TypeFilter -> type = filter.selected()
                is StatusFilter -> status = filter.selected()
                else -> {}
            }
        }

        return searchRequest(page, query, order, genre, type, status)
    }

    private fun searchRequest(
        page: Int,
        query: String,
        order: String,
        genre: String,
        type: String,
        status: String,
    ): Request {
        val url = "$apiUrl/api/mangas/search".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", "24")
            .addQueryParameter("order", order)
            .addQueryParameter("role", "standard")
            .addQueryParameter("search", query.trim())
            .addQueryParameter("genre", genre)
            .addQueryParameter("type", type)
            .addQueryParameter("status", status)
            .build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val result = response.parseAs<SearchDto>()
        return MangasPage(
            result.data.map(MangaDto::toSManga),
            result.page < result.totalPages,
        )
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manga-details?id=${manga.url}"

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$apiUrl/api/mangas/${manga.url}", headers)

    override fun mangaDetailsParse(response: Response): SManga = response.parseAs<MangaDto>().toSManga(details = true)

    override fun chapterListRequest(manga: SManga): Request = GET("$apiUrl/api/chapters/${manga.url}", headers)

    override fun chapterListParse(response: Response): List<SChapter> = response.parseAs<Map<String, ChapterDto>>()
        .entries
        .sortedByDescending { it.key.toDoubleOrNull() ?: Double.NEGATIVE_INFINITY }
        .map { (number, chapter) ->
            SChapter.create().apply {
                name = "Capítulo $number"
                chapter_number = number.toFloatOrNull() ?: -1F
                date_upload = chapter.postedDate.toTimestamp()
                url = "/leitor?id=${chapter.mangaKey}&capitulo=$number"
            }
        }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl${chapter.url}"

    override fun pageListRequest(chapter: SChapter): Request {
        val webUrl = getChapterUrl(chapter).toHttpUrl()
        val mangaKey = requireNotNull(webUrl.queryParameter("id"))
        val chapterNumber = requireNotNull(webUrl.queryParameter("capitulo")).replace('.', '_')
        return GET("$apiUrl/api/chapters/$mangaKey/$chapterNumber", headers)
    }

    override fun pageListParse(response: Response): List<Page> = response.parseAs<PagesDto>().pages.mapIndexed { index, path ->
        Page(
            index = index,
            imageUrl = when {
                path.startsWith("http://") || path.startsWith("https://") -> path
                else -> "$apiUrl/${path.trimStart('/')}"
            },
        )
    }

    override fun imageUrlParse(response: Response): String = ""

    override fun getFilterList(): FilterList = FilterList(
        OrderFilter(),
        Filter.Separator(),
        TypeFilter(),
        StatusFilter(),
        TagFilter(),
    )

    companion object {
        private const val BROWSER_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 15; Mobile) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/140.0.0.0 Mobile Safari/537.36"
    }
}
