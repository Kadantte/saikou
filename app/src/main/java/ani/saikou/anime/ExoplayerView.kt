
package ani.saikou.anime

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import ani.saikou.*
import ani.saikou.databinding.ActivityExoplayerBinding
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.MappingTrackSelector
import com.google.android.exoplayer2.ui.CaptionStyleCompat
import com.google.android.exoplayer2.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.ui.TrackSelectionDialogBuilder
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.exoplayer2.upstream.HttpDataSource
import com.google.android.exoplayer2.util.MimeTypes
import android.view.GestureDetector
import android.widget.TextView
import androidx.activity.viewModels
import androidx.core.math.MathUtils.clamp
import ani.saikou.DoubleClickListener
import ani.saikou.media.Media
import ani.saikou.media.MediaDetailsViewModel
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.TracksInfo
import com.google.android.exoplayer2.Player
import com.google.android.material.slider.Slider
import java.util.*
import kotlin.math.roundToInt

class ExoplayerView : AppCompatActivity(), Player.Listener {
    private lateinit var binding : ActivityExoplayerBinding
    private lateinit var exoPlayer: ExoPlayer
    private lateinit var dataSourceFactory: DataSource.Factory

    private lateinit var playerView: PlayerView
    private lateinit var exoSource: ImageButton
    private lateinit var exoQuality: ImageButton
    private lateinit var exoSpeed: ImageButton
    private lateinit var exoScreen: ImageButton
    private lateinit var exoBrightness: Slider
    private lateinit var exoVolume: Slider
    private lateinit var exoBrightnessCont: View
    private lateinit var exoVolumeCont: View
    private lateinit var animeTitle : TextView
    private lateinit var episodeTitle : TextView

    private lateinit var mediaItem : MediaItem

    private lateinit var media: Media

    private var currentWindow = 0
    private var playbackPosition: Long = 0
    private var isFullscreen = false
    private var isInitialized = false
    private var isPlayerPlaying = true

    private val model: MediaDetailsViewModel by viewModels()

    override fun onDestroy() {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExoplayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initActivity(this)
        if (Settings.System.getInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) != 1) requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        hideSystemBars()

        playerView = findViewById(R.id.player_view)
        exoQuality = playerView.findViewById(R.id.exo_quality)
        exoSource = playerView.findViewById(R.id.exo_source)
        exoSpeed = playerView.findViewById(R.id.exo_playback_speed)
        exoScreen = playerView.findViewById(R.id.exo_screen)
        exoBrightness = playerView.findViewById(R.id.exo_brightness)
        exoVolume = playerView.findViewById(R.id.exo_volume)
        exoBrightnessCont = playerView.findViewById(R.id.exo_brightness_cont)
        exoVolumeCont = playerView.findViewById(R.id.exo_volume_cont)
        animeTitle = playerView.findViewById(R.id.exo_anime_title)
        episodeTitle = playerView.findViewById(R.id.exo_ep_title)

        playerView.controllerShowTimeoutMs = 5000
        val audioManager = applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        playerView.subtitleView?.setStyle(CaptionStyleCompat(Color.WHITE,Color.TRANSPARENT,Color.TRANSPARENT,EDGE_TYPE_OUTLINE,Color.BLACK,
            ResourcesCompat.getFont(currActivity()!!, R.font.poppins)))

        //Speed
        val speedsName:Array<String> = resources.getStringArray(R.array.exo_playback_speeds)
        val speeds: IntArray = resources.getIntArray(R.array.exo_speed_multiplied_by_100)
        var curSpeed = 3
        var speed: Float
        val speedDialog = AlertDialog.Builder(this,R.style.Theme_Saikou).setTitle("Speed")

        exoSpeed.setOnClickListener{
            speedDialog.setSingleChoiceItems(speedsName,curSpeed) { dialog, i ->
                speed = (speeds[i]).toFloat() / 100
                curSpeed = i
                exoPlayer.playbackParameters = PlaybackParameters(speed)
                dialog.dismiss()
                hideSystemBars()
            }.show()
        }

        speedDialog.setOnCancelListener {
            hideSystemBars()
        }

        val handler = Handler(Looper.getMainLooper())

        var brightnessTimer = Timer()
        val brightnessRunnable = Runnable { exoBrightnessCont.visibility = View.GONE }
        fun brightnessHide(){
            brightnessTimer.cancel()
            brightnessTimer.purge()
            val timerTask: TimerTask = object : TimerTask() {
                override fun run() {
                    handler.post(brightnessRunnable)
                }
            }
            brightnessTimer = Timer()
            brightnessTimer.schedule(timerTask, 3000)
        }
        exoBrightness.value = clamp(window.attributes.screenBrightness*10,0f,10f)
        exoBrightness.addOnChangeListener { _, value, _ ->
            val lp = window.attributes
            lp.screenBrightness = value / 10f
            println(lp.screenBrightness)
            window.attributes = lp
            brightnessHide()
        }

        var volumeTimer = Timer()
        val volumeRunnable = Runnable { exoVolumeCont.visibility = View.GONE }
        val volumeMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        exoVolume.value = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)/volumeMax*10f
        fun volumeHide(){
            volumeTimer.cancel()
            volumeTimer.purge()
            val timerTask: TimerTask = object : TimerTask() {
                override fun run() {
                    handler.post(volumeRunnable)
                }
            }
            volumeTimer = Timer()
            volumeTimer.schedule(timerTask, 3000)
        }
        exoVolume.addOnChangeListener { _, value, _ ->
            val volume = (value/10*volumeMax).roundToInt()
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC,volume,0)
            volumeHide()
        }

        exoScreen.setOnClickListener {
            if(!isFullscreen) {
                playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                isFullscreen = true
            }
            else {
                playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                isFullscreen = false
            }
        }

        playerView.findViewById<ImageButton>(R.id.exo_back).setOnClickListener{
            onBackPressed()
        }

        fun handleController(){
            if(playerView.isControllerVisible){
                playerView.hideController()
                exoBrightnessCont.visibility = View.GONE
                exoVolumeCont.visibility = View.GONE
            }else{
                playerView.showController()
                ObjectAnimator.ofFloat(playerView.findViewById(R.id.exo_controller),"alpha",0f,1f).setDuration(200).start()
            }
        }

        val fastForwardDetector = GestureDetector(this, object : DoubleClickListener() {
            override fun onDoubleClick() = exoPlayer.seekTo(exoPlayer.currentPosition + 10000)
            override fun onScrollYClick(y: Float) {
                exoVolume.value = clamp(exoVolume.value+y/50,0f,10f)
                exoVolumeCont.visibility = View.VISIBLE
            }

            override fun onSingleClick() = handleController()
        })

        val fastRewindDetector = GestureDetector(this, object : DoubleClickListener() {
            override fun onDoubleClick() = exoPlayer.seekTo(exoPlayer.currentPosition - 10000)

            override fun onScrollYClick(y: Float) {
                exoBrightness.value = clamp(exoBrightness.value+y/50,0f,10f)
                exoBrightnessCont.visibility = View.VISIBLE
            }
            override fun onSingleClick() = handleController()
        })

        playerView.findViewById<View>(R.id.exo_fast_rewind).setOnTouchListener { v, event ->
            fastRewindDetector.onTouchEvent(event)
            v.performClick()
            true
        }

        playerView.findViewById<View>(R.id.exo_fast_forward).setOnTouchListener { v, event ->
            fastForwardDetector.onTouchEvent(event)
            v.performClick()
            true
        }

        playerView.findViewById<View>(R.id.exo_skip).setOnClickListener {
            exoPlayer.seekTo(exoPlayer.currentPosition + 85000)
        }

        if (savedInstanceState != null) {
            currentWindow = savedInstanceState.getInt(STATE_RESUME_WINDOW)
            playbackPosition = savedInstanceState.getLong(STATE_RESUME_POSITION)
            isFullscreen = savedInstanceState.getBoolean(STATE_PLAYER_FULLSCREEN)
            isPlayerPlaying = savedInstanceState.getBoolean(STATE_PLAYER_PLAYING)
        }

        media = intent.getSerializableExtra("media")!! as Media
        model.setMedia(media)

        model.getEpisode().observe(this,{
            if(it!=null) {
                media.selected = model.loadSelected(media.id)
                if (isInitialized) releasePlayer()
                initPlayer(it)
            }
        })

        animeTitle.text = media.userPreferredName
        model.setEpisode(media.anime!!.episodes!![media.anime!!.selectedEpisode!!]!!)
    }

    private fun hideSystemBars() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
               View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        )
    }

    @SuppressLint("SetTextI18n")
    private fun initPlayer(episode: Episode){
        episodeTitle.text = "Episode ${episode.number}${if(episode.title!="") " : "+episode.title else ""}${if(episode.filler) "\n[Filler]" else ""}"

        //Url
        dataSourceFactory = DataSource.Factory {
            val dataSource: HttpDataSource = DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true).createDataSource()
            dataSource.setRequestProperty("User-Agent","Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/93.0.4577.63 Safari/537.36")
            if (episode.streamLinks[episode.selectedStream]!!.referer!=null)
                dataSource.setRequestProperty("referer", episode.streamLinks[episode.selectedStream]!!.referer!!)
            dataSource
        }

        //Subtitles
        val a = episode.streamLinks[episode.selectedStream]!!.subtitles
        val subtitle: MediaItem.SubtitleConfiguration? = if (a!=null && a.contains("English"))
            MediaItem.SubtitleConfiguration.Builder(Uri.parse(a["English"]))
                .setMimeType(MimeTypes.TEXT_VTT).setSelectionFlags(C.SELECTION_FLAG_FORCED)
                .build()
        else null

        val builder =  MediaItem.Builder().setUri(episode.streamLinks[episode.selectedStream]!!.quality[episode.selectedQuality].url)
        if(subtitle!=null) builder.setSubtitleConfigurations(mutableListOf(subtitle))
        mediaItem = builder.build()

        //Source
        exoSource.setOnClickListener {
            media.selected!!.stream = null
            model.saveSelected(media.id,media.selected!!,this)
            model.onEpisodeClick(media,episode.number,this.supportFragmentManager,false)
        }


        val trackSelector = DefaultTrackSelector(this)
        trackSelector.setParameters(trackSelector.buildUponParameters().setMaxVideoSize(MAX_WIDTH, MAX_HEIGHT))
        exoPlayer = ExoPlayer.Builder(this).setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory)).setTrackSelector(trackSelector).build().apply {
            playWhenReady = isPlayerPlaying
            setMediaItem(mediaItem)
            prepare()
        }
        playerView.player = exoPlayer

        exoPlayer.addListener(object : Player.Listener {
            override fun onTracksInfoChanged(tracksInfo: TracksInfo) {
                if(tracksInfo.trackGroupInfos.size<=2) exoQuality.visibility = View.GONE
                else {
                    exoQuality.visibility = View.VISIBLE
                    exoQuality.setOnClickListener {
                        initPopupQuality(trackSelector)?.show()
                    }
                }
            }
        })

        isInitialized = true
    }

    private fun releasePlayer(){
        isPlayerPlaying = exoPlayer.playWhenReady
        playbackPosition = exoPlayer.currentPosition
        exoPlayer.release()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_RESUME_WINDOW, exoPlayer.currentMediaItemIndex)
        outState.putLong(STATE_RESUME_POSITION, exoPlayer.currentPosition)
        outState.putBoolean(STATE_PLAYER_FULLSCREEN, isFullscreen)
        outState.putBoolean(STATE_PLAYER_PLAYING, isPlayerPlaying)
        super.onSaveInstanceState(outState)
    }

    override fun onPause() {
        super.onPause()
        playerView.player?.pause()
    }

    override fun onResume() {
        super.onResume()
        if(isInitialized) {
            playerView.onResume()
            playerView.useController = true
        }
    }

    override fun onStop() {
        super.onStop()
        playerView.player?.pause()
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        playerView.keepScreenOn = isPlaying
    }

    // QUALITY SELECTOR
    private fun initPopupQuality(trackSelector:DefaultTrackSelector):Dialog? {
        val mappedTrackInfo = trackSelector.currentMappedTrackInfo?:return null
        var videoRenderer : Int? = null

        for(i in 0 until mappedTrackInfo.rendererCount){
            if(isVideoRenderer(mappedTrackInfo, i)){
                videoRenderer = i
            }
        }

        if(videoRenderer == null){
            return null
        }

        val trackSelectionDialogBuilder = TrackSelectionDialogBuilder(this, "Available Qualities", trackSelector, videoRenderer)
        trackSelectionDialogBuilder.setTrackNameProvider{ it.height.toString()+"p" }
        val trackDialog = trackSelectionDialogBuilder.build()
        trackDialog.setOnDismissListener {
            hideSystemBars()
        }
        return trackDialog
    }

    private fun isVideoRenderer(mappedTrackInfo: MappingTrackSelector.MappedTrackInfo, rendererIndex: Int): Boolean {
        val trackGroupArray = mappedTrackInfo.getTrackGroups(rendererIndex)
        if (trackGroupArray.length == 0) {
            return false
        }
        val trackType = mappedTrackInfo.getRendererType(rendererIndex)
        return C.TRACK_TYPE_VIDEO == trackType
    }
}