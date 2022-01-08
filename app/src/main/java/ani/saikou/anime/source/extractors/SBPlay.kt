package ani.saikou.anime.source.extractors

import ani.saikou.anime.Episode
import ani.saikou.anime.source.Extractor
import ani.saikou.getSize
import org.jsoup.Jsoup

class SBPlay: Extractor(){
    override fun getStreamLinks(name: String, url: String): Episode.StreamLinks {
        val tempQuality = mutableListOf<Episode.Quality>()
        Jsoup.connect(url.replace("/e/","/d/")).get().select("table > tbody > tr > td > a").forEach {
            val tempArray = it.attr("onclick").split("'")
            val fileURL = Jsoup.connect("https://sbplay.one/dl?op=download_orig&id=${tempArray[1]}&mode=${tempArray[3]}&hash=${tempArray[5]}")
                .get().select(".contentbox > span > a").attr("abs:href")
            tempQuality.add(
                Episode.Quality(
                    fileURL,
                    it.text(),
                    getSize(fileURL)
                )
            )
        }
        return Episode.StreamLinks(
            name,
            tempQuality,
            "http://sbplay.one"
        )
    }
}
