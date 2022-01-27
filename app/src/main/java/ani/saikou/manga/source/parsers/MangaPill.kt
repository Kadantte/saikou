package ani.saikou.manga.source.parsers

import ani.saikou.loadData
import ani.saikou.logger
import ani.saikou.manga.MangaChapter
import ani.saikou.manga.source.MangaParser
import ani.saikou.media.Media
import ani.saikou.media.Source
import ani.saikou.saveData
import org.jsoup.Jsoup

class MangaPill(override val name: String="mangapill.com") :MangaParser() {
    override fun getChapter(chapter: MangaChapter): MangaChapter {
        chapter.images = arrayListOf()
        Jsoup.connect(chapter.link!!).get().select("img.js-page").forEach {
            chapter.images!!.add(it.attr("data-src"))
        }
        return chapter
    }

    override fun getLinkChapters(link:String):MutableMap<String,MangaChapter>{
        val responseArray = mutableMapOf<String, MangaChapter>()
        Jsoup.connect(link).get().select("#chapters > div > a").reversed().forEach{
            val chap = it.text().replace("Chapter ","")
            responseArray[chap] = MangaChapter(chap,link=it.attr("abs:href"))
        }
        return responseArray
    }

    override fun getChapters(media: Media): MutableMap<String, MangaChapter> {
        var source:Source? = loadData("mangapill_${media.id}")
        if (source==null) {
            live.postValue("Searching : ${media.getMangaName()}")
            val search = search(media.getMangaName())
            if (search.isNotEmpty()) {
                logger("MangaPill : ${search[0]}")
                source = search[0]
                saveSource(source,media.id,false)
            }else{
                val a = search(media.nameRomaji)
                if (a.isNotEmpty()) {
                    logger("MangaPill : ${a[0]}")
                    source = a[0]
                    saveSource(source,media.id,false)
                }
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
        Jsoup.connect("https://mangapill.com/quick-search?q=$string").get().select(".bg-card").forEach{
            val text2 = it.select(".text-sm").text()
            response.add(Source(
                link = it.attr("abs:href"),
                name = it.select(".flex .flex-col").text().replace(text2,"").trim(),
                cover = it.select("img").attr("src")
            ))
        }
        return response
    }

    override fun saveSource(source: Source, id: Int, selected: Boolean) {
        live.postValue("${if(selected) "Selected" else "Found"} : ${source.name}")
        saveData("mangapill_$id", source)
    }
}