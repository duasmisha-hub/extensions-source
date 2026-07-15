package eu.kanade.tachiyomi.extension.pt.mangeek

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonRequestBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale

@Source
abstract class ManGeek : HttpSource() {

    override val supportsLatest = true

    override val client = network.client.newBuilder()
        .rateLimit(3)
        .build()

    private val apiUrl = "http://geekstations.com.br/api/v2/pt".toHttpUrl()

    override fun popularMangaRequest(page: Int): Request = GET(signedUrl("home"), headers)
        .newBuilder()
        .header(PAGE_HEADER, page.coerceAtLeast(1).toString())
        .build()

    override fun popularMangaParse(response: Response): MangasPage {
        val mangas = response.parseAs<ManGeekHomeDto>().catalogMangas()
        val page = response.request.header(PAGE_HEADER)?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val start = (page - 1) * CATALOG_PAGE_SIZE
        val pageMangas = mangas.drop(start).take(CATALOG_PAGE_SIZE)

        return MangasPage(
            pageMangas.map { it.toSManga() },
            start + pageMangas.size < mangas.size,
        )
    }

    override fun latestUpdatesRequest(page: Int): Request = GET(signedUrl("home"), headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val mangas = response.parseAs<ManGeekHomeDto>().news
            .map { it.manga }
            .distinctBy { it.id }
            .map { it.toSManga() }

        return MangasPage(mangas, false)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val normalizedQuery = query.trim().lowercase(Locale.ROOT)
        val includedTags = filters.includedTags()
        val excludedTags = filters.excludedTags()

        val (mode, request) = when {
            normalizedQuery.isBlank() && includedTags.isEmpty() ->
                SEARCH_MODE_HOME to
                    GET(signedUrl("home"), headers)
            normalizedQuery.isBlank() ->
                SEARCH_MODE_DISCOVER to
                    POST(
                        signedUrl("discover").toString(),
                        headers,
                        ManGeekDiscoverBody(includedTags).toJsonRequestBody(),
                    )
            else ->
                SEARCH_MODE_QUERY to
                    POST(
                        signedUrl("search", normalizedQuery).toString(),
                        headers,
                        ManGeekSearchBody(includedTags).toJsonRequestBody(),
                    )
        }

        return request.newBuilder()
            .header(SEARCH_MODE_HEADER, mode)
            .header(PAGE_HEADER, page.coerceAtLeast(1).toString())
            .header(STATUS_HEADER, filters.status())
            .header(SORT_HEADER, filters.sort())
            .header(EXCLUDED_TAGS_HEADER, excludedTags.encodeHeader())
            .build()
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val mode = response.request.header(SEARCH_MODE_HEADER).orEmpty()
        val status = response.request.header(STATUS_HEADER).orEmpty()
        val sort = response.request.header(SORT_HEADER).orEmpty()
        val excludedTags = response.request.header(EXCLUDED_TAGS_HEADER).orEmpty().decodeHeader()

        var mangas = if (mode == SEARCH_MODE_HOME) {
            response.parseAs<ManGeekHomeDto>().catalogMangas()
        } else {
            response.parseAs<List<ManGeekMangaDto>>()
        }

        mangas = mangas
            .filter { manga ->
                excludedTags.none { excluded ->
                    manga.tags.any { it.equals(excluded, ignoreCase = true) }
                }
            }

        mangas = when (status) {
            "ongoing" -> mangas.filter { it.finished == false }
            "completed" -> mangas.filter { it.finished == true }
            else -> mangas
        }

        mangas = when (sort) {
            "views_desc" -> mangas.sortedByDescending { it.views }
            "views_asc" -> mangas.sortedBy { it.views }
            "title_asc" -> mangas.sortedBy { it.title.lowercase(Locale.ROOT) }
            "title_desc" -> mangas.sortedByDescending { it.title.lowercase(Locale.ROOT) }
            else -> mangas
        }

        if (mode != SEARCH_MODE_QUERY) {
            return MangasPage(mangas.map { it.toSManga() }, false)
        }

        val page = response.request.header(PAGE_HEADER)?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val start = (page - 1) * SEARCH_PAGE_SIZE
        val pageMangas = mangas.drop(start).take(SEARCH_PAGE_SIZE)

        return MangasPage(
            pageMangas.map { it.toSManga() },
            start + pageMangas.size < mangas.size,
        )
    }

    override fun getFilterList(): FilterList = getManGeekFilters()

    override fun getMangaUrl(manga: SManga): String = "$baseUrl${manga.url}"

    override fun mangaDetailsRequest(manga: SManga): Request = GET(
        signedUrl("manga", manga.url.id()),
        headers,
    )

    override fun mangaDetailsParse(response: Response): SManga = response.parseAs<ManGeekMangaDto>().toSManga(details = true)

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> = response.parseAs<ManGeekMangaDto>().chapters
        .asReversed()
        .map { it.toSChapter() }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl${chapter.url}"

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> = Observable.fromCallable {
        val chapterId = chapter.url.id()
        val primary = runCatching { fetchChapterPages("chapter", chapterId) }.getOrNull()

        if (!primary.isNullOrEmpty()) {
            primary
        } else {
            fetchChapterPages("mirror", chapterId)
        }
    }

    private fun fetchChapterPages(route: String, chapterId: String): List<Page> = client.newCall(GET(signedUrl(route, chapterId), headers)).execute().use { response ->
        if (!response.isSuccessful) error("HTTP ${response.code}")
        response.parseAs<ManGeekChapterPagesDto>().toPageList()
    }

    override fun pageListParse(response: Response): List<Page> = throw UnsupportedOperationException()

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    private fun signedUrl(route: String, input: String? = null): HttpUrl {
        val nonce = System.currentTimeMillis().toString(16).uppercase(Locale.ROOT)
        val signatureInput = input ?: nonce
        val key = md5("M<$signatureInput#MANG33K>D")

        return apiUrl.newBuilder()
            .addPathSegment(route)
            .addPathSegment(nonce)
            .apply { input?.let(::addPathSegment) }
            .addPathSegment(key)
            .build()
    }

    private fun md5(value: String): String = MessageDigest.getInstance("MD5")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun String.id(): String = substringAfterLast('/').substringBefore('?')

    private fun List<String>.encodeHeader(): String = joinToString("|") {
        URLEncoder.encode(it, StandardCharsets.UTF_8.name())
    }

    private fun String.decodeHeader(): List<String> = takeIf(String::isNotBlank)
        ?.split('|')
        ?.map { URLDecoder.decode(it, StandardCharsets.UTF_8.name()) }
        .orEmpty()

    companion object {
        private const val SEARCH_MODE_HEADER = "X-Mihon-ManGeek-Search-Mode"
        private const val PAGE_HEADER = "X-Mihon-ManGeek-Page"
        private const val STATUS_HEADER = "X-Mihon-ManGeek-Status"
        private const val SORT_HEADER = "X-Mihon-ManGeek-Sort"
        private const val EXCLUDED_TAGS_HEADER = "X-Mihon-ManGeek-Excluded-Tags"

        private const val SEARCH_MODE_HOME = "home"
        private const val SEARCH_MODE_DISCOVER = "discover"
        private const val SEARCH_MODE_QUERY = "query"
        private const val CATALOG_PAGE_SIZE = 24
        private const val SEARCH_PAGE_SIZE = 24
    }
}
