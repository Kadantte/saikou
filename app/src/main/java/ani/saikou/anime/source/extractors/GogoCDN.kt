package ani.saikou.anime.source.extractors

import ani.saikou.anime.Episode
import ani.saikou.anime.source.Extractor
import org.jsoup.Jsoup

class GogoCDN: Extractor() {
    override fun getStreamLinks(name: String, url: String): Episode.StreamLinks {
        println(url)
        val list = arrayListOf<Episode.Quality>()
//        val moiJS = Jsoup.connect(url.replace("embedplus","download")).header("User-Agent","Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/93.0.4577.63 Safari/537.36").get().select(".mirror_link")[0].select(".dowload > a")
//        moiJS.forEach {
//            list.add(Episode.Quality(it.attr("href"),Regex("(?<=download \\().+(?= - mp4)").find(it.text().lowercase())!!.value,1))
//        }
        return Episode.StreamLinks(name, list, "https://gogoplay1.com/")
    }
}