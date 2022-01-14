package ani.saikou.anime.source.extractors

import ani.saikou.anime.Episode
import ani.saikou.anime.source.Extractor

class RapidCloud : Extractor() {
    override fun getStreamLinks(name: String, url: String): Episode.StreamLinks {
        return Episode.StreamLinks("", listOf(Episode.Quality("", "", null)),null)
    }
}