package ani.saikou.anime.source

import ani.saikou.anime.source.parsers.Gogo
import ani.saikou.anime.source.parsers.NineAnime
import ani.saikou.anime.source.parsers.Twist
import ani.saikou.anime.source.parsers.Zoro

object AnimeSources {
    val Names = arrayListOf(
        "GOGO",
        "GOGO-DUB",
        "9ANIME",
        "9ANIME-DUB",
        "ZORO",
        "TWIST",
    )

    private val animeParsers:MutableMap<Int,AnimeParser> = mutableMapOf()
    operator fun get(i:Int) : AnimeParser?{
        val a = when (i) {
            0 -> animeParsers.getOrPut(i) { Gogo() }
            1 -> animeParsers.getOrPut(i) { Gogo(true) }
            2 -> animeParsers.getOrPut(i) { NineAnime() }
            3 -> animeParsers.getOrPut(i) { NineAnime(true) }
            4 -> animeParsers.getOrPut(i) { Zoro() }
            5 -> animeParsers.getOrPut(i) { Twist() }
            else -> null
        }
        return a
    }
    fun flushLive(){
        animeParsers.forEach{
            it.value.live.value=null
        }
    }
}