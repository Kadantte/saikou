package ani.saikou.anime.source.extractors

import ani.saikou.anime.Episode
import ani.saikou.anime.source.Extractor
import org.jsoup.Jsoup

class Doodla : Extractor() {
    // works, need cloudflare bypass
    override fun getStreamLinks(name: String, url: String): Episode.StreamLinks {
//        println(name)
        val stockPage = Jsoup.connect(url.replace("/e/","/d/")).get()
        val size = stockPage.select("div.size").text().toDouble()
//        println(
//            Jsoup.connect(stockPage.select(".download-content > a").attr("href")).get().select(".container a").attr("onclick").split("'")[1]
//        )

        return Episode.StreamLinks("", listOf(Episode.Quality("", "", size)), "lol")
    }
}