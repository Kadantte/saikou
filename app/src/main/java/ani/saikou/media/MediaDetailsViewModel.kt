package ani.saikou.media

import android.app.Activity
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import ani.saikou.anilist.Anilist
import ani.saikou.anime.Episode
import ani.saikou.anime.source.AnimeSources
import ani.saikou.kitsu.Kitsu
import ani.saikou.loadData
import ani.saikou.logger
import ani.saikou.manga.MangaChapter
import ani.saikou.manga.source.MangaSources
import ani.saikou.saveData

class MediaDetailsViewModel:ViewModel() {
    fun saveSelected(id:Int,data:Selected,activity: Activity){
        saveData("$id-select",data,activity)
    }
    fun loadSelected(id:Int):Selected{
        return loadData<Selected>("$id-select")?: Selected()
    }

    private val media: MutableLiveData<Media> = MutableLiveData<Media>(null)
    fun getMedia(): LiveData<Media> = media
    fun loadMedia(m:Media) { if (media.value==null) media.postValue(Anilist.query.mediaDetails(m)) }

    val sources = MutableLiveData<ArrayList<Source>?>(null)

    val userScore = MutableLiveData<Double?>(null)
    val userProgress = MutableLiveData<Int?>(null)
    val userStatus = MutableLiveData<String?>(null)

    private val kitsuEpisodes: MutableLiveData<MutableMap<String,Episode>> = MutableLiveData<MutableMap<String,Episode>>(null)
    fun getKitsuEpisodes() : LiveData<MutableMap<String,Episode>> = kitsuEpisodes
    fun loadKitsuEpisodes(s:Media){ if (kitsuEpisodes.value==null) kitsuEpisodes.postValue(Kitsu.getKitsuEpisodesDetails(s))}

    private val episodes: MutableLiveData<MutableMap<Int,MutableMap<String,Episode>>> = MutableLiveData<MutableMap<Int,MutableMap<String,Episode>>>(null)
    private val epsLoaded = mutableMapOf<Int,MutableMap<String,Episode>>()
    fun getEpisodes() : LiveData<MutableMap<Int,MutableMap<String,Episode>>> = episodes
    fun loadEpisodes(media: Media,i:Int){
        logger("Loading Episodes : $epsLoaded")
        if(!epsLoaded.containsKey(i)) {
            epsLoaded[i] = AnimeSources[i]!!.getEpisodes(media)
        }
        episodes.postValue(epsLoaded)
    }
    fun overrideEpisodes(i: Int, source: Source,id:Int){
        AnimeSources[i]!!.saveSource(source,id)
        epsLoaded[i] = AnimeSources[i]!!.getSlugEpisodes(source.link)
        episodes.postValue(epsLoaded)
    }
    private var streams: MutableLiveData<Episode> = MutableLiveData<Episode>(null)
    fun getStreams() : LiveData<Episode> = streams
    fun loadStreams(episode: Episode,i:Int){
        streams.postValue(AnimeSources[i]?.getStreams(episode)?:episode)
        streams = MutableLiveData<Episode>(null)
    }
    fun loadStream(episode: Episode,selected: Selected):Boolean{
        return if(selected.stream!=null) {
            streams.postValue(AnimeSources[selected.source]?.getStream(episode, selected.stream!!))
            streams = MutableLiveData<Episode>(null)
            true
        } else false
    }

    private val mangaChapters: MutableLiveData<MutableMap<Int,MutableMap<String,MangaChapter>>> = MutableLiveData<MutableMap<Int,MutableMap<String,MangaChapter>>>(null)
    private val mangaLoaded = mutableMapOf<Int,MutableMap<String,MangaChapter>>()
    fun getMangaChapters() : LiveData<MutableMap<Int,MutableMap<String,MangaChapter>>> = mangaChapters
    fun loadMangaChapters(media:Media,i:Int){
        logger("Loading Manga Chapters : $mangaLoaded")
        if(!mangaLoaded.containsKey(i)){
            mangaLoaded[i] = MangaSources[i]!!.getChapters(media)
        }
        mangaChapters.postValue(mangaLoaded)
    }
    fun overrideMangaChapters(i: Int, source: Source,id:Int){
        MangaSources[i]!!.saveSource(source,id)
        mangaLoaded[i] = MangaSources[i]!!.getLinkChapters(source.link)
        mangaChapters.postValue(mangaLoaded)
    }
}