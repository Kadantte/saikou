package ani.saikou.anime.source.extractors

import ani.saikou.anime.Episode
import ani.saikou.anime.source.Extractor
import ani.saikou.getSize
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*
import org.jsoup.Jsoup

class FPlayer: Extractor() {
    override fun getStreamLinks(name: String, url: String): Episode.StreamLinks {
        val apiLink = url.replace("/v/","/api/source/")
        val tempQuality = mutableListOf<Episode.Quality>()
        val jsonResponse = Json.decodeFromString<JsonObject>(Jsoup.connect(apiLink).ignoreContentType(true).post().body().text())
        if(jsonResponse["success"].toString() == "true") {
            jsonResponse.jsonObject["data"]!!.jsonArray.forEach {
                tempQuality.add(
                    Episode.Quality(
                        it.jsonObject["file"].toString().trim('"'),
                        it.jsonObject["label"].toString().trim('"'),
                        getSize(it.jsonObject["file"].toString().trim('"'))
                    )
                )
            }
        }
        return Episode.StreamLinks(
            name,
            tempQuality,
            null
        )
    }

}