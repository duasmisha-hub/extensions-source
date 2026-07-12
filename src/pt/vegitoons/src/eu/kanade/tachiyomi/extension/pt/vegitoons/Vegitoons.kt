package eu.kanade.tachiyomi.extension.pt.vegitoons

import eu.kanade.tachiyomi.multisrc.greenshit.GreenShit
import eu.kanade.tachiyomi.multisrc.greenshit.GreenShitMangaDto
import eu.kanade.tachiyomi.multisrc.greenshit.toSManga
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response

@Source
abstract class Vegitoons : GreenShit() {
    override val apiUrl = "https://api.vegitoons.black"
    override val cdnUrl = "https://cdn.vegitoons.black"
    override val cdnApiUrl = cdnUrl
    override val scanId = "1"

    override val defaultGenreId = "1,4,6,8"
    override val limitPerPage = "24"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$apiUrl/obras/buscar".toHttpUrl().newBuilder()
            .addQueryParameter("pagina", page.toString())
            .addQueryParameter("limite", limitPerPage)

        if (query.isNotBlank()) {
            url.addQueryParameter("busca", query.trim())
        }

        filters.forEach { filter ->
            when (filter) {
                is GeneroFilter -> url.addQueryParameterIfNotEmpty("gen_id", filter.selected)
                is FormatoFilter -> url.addQueryParameterIfNotEmpty("formt_id", filter.selected)
                is StatusFilter -> url.addQueryParameterIfNotEmpty("stt_id", filter.selected)
                is TagsFilter ->
                    filter.state
                        .filter { it.state }
                        .joinToString(",") { it.value }
                        .takeIf { it.isNotEmpty() }
                        ?.let { url.addQueryParameter("tag_ids", it) }
                is SortFilter -> getSortFilterOptions()[filter.state].second
                    .takeIf { it.isNotEmpty() }
                    ?.split(",", limit = 2)
                    ?.let { (orderBy, orderDirection) ->
                        url.addQueryParameter("orderBy", orderBy)
                        url.addQueryParameter("orderDirection", orderDirection)
                    }
                else -> {}
            }
        }

        return GET(url.build(), headers)
    }

    override fun getGeneroFilterOptions(): Array<Pair<String, String>> = arrayOf(
        Pair("Padrão (sem +18)", defaultGenreId),
        Pair("Conteúdo +18", "5,7,16,18"),
        Pair("Todos", "1,4,5,6,7,8,16,18"),
        Pair("Hentais", "5"),
        Pair("Livres", "1"),
        Pair("Mangás", "8"),
        Pair("Novel", "6"),
        Pair("One-Shot", "18"),
        Pair("Shoujo / Romances", "4"),
        Pair("Yaoi", "7"),
        Pair("Yuri", "16"),
    )

    override fun getFormatoFilterOptions(): Array<Pair<String, String>> = arrayOf(
        Pair("Todos", ""),
        Pair("Anime", "7"),
        Pair("Livre", "22"),
        Pair("Mangá", "3"),
        Pair("Manhua", "2"),
        Pair("Manhwa", "1"),
        Pair("Nacional", "5"),
        Pair("Novel", "4"),
        Pair("One Shot", "19"),
    )

    override fun getSortFilterOptions(): Array<Pair<String, String>> = arrayOf(
        Pair("Padrão", ""),
        Pair("Última atualização", "ultima_atualizacao,DESC"),
        Pair("Lançamentos (recentes)", "criacao,DESC"),
        Pair("Lançamentos (antigos)", "criacao,ASC"),
        Pair("Mais visualizadas", "visualizacoes,DESC"),
        Pair("Menos visualizadas", "visualizacoes,ASC"),
        Pair("A-Z", "nome,ASC"),
        Pair("Z-A", "nome,DESC"),
    )

    override fun mangaDetailsParse(response: Response): SManga {
        val dto = response.parseAs<GreenShitMangaDto>()
        return dto.toSManga(cdnApiUrl, isDetails = true).apply {
            status = when (dto.status?.name?.lowercase()) {
                "em andamento", "ativo" -> SManga.ONGOING
                "concluído", "concluido" -> SManga.COMPLETED
                "hiato" -> SManga.ON_HIATUS
                "cancelado" -> SManga.CANCELLED
                else -> SManga.UNKNOWN
            }
        }
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl${manga.url}"
}
