package ani.saikou

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import androidx.core.util.Pair
import androidx.core.view.ViewCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePaddingRelative
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.CompositePageTransformer
import androidx.viewpager2.widget.MarginPageTransformer
import androidx.viewpager2.widget.ViewPager2
import ani.saikou.anilist.Anilist
import ani.saikou.anilist.AnilistMangaViewModel
import ani.saikou.anilist.AnilistSearch
import ani.saikou.databinding.FragmentMangaBinding
import ani.saikou.media.MediaAdaptor
import ani.saikou.media.MediaLargeAdaptor
import kotlinx.coroutines.*
import java.lang.Runnable
import kotlin.math.abs

class MangaFragment : Fragment() {
    private var _binding: FragmentMangaBinding? = null
    private val binding get() = _binding!!
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var trendHandler : Handler?=null
    private lateinit var trendRun : Runnable

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMangaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() { super.onDestroyView();_binding = null }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val model: AnilistMangaViewModel by viewModels()
        binding.mangaContainer.updateLayoutParams<ViewGroup.MarginLayoutParams> { topMargin = statusBarHeight }

        binding.mangaScroll.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { v, _, _, _, _ ->
            if(!v.canScrollVertically(1)) {
                binding.mangaPopularRecyclerView.requestDisallowInterceptTouchEvent(false)
                activity?.window?.statusBarColor = ContextCompat.getColor(requireContext(), R.color.bg)
                ObjectAnimator.ofFloat(bottomBar,"scaleX",0f).setDuration(200).start()
                ObjectAnimator.ofFloat(bottomBar,"scaleY",0f).setDuration(200).start()
            }
            if(!v.canScrollVertically(-1)){
                binding.mangaPopularRecyclerView.requestDisallowInterceptTouchEvent(true)
                activity?.window?.statusBarColor = ContextCompat.getColor(requireContext(), R.color.status)
                ObjectAnimator.ofFloat(bottomBar,"scaleX",1f).setDuration(200).start()
                ObjectAnimator.ofFloat(bottomBar,"scaleY",1f).setDuration(200).start()
            }
        })

        binding.mangaPopularRecyclerView.updateLayoutParams{ height=resources.displayMetrics.heightPixels+navBarHeight-80f.px }
        binding.mangaPopularProgress.updateLayoutParams<ViewGroup.MarginLayoutParams> { bottomMargin += navBarHeight }
        binding.mangaPopularRecyclerView.updatePaddingRelative(bottom = navBarHeight+80f.px)
        binding.mangaRefresh.setSlingshotDistance(statusBarHeight+128)
        binding.mangaRefresh.setProgressViewEndTarget(false, statusBarHeight+128)
        binding.mangaRefresh.setOnRefreshListener {
            model.mangaRefresh.postValue(true)
        }
        if(Anilist.avatar!=null){
            loadImage(Anilist.avatar,binding.mangaUserAvatar)
            binding.mangaUserAvatar.scaleType = ImageView.ScaleType.FIT_CENTER
        }

        binding.mangaSearchBar.hint = "MANGA"
        binding.mangaSearchBarText.setOnClickListener{
            ContextCompat.startActivity(
                requireActivity(),
                Intent(requireActivity(), SearchActivity::class.java).putExtra("type","MANGA"),
                ActivityOptionsCompat.makeSceneTransitionAnimation(requireActivity(),
                    Pair.create(binding.mangaSearchBar, ViewCompat.getTransitionName(binding.mangaSearchBar)!!),
                ).toBundle()
            )
        }

        loadImage("https://bit.ly/31bsIHq",binding.mangaGenreImage)
        loadImage( "https://bit.ly/2ZGfcuG",binding.mangaTopScoreImage)

        binding.mangaGenre.setOnClickListener {
            ContextCompat.startActivity(
                requireActivity(), Intent(requireActivity(), GenreActivity::class.java).putExtra("type","MANGA"),null)
        }

        binding.mangaTopScore.setOnClickListener {
            ContextCompat.startActivity(
                requireActivity(), Intent(requireActivity(), SearchActivity::class.java).putExtra("type","MANGA").putExtra("sortBy","Score"),null)
        }

        model.getTrending().observe(viewLifecycleOwner,{
            if(it!=null){
                binding.mangaTrendingProgressBar.visibility = View.GONE
                binding.mangaTrendingViewPager.adapter = MediaLargeAdaptor(it,requireActivity(),binding.mangaTrendingViewPager)
                binding.mangaTrendingViewPager.offscreenPageLimit = 3
                binding.mangaTrendingViewPager.getChildAt(0).overScrollMode = RecyclerView.OVER_SCROLL_NEVER

                val a = CompositePageTransformer()
                a.addTransformer(MarginPageTransformer(8f.px))
                a.addTransformer { page, position ->
                    page.scaleY = 0.85f + (1 - abs(position))*0.15f
                }
                binding.mangaTrendingViewPager.setPageTransformer(a)
                trendHandler = Handler(Looper.getMainLooper())
                trendRun = Runnable {
                    if (_binding!=null) binding.mangaTrendingViewPager.currentItem = binding.mangaTrendingViewPager.currentItem+1
                }
                binding.mangaTrendingViewPager.registerOnPageChangeCallback(
                    object : ViewPager2.OnPageChangeCallback(){
                        override fun onPageSelected(position: Int) {
                            super.onPageSelected(position)
                            trendHandler!!.removeCallbacks(trendRun)
                            trendHandler!!.postDelayed(trendRun,4000)
                        }
                    }
                )
            }
        })

        model.getTrendingNovel().observe(viewLifecycleOwner,{
            if(it!=null){
                binding.mangaNovelProgressBar.visibility = View.GONE
                binding.mangaNovelRecyclerView.adapter = MediaAdaptor(it,requireActivity())
                binding.mangaNovelRecyclerView.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
                binding.mangaNovelRecyclerView.visibility = View.VISIBLE
            }
        })

        val popularModel: AnilistSearch by viewModels()
        popularModel.getSearch().observe(viewLifecycleOwner,{
            if(it!=null){
                val adapter = MediaLargeAdaptor(it.results,requireActivity())
                var loading = false
                binding.mangaPopularRecyclerView.adapter = adapter
                binding.mangaPopularRecyclerView.layoutManager = LinearLayoutManager(requireContext())
                binding.mangaPopularProgress.visibility = View.GONE
                if(it.hasNextPage) {
                    binding.mangaPopularRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                        override fun onScrolled(v: RecyclerView, dx: Int, dy: Int) {
                            if (!v.canScrollVertically(1)) {
                                if (it.hasNextPage)
                                    if (!loading) {
                                        binding.mangaPopularProgress.visibility = View.VISIBLE
                                        scope.launch {
                                            loading = true
                                            val get = popularModel.loadNextPage(it)
                                            if(get!=null) {
                                                val a = it.results.size
                                                it.results.addAll(get.results)
                                                requireActivity().runOnUiThread {
                                                    adapter.notifyItemRangeInserted(
                                                        a,
                                                        get.results.size
                                                    )
                                                    binding.mangaPopularProgress.visibility =
                                                        View.GONE
                                                }
                                                it.page = get.page
                                                it.hasNextPage = get.hasNextPage
                                                loading = false
                                            }
                                        }
                                    }
                                    else binding.mangaPopularProgress.visibility = View.GONE
                            }
                            if (!v.canScrollVertically(-1)){
                                _binding?.mangaPopularRecyclerView?.post {
                                    val a = _binding
                                    a?.mangaPopularRecyclerView?.requestDisallowInterceptTouchEvent(true)
                                }
                                activity?.window?.statusBarColor = ContextCompat.getColor(requireContext(), R.color.status)
                                ObjectAnimator.ofFloat(bottomBar,"scaleX",1f).setDuration(200).start()
                                ObjectAnimator.ofFloat(bottomBar,"scaleY",1f).setDuration(200).start()
                            }
                            super.onScrolled(v, dx, dy)
                        }
                    })
                }
            }
        })

        model.mangaRefresh.observe(viewLifecycleOwner,{
            if(it) {
                scope.launch {
                    model.loadTrending()
                    model.loadTrendingNovel()
                    popularModel.loadSearch("MANGA",sort="POPULARITY_DESC")
                    activity?.runOnUiThread {
                        model.mangaRefresh.postValue(false)
                        _binding?.mangaRefresh?.isRefreshing = false
                    }
                }
            }
        })
    }

    override fun onPause() {
        super.onPause()
        trendHandler?.removeCallbacks(trendRun)
    }

    override fun onResume() {
        super.onResume()
        trendHandler?.postDelayed(trendRun,4000)
    }
}