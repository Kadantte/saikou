package ani.saikou.anime

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ani.saikou.R
import ani.saikou.databinding.BottomSheetSelectorBinding
import ani.saikou.databinding.ItemStreamBinding
import ani.saikou.databinding.ItemUrlBinding
import ani.saikou.media.Media
import ani.saikou.media.MediaDetailsViewModel
import ani.saikou.navBarHeight
import ani.saikou.toastString
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.text.DecimalFormat

class SelectorDialogFragment : BottomSheetDialogFragment(){
    private var _binding: BottomSheetSelectorBinding? = null
    private val binding get() = _binding!!
    private lateinit var model : MediaDetailsViewModel
    private var media: Media? = null
    private lateinit var episode: Episode
    private var makeDefault = false
    private val scope = CoroutineScope(Dispatchers.Default)
    private var selected:String?=null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            selected = it.getString("server")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetSelectorBinding.inflate(inflater, container, false)
        return binding.root
    }

    private fun onCheckboxClicked(view: View) {
        if (view is CheckBox) {
            val checked: Boolean = view.isChecked

            when (view.id) {
                R.id.selectorMakeDefault-> {
                    makeDefault = checked
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val mo : MediaDetailsViewModel by activityViewModels()
        model = mo

        model.getMedia().observe(viewLifecycleOwner,{ m->
            media = m
            if (media!=null){
                episode = media!!.anime!!.episodes!![media!!.anime!!.selectedEpisode!!]!!
                val server = media!!.selected!!.stream
                if(server!=null) {
                    binding.selectorListContainer.visibility = View.GONE
                    binding.selectorAutoListContainer.visibility = View.VISIBLE
                    binding.selectorAutoText.text = server
                    binding.selectorCancel.setOnClickListener {
                        media!!.selected!!.stream = null
                        model.saveSelected(media!!.id,media!!.selected!!,requireActivity())
                        dismiss()
                    }
                    fun fail(){
                        toastString("Couldn't auto select the server, Please try again!")
                        binding.selectorCancel.performClick()
                    }
                    fun load(){
                        if(episode.streamLinks.containsKey(server)){
                            if(episode.streamLinks[server]!!.quality.size >= media!!.selected!!.quality){
                                media!!.anime!!.episodes!![media!!.anime!!.selectedEpisode!!]!!.selectedStream = server
                                media!!.anime!!.episodes!![media!!.anime!!.selectedEpisode!!]!!.selectedQuality = media!!.selected!!.quality
                                dismiss()
                                startExoplayer(media!!)
                            }
                            else fail()
                        }
                        else fail()
                    }
                    if(episode.streamLinks.isEmpty()) {
                        model.getStreams().observe(this,{
                            if (it!=null){
                                episode = it
                                load()
                            }
                        })
                        scope.launch {
                            if (!model.loadStream(episode, media!!.selected!!)) fail()
                        }
                    }
                    else load()
                }
                else{
                    binding.selectorRecyclerView.updateLayoutParams<ViewGroup.MarginLayoutParams> { bottomMargin += navBarHeight }
                    binding.selectorRecyclerView.adapter = null
                    binding.selectorProgressBar.visibility = View.VISIBLE

                    binding.selectorMakeDefault.setOnClickListener {
                        onCheckboxClicked(it)
                    }
                    fun load(){
                        binding.selectorProgressBar.visibility = View.GONE
                        media!!.anime!!.episodes!![media!!.anime!!.selectedEpisode!!] = episode
                        binding.selectorRecyclerView.layoutManager = LinearLayoutManager(requireActivity(),LinearLayoutManager.VERTICAL,false)
                        binding.selectorRecyclerView.adapter = StreamAdapter()
                    }
                    if(episode.streamLinks.isEmpty()) {
                        model.getStreams().observe(this,{
                            if (it!=null){
                                episode = it
                                load()
                            }
                        })
                        scope.launch {
                            model.loadStreams(episode, media!!.selected!!.source)
                        }
                    }
                    else load()
                }
            }
        })

        super.onViewCreated(view, savedInstanceState)
    }

    fun startExoplayer(media: Media){
        val intent = Intent(activity, ExoplayerView::class.java).apply {
            putExtra("ep", media.anime!!.episodes!![media.anime.selectedEpisode!!])
        }
        startActivity(intent)
    }

    private inner class StreamAdapter : RecyclerView.Adapter<StreamAdapter.StreamViewHolder>() {
        val links = episode.streamLinks
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StreamViewHolder = StreamViewHolder(ItemStreamBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        override fun onBindViewHolder(holder: StreamViewHolder, position: Int) {
            val server = links[links.keys.toList()[position]]!!.server
            holder.binding.streamName.text = server
            holder.binding.streamRecyclerView.layoutManager = LinearLayoutManager(requireContext())
            holder.binding.streamRecyclerView.adapter = QualityAdapter(server)
        }
        override fun getItemCount(): Int = links.size
        private inner class StreamViewHolder(val binding: ItemStreamBinding) : RecyclerView.ViewHolder(binding.root)
    }

    private inner class QualityAdapter(private val stream:String) : RecyclerView.Adapter<QualityAdapter.UrlViewHolder>() {
        val urls = episode.streamLinks[stream]!!.quality

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
                    media!!.anime!!.episodes!![media!!.anime!!.selectedEpisode!!]!!.selectedStream = stream
                    media!!.anime!!.episodes!![media!!.anime!!.selectedEpisode!!]!!.selectedQuality = bindingAdapterPosition
                    if(makeDefault){
                        media!!.selected!!.stream = stream
                        media!!.selected!!.quality = bindingAdapterPosition
                        model.saveSelected(media!!.id,media!!.selected!!,requireActivity())
                    }
                    dismiss()
                    startExoplayer(media!!)
                }
            }
        }
    }

    companion object {
        fun newInstance(server:String?=null): SelectorDialogFragment =
            SelectorDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(server,"server")
                }
            }
    }

    override fun onDismiss(dialog: DialogInterface) {
        scope.cancel()
        super.onDismiss(dialog)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}