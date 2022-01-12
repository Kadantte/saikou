package ani.saikou.manga.source.parsers

import ani.saikou.loadData
import ani.saikou.logger
import ani.saikou.manga.MangaChapter
import ani.saikou.manga.source.MangaParser
import ani.saikou.media.Media
import ani.saikou.media.Source
import ani.saikou.saveData
import ani.saikou.toastString
import org.jsoup.Jsoup

class MangaBuddy(override val name: String="mangabuddy.com") : MangaParser() {
     init {
         referer = "https://mangabuddy.com/"
     }
    override fun getLinkChapters(link:String):MutableMap<String,MangaChapter>{
        val arr = mutableMapOf<String, MangaChapter>()
        try {
        Jsoup.connect("https://mangabuddy.com/api/manga${link}/chapters?source=detail").get().select("#chapter-list>li").reversed().forEach {
//            println(it.toString())
            if (it.select("strong").text().contains("Chapter")) {
                val chap = Regex("(Chapter ([A-Za-z0-9.]+))( ?: ?)?( ?(.+))?").find(it.select("strong").text())?.destructured
                if(chap!=null) {
                    arr[chap.component2()] = MangaChapter(
                        number = chap.component2(),
                        link = it.select("a").attr("abs:href"),
                        title = chap.component5()
                    )
                }else{
                    arr[it.select("strong").text()] = MangaChapter(
                        number = it.select("strong").text(),
                        link = it.select("a").attr("abs:href"),
                    )
                }
            }
        }
        }catch (e:Exception){
            toastString("$e")
        }
        return arr
    }

    override fun getChapter(chapter: MangaChapter): MangaChapter {
        chapter.images = arrayListOf()
        val arr = Regex("(?<=var chapImages = ').+(?=')").find(Jsoup.connect(chapter.link!!).get().toString())?.value?.split(",")
        arr?.forEach {
            val link = "https://s1.madcdnv2.xyz/file/img-mbuddy/manga/$it"
            chapter.images!!.add(link)
        }
//        println("${chapter.images}")
        chapter.referer = "https://mangabuddy.com/"
        return chapter
    }

    override fun getChapters(media: Media): MutableMap<String, MangaChapter> {
        var source:Source? = loadData("mangabuddy_${media.id}")
        if (source==null) {
            live.postValue("Searching : ${media.getMangaName()}")
            val search = search(media.getMangaName())
            if (search.isNotEmpty()) {
                logger("MangaBuddy : ${search[0]}")
                source = search[0]
                live.postValue("Found : ${source.name}")
                saveSource(source,media.id)
            }
        }
        else{
            live.postValue("Selected : ${source.name}")
        }
        if (source!=null) return getLinkChapters(source.link)
        return mutableMapOf()
    }

    override fun search(string: String): ArrayList<Source> {
        val response = arrayListOf<Source>()
        try{
        Jsoup.connect("https://mangabuddy.com/search?status=all&sort=views&q=$string").get().select(".list > .book-item > .book-detailed-item > .thumb > a").forEach {
            if (it.attr("title")!=""){
                response.add(Source(
                    link = it.attr("href"),
                    name = it.attr("title"),
                    cover = it.select("img").attr("data-src"))
                )
            }
        }} catch(e:Exception){ toastString(e.toString()) }
        return response
    }

    override fun saveSource(source: Source, id: Int, selected: Boolean) {
        live.postValue("${if(selected) "Selected" else "Found"} : ${source.name}")
        saveData("mangabuddy_$id", source)
    }
}