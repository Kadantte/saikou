package ani.saikou.anime.source.parsers

import android.annotation.SuppressLint
import ani.saikou.anime.Episode
import ani.saikou.anime.source.Extractor
import ani.saikou.anime.source.AnimeParser
import ani.saikou.anime.source.extractors.*
import ani.saikou.loadData
import ani.saikou.logger
import ani.saikou.media.Media
import ani.saikou.media.Source
import ani.saikou.saveData
import ani.saikou.toastString
import kotlinx.coroutines.*
import org.jsoup.Jsoup

@SuppressLint("SetTextI18n")
class Gogo(private val dub:Boolean=false, override val name: String = "gogoanime.cm"): AnimeParser(){


    private val host = listOf(
        "http://gogoanime.cm"
    )

    private fun httpsIfy(text: String): String {
        return if(text.take(2)=="//"){"https:$text"}
        else{text}
    }

    private fun directLinkify(name: String,url: String): Episode.StreamLinks? {
        val domain = Regex("""(?<=^http[s]?://).+?(?=/)""").find(url)!!.value
        val extractor : Extractor?=when {
            "gogo" in domain -> GogoCDN()
            "sb" in domain ->  StreamSB()
            "fplayer" in domain -> FPlayer()
            "fembed" in domain -> FPlayer()
//            "dood" in domain -> Doodla()
            else -> null
        }
        val a = extractor?.getStreamLinks(name,url)
        if (a!=null && a.quality.isNotEmpty()) return a
        return null
    }

    override fun getStream(episode: Episode): Episode {
//        try {
        episode.streamLinks = runBlocking {
            val linkForVideos = arrayListOf<Episode.StreamLinks?>()
            withContext(Dispatchers.Default) {
                Jsoup.connect(episode.link!!).ignoreHttpErrors(true).get().select("div.anime_muti_link > ul > li:not(li.anime)").forEach {
//                    println(it.select("a").attr("data-video"))
                    launch {
                        val directLinks = directLinkify(
                            it.select("a").text().replace("Choose this server", ""),
                            httpsIfy(it.select("a").attr("data-video"))
                        )
                        if(directLinks != null){linkForVideos.add(directLinks)}
                    }
                }
            }
            return@runBlocking (linkForVideos)
        }
//        }catch (e:Exception){
//            toastString("$e")
//        }
        return episode
    }

    override fun getEpisodes(media: Media): MutableMap<String, Episode> {
        try{
        var slug:Source? = loadData("go-go${if(dub) "dub" else ""}_${media.id}")
        if (slug==null) {
            var it = (media.nameMAL ?: media.nameRomaji) + if (dub) " (Dub)" else ""
            live.postValue("Searching for $it")
            logger("Gogo : Searching for $it")
            var search = search(it)
            if (search.isNotEmpty()) {
                slug = search[0]
                saveSource(slug,media.id,false)
            } else{
                it = media.nameRomaji+ if (dub) " (Dub)" else ""
                search = search(it)
                live.postValue("Searching for $it")
                logger("Gogo : Searching for $it")
                if (search.isNotEmpty()) {
                    slug = search[0]
                    saveSource(slug,media.id,false)
                }
            }
        }
        else{
            live.postValue("Selected : ${slug.name}")
        }
        if (slug!=null) return getSlugEpisodes(slug.link)
        }catch (e:Exception){
            toastString("$e")
        }
        return mutableMapOf()
    }

    override fun search(string: String): ArrayList<Source> {
        // make search and get all links
        logger("Searching for : $string")
        val responseArray = arrayListOf<Source>()
        Jsoup.connect("${host[0]}/search.html?keyword=$string").get().body()
            .select(".last_episodes > ul > li div.img > a").forEach {
                val link = it.attr("href").toString().replace("/category/", "")
                val title = it.attr("title")
                val cover = it.select("img").attr("src")
                responseArray.add(Source(link,title,cover))
            }
        return responseArray
    }

    override fun getSlugEpisodes(slug: String): MutableMap<String, Episode> {
        val pageBody = Jsoup.connect("${host[0]}/category/$slug").get().body()
        val lastEpisode = pageBody.select("ul#episode_page > li:last-child > a").attr("ep_end").toString()
        val animeId = pageBody.select("input#movie_id").attr("value").toString()

        val responseArray = mutableMapOf<String,Episode>()
        val a = Jsoup.connect("https://ajax.gogo-load.com/ajax/load-list-episode?ep_start=0&ep_end=$lastEpisode&id=$animeId").get().body().select("ul > li > a").reversed()
        a.forEach{
            val num = it.select(".name").text().replace("EP","").trim()
            responseArray[num] = Episode(number = num,link = host[0]+it.attr("href").trim())
        }
        logger("Response Episodes : $responseArray")
        return responseArray
    }

    override fun saveSource(source: Source, id: Int, selected: Boolean) {
        super.saveSource(source, id, selected)
        saveData("go-go${if(dub) "dub" else ""}_$id", source)
    }
}
