package eu.kanade.tachiyomi.extension.pt.onereader

import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.parseAs
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.time.Instant
import java.util.Locale

@Serializable
internal data class SearchDto(
    val data: List<MangaDto> = emptyList(),
    val page: Int = 1,
    val totalPages: Int = 1,
)

@Serializable
internal data class MangaDto(
    @SerialName("manga_key")
    val mangaKey: String,
    @SerialName("display_name")
    val displayName: String,
    val name: String? = null,
    @SerialName("japanese_name")
    val japaneseName: String? = null,
    val author: String? = null,
    val artist: String? = null,
    val synopsis: String? = null,
    val status: String? = null,
    val type: String? = null,
    @SerialName("origin_country")
    val originCountry: String? = null,
    val year: Int? = null,
    val tags: JsonElement? = null,
    @SerialName("cover_image")
    val coverImage: String? = null,
    @SerialName("publisher_name")
    val publisherName: String? = null,
) {
    fun toSManga(details: Boolean = false): SManga = SManga.create().apply {
        url = mangaKey
        title = displayName
        thumbnail_url = coverImage
        author = this@MangaDto.author?.takeIf(String::isNotBlank)
        artist = this@MangaDto.artist?.takeIf(String::isNotBlank)
        genre = tags.toTagList().takeIf(List<String>::isNotEmpty)?.joinToString()
        status = this@MangaDto.status.toStatus()
        description = buildDescription()
        initialized = details
    }

    private fun buildDescription(): String? = buildList {
        synopsis?.takeIf(String::isNotBlank)?.let(::add)

        val metadata = buildList {
            name?.takeIf { it.isNotBlank() && !it.equals(displayName, ignoreCase = true) }
                ?.let { add("Título original: $it") }
            japaneseName?.takeIf {
                it.isNotBlank() &&
                    !it.equals(displayName, ignoreCase = true) &&
                    !it.equals(name, ignoreCase = true)
            }?.let { add("Título nativo: $it") }
            type?.takeIf(String::isNotBlank)?.let { add("Tipo: $it") }
            originCountry?.takeIf(String::isNotBlank)?.let { add("País: $it") }
            year?.let { add("Ano: $it") }
            publisherName?.takeIf(String::isNotBlank)?.let { add("Editora: $it") }
        }

        if (metadata.isNotEmpty()) add(metadata.joinToString("\n"))
    }.takeIf(List<String>::isNotEmpty)?.joinToString("\n\n")
}

@Serializable
internal data class ChapterDto(
    @SerialName("manga_key")
    val mangaKey: String,
    @SerialName("posted_date")
    val postedDate: String? = null,
)

@Serializable
internal data class PagesDto(
    val pages: List<String> = emptyList(),
)

private fun JsonElement?.toTagList(): List<String> = when (this) {
    is JsonArray -> mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
    is JsonPrimitive ->
        contentOrNull
            ?.let { runCatching { it.parseAs<List<String>>() }.getOrDefault(emptyList()) }
            .orEmpty()
    else -> emptyList()
}

private fun String?.toStatus(): Int = when (this?.trim()?.lowercase(Locale.ROOT)) {
    "em lançamento", "lançando" -> SManga.ONGOING
    "completo" -> SManga.COMPLETED
    "hiatus", "hiato" -> SManga.ON_HIATUS
    "cancelado" -> SManga.CANCELLED
    else -> SManga.UNKNOWN
}

internal fun String?.toTimestamp(): Long = this
    ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrDefault(0L) }
    ?: 0L
