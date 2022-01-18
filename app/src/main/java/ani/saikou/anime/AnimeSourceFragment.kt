package ani.saikou.anime

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.GridLayoutManager
import ani.saikou.R
import ani.saikou.anime.source.AnimeSources
import ani.saikou.databinding.FragmentAnimeSourceBinding
import ani.saikou.media.Media
import ani.saikou.media.MediaDetailsViewModel
import ani.saikou.media.SourceSearchDialogFragment
import ani.saikou.navBarHeight
import com.google.android.material.chip.Chip
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.ceil

@SuppressLint("SetTextI18n")
class AnimeSourceFragment : Fragment() {
    private var _binding: FragmentAnimeSourceBinding? = null
    private val binding get() = _binding!!
    private val scope = CoroutineScope(Dispatchers.Default)
    private var screenWidth:Float =0f

    private var selected:ImageView?=null
    private var selectedChip:Chip?= null
    private var start = 0
    private var end:Int?=null
    private var loading = true
    private var progress = View.VISIBLE
    private lateinit var model : MediaDetailsViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAnimeSourceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() { super.onDestroyView();_binding = null }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        screenWidth = resources.displayMetrics.run { widthPixels / density }
        binding.animeSourceContainer.updateLayoutParams<ViewGroup.MarginLayoutParams> { bottomMargin += navBarHeight }
        binding.animeSourceTitle.isSelected = true
        super.onViewCreated(view, savedInstanceState)
        val a : MediaDetailsViewModel by activityViewModels()
        model = a
        model.getMedia().observe(viewLifecycleOwner,{
            val media = it
            if (media?.anime != null) {
                binding.animeSourceContainer.visibility = View.VISIBLE
                binding.mediaLoadProgressBar.visibility = View.GONE
                progress = View.GONE
                if (media.anime.youtube!=null) {
                    binding.animeSourceYT.visibility = View.VISIBLE
                    binding.animeSourceYT.setOnClickListener {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(media.anime.youtube))
                        requireContext().startActivity(intent)
                    }
                }
                val sources : Array<String> = resources.getStringArray(R.array.anime_sources)
                binding.animeSource.setText(sources[media.selected!!.source])
                binding.animeSource.setAdapter(ArrayAdapter(requireContext(), R.layout.item_dropdown,sources))
                binding.animeSource.setOnItemClickListener { _, _, i, _ ->
                    binding.animeEpisodesRecycler.adapter = null
                    binding.animeSourceChipGroup.removeAllViews()
                    loading=true
                    binding.animeSourceProgressBar.visibility=View.VISIBLE
                    media.selected!!.source = i
                    model.saveSelected(media.id,media.selected!!,requireActivity())
                    AnimeSources[i]!!.live.observe(viewLifecycleOwner,{ j->
                        binding.animeSourceTitle.text = j
                    })
                    scope.launch{
                        model.loadEpisodes(media,i)
                    }
                }
                selected = when(media.selected!!.recyclerStyle){
                    0->binding.animeSourceList
                    1->binding.animeSourceGrid
                    2->binding.animeSourceCompact
                    else -> binding.animeSourceList
                }

                binding.animeSourceSearch.setOnClickListener {
                    SourceSearchDialogFragment().show(requireActivity().supportFragmentManager,null)
                }

                selected?.alpha = 1f
                binding.animeSourceTop.rotation = if (!media.selected!!.recyclerReversed) 90f else -90f
                binding.animeSourceTop.setOnClickListener {
                    binding.animeSourceTop.rotation = if (media.selected!!.recyclerReversed) 90f else -90f
                    media.selected!!.recyclerReversed=!media.selected!!.recyclerReversed
                    updateRecycler(media)
                }
                binding.animeSourceList.setOnClickListener {
                    media.selected!!.recyclerStyle=0
                    selected?.alpha = 0.33f
                    selected = binding.animeSourceList
                    selected?.alpha = 1f
                    updateRecycler(media)
                }
                binding.animeSourceGrid.setOnClickListener {
                    media.selected!!.recyclerStyle=1
                    selected?.alpha = 0.33f
                    selected = binding.animeSourceGrid
                    selected?.alpha = 1f
                    updateRecycler(media)
                }
                binding.animeSourceCompact.setOnClickListener {
                    media.selected!!.recyclerStyle=2
                    selected?.alpha = 0.33f
                    selected = binding.animeSourceCompact
                    selected?.alpha = 1f
                    updateRecycler(media)
                }

                model.getEpisodes().observe(viewLifecycleOwner,{loadedEpisodes->
                    if(loadedEpisodes!=null) {
                        binding.animeEpisodesRecycler.adapter = null
                        binding.animeSourceChipGroup.removeAllViews()
                        loading=true
                        binding.animeSourceProgressBar.visibility=View.VISIBLE
                        val episodes = loadedEpisodes[media.selected!!.source]
                        if (episodes != null) {
                            episodes.forEach { (i, episode) ->
                                if (media.anime.kitsuEpisodes != null) {
                                    if (media.anime.kitsuEpisodes!!.containsKey(i)) {
                                        episode.desc = media.anime.kitsuEpisodes!![i]?.desc
                                        episode.title = media.anime.kitsuEpisodes!![i]?.title
                                        episode.thumb =
                                            media.anime.kitsuEpisodes!![i]?.thumb ?: media.cover
                                    }
                                }
                            }
                            media.anime.episodes = episodes
                            //CHIP GROUP
                            addPageChips(media, episodes.size)
                            updateRecycler(media)
                        }
                    }
                })
                model.getKitsuEpisodes().observe(viewLifecycleOwner,{ i->
                    if (i!=null) {
                        media.anime.kitsuEpisodes = i
                    }
                })
                AnimeSources[media.selected!!.source]!!.live.observe(viewLifecycleOwner,{ j->
                    binding.animeSourceTitle.text = j
                })
                scope.launch{
                    model.loadKitsuEpisodes(media)
                    model.loadEpisodes(media,media.selected!!.source)
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        binding.mediaLoadProgressBar.visibility = progress
    }

    override fun onDestroy() {
        AnimeSources.flushLive()
        super.onDestroy()
    }

    private fun updateRecycler(media: Media){
        model.saveSelected(media.id,media.selected!!,requireActivity())
        if(media.anime?.episodes!=null) {
            binding.animeEpisodesRecycler.adapter = episodeAdapter(media, this, media.selected!!.recyclerStyle, media.selected!!.recyclerReversed, start, end)
            val gridCount = when (media.selected!!.recyclerStyle){
                0->1
                1->(screenWidth/200f).toInt()
                2->(screenWidth/80f).toInt()
                else->1
            }
            binding.animeEpisodesRecycler.layoutManager = GridLayoutManager(requireContext(), gridCount)
            loading = false
            binding.animeSourceProgressBar.visibility = View.GONE
            if(media.anime.episodes!!.isNotEmpty())
                binding.animeSourceNotFound.visibility = View.GONE
            else
                binding.animeSourceNotFound.visibility = View.VISIBLE
        }
    }
    fun onEpisodeClick(media: Media, i:String){
        model.onEpisodeClick(media,i,requireActivity().supportFragmentManager)
    }

    private fun addPageChips(media: Media, total: Int){
        val divisions = total.toDouble() / 10
        start = 0
        end = null
        val limit = when{
            (divisions < 25) -> 25
            (divisions < 50) -> 50
            else -> 100
        }
        if (total>limit) {
            val arr = media.anime!!.episodes!!.keys.toTypedArray()
            val stored = ceil((total).toDouble() / limit).toInt()
//            println(stored)
            (1..stored).forEach {
                val chip = Chip(requireContext())
                chip.isCheckable = true
                val last = if (it == stored) total else (limit * it)

                if(it==media.selected!!.chip && selectedChip==null){
                    selectedChip=chip
                    chip.isChecked = true
                    start = limit * (it - 1)
                    end = last - 1
                }
                if (end == null) { end = limit * it - 1 }
                chip.text = "${arr[limit * (it - 1)]} - ${arr[last-1]}"
                chip.setOnClickListener { _ ->
                    media.selected!!.chip = it
                    selectedChip?.isChecked = false
                    selectedChip = chip
                    selectedChip!!.isChecked = true
                    start = limit * (it - 1)
                    end = last - 1
                    updateRecycler(media)
                }
                binding.animeSourceChipGroup.addView(chip)
            }
        }
    }
}