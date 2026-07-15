package eu.kanade.tachiyomi.extension.pt.mangeek

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import keiyoushi.utils.firstInstanceOrNull

internal class StatusFilter :
    Filter.Select<String>(
        "Status",
        arrayOf("Todos", "Em andamento", "Concluído"),
    ) {
    val value: String
        get() = arrayOf("all", "ongoing", "completed")[state]
}

internal class SortFilter :
    Filter.Select<String>(
        "Ordenar por",
        arrayOf(
            "Padrão",
            "Mais visualizados",
            "Menos visualizados",
            "Título (A-Z)",
            "Título (Z-A)",
        ),
    ) {
    val value: String
        get() = arrayOf("default", "views_desc", "views_asc", "title_asc", "title_desc")[state]
}

internal class TagFilter(name: String) : Filter.TriState(name)

internal class TagsFilter :
    Filter.Group<TagFilter>(
        "Tags (incluir / excluir)",
        TAGS.map(::TagFilter),
    )

internal fun FilterList.status(): String = firstInstanceOrNull<StatusFilter>()?.value ?: "all"

internal fun FilterList.sort(): String = firstInstanceOrNull<SortFilter>()?.value ?: "default"

internal fun FilterList.includedTags(): List<String> = firstInstanceOrNull<TagsFilter>()?.state
    ?.filter { it.state == Filter.TriState.STATE_INCLUDE }
    ?.map { it.name }
    .orEmpty()

internal fun FilterList.excludedTags(): List<String> = firstInstanceOrNull<TagsFilter>()?.state
    ?.filter { it.state == Filter.TriState.STATE_EXCLUDE }
    ?.map { it.name }
    .orEmpty()

internal fun getManGeekFilters() = FilterList(
    StatusFilter(),
    SortFilter(),
    Filter.Separator(),
    TagsFilter(),
)

private val TAGS = listOf(
    "+16",
    "+18",
    "Artes marciais",
    "Aventura",
    "Ação",
    "Comédia",
    "Culinária",
    "Demônio",
    "Doujinshi",
    "Drama",
    "Ecchi",
    "Escolar",
    "Esporte",
    "Fantasia",
    "Ficção",
    "Filosófico",
    "Harém",
    "Hentai",
    "Histórico",
    "Horror",
    "Isekai",
    "Jogo",
    "Josei",
    "Magia",
    "Manga",
    "Manhua",
    "Manhwa",
    "Mecha",
    "Medicina",
    "Militar",
    "Mistério",
    "Monstro",
    "Murim",
    "Musical",
    "Ninja",
    "Novel",
    "One-shot",
    "Policial",
    "Psicológico",
    "Romance",
    "Samurai",
    "Sci-fi",
    "Seinen",
    "Shoujo",
    "Shounen",
    "Slice of life",
    "Sobrenatural",
    "Super poderes",
    "Tensei",
    "Terror",
    "Tragédia",
    "Vampiro",
    "Webtoon",
    "Zumbi",
)
