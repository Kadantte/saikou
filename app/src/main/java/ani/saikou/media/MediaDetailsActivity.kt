package ani.saikou.media

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.math.MathUtils
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import ani.saikou.*
import ani.saikou.anime.AnimeSourceFragment
import ani.saikou.databinding.ActivityMediaBinding
import ani.saikou.manga.MangaSourceFragment
import com.google.android.material.appbar.AppBarLayout
import com.squareup.picasso.Picasso
import kotlinx.coroutines.*
import nl.joery.animatedbottombar.AnimatedBottomBar
import kotlin.math.abs

class MediaDetailsActivity : AppCompatActivity(), AppBarLayout.OnOffsetChangedListener {

    private lateinit var binding: ActivityMediaBinding
    private val scope = CoroutineScope(Dispatchers.Default)
    private val model: MediaDetailsViewModel by viewModels()
    private lateinit var tabLayout : AnimatedBottomBar
    var selected = 0

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMediaBinding.inflate(layoutInflater)
        setContentView(binding.root)
        screenWidth = resources.displayMetrics.widthPixels.toFloat()

        //Ui init
        initActivity(window)

        binding.mediaBanner.updateLayoutParams{ height += statusBarHeight }
        binding.mediaBannerStatus.updateLayoutParams{ height += statusBarHeight }
        binding.mediaBanner.translationY = -statusBarHeight.toFloat()
        binding.mediaClose.updateLayoutParams<ViewGroup.MarginLayoutParams> { topMargin += statusBarHeight }
        binding.mediaAppBar.updateLayoutParams<ViewGroup.MarginLayoutParams> { topMargin += statusBarHeight }
        binding.mediaCover.updateLayoutParams<ViewGroup.MarginLayoutParams> { topMargin += statusBarHeight }
        binding.mediaTitle.isSelected = true
        binding.mediaTitleCollapse.isSelected = true
        binding.mediaUserStatus.isSelected = true
        binding.mediaAddToList.isSelected = true
        binding.mediaTotal.isSelected = true
        mMaxScrollSize = binding.mediaAppBar.totalScrollRange
        binding.mediaAppBar.addOnOffsetChangedListener(this)

        binding.mediaClose.setOnClickListener{
            onBackPressed()
        }
        val viewPager = binding.mediaViewPager
        viewPager.isUserInputEnabled = false

        val media: Media = intent.getSerializableExtra("media") as Media
        media.selected = loadData<Selected>(this,media.id.toString())?: Selected()
        Picasso.get().load(media.cover).into(binding.mediaCoverImage)
        Picasso.get().load(media.banner).into(binding.mediaBanner)
        Picasso.get().load(media.banner).into(binding.mediaBannerStatus)
        binding.mediaTitle.text=media.userPreferredName
        binding.mediaTitleCollapse.text=media.userPreferredName

        //Fav Button
        if (media.isFav) binding.mediaFav.setImageDrawable(AppCompatResources.getDrawable(this,R.drawable.ic_round_favorite_24))
        val favButton = PopImageButton(scope,this,binding.mediaFav,media,R.drawable.ic_round_favorite_24,R.drawable.ic_round_favorite_border_24,R.color.nav_tab,R.color.fav,true)
        binding.mediaFav.setOnClickListener {
            favButton.clicked()
        }

        //Notify Button
        if (media.notify) binding.mediaNotify.setImageDrawable(AppCompatResources.getDrawable(this,R.drawable.ic_round_notifications_active_24))
        val notifyButton = PopImageButton(scope,this,binding.mediaNotify,media, R.drawable.ic_round_notifications_active_24, R.drawable.ic_round_notifications_none_24,R.color.nav_tab, R.color.violet_400,false)
        binding.mediaNotify.setOnClickListener { notifyButton.clicked() }

        model.userStatus.value = media.userStatus
        model.userScore.value = media.userScore.toDouble()
        model.userProgress.value = media.userProgress
        model.userStatus.observe(this, {
            if (it != null) {
                binding.mediaAddToList.setText(R.string.list_editor)
                binding.mediaUserStatus.text = it
                binding.mediaUserProgress.text = (model.userProgress.value ?: "~").toString()
            } else {
                binding.mediaUserStatus.visibility = View.GONE
                binding.mediaUserProgress.visibility = View.GONE
                binding.mediaTotal.visibility = View.GONE
                binding.mediaAddToList.setText(R.string.add)
            }
            media.userStatus = it
        })
        model.userProgress.observe(this,{media.userProgress = it})
        model.userScore.observe(this,{media.userScore = (it?:0).toInt()})

        binding.mediaAddToList.setOnClickListener{
            MediaListDialogFragment().show(supportFragmentManager, "dialog")
        }
        if (media.anime!=null){
            binding.mediaTotal.text = if (media.anime.nextAiringEpisode!=null) " | "+(media.anime.nextAiringEpisode.toString()+" | "+(media.anime.totalEpisodes?:"~").toString()) else " | "+(media.anime.totalEpisodes?:"~").toString()
            tabLayout = binding.mediaAnimeTab
            viewPager.adapter = ViewPagerAdapter(supportFragmentManager, lifecycle,true)
        }
        else if (media.manga!=null){
            binding.mediaTotal.text = " | "+(media.manga.totalChapters?:"~").toString()
            tabLayout = binding.mediaMangaTab
            viewPager.adapter = ViewPagerAdapter(supportFragmentManager, lifecycle,false)
        }
        selected = media.selected!!.window
        viewPager.setPageTransformer(ZoomOutPageTransformer())
        binding.mediaTitle.translationX = -screenWidth
        tabLayout.visibility = View.VISIBLE
        tabLayout.setupWithViewPager2(viewPager)
        tabLayout.selectTabAt(selected,false)
        viewPager.post { viewPager.setCurrentItem(selected, false) }
        tabLayout.setOnTabSelectListener(object : AnimatedBottomBar.OnTabSelectListener {
            override fun onTabSelected(lastIndex: Int, lastTab: AnimatedBottomBar.Tab?, newIndex: Int, newTab: AnimatedBottomBar.Tab) {
                selected = newIndex
                media.selected!!.window = newIndex
                saveData(this@MediaDetailsActivity,media.id.toString(),media.selected!!)
            }
        })
        scope.launch {
            model.loadMedia(media)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onResume() {
        tabLayout.selectTabAt(selected,false)
        binding.mediaBannerStatus.visibility=if (!isCollapsed) View.VISIBLE else View.GONE
        super.onResume()
    }
    //ViewPager
    private class ViewPagerAdapter(fragmentManager: FragmentManager, lifecycle: Lifecycle,private val anime:Boolean=true) :
        FragmentStateAdapter(fragmentManager, lifecycle) {

        override fun getItemCount(): Int = 2

        override fun createFragment(position: Int): Fragment {
            if (anime){
                when (position) {
                    0 -> return MediaInfoFragment()
                    1 -> return AnimeSourceFragment()
                }
            }
            else{
                when (position) {
                    0 -> return MediaInfoFragment()
                    1 -> return MangaSourceFragment()
                }
            }
            return MediaInfoFragment()
        }
    }
    //Collapsing UI Stuff
    private var isCollapsed = false
    private val percent = 30
    private var mMaxScrollSize = 0
    private var screenWidth:Float = 0f

    override fun onOffsetChanged(appBar: AppBarLayout, i: Int) {
        if (mMaxScrollSize == 0) mMaxScrollSize = appBar.totalScrollRange
        val percentage = abs(i) * 100 / mMaxScrollSize
        val cap = MathUtils.clamp((percent - percentage) / percent.toFloat(), 0f, 1f)

        binding.mediaCover.scaleX = 1f*cap
        binding.mediaCover.scaleY = 1f*cap
        binding.mediaCover.cardElevation = 32f*cap

        if (percentage >= percent && !isCollapsed) {
            isCollapsed = true
            ObjectAnimator.ofFloat(binding.mediaTitle,"translationX",0f).setDuration(200).start()
            ObjectAnimator.ofFloat(binding.mediaAccessContainer,"translationX",screenWidth).setDuration(200).start()
            ObjectAnimator.ofFloat(binding.mediaTitleCollapse,"translationX",screenWidth).setDuration(200).start()
            binding.mediaBannerStatus.visibility=View.GONE
            this.window.statusBarColor = ContextCompat.getColor(this, R.color.nav_bg)
        }
        if (percentage <= percent && isCollapsed) {
            isCollapsed = false
            ObjectAnimator.ofFloat(binding.mediaTitle,"translationX",-screenWidth).setDuration(200).start()
            ObjectAnimator.ofFloat(binding.mediaAccessContainer,"translationX",0f).setDuration(200).start()
            ObjectAnimator.ofFloat(binding.mediaTitleCollapse,"translationX",0f).setDuration(200).start()
            binding.mediaBannerStatus.visibility=View.VISIBLE
            this.window.statusBarColor = ContextCompat.getColor(this, R.color.status)
        }
    }
    inner class PopImageButton(private val scope: CoroutineScope,private val activity: Activity,private val image:ImageView,private val media:Media,private val d1:Int,private val d2:Int,private val c1:Int,private val c2:Int,private val fav_or_not:Boolean? = null){
        private var pressable = true
        private var clicked = false
        fun clicked(){
            if (pressable){
                pressable = false
                if (fav_or_not!=null) {
                    if (fav_or_not) {
                        media.isFav = !media.isFav
                        clicked = media.isFav
                        scope.launch { anilist.mutation.toggleFav(media.anime!=null,media.id) }
                        homeRefresh.postValue(true)
                    }
                    else {
                        media.notify = !media.notify
                        clicked = media.notify
                    }
                }
                else clicked = !clicked
                ObjectAnimator.ofFloat(image,"scaleX",1f,0f).setDuration(69).start()
                ObjectAnimator.ofFloat(image,"scaleY",1f,0f).setDuration(100).start()
                scope.launch {
                    delay(100)
                    activity.runOnUiThread {
                        if (clicked) {
                            ObjectAnimator.ofArgb(image,"ColorFilter",ContextCompat.getColor(activity, c1),ContextCompat.getColor(activity, c2)).setDuration(120).start()
                            image.setImageDrawable(AppCompatResources.getDrawable(activity,d1))
                        }
                        else{
                            image.setImageDrawable(AppCompatResources.getDrawable(activity,d2))
                        }
                        ObjectAnimator.ofFloat(image,"scaleX",0f,1.5f).setDuration(120).start()
                        ObjectAnimator.ofFloat(image,"scaleY",0f,1.5f).setDuration(100).start()
                    }
                    delay(120)
                    activity.runOnUiThread {
                        ObjectAnimator.ofFloat(image,"scaleX",1.5f,1f).setDuration(100).start()
                        ObjectAnimator.ofFloat(image,"scaleY",1.5f,1f).setDuration(100).start()
                    }
                    delay(200)
                    activity.runOnUiThread{
                        if (clicked) {
                            ObjectAnimator.ofArgb(image,"ColorFilter", ContextCompat.getColor(activity, c2), ContextCompat.getColor(activity, c1)).setDuration(200).start()
                        }
                    }
                    pressable = true
                }
            }
        }
    }
}

