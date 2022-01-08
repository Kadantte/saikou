package ani.saikou.anime

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ani.saikou.databinding.BottomSheetSelectorBinding
import ani.saikou.databinding.ItemStreamBinding
import ani.saikou.databinding.ItemUrlBinding
import ani.saikou.media.Media
import ani.saikou.media.MediaDetailsViewModel
import ani.saikou.navBarHeight
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.DecimalFormat

class SelectorDialogFragment : BottomSheetDialogFragment(){
    private var _binding: BottomSheetSelectorBinding? = null
    private val binding get() = _binding!!
    private lateinit var media: Media
    private lateinit var episode: Episode

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            media = it.getSerializable("media") as Media
            episode = it.getSerializable("ep") as Episode
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetSelectorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.selectorContainer.updateLayoutParams<ViewGroup.MarginLayoutParams> { bottomMargin += navBarHeight }
        binding.selectorRecyclerView.adapter = null
        binding.selectorProgressBar.visibility = View.VISIBLE

        fun load(){
            binding.selectorProgressBar.visibility = View.GONE
            media.anime!!.episodes!![media.anime!!.selectedEpisode!!] = episode
            binding.selectorRecyclerView.layoutManager = LinearLayoutManager(requireActivity(),LinearLayoutManager.VERTICAL,false)
            binding.selectorRecyclerView.adapter = StreamAdapter()
        }
        if(episode.streamLinks.isEmpty()) {
            val model : MediaDetailsViewModel by activityViewModels()
            model.getStreams().observe(this,{
                if (it!=null){
                    episode = it
                    load()
                }
            })
            CoroutineScope(Dispatchers.Default).launch {
                model.loadStreams(episode, media.selected!!.source)
            }
        }
        else load()
        super.onViewCreated(view, savedInstanceState)
    }

    fun startExoplayer(media: Media){
        val intent = Intent(activity, ExoplayerView::class.java).apply {
            putExtra("ep", media.anime!!.episodes!![media.anime.selectedEpisode!!])
        }
        startActivity(intent)
    }

    private inner class StreamAdapter : RecyclerView.Adapter<StreamAdapter.StreamViewHolder>() {
        val links = media.anime!!.episodes!![media.anime!!.selectedEpisode!!]!!.streamLinks
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StreamViewHolder = StreamViewHolder(ItemStreamBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        override fun onBindViewHolder(holder: StreamViewHolder, position: Int) {
            holder.binding.streamName.text=links[position]!!.server
            holder.binding.streamRecyclerView.layoutManager = LinearLayoutManager(requireContext())
            holder.binding.streamRecyclerView.adapter = QualityAdapter(position)
        }
        override fun getItemCount(): Int = links.size
        private inner class StreamViewHolder(val binding: ItemStreamBinding) : RecyclerView.ViewHolder(binding.root)
    }

    private inner class QualityAdapter(private val stream:Int) : RecyclerView.Adapter<QualityAdapter.UrlViewHolder>() {
        val urls = media.anime!!.episodes!![media.anime!!.selectedEpisode!!]!!.streamLinks[stream]!!.quality

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UrlViewHolder {
            return UrlViewHolder(ItemUrlBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }

        @SuppressLint("SetTextI18n")
        override fun onBindViewHolder(holder: UrlViewHolder, position: Int) {
            val binding = holder.binding
            val url = urls[position]
            binding.urlQuality.text = url.quality
            binding.urlSize.visibility = if(url.size!=null) View.VISIBLE else View.GONE

            binding.urlSize.text = DecimalFormat("#.##").format(url.size?:0).toString()+" MB"
        }

        override fun getItemCount(): Int = urls.size

        private inner class UrlViewHolder(val binding: ItemUrlBinding) : RecyclerView.ViewHolder(binding.root) {
            init {
                itemView.setOnClickListener {
                    media.anime!!.episodes!![media.anime!!.selectedEpisode!!]!!.selectedStream = stream
                    media.anime!!.episodes!![media.anime!!.selectedEpisode!!]!!.selectedQuality = bindingAdapterPosition
                    dismiss()
                    startExoplayer(media)
                }
            }
        }
    }
    companion object {
        fun newInstance(media: Media, episode: Episode): SelectorDialogFragment =
            SelectorDialogFragment().apply {
                arguments = Bundle().apply {
                    putSerializable("media", media)
                    putSerializable("ep", episode)
                }
            }
    }
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}