package eu.kanade.tachiyomi.extension.pt.mangeek

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class ManGeekHomeDto(
    val tops: List<ManGeekMangaDto> = emptyList(),
    val trending: List<ManGeekMangaDto> = emptyList(),
    val news: List<ManGeekNewsDto> = emptyList(),
    val reading: List<ManGeekNewsDto> = emptyList(),
    val categories: List<ManGeekCategoryDto> = emptyList(),
) {
    fun catalogMangas(): List<ManGeekMangaDto> = buildList {
        addAll(tops)
        addAll(trending)
        addAll(news.map { it.manga })
        addAll(reading.map { it.manga })
        categories.forEach { addAll(it.list) }
    }.distinctBy { it.id }
}

@Serializable
internal data class ManGeekNewsDto(
    val manga: ManGeekMangaDto,
)

@Serializable
internal data class ManGeekCategoryDto(
    val list: List<ManGeekMangaDto> = emptyList(),
)

@Serializable
internal data class ManGeekMangaDto(
    val id: Long,
    val title: String,
    @SerialName("alternative_title") val alternativeTitle: String? = null,
    val description: String? = null,
    val tags: List<String> = emptyList(),
    val thumbnail: String? = null,
    val finished: Boolean? = null,
    val views: Long = 0L,
    val chapters: List<ManGeekChapterDto> = emptyList(),
) {
    fun toSManga(details: Boolean = false): SManga = SManga.create().apply {
        url = "/manga/$id"
        title = this@ManGeekMangaDto.title
        thumbnail_url = thumbnail?.normalizeManGeekImageUrl()
        status = when (finished) {
            true -> SManga.COMPLETED
            false -> SManga.ONGOING
            null -> SManga.UNKNOWN
        }

        if (details) {
            description = buildList {
                alternativeTitle
                    ?.takeIf { it.isNotBlank() && !it.equals(title, ignoreCase = true) }
                    ?.let { add("Título alternativo: $it") }
                this@ManGeekMangaDto.description?.trim()?.takeIf(String::isNotBlank)?.let(::add)
            }.joinToString("\n\n")
            genre = tags.joinToString()
            initialized = true
        }
    }
}

@Serializable
internal data class ManGeekChapterDto(
    val id: Long,
    val title: String,
) {
    fun toSChapter(): SChapter = SChapter.create().apply {
        url = "/chapter/$id"
        name = title
        CHAPTER_NUMBER.find(title)?.value
            ?.replace(',', '.')
            ?.toFloatOrNull()
            ?.let { chapter_number = it }
    }
}

@Serializable
internal data class ManGeekChapterPagesDto(
    val pages: List<String> = emptyList(),
) {
    fun toPageList(): List<Page> = pages.mapIndexed { index, imageUrl ->
        Page(index, imageUrl = imageUrl.normalizeManGeekImageUrl())
    }
}

@Serializable
internal data class ManGeekSearchBody(
    val tags: List<String>,
)

@Serializable
internal data class ManGeekDiscoverBody(
    val tags: List<String>,
    val ignore: List<Long> = emptyList(),
)

internal fun String.normalizeManGeekImageUrl(): String = when {
    startsWith("http://51.79.78.152") -> replaceFirst(
        "http://51.79.78.152",
        "https://static.geekstations.com.br",
    )
    startsWith("https://51.79.78.152") -> replaceFirst(
        "https://51.79.78.152",
        "https://static.geekstations.com.br",
    )
    else -> this
}

private val CHAPTER_NUMBER = Regex("""\d+(?:[.,]\d+)?""")
