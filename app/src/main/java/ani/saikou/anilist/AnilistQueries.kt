package ani.saikou.anilist

import android.app.Activity
import ani.saikou.*
import ani.saikou.anime.Anime
import ani.saikou.manga.Manga
import ani.saikou.media.Character
import ani.saikou.media.Media
import com.google.android.material.snackbar.Snackbar
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*
import org.jsoup.Jsoup
import java.io.Serializable
import java.net.UnknownHostException
import java.util.*
import kotlin.collections.ArrayList


fun executeQuery(query:String, variables:String="",force:Boolean=false,useToken:Boolean=true): JsonObject? {
    try {
        val set = Jsoup.connect("https://graphql.anilist.co/")
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .requestBody("""{"query":"$query","variables": "$variables"}""")
            .ignoreContentType(true).ignoreHttpErrors(true)
        if (Anilist.token!=null || force) {
            if (Anilist.token!=null && useToken) set.header("Authorization", "Bearer ${Anilist.token}")
            val json = set.post().body().text()
            logger("JSON : $json", false)
            return Json.decodeFromString(json)
        }
    } catch (e:Exception){
        if(e is UnknownHostException) toastString("Network error, please Retry.")
        else toastString("$e")
    }
    return null
}

data class SearchResults(
    val type: String,
    val perPage:Int?=null,
    val search: String? = null,
    val sort: String? = null,
    val genres: ArrayList<String>? = null,
    var page: Int=1,
    var results:ArrayList<Media>,
    var hasNextPage:Boolean,
):Serializable

class AnilistQueries{
    fun getUserData():Boolean{
        return try{
            val response = executeQuery("""{Viewer {name avatar{medium}id statistics{anime{episodesWatched}manga{chaptersRead}}}}""")!!["data"]!!.jsonObject["Viewer"]!!

            Anilist.userid = response.jsonObject["id"].toString().toInt()
            Anilist.username = response.jsonObject["name"].toString().trim('"')
            Anilist.avatar = response.jsonObject["avatar"]!!.jsonObject["medium"].toString().trim('"')
            Anilist.episodesWatched = response.jsonObject["statistics"]!!.jsonObject["anime"]!!.jsonObject["episodesWatched"].toString().toInt()
            Anilist.chapterRead = response.jsonObject["statistics"]!!.jsonObject["manga"]!!.jsonObject["chaptersRead"].toString().toInt()
            true
        } catch (e: Exception){
            logger(e)
            false
        }
    }

    fun getMedia(id:Int,mal:Boolean=false):Media?{
        val response = executeQuery("""{Media(${if(!mal) "id:" else "idMal:"}$id){id status chapters episodes nextAiringEpisode{episode}type meanScore isFavourite bannerImage coverImage{large}title{english romaji userPreferred}mediaListEntry{progress score(format:POINT_100)status}}}""", force = true)
        val i = response?.get("data")!!.jsonObject["Media"]!!
        if (i!=JsonNull){
            return Media(
                id = i.jsonObject["id"].toString().toInt(),
                name = i.jsonObject["title"]!!.jsonObject["english"].toString().trim('"').replace("\\\"","\""),
                nameRomaji = i.jsonObject["title"]!!.jsonObject["romaji"].toString().trim('"').replace("\\\"","\""),
                userPreferredName = i.jsonObject["title"]!!.jsonObject["userPreferred"].toString().trim('"').replace("\\\"","\""),
                cover = i.jsonObject["coverImage"]!!.jsonObject["large"].toString().trim('"'),
                banner = i.jsonObject["bannerImage"].toString().trim('"'),
                status = i.jsonObject["status"].toString().trim('"'),
                isFav = i.jsonObject["isFavourite"].toString() == "true",
                userProgress = if (i.jsonObject["mediaListEntry"] != JsonNull) i.jsonObject["mediaListEntry"]!!.jsonObject["progress"].toString().toInt() else null,
                userScore = if (i.jsonObject["mediaListEntry"] != JsonNull) i.jsonObject["mediaListEntry"]!!.jsonObject["score"].toString().toInt() else 0,
                userStatus = if (i.jsonObject["mediaListEntry"] != JsonNull) i.jsonObject["mediaListEntry"]!!.jsonObject["status"].toString().trim('"') else null,
                meanScore = if (i.jsonObject["meanScore"].toString().trim('"') != "null") i.jsonObject["meanScore"].toString().toInt() else null,
                anime = if (i.jsonObject["type"].toString().trim('"') == "ANIME") Anime(totalEpisodes = if (i.jsonObject["episodes"] != JsonNull) i.jsonObject["episodes"].toString().toInt() else null, nextAiringEpisode = if (i.jsonObject["nextAiringEpisode"] != JsonNull) i.jsonObject["nextAiringEpisode"]!!.jsonObject["episode"].toString().toInt() - 1 else null) else null,
                manga = if (i.jsonObject["type"].toString().trim('"') == "MANGA") Manga(totalChapters = if (i.jsonObject["chapters"] != JsonNull) i.jsonObject["chapters"].toString().toInt() else null) else null,
            )
        }
        return null
    }

    fun mediaDetails(media:Media): Media? {
        val query = """{Media(id:${media.id}){mediaListEntry{id status score(format:POINT_100) progress repeat updatedAt startedAt{year month day}completedAt{year month day}}isFavourite idMal nextAiringEpisode{episode airingAt}source countryOfOrigin format duration season seasonYear startDate{year month day}endDate{year month day}genres studios(isMain:true){nodes{id name siteUrl}}description characters(sort:[ROLE,FAVOURITES_DESC],perPage:25,page:1){edges{role node{id image{medium}name{userPreferred}}}}relations{edges{relationType(version:2)node{id mediaListEntry{progress score(format:POINT_100) status} chapters episodes episodes chapters nextAiringEpisode{episode}meanScore isFavourite title{english romaji userPreferred}type status(version:2)bannerImage coverImage{large}}}}recommendations{nodes{mediaRecommendation{id mediaListEntry{progress score(format:POINT_100) status} chapters episodes chapters nextAiringEpisode{episode}meanScore isFavourite title{english romaji userPreferred}type status(version:2)bannerImage coverImage{large}}}}streamingEpisodes{title thumbnail}externalLinks{url}}}"""
        var response =  executeQuery(query, force = true)
        println("$response")
        if (response!=null){
            fun parse():Media{
                val it = response!!["data"]!!.jsonObject["Media"]!!

                media.source = it.jsonObject["source"]!!.toString().trim('"')
                media.countryOfOrigin = it.jsonObject["countryOfOrigin"].toString().trim('"')
                media.format = it.jsonObject["format"]!!.toString().trim('"')

                media.startDate = FuzzyDate(
                    if(it.jsonObject["startDate"]!!.jsonObject["year"]!=JsonNull) it.jsonObject["startDate"]!!.jsonObject["year"].toString().toInt() else null,
                    if(it.jsonObject["startDate"]!!.jsonObject["month"]!=JsonNull) it.jsonObject["startDate"]!!.jsonObject["month"].toString().toInt() else null,
                    if(it.jsonObject["startDate"]!!.jsonObject["day"]!=JsonNull) it.jsonObject["startDate"]!!.jsonObject["day"].toString().toInt() else null
                )
                media.endDate = FuzzyDate(
                    if(it.jsonObject["endDate"]!!.jsonObject["year"]!=JsonNull) it.jsonObject["endDate"]!!.jsonObject["year"].toString().toInt() else null,
                    if(it.jsonObject["endDate"]!!.jsonObject["month"]!=JsonNull) it.jsonObject["endDate"]!!.jsonObject["month"].toString().toInt() else null,
                    if(it.jsonObject["endDate"]!!.jsonObject["day"]!=JsonNull) it.jsonObject["endDate"]!!.jsonObject["day"].toString().toInt() else null
                )
                if (it.jsonObject["genres"]!!!=JsonNull){
                    media.genres = arrayListOf()
                    it.jsonObject["genres"]!!.jsonArray.forEach { i ->
                        media.genres!!.add(i.toString().trim('"'))
                    }
                }
                media.description = it.jsonObject["description"]!!.toString().trim('"').replace("\\\"","\"")

                if(it.jsonObject["characters"]!=JsonNull){
                    media.characters = arrayListOf()
                    it.jsonObject["characters"]!!.jsonObject["edges"]!!.jsonArray.forEach { i->
                        media.characters!!.add(Character(
                            id = i.jsonObject["node"]!!.jsonObject["id"]!!.toString().toInt(),
                            name = i.jsonObject["node"]!!.jsonObject["name"]!!.jsonObject["userPreferred"].toString().trim('"').replace("\\\"","\""),
                            image = i.jsonObject["node"]!!.jsonObject["image"]!!.jsonObject["medium"].toString().trim('"'),
                            role = i.jsonObject["role"]!!.toString().trim('"')
                        ))
                    }
                }
                if(it.jsonObject["relations"]!=JsonNull){
                    media.relations = arrayListOf()
                    it.jsonObject["relations"]!!.jsonObject["edges"]!!.jsonArray.forEach { i->
                        media.relations!!.add(
                            Media(
                                id = i.jsonObject["node"]!!.jsonObject["id"].toString().toInt(),
                                name = i.jsonObject["node"]!!.jsonObject["title"]!!.jsonObject["english"].toString().trim('"').replace("\\\"","\""),
                                nameRomaji = i.jsonObject["node"]!!.jsonObject["title"]!!.jsonObject["romaji"].toString().trim('"').replace("\\\"","\""),
                                userPreferredName = i.jsonObject["node"]!!.jsonObject["title"]!!.jsonObject["userPreferred"].toString().trim('"').replace("\\\"","\""),
                                cover = i.jsonObject["node"]!!.jsonObject["coverImage"]!!.jsonObject["large"].toString().trim('"'),
                                banner = i.jsonObject["node"]!!.jsonObject["bannerImage"].toString().trim('"'),
                                status = i.jsonObject["node"]!!.jsonObject["status"].toString().trim('"'),
                                isFav = i.jsonObject["node"]!!.jsonObject["isFavourite"].toString()=="true",
                                userProgress = if (i.jsonObject["node"]!!.jsonObject["mediaListEntry"]!=JsonNull) i.jsonObject["node"]!!.jsonObject["mediaListEntry"]!!.jsonObject["progress"].toString().toInt() else null,
                                userScore = if (i.jsonObject["node"]!!.jsonObject["mediaListEntry"]!=JsonNull) i.jsonObject["node"]!!.jsonObject["mediaListEntry"]!!.jsonObject["score"].toString().toInt() else 0,
                                userStatus = if (i.jsonObject["node"]!!.jsonObject["mediaListEntry"]!=JsonNull) i.jsonObject["node"]!!.jsonObject["mediaListEntry"]!!.jsonObject["status"].toString().trim('"') else null,
                                meanScore = if (i.jsonObject["node"]!!.jsonObject["meanScore"].toString().trim('"')!="null") i.jsonObject["node"]!!.jsonObject["meanScore"].toString().toInt() else null,
                                relation = i.jsonObject["relationType"].toString().trim('"'),
                                anime = if (i.jsonObject["node"]!!.jsonObject["type"].toString().trim('"')=="ANIME") Anime(totalEpisodes = if (i.jsonObject["node"]!!.jsonObject["episodes"] != JsonNull) i.jsonObject["node"]!!.jsonObject["episodes"].toString().toInt() else null, nextAiringEpisode = if(i.jsonObject["node"]!!.jsonObject["nextAiringEpisode"] != JsonNull) i.jsonObject["node"]!!.jsonObject["nextAiringEpisode"]!!.jsonObject["episode"].toString().toInt()-1 else null) else null,
                                manga = if (i.jsonObject["node"]!!.jsonObject["type"].toString().trim('"')=="MANGA") Manga(totalChapters = if (i.jsonObject["node"]!!.jsonObject["chapters"] != JsonNull) i.jsonObject["node"]!!.jsonObject["chapters"].toString().toInt() else null) else null,
                            )
                        )
                    }
                }
                if(it.jsonObject["recommendations"]!=JsonNull) {
                    media.recommendations = arrayListOf()
                    it.jsonObject["recommendations"]!!.jsonObject["nodes"]!!.jsonArray.forEach { i ->
                        if (i.jsonObject["mediaRecommendation"]!=JsonNull){
                            media.recommendations!!.add(
                                Media(
                                    id = i.jsonObject["mediaRecommendation"]!!.jsonObject["id"].toString().toInt(),
                                    name = i.jsonObject["mediaRecommendation"]!!.jsonObject["title"]!!.jsonObject["english"].toString().trim('"').replace("\\\"","\""),
                                    nameRomaji = i.jsonObject["mediaRecommendation"]!!.jsonObject["title"]!!.jsonObject["romaji"].toString().trim('"').replace("\\\"","\""),
                                    userPreferredName = i.jsonObject["mediaRecommendation"]!!.jsonObject["title"]!!.jsonObject["userPreferred"].toString().trim('"').replace("\\\"","\""),
                                    status = i.jsonObject["mediaRecommendation"]!!.jsonObject["status"].toString().trim('"'),
                                    cover = i.jsonObject["mediaRecommendation"]!!.jsonObject["coverImage"]!!.jsonObject["large"].toString().trim('"'),
                                    banner = i.jsonObject["mediaRecommendation"]!!.jsonObject["bannerImage"].toString().trim('"'),
                                    meanScore = if (i.jsonObject["mediaRecommendation"]!!.jsonObject["meanScore"].toString().trim('"')!="null") i.jsonObject["mediaRecommendation"]!!.jsonObject["meanScore"].toString().toInt() else null,
                                    isFav = i.jsonObject["mediaRecommendation"]!!.jsonObject["isFavourite"].toString()=="true",
                                    userProgress = if (i.jsonObject["mediaRecommendation"]!!.jsonObject["mediaListEntry"]!=JsonNull) i.jsonObject["mediaRecommendation"]!!.jsonObject["mediaListEntry"]!!.jsonObject["progress"].toString().toInt() else null,
                                    userScore = if (i.jsonObject["mediaRecommendation"]!!.jsonObject["mediaListEntry"]!=JsonNull) i.jsonObject["mediaRecommendation"]!!.jsonObject["mediaListEntry"]!!.jsonObject["score"].toString().toInt() else 0,
                                    userStatus = if (i.jsonObject["mediaRecommendation"]!!.jsonObject["mediaListEntry"]!=JsonNull) i.jsonObject["mediaRecommendation"]!!.jsonObject["mediaListEntry"]!!.jsonObject["status"].toString().trim('"') else null,
                                    anime = if (i.jsonObject["mediaRecommendation"]!!.jsonObject["type"].toString().trim('"') == "ANIME") Anime(totalEpisodes = if (i.jsonObject["mediaRecommendation"]!!.jsonObject["episodes"] != JsonNull) i.jsonObject["mediaRecommendation"]!!.jsonObject["episodes"].toString().toInt() else null, nextAiringEpisode = if (i.jsonObject["mediaRecommendation"]!!.jsonObject["nextAiringEpisode"] != JsonNull) i.jsonObject["mediaRecommendation"]!!.jsonObject["nextAiringEpisode"]!!.jsonObject["episode"].toString().toInt() - 1 else null) else null,
                                    manga = if (i.jsonObject["mediaRecommendation"]!!.jsonObject["type"].toString().trim('"') == "MANGA") Manga(totalChapters = if (i.jsonObject["mediaRecommendation"]!!.jsonObject["chapters"] != JsonNull) i.jsonObject["mediaRecommendation"]!!.jsonObject["chapters"].toString().toInt() else null) else null
                                )
                            )
                        }
                    }
                }

                if (it.jsonObject["mediaListEntry"]!=JsonNull) {
                    media.userRepeat =
                        if (it.jsonObject["mediaListEntry"]!!.jsonObject["repeat"].toString() == "null") it.jsonObject["mediaListEntry"]!!.jsonObject["repeat"]!!.toString().toInt() else 0
                    media.userUpdatedAt = Date(it.jsonObject["mediaListEntry"]!!.jsonObject["repeat"]!!.toString().toLong()*1000)
                    media.userCompletedAt = FuzzyDate(
                        if (it.jsonObject["mediaListEntry"]!!.jsonObject["completedAt"]!!.jsonObject["year"] != JsonNull) it.jsonObject["mediaListEntry"]!!.jsonObject["completedAt"]!!.jsonObject["year"].toString().toInt() else null,
                        if (it.jsonObject["mediaListEntry"]!!.jsonObject["completedAt"]!!.jsonObject["month"] != JsonNull) it.jsonObject["mediaListEntry"]!!.jsonObject["completedAt"]!!.jsonObject["month"].toString().toInt() else null,
                        if (it.jsonObject["mediaListEntry"]!!.jsonObject["completedAt"]!!.jsonObject["day"] != JsonNull) it.jsonObject["mediaListEntry"]!!.jsonObject["completedAt"]!!.jsonObject["day"].toString().toInt() else null
                    )
                    media.userStartedAt = FuzzyDate(
                        if (it.jsonObject["mediaListEntry"]!!.jsonObject["startedAt"]!!.jsonObject["year"] != JsonNull) it.jsonObject["mediaListEntry"]!!.jsonObject["startedAt"]!!.jsonObject["year"].toString().toInt() else null,
                        if (it.jsonObject["mediaListEntry"]!!.jsonObject["startedAt"]!!.jsonObject["month"] != JsonNull) it.jsonObject["mediaListEntry"]!!.jsonObject["startedAt"]!!.jsonObject["month"].toString().toInt() else null,
                        if (it.jsonObject["mediaListEntry"]!!.jsonObject["startedAt"]!!.jsonObject["day"] != JsonNull) it.jsonObject["mediaListEntry"]!!.jsonObject["startedAt"]!!.jsonObject["day"].toString().toInt() else null
                    )
                }

                if (media.anime != null) {
                    media.anime.episodeDuration = if (it.jsonObject["duration"]!!!=JsonNull) it.jsonObject["duration"]!!.toString().toInt() else null
                    media.anime.season = if (it.jsonObject["season"]!!!=JsonNull) it.jsonObject["season"]!!.toString().trim('"') else null
                    media.anime.seasonYear = if (it.jsonObject["seasonYear"]!!!=JsonNull) it.jsonObject["seasonYear"]!!.toString().toInt() else null

                    if (it.jsonObject["studios"]!!.jsonObject["nodes"]!!.jsonArray.isNotEmpty()) {
                        media.anime.mainStudioID = it.jsonObject["studios"]!!.jsonObject["nodes"]!!.jsonArray[0].jsonObject["id"].toString().toInt()
                        media.anime.mainStudioName = it.jsonObject["studios"]!!.jsonObject["nodes"]!!.jsonArray[0].jsonObject["name"].toString().trim('"')
                    }
                    media.anime.nextAiringEpisodeTime = if(it.jsonObject["nextAiringEpisode"] != JsonNull) it.jsonObject["nextAiringEpisode"]!!.jsonObject["airingAt"].toString().toLong() else null

                    it.jsonObject["externalLinks"]!!.jsonArray.forEach{ i->
                        val url = i.jsonObject["url"].toString().trim('"')
                        if(url.startsWith("https://www.youtube.com") ){
                            media.anime.youtube = url
                        }
                    }
                }
                else if (media.manga != null) {
                    logger ("Nothing Here lmao",false)
                }
                if (it.jsonObject["idMal"]!!!=JsonNull) {
                    media.idMAL = it.jsonObject["idMal"]!!.toString().toInt()
                    return getMalMedia(media)
                }
                return media
            }
            return if (response["data"]!!.jsonObject["Media"]!!!=JsonNull) parse() else {
                toastString("Adult Stuff? ( ͡° ͜ʖ ͡°)")
                response = executeQuery(query, force = true, useToken = false)
                println("$response")
                parse()
            }
        }
        return null
    }


    fun continueMedia(type:String): ArrayList<Media> {
        val response = executeQuery(""" { MediaListCollection(userId: ${Anilist.userid}, type: $type, status: CURRENT) { lists { entries { progress score(format:POINT_100) status media { id status chapters episodes nextAiringEpisode {episode} meanScore isFavourite bannerImage coverImage{large} title { english romaji userPreferred } } } } } } """)
        val returnArray = arrayListOf<Media>()
        val list = if (response!=null) response["data"]!!.jsonObject["MediaListCollection"]!!.jsonObject["lists"]!!.jsonArray else null
        if (list!=null && list.isNotEmpty()){
            list[0].jsonObject["entries"]!!.jsonArray.reversed().forEach {
                returnArray.add(
                    Media(
                        id = it.jsonObject["media"]!!.jsonObject["id"].toString().toInt(),
                        name = it.jsonObject["media"]!!.jsonObject["title"]!!.jsonObject["english"].toString().trim('"').replace("\\\"","\""),
                        nameRomaji = it.jsonObject["media"]!!.jsonObject["title"]!!.jsonObject["romaji"].toString().trim('"').replace("\\\"","\""),
                        userPreferredName = it.jsonObject["media"]!!.jsonObject["title"]!!.jsonObject["userPreferred"].toString().trim('"').replace("\\\"","\""),
                        cover = it.jsonObject["media"]!!.jsonObject["coverImage"]!!.jsonObject["large"].toString().trim('"'),
                        banner = it.jsonObject["media"]!!.jsonObject["bannerImage"].toString().trim('"'),
                        status = it.jsonObject["media"]!!.jsonObject["status"].toString().trim('"'),
                        meanScore = if(it.jsonObject["media"]!!.jsonObject["meanScore"]!=JsonNull) it.jsonObject["media"]!!.jsonObject["meanScore"].toString().toInt() else null,
                        isFav = it.jsonObject["media"]!!.jsonObject["isFavourite"].toString() == "true",
                        userProgress = it.jsonObject["progress"].toString().toInt(),
                        userScore = it.jsonObject["score"].toString().toInt(),
                        userStatus = it.jsonObject["status"].toString().trim('"'),
                        anime = if (type == "ANIME") Anime(totalEpisodes = if (it.jsonObject["media"]!!.jsonObject["episodes"] != JsonNull) it.jsonObject["media"]!!.jsonObject["episodes"].toString().toInt() else null, nextAiringEpisode = if(it.jsonObject["media"]!!.jsonObject["nextAiringEpisode"] != JsonNull) it.jsonObject["media"]!!.jsonObject["nextAiringEpisode"]!!.jsonObject["episode"].toString().toInt()-1 else null) else null,
                        manga = if (type == "MANGA") Manga(totalChapters = if (it.jsonObject["media"]!!.jsonObject["chapters"] != JsonNull) it.jsonObject["media"]!!.jsonObject["chapters"].toString().toInt() else null) else null,
                    )
                )
            }
        }
        return returnArray
    }

    fun recommendations(): ArrayList<Media> {
        val response = executeQuery(""" { Page(page: 1, perPage:30) { pageInfo { total currentPage hasNextPage } recommendations(sort: RATING_DESC, onList: true) { rating userRating mediaRecommendation { id mediaListEntry {progress score(format:POINT_100) status} chapters isFavourite episodes nextAiringEpisode {episode} meanScore isFavourite title {english romaji userPreferred } type status(version: 2) bannerImage coverImage { large } } } } } """)
        val responseArray = arrayListOf<Media>()
        val ids = arrayListOf<Int>()
        if (response!=null) response["data"]!!.jsonObject["Page"]!!.jsonObject["recommendations"]!!.jsonArray.reversed().forEach{
            val id =  it.jsonObject["mediaRecommendation"]!!.jsonObject["id"].toString().toInt()
            if (id !in ids) {
                ids.add(id)
                responseArray.add(
                    Media(
                        id = id,
                        name = it.jsonObject["mediaRecommendation"]!!.jsonObject["title"]!!.jsonObject["english"].toString().trim('"').replace("\\\"","\""),
                        nameRomaji = it.jsonObject["mediaRecommendation"]!!.jsonObject["title"]!!.jsonObject["romaji"].toString().trim('"').replace("\\\"","\""),
                        userPreferredName = it.jsonObject["mediaRecommendation"]!!.jsonObject["title"]!!.jsonObject["userPreferred"].toString().trim('"').replace("\\\"","\""),
                        status = it.jsonObject["mediaRecommendation"]!!.jsonObject["status"].toString().trim('"'),
                        cover = it.jsonObject["mediaRecommendation"]!!.jsonObject["coverImage"]!!.jsonObject["large"].toString().trim('"'),
                        banner = it.jsonObject["mediaRecommendation"]!!.jsonObject["bannerImage"].toString().trim('"'),
                        meanScore = if(it.jsonObject["mediaRecommendation"]!!.jsonObject["meanScore"]!=JsonNull) it.jsonObject["mediaRecommendation"]!!.jsonObject["meanScore"].toString().toInt() else null,
                        isFav = it.jsonObject["mediaRecommendation"]!!.jsonObject["isFavourite"].toString()=="true",
                        userProgress = if (it.jsonObject["mediaRecommendation"]!!.jsonObject["mediaListEntry"]!=JsonNull) it.jsonObject["mediaRecommendation"]!!.jsonObject["mediaListEntry"]!!.jsonObject["progress"].toString().toInt() else null,
                        userScore = if (it.jsonObject["mediaRecommendation"]!!.jsonObject["mediaListEntry"]!=JsonNull) it.jsonObject["mediaRecommendation"]!!.jsonObject["mediaListEntry"]!!.jsonObject["score"].toString().toInt() else 0,
                        userStatus = if (it.jsonObject["mediaRecommendation"]!!.jsonObject["mediaListEntry"]!=JsonNull) it.jsonObject["mediaRecommendation"]!!.jsonObject["mediaListEntry"]!!.jsonObject["status"].toString().trim('"') else null,
                        anime = if(it.jsonObject["mediaRecommendation"]!!.jsonObject["type"].toString().trim('"') == "ANIME") Anime(totalEpisodes = if (it.jsonObject["mediaRecommendation"]!!.jsonObject["episodes"] != JsonNull) it.jsonObject["mediaRecommendation"]!!.jsonObject["episodes"].toString().toInt() else null, nextAiringEpisode = if (it.jsonObject["mediaRecommendation"]!!.jsonObject["nextAiringEpisode"] != JsonNull) it.jsonObject["mediaRecommendation"]!!.jsonObject["nextAiringEpisode"]!!.jsonObject["episode"].toString().toInt() - 1 else null) else null,
                        manga = if(it.jsonObject["mediaRecommendation"]!!.jsonObject["type"].toString().trim('"') == "MANGA") Manga(totalChapters = if (it.jsonObject["mediaRecommendation"]!!.jsonObject["chapters"] != JsonNull) it.jsonObject["mediaRecommendation"]!!.jsonObject["chapters"].toString().toInt() else null) else null,
                    )
                )
            }
        }
        return responseArray
    }

    private fun bannerImage(type: String): String? {
        val response = executeQuery("""{ MediaListCollection(userId: ${Anilist.userid}, type: $type, sort:[SCORE_DESC,UPDATED_TIME_DESC],chunk:1,perChunk:1) { lists { entries{ media { bannerImage } } } } } """)
        val list = if (response!=null) response["data"]!!.jsonObject["MediaListCollection"]!!.jsonObject["lists"]!!.jsonArray else null
        if (list!=null && list.isNotEmpty()){
            return list[0].jsonObject["entries"]!!.jsonArray[0].jsonObject["media"]!!.jsonObject["bannerImage"].toString().trim('"')
        }
        return null
    }

    fun getBannerImages(): ArrayList<String?> {
        val default = arrayListOf<String?>(null,null)
        default[0]=bannerImage("ANIME")
        default[1]=bannerImage("MANGA")
        return default
    }

    fun getMediaLists(anime:Boolean,userId:Int): MutableMap<String,ArrayList<Media>> {
        val response = executeQuery("""{ MediaListCollection(userId: $userId, type: ${if(anime) "ANIME" else "MANGA"}) { lists { name entries { status progress score(format:POINT_100) media { id status chapters episodes nextAiringEpisode {episode} bannerImage meanScore isFavourite coverImage{large} title {english romaji userPreferred } } } } } }""")
        val map = mutableMapOf<String,ArrayList<Media>>()
        val all = arrayListOf<Media>()
        if(response!=null) response["data"]!!.jsonObject["MediaListCollection"]!!.jsonObject["lists"]!!.jsonArray.reversed().forEach { i ->
            val name = i.jsonObject["name"].toString().trim('"')
            map[name] = arrayListOf()
            i.jsonObject["entries"]!!.jsonArray.forEach {
                val a = Media(
                    id = it.jsonObject["media"]!!.jsonObject["id"].toString().toInt(),
                    name = it.jsonObject["media"]!!.jsonObject["title"]!!.jsonObject["english"].toString().trim('"'),
                    nameRomaji = it.jsonObject["media"]!!.jsonObject["title"]!!.jsonObject["romaji"].toString().trim('"'),
                    userPreferredName = it.jsonObject["media"]!!.jsonObject["title"]!!.jsonObject["userPreferred"].toString().trim('"'),
                    cover = it.jsonObject["media"]!!.jsonObject["coverImage"]!!.jsonObject["large"].toString().trim('"'),
                    banner = it.jsonObject["media"]!!.jsonObject["bannerImage"].toString().trim('"'),
                    status = it.jsonObject["media"]!!.jsonObject["status"].toString().trim('"'),
                    meanScore = if(it.jsonObject["media"]!!.jsonObject["meanScore"] != JsonNull) it.jsonObject["media"]!!.jsonObject["meanScore"].toString().toInt() else null,
                    isFav = it.jsonObject["media"]!!.jsonObject["isFavourite"].toString()=="true",
                    userScore = it.jsonObject["score"].toString().toInt(),
                    userStatus = it.jsonObject["status"].toString().trim('"'),
                    userProgress = it.jsonObject["progress"].toString().toInt(),
                    manga = if(!anime) Manga(totalChapters = if (it.jsonObject["media"]!!.jsonObject["chapters"] != JsonNull) it.jsonObject["media"]!!.jsonObject["chapters"].toString().toInt() else null) else null,
                    anime = if(anime) Anime(totalEpisodes = if(it.jsonObject["media"]!!.jsonObject["episodes"] != JsonNull) it.jsonObject["media"]!!.jsonObject["episodes"].toString().toInt() else null, nextAiringEpisode = if (it.jsonObject["media"]!!.jsonObject["nextAiringEpisode"] != JsonNull) it.jsonObject["media"]!!.jsonObject["nextAiringEpisode"]!!.jsonObject["episode"].toString().toInt()-1 else null) else null
                )
                map[name]!!.add(a)
                all.add(a)
            }
        }
        map["All"] = all
        return map
    }

    fun genreCollection(activity:Activity):Boolean{
        var success = false
        logger("GenreCollection started")
        val genres:MutableMap<String,String>? = loadData("genres",activity)
        val time :Long?= loadData("genresTime",activity)
        fun get(){
            fun snack(string:String){
                activity.runOnUiThread {
                    val snackBar = Snackbar.make(activity.window.decorView.findViewById(android.R.id.content), string, Snackbar.LENGTH_LONG)
                    snackBar.view.translationY = -navBarHeight.dp
                    snackBar.show()
                }
            }
            snack("Updating Genres, Please wait...")
            val returnMap = mutableMapOf<String,String>()
            val query = "{GenreCollection}"
            val ids = arrayListOf<String>()
            try {
                val executedQuery = executeQuery(query, force = true)!!
                if (executedQuery["data"] != JsonNull) {
                    executedQuery["data"]!!.jsonObject["GenreCollection"]!!.jsonArray.forEach { genre ->
                        val genreQuery = """{ Page(perPage: 10){media(genre:${
                            genre.toString().replace("\"", "\\\"")
                        }, sort: TRENDING_DESC, type: ANIME, countryOfOrigin:\"JP\") {id bannerImage } } }"""
                        val response =
                            executeQuery(genreQuery, force = true)!!["data"]!!.jsonObject["Page"]!!
                        if (response.jsonObject["media"] != JsonNull) {
                            run next@{
                                response.jsonObject["media"]!!.jsonArray.forEach {
                                    if (it.jsonObject["id"].toString() !in ids && it.jsonObject["bannerImage"] != JsonNull) {
                                        ids.add(it.jsonObject["id"].toString())
                                        returnMap[genre.toString().trim('"')] =
                                            it.jsonObject["bannerImage"].toString().trim('"')
                                        return@next
                                    }
                                }
                            }
                        }
                    }
                }
                saveData("genres", returnMap, activity)
                saveData("genresTime", System.currentTimeMillis(), activity)
                success = true
                Anilist.genres = returnMap
                logger("$returnMap \nfinished")
                snack("Genres Updated! Now Loading...")
            }catch (e:Exception){
                toastString(e.toString())
            }
        }
        if (genres==null) {
            get();println("genres null")
        }
        else{
            if(time!=null)
                if (time - System.currentTimeMillis() < 604800000) {
                    logger("Loaded Genres from Save.")
                    success = true
                    Anilist.genres = genres

                } else get()
            else get()
        }
        return success
    }

    fun search(
        type: String,
        page: Int? = null,
        perPage:Int?=null,
        search: String? = null,
        sort: String? = null,
        genres: ArrayList<String>? = null,
        format:String?=null,
        id: Int?=null
    ): SearchResults {
        val query = """
query (${"$"}page: Int = 1, ${"$"}id: Int, ${"$"}type: MediaType, ${"$"}isAdult: Boolean = false, ${"$"}search: String, ${"$"}format: [MediaFormat], ${"$"}status: MediaStatus, ${"$"}countryOfOrigin: CountryCode, ${"$"}source: MediaSource, ${"$"}season: MediaSeason, ${"$"}seasonYear: Int, ${"$"}year: String, ${"$"}onList: Boolean, ${"$"}yearLesser: FuzzyDateInt, ${"$"}yearGreater: FuzzyDateInt, ${"$"}episodeLesser: Int, ${"$"}episodeGreater: Int, ${"$"}durationLesser: Int, ${"$"}durationGreater: Int, ${"$"}chapterLesser: Int, ${"$"}chapterGreater: Int, ${"$"}volumeLesser: Int, ${"$"}volumeGreater: Int, ${"$"}licensedBy: [String], ${"$"}isLicensed: Boolean, ${"$"}genres: [String], ${"$"}excludedGenres: [String], ${"$"}tags: [String], ${"$"}excludedTags: [String], ${"$"}minimumTagRank: Int, ${"$"}sort: [MediaSort] = [POPULARITY_DESC, SCORE_DESC]) {
  Page(page: ${"$"}page, perPage: ${perPage?:50}) {
    pageInfo {
      total
      perPage
      currentPage
      lastPage
      hasNextPage
    }
    media(id: ${"$"}id, type: ${"$"}type, season: ${"$"}season, format_in: ${"$"}format, status: ${"$"}status, countryOfOrigin: ${"$"}countryOfOrigin, source: ${"$"}source, search: ${"$"}search, onList: ${"$"}onList, seasonYear: ${"$"}seasonYear, startDate_like: ${"$"}year, startDate_lesser: ${"$"}yearLesser, startDate_greater: ${"$"}yearGreater, episodes_lesser: ${"$"}episodeLesser, episodes_greater: ${"$"}episodeGreater, duration_lesser: ${"$"}durationLesser, duration_greater: ${"$"}durationGreater, chapters_lesser: ${"$"}chapterLesser, chapters_greater: ${"$"}chapterGreater, volumes_lesser: ${"$"}volumeLesser, volumes_greater: ${"$"}volumeGreater, licensedBy_in: ${"$"}licensedBy, isLicensed: ${"$"}isLicensed, genre_in: ${"$"}genres, genre_not_in: ${"$"}excludedGenres, tag_in: ${"$"}tags, tag_not_in: ${"$"}excludedTags, minimumTagRank: ${"$"}minimumTagRank, sort: ${"$"}sort, isAdult: ${"$"}isAdult) {
      id
      status
      chapters
      episodes
      nextAiringEpisode {
        episode
      }
      type
      meanScore
      isFavourite
      bannerImage
      coverImage {
        large
      }
      title {
        english
        romaji
        userPreferred
      }
      mediaListEntry {
        progress
        score(format: POINT_100)
        status
      }
    }
  }
}
        """.replace("\n", " ").replace("""  """, "")
        val variables = """{\"type\":\"$type\"
            ${if (page != null) """,\"page\":\"$page\"""" else ""}
            ${if (id != null) """,\"id\":\"$id\"""" else ""}
            ${if (search != null) """,\"search\":\"$search\"""" else ""}
            ${if (sort != null) """,\"sort\":\"$sort\"""" else ""}
            ${if (format != null) """,\"format\":\"$format\"""" else ""}
            ${if (genres != null && genres.isNotEmpty()) """,\"genres\":\"${genres[0]}\"""" else ""}
            }""".replace("\n", " ").replace("""  """, "")
//        println(variables)
        val response = executeQuery(query, variables, true)!!
//        println("$response")
        val a = response["data"]!!.jsonObject["Page"]!!
        val responseArray = arrayListOf<Media>()
        a.jsonObject["media"]!!.jsonArray.forEach { i ->
            responseArray.add(
                Media(
                    id = i.jsonObject["id"].toString().toInt(),
                    name = i.jsonObject["title"]!!.jsonObject["english"].toString().trim('"').replace("\\\"","\""),
                    nameRomaji = i.jsonObject["title"]!!.jsonObject["romaji"].toString().trim('"').replace("\\\"","\""),
                    userPreferredName = i.jsonObject["title"]!!.jsonObject["userPreferred"].toString().trim('"').replace("\\\"","\""),
                    cover = i.jsonObject["coverImage"]!!.jsonObject["large"].toString().trim('"'),
                    banner = i.jsonObject["bannerImage"].toString().trim('"'),
                    status = i.jsonObject["status"].toString().trim('"'),
                    isFav = i.jsonObject["isFavourite"].toString() == "true",
                    userProgress = if (i.jsonObject["mediaListEntry"] != JsonNull) i.jsonObject["mediaListEntry"]!!.jsonObject["progress"].toString().toInt() else null,
                    userScore = if (i.jsonObject["mediaListEntry"] != JsonNull) i.jsonObject["mediaListEntry"]!!.jsonObject["score"].toString().toInt() else 0,
                    userStatus = if (i.jsonObject["mediaListEntry"] != JsonNull) i.jsonObject["mediaListEntry"]!!.jsonObject["status"].toString().trim('"') else null,
                    meanScore = if (i.jsonObject["meanScore"].toString().trim('"') != "null") i.jsonObject["meanScore"].toString().toInt() else null,
                    anime = if (i.jsonObject["type"].toString().trim('"') == "ANIME") Anime(totalEpisodes = if (i.jsonObject["episodes"] != JsonNull) i.jsonObject["episodes"].toString().toInt() else null, nextAiringEpisode = if (i.jsonObject["nextAiringEpisode"] != JsonNull) i.jsonObject["nextAiringEpisode"]!!.jsonObject["episode"].toString().toInt() - 1 else null) else null,
                    manga = if (i.jsonObject["type"].toString().trim('"') == "MANGA") Manga(totalChapters = if (i.jsonObject["chapters"] != JsonNull) i.jsonObject["chapters"].toString().toInt() else null) else null,
                )
            )
        }
        return SearchResults(
            type = type,
            perPage = perPage,
            search = search,
            sort = sort,
            genres = genres,
            results = responseArray,
            page = a.jsonObject["pageInfo"]!!.jsonObject["currentPage"].toString().toInt(),
            hasNextPage = a.jsonObject["pageInfo"]!!.jsonObject["hasNextPage"].toString()=="true",
        )
    }

    fun recentlyUpdated():ArrayList<Media>{
        val query="""{
Page(page:1,perPage:50) {
    pageInfo {
        hasNextPage
        total
    }
    airingSchedules(
        airingAt_greater: 0
        airingAt_lesser: ${System.currentTimeMillis()/1000-10000}
        sort:TIME_DESC
    ) {
        media {
            id
            status
            chapters
            episodes
            nextAiringEpisode { episode }
            type
            meanScore
            isFavourite
            bannerImage
            countryOfOrigin
            coverImage { large }
            title {
                english
                romaji
                userPreferred
            }
            mediaListEntry {
                progress
                score(format: POINT_100)
                status
            }
        }
    }
}
        }""".replace("\n", " ").replace("""  """, "")
        val response = executeQuery(query, force = true)!!
        val a = response["data"]!!.jsonObject["Page"]!!.jsonObject["airingSchedules"]!!
        val responseArray = arrayListOf<Media>()
        a.jsonArray.forEach {
            val i = it.jsonObject["media"]!!
            if (i.jsonObject["countryOfOrigin"].toString().trim('"')=="JP")
            responseArray.add(
                Media(
                    id = i.jsonObject["id"].toString().toInt(),
                    name = i.jsonObject["title"]!!.jsonObject["english"].toString().trim('"').replace("\\\"","\""),
                    nameRomaji = i.jsonObject["title"]!!.jsonObject["romaji"].toString().trim('"').replace("\\\"","\""),
                    userPreferredName = i.jsonObject["title"]!!.jsonObject["userPreferred"].toString().trim('"').replace("\\\"","\""),
                    cover = i.jsonObject["coverImage"]!!.jsonObject["large"].toString().trim('"'),
                    banner = i.jsonObject["bannerImage"].toString().trim('"'),
                    status = i.jsonObject["status"].toString().trim('"'),
                    isFav = i.jsonObject["isFavourite"].toString() == "true",
                    userProgress = if (i.jsonObject["mediaListEntry"] != JsonNull) i.jsonObject["mediaListEntry"]!!.jsonObject["progress"].toString().toInt() else null,
                    userScore = if (i.jsonObject["mediaListEntry"] != JsonNull) i.jsonObject["mediaListEntry"]!!.jsonObject["score"].toString().toInt() else 0,
                    userStatus = if (i.jsonObject["mediaListEntry"] != JsonNull) i.jsonObject["mediaListEntry"]!!.jsonObject["status"].toString().trim('"') else null,
                    meanScore = if (i.jsonObject["meanScore"].toString().trim('"') != "null") i.jsonObject["meanScore"].toString().toInt() else null,
                    anime = Anime(totalEpisodes = if (i.jsonObject["episodes"] != JsonNull) i.jsonObject["episodes"].toString().toInt() else null, nextAiringEpisode = if (i.jsonObject["nextAiringEpisode"] != JsonNull) i.jsonObject["nextAiringEpisode"]!!.jsonObject["episode"].toString().toInt() - 1 else null)
                )
            )
        }
        return responseArray
    }
}
