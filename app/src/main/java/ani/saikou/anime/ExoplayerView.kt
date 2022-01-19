
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
import android.provider.Settings.System
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.res.ResourcesCompat
import androidx.core.math.MathUtils.clamp
import ani.saikou.*
import ani.saikou.databinding.ActivityExoplayerBinding
import ani.saikou.media.Media
import ani.saikou.media.MediaDetailsViewModel
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.MappingTrackSelector
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.CaptionStyleCompat
import com.google.android.exoplayer2.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.ui.TrackSelectionDialogBuilder
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.exoplayer2.upstream.HttpDataSource
import com.google.android.exoplayer2.upstream.cache.CacheDataSource
import com.google.android.exoplayer2.util.MimeTypes
import com.google.android.material.slider.Slider
import java.util.*
import kotlin.math.roundToInt

class ExoplayerView : AppCompatActivity(), Player.Listener {
    private lateinit var binding : ActivityExoplayerBinding
    private lateinit var exoPlayer: ExoPlayer
    private lateinit var trackSelector: DefaultTrackSelector

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

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExoplayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        //Initialize
        initActivity(this)
        if (System.getInt(contentResolver, System.ACCELEROMETER_ROTATION, 0) != 1) requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
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

        val handler = Handler(Looper.getMainLooper())
        playerView.controllerShowTimeoutMs = 5000
        val audioManager = applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        playerView.subtitleView?.setStyle(CaptionStyleCompat(Color.WHITE,Color.TRANSPARENT,Color.TRANSPARENT,EDGE_TYPE_OUTLINE,Color.BLACK,
            ResourcesCompat.getFont(currActivity()!!, R.font.poppins)))

        if (savedInstanceState != null) {
            currentWindow = savedInstanceState.getInt(STATE_RESUME_WINDOW)
            playbackPosition = savedInstanceState.getLong(STATE_RESUME_POSITION)
            isFullscreen = savedInstanceState.getBoolean(STATE_PLAYER_FULLSCREEN)
            isPlayerPlaying = savedInstanceState.getBoolean(STATE_PLAYER_PLAYING)
        }

        //Speed
        val speedsName:Array<String> = arrayOf("0.25x","0.33x","0.5x","0.66x","0.75x","1x","1.25x","1.33x","1.5x","1.66x","1.75x","2x")
        val speeds: Array<Float>     = arrayOf( 0.25f , 0.33f , 0.5f , 0.66f , 0.75f , 1f , 1.25f , 1.33f , 1.5f , 1.66f , 1.75f , 2f )
        var curSpeed = 5
        var speed: Float
        val speedDialog = AlertDialog.Builder(this,R.style.Theme_Saikou).setTitle("Speed")
        exoSpeed.setOnClickListener{
            speedDialog.setSingleChoiceItems(speedsName,curSpeed) { dialog, i ->
                speed = speeds[i]
                curSpeed = i
                exoPlayer.playbackParameters = PlaybackParameters(speed)
                dialog.dismiss()
                hideSystemBars()
            }.show()
        }
        speedDialog.setOnCancelListener { hideSystemBars() }

        //FullScreen
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

        //BackButton
        playerView.findViewById<ImageButton>(R.id.exo_back).setOnClickListener{
            onBackPressed()
        }

        //SliderLock
        var sliderLocked = false
        playerView.findViewById<ImageButton>(R.id.exo_slider_lock).setOnClickListener {
            sliderLocked = if(sliderLocked){
                (it as ImageButton).setImageDrawable(AppCompatResources.getDrawable(this,R.drawable.ic_round_piano_24))
                false
            } else{
                (it as ImageButton).setImageDrawable(AppCompatResources.getDrawable(this,R.drawable.ic_round_piano_off_24))
                true
            }
        }

        //LockButton
        var prevSliderLocked = sliderLocked
        var locked = false
        val container = playerView.findViewById<View>(R.id.exo_controller_cont)
        val lockButton = playerView.findViewById<ImageButton>(R.id.exo_unlock)
        playerView.findViewById<ImageButton>(R.id.exo_lock).setOnClickListener{
            prevSliderLocked = sliderLocked
            sliderLocked = true
            locked = true
            container.visibility = View.GONE
            lockButton.visibility = View.VISIBLE
        }
        lockButton.setOnClickListener {
            sliderLocked = prevSliderLocked
            locked = false
            container.visibility = View.VISIBLE
            it.visibility = View.GONE
        }

        //+85 Button
        playerView.findViewById<View>(R.id.exo_skip).setOnClickListener {
            exoPlayer.seekTo(exoPlayer.currentPosition + 85000)
        }

        //Player UI Visibility Handler
        val brightnessRunnable = Runnable {
            if(exoBrightnessCont.translationX==0f) ObjectAnimator.ofFloat(exoBrightnessCont,"translationX",0f,120f).setDuration(150).start()
        }
        val volumeRunnable = Runnable {
            if(exoVolumeCont.translationX==0f) ObjectAnimator.ofFloat(exoVolumeCont,"translationX",0f,-120f).setDuration(150).start()
        }
        playerView.setControllerVisibilityListener {
            if(it==View.GONE) {
                brightnessRunnable.run()
                volumeRunnable.run()
            }
        }

        fun handleController(){
            if(playerView.isControllerVisible){
                playerView.hideController()
            }else{
                playerView.showController()
                ObjectAnimator.ofFloat(playerView.findViewById(R.id.exo_controller),"alpha",0f,1f).setDuration(200).start()
            }
        }

        //Brightness
        var brightnessTimer = Timer()
        exoBrightnessCont.translationX = 120f

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
        exoBrightness.value = clamp(System.getInt(contentResolver, System.SCREEN_BRIGHTNESS,127)/255f*10f,0f,10f)
        exoBrightness.addOnChangeListener { _, value, _ ->
            val lp = window.attributes
            lp.screenBrightness = value / 10f
            window.attributes = lp
            brightnessHide()
        }

        //FastRewind (Left Panel)
        val fastRewindCard = playerView.findViewById<View>(R.id.exo_fast_rewind)
        val fastRewindDetector = GestureDetector(this, object : DoubleClickListener() {
            override fun onDoubleClick(event: MotionEvent?) {
                if(!locked) {
                    exoPlayer.seekTo(exoPlayer.currentPosition - 10000)
                    viewDoubleTapped(fastRewindCard,event)
                }
            }

            override fun onScrollYClick(y: Float) {
                if(!sliderLocked) {
                    exoBrightness.value = clamp(exoBrightness.value + y / 50, 0f, 10f)
                    if (exoBrightnessCont.translationX == 120f) ObjectAnimator.ofFloat(exoBrightnessCont, "translationX", 120f, 0f).setDuration(150).start()
                }
            }
            override fun onSingleClick(event: MotionEvent?) = handleController()
        })
        fastRewindCard.setOnTouchListener { v, event ->
            fastRewindDetector.onTouchEvent(event)
            v.performClick()
            true
        }


        //Volume
        var volumeTimer = Timer()
        exoVolumeCont.translationX = -120f
        val volumeMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        exoVolume.value = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()/volumeMax*10
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

        //FastForward (Right Panel)
        val fastForwardCard = playerView.findViewById<View>(R.id.exo_fast_forward)
        val fastForwardDetector = GestureDetector(this, object : DoubleClickListener() {
            override fun onDoubleClick(event: MotionEvent?) {
                if(!locked) {
                    exoPlayer.seekTo(exoPlayer.currentPosition + 10000)
                    viewDoubleTapped(fastForwardCard,event)
                }
            }
            override fun onScrollYClick(y: Float) {
                if(!sliderLocked) {
                    exoVolume.value = clamp(exoVolume.value+y/50,0f,10f)
                    if (exoVolumeCont.translationX == -120f) ObjectAnimator.ofFloat(exoVolumeCont,"translationX",-120f,0f).setDuration(150).start()
                }
            }

            override fun onSingleClick(event: MotionEvent?) = handleController()
        })
        fastForwardCard.setOnTouchListener { v, event ->
            fastForwardDetector.onTouchEvent(event)
            v.performClick()
            true
        }

        //Handle Media
        media = intent.getSerializableExtra("media")!! as Media
        model.setMedia(media)

        model.getEpisode().observe(this,{
            if(it!=null) {
                media.selected = model.loadSelected(media.id)
                model.setMedia(media)
                if (isInitialized) releasePlayer()
                initPlayer(it)
            }
        })
        //Anime Title
        animeTitle.text = media.userPreferredName

        //Set Episode, to invoke getEpisode() at Start
        model.setEpisode(media.anime!!.episodes!![media.anime!!.selectedEpisode!!]!!)
    }

    @SuppressLint("SetTextI18n")
    private fun initPlayer(episode: Episode){
        //Title
        episodeTitle.text = "Episode ${episode.number}${if(episode.title!="" || episode.title!=null) " : "+episode.title else ""}${if(episode.filler) "\n[Filler]" else ""}"

        val simpleCache = VideoCache.getInstance(this)
        val dataSourceFactory = DataSource.Factory {
            val dataSource: HttpDataSource = DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true).createDataSource()
            dataSource.setRequestProperty("User-Agent","Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/93.0.4577.63 Safari/537.36")
            if (episode.streamLinks[episode.selectedStream]!!.referer!=null)
                dataSource.setRequestProperty("referer", episode.streamLinks[episode.selectedStream]!!.referer!!)
            dataSource
        }
        val cacheFactory = CacheDataSource.Factory().apply {
            setCache(simpleCache)
            setUpstreamDataSourceFactory(dataSourceFactory)
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

        //Quality Track
        trackSelector = DefaultTrackSelector(this)
        trackSelector.setParameters(trackSelector.buildUponParameters().setMaxVideoSize(MAX_WIDTH, MAX_HEIGHT))

        //Player
        exoPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cacheFactory))
            .setTrackSelector(trackSelector)
            .build().apply {
                playWhenReady = isPlayerPlaying
                setMediaItem(mediaItem)
                prepare()
        }
        playerView.player = exoPlayer
        exoPlayer.addListener(this)

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

    override fun onTracksInfoChanged(tracksInfo: TracksInfo) {
        if(tracksInfo.trackGroupInfos.size<=2) exoQuality.visibility = View.GONE
        else {
            trackSelector.buildUponParameters().setMinVideoFrameRate(1)
            exoQuality.visibility = View.VISIBLE
            exoQuality.setOnClickListener {
                initPopupQuality(trackSelector)?.show()
            }
        }
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

    // QUALITY SELECTOR
    private fun initPopupQuality(trackSelector:DefaultTrackSelector):Dialog? {
        val mappedTrackInfo = trackSelector.currentMappedTrackInfo?:return null
        var videoRenderer : Int? = null

        fun isVideoRenderer(mappedTrackInfo: MappingTrackSelector.MappedTrackInfo, rendererIndex: Int): Boolean {
            if (mappedTrackInfo.getTrackGroups(rendererIndex).length == 0) return false
            return C.TRACK_TYPE_VIDEO == mappedTrackInfo.getRendererType(rendererIndex)
        }

        for(i in 0 until mappedTrackInfo.rendererCount)
            if(isVideoRenderer(mappedTrackInfo, i))
                videoRenderer = i

        val trackSelectionDialogBuilder = TrackSelectionDialogBuilder(this, "Available Qualities", trackSelector, videoRenderer?:return null)
        trackSelectionDialogBuilder.setTheme(R.style.Theme_Saikou)
        trackSelectionDialogBuilder.setTrackNameProvider{
            if(it.frameRate>0f) it.height.toString()+"p" else it.height.toString()+"p (fps : N/A)"
        }
        val trackDialog = trackSelectionDialogBuilder.build()

        trackDialog.setOnDismissListener { hideSystemBars() }
        return trackDialog
    }

    private fun viewDoubleTapped(v:View,event:MotionEvent?){
        if(event!=null) v.circularReveal(event.x.toInt(), event.y.toInt(), 300)
        v.alpha=1f
        ObjectAnimator.ofFloat(v,"alpha",0f,1f).setDuration(300).start()
        v.postDelayed({
            v.alpha=0f
            ObjectAnimator.ofFloat(v,"alpha",1f,0f).setDuration(150).start()
        },450)
    }
}