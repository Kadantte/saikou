package ani.saikou

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.LiveData
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

import ani.saikou.anilist.Anilist
import ani.saikou.anilist.AnilistHomeViewModel
import ani.saikou.databinding.FragmentHomeBinding
import ani.saikou.media.Media
import ani.saikou.media.MediaAdaptor
import ani.saikou.user.ListActivity

import kotlinx.coroutines.*

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        scope.cancel()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val model: AnilistHomeViewModel by viewModels()
        fun load(){
            requireActivity().runOnUiThread {
                binding.homeUserName.text = Anilist.username
                binding.homeUserEpisodesWatched.text = Anilist.episodesWatched.toString()
                binding.homeUserChaptersRead.text = Anilist.chapterRead.toString()
                loadImage(Anilist.avatar,binding.homeUserAvatar)
                binding.homeUserAvatar.scaleType = ImageView.ScaleType.FIT_CENTER
                binding.homeUserDataProgressBar.visibility = View.GONE
                binding.homeUserDataContainer.visibility = View.VISIBLE
            }
        }


        binding.homeContainer.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            topMargin = statusBarHeight
            bottomMargin = navBarHeight
        }

        var reached = false
        binding.homeScroll.setOnScrollChangeListener { _, _, _, _, _ ->
            if (!binding.homeScroll.canScrollVertically(1)) {
                reached = true
                bottomBar.animate().translationZ(0f).setDuration(200).start()
                ObjectAnimator.ofFloat(bottomBar, "elevation", 4f, 0f).setDuration(200).start()
            }
            else{
                if (reached){
                    bottomBar.animate().translationZ(12f).setDuration(200).start()
                    ObjectAnimator.ofFloat(bottomBar, "elevation", 0f, 4f).setDuration(200).start()
                }
            }
        }
        binding.homeRefresh.setSlingshotDistance(statusBarHeight+128)
        binding.homeRefresh.setProgressViewEndTarget(false, statusBarHeight+128)
        binding.homeRefresh.setOnRefreshListener {
            model.homeRefresh.postValue(true)
        }

        //UserData
        binding.homeUserDataProgressBar.visibility = View.VISIBLE
        binding.homeUserDataContainer.visibility = View.GONE
        if(model.load.value!!){
            load()
        }
        //List Images
        model.getListImages().observe(viewLifecycleOwner, {
            if (it.isNotEmpty()) {
                loadImage(it[0] ?: "https://bit.ly/31bsIHq",binding.homeAnimeListImage)
                loadImage(it[1] ?: "https://bit.ly/2ZGfcuG",binding.homeMangaListImage)
            }
            binding.homeAnimeList.setOnClickListener {
                ContextCompat.startActivity(
                    requireActivity(), Intent(requireActivity(), ListActivity::class.java)
                        .putExtra("anime",true)
                        .putExtra("userId",Anilist.userid)
                        .putExtra("username",Anilist.username)
                    ,null
                )
            }
            binding.homeMangaList.setOnClickListener {
                ContextCompat.startActivity(
                    requireActivity(), Intent(requireActivity(), ListActivity::class.java)
                        .putExtra("anime",false)
                        .putExtra("userId",Anilist.userid)
                        .putExtra("username",Anilist.username)
                    ,null
                )
            }
        })

        //Function For Recycler Views
        fun initRecyclerView(mode: Int, recyclerView: RecyclerView, progress: View, empty: View,emptyButton:Button?=null) {
            lateinit var modelFunc: LiveData<ArrayList<Media>>
            when (mode) {
                0 -> modelFunc = model.getAnimeContinue();1 -> modelFunc =
                model.getMangaContinue();2 -> modelFunc = model.getRecommendation()
            }
            progress.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
            empty.visibility = View.GONE

            modelFunc.observe(viewLifecycleOwner, {
                recyclerView.visibility = View.GONE
                empty.visibility = View.GONE
                if (it != null) {
                    if (it.isNotEmpty()) {
                        recyclerView.adapter = MediaAdaptor(it,requireActivity())
                        recyclerView.layoutManager = LinearLayoutManager(
                            requireContext(),
                            LinearLayoutManager.HORIZONTAL,
                            false
                        )
                        recyclerView.visibility = View.VISIBLE
                    } else {
                        empty.visibility = View.VISIBLE
                        emptyButton?.setOnClickListener{
                            when(mode){
                                0-> bottomBar.selectTabAt(0)
                                1-> bottomBar.selectTabAt(2)
                            }
                        }
                    }
                    progress.visibility = View.GONE
                }
            })
        }

        // Recycler Views
        initRecyclerView(
            0,
            binding.homeWatchingRecyclerView,
            binding.homeWatchingProgressBar,
            binding.homeWatchingEmpty,
            binding.homeWatchingBrowseButton
        )
        initRecyclerView(
            1,
            binding.homeReadingRecyclerView,
            binding.homeReadingProgressBar,
            binding.homeReadingEmpty,
            binding.homeReadingBrowseButton
        )
        initRecyclerView(
            2,
            binding.homeRecommendedRecyclerView,
            binding.homeRecommendedProgressBar,
            binding.homeRecommendedEmpty
        )

        model.homeRefresh.observe(viewLifecycleOwner, {
            if (it) {
                scope.launch {
                    //Get userData First
                    if (Anilist.userid == null)
                        if (Anilist.query.getUserData()) load() else logger("Error loading data")
                    else load()
                    model.load.postValue(true)
                    //get Watching in new Thread
                    val a = async { model.setAnimeContinue() }
                    //get Reading in new Thread
                    val b = async { model.setMangaContinue() }
                    // get genres and respective images
                    val c = async { model.setListImages() }
                    //get List Images in current Thread(idle)

                    //get Recommended in current Thread(idle)
                    model.setRecommendation()

                    awaitAll(a, b, c)
                    requireActivity().runOnUiThread {
                        model.homeRefresh.postValue(false)
                        binding.homeRefresh.isRefreshing = false
                    }
                }
            }
        })
    }
}