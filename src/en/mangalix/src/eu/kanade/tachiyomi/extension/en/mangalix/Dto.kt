package eu.kanade.tachiyomi.extension.en.mangalix

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

@Serializable
internal data class MangaDto(
    val id: String = "",
    val slug: String = "",
    val title: String = "",
    val description: String = "",
    val coverImage: String = "",
    val author: String = "",
    val status: String = "",
    val views: String = "",
    val rating: Double = 0.0,
    val releaseYear: Double = 0.0,
    val genres: List<String> = emptyList(),
    val latestChapter: LatestChapterDto? = null,
) {
    val latestTimestamp: Long
        get() = latestChapter?.releaseDate.toMangaTimestamp()

    val viewCount: Long
        get() {
            val normalized = views.trim().replace(",", "")
            if (normalized.isEmpty()) return 0L

            val multiplier = when (normalized.last().uppercaseChar()) {
                'K' -> 1_000L
                'M' -> 1_000_000L
                'B' -> 1_000_000_000L
                else -> 1L
            }
            val value = if (multiplier == 1L) normalized else normalized.dropLast(1)

            return value.toDoubleOrNull()?.times(multiplier)?.toLong() ?: 0L
        }

    fun toSManga(baseUrl: String): SManga = SManga.create().apply {
        url = "/manga/$slug"
        title = this@MangaDto.title
        thumbnail_url = coverImage.takeIf { it.isNotBlank() }?.let {
            when {
                it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) -> it
                it.startsWith("//") -> "https:$it"
                else -> "${baseUrl.trimEnd('/')}/${it.trimStart('/')}"
            }
        }
        author = this@MangaDto.author.takeIf { it.isNotBlank() }
        description = this@MangaDto.description.takeIf { it.isNotBlank() }
        genre = genres.takeIf { it.isNotEmpty() }?.joinToString()
        status = this@MangaDto.status.toMangaStatus()
        initialized = true
    }
}

@Serializable
internal data class LatestChapterDto(
    val id: String = "",
    val number: Double = 0.0,
    val releaseDate: String? = null,
)

@Serializable
internal data class ChapterDto(
    val id: String = "",
    val number: Double = 0.0,
    val title: String = "",
    val pages: List<String> = emptyList(),
    val releaseDate: String? = null,
)

internal fun String?.toMangaTimestamp(): Long = this?.let { value ->
    runCatching { Instant.parse(value).toEpochMilli() }
        .recoverCatching { LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
        .getOrDefault(0L)
} ?: 0L

private fun String.toMangaStatus(): Int = when (
    lowercase(Locale.ROOT)
        .trim()
        .replace("-", " ")
        .replace(Regex("""\s+"""), " ")
) {
    "ongoing", "publishing", "releasing", "active" -> SManga.ONGOING
    "completed", "complete", "finished" -> SManga.COMPLETED
    "hiatus", "on hiatus", "paused" -> SManga.ON_HIATUS
    "cancelled", "canceled", "dropped", "axed", "discontinued" -> SManga.CANCELLED
    else -> SManga.UNKNOWN
}
