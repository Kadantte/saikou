package ani.saikou.anime

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import ani.saikou.databinding.ItemEpisodeCompactBinding
import ani.saikou.databinding.ItemEpisodeGridBinding
import ani.saikou.databinding.ItemEpisodeListBinding
import ani.saikou.loadImage
import ani.saikou.media.Media
import com.squareup.picasso.Picasso


fun episodeAdapter(media:Media,fragment: AnimeSourceFragment,style:Int,reversed:Boolean=false,start:Int=0,e:Int?=null): RecyclerView.Adapter<*> {
    val end = e?:(media.anime!!.episodes!!.size-1)
    var arr = media.anime!!.episodes!!.values.toList().slice(start..end)
    arr = if (reversed) arr.reversed() else arr
//    println(" Start : $start End : $end")
//    println("First ep : ${arr[0].number} Last ep : ${arr[arr.size - 1].number}")
    return when (style){
        0 -> EpisodeListAdapter(media, fragment,arr)
        1 -> EpisodeGridAdapter(media, fragment,arr)
        2 -> EpisodeCompactAdapter(media, fragment,arr)
        else -> EpisodeGridAdapter(media, fragment,arr)
    }
}

class EpisodeCompactAdapter(
    private val media: Media,
    private val fragment: AnimeSourceFragment,
    private val arr: List<Episode>,
): RecyclerView.Adapter<EpisodeCompactAdapter.EpisodeCompactViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EpisodeCompactViewHolder {
        val binding = ItemEpisodeCompactBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return EpisodeCompactViewHolder(binding)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: EpisodeCompactViewHolder, position: Int) {
        val binding = holder.binding

        val ep = arr[position]
        binding.itemEpisodeNumber.text = ep.number
        if (ep.filler) binding.itemEpisodeFillerView.visibility = View.VISIBLE
        if (media.userProgress!=null) {
            if (ep.number.toIntOrNull()?:9999<=media.userProgress!!) binding.root.alpha = 0.66f
        }
    }

    override fun getItemCount(): Int = arr.size

    inner class EpisodeCompactViewHolder(val binding: ItemEpisodeCompactBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            itemView.setOnClickListener {
                fragment.onEpisodeClick(media,arr[bindingAdapterPosition].number)
            }
        }
    }
}

class EpisodeGridAdapter(
    private val media: Media,
    private val fragment: AnimeSourceFragment,
    private val arr: List<Episode>,
): RecyclerView.Adapter<EpisodeGridAdapter.EpisodeGridViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EpisodeGridViewHolder {
        val binding = ItemEpisodeGridBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return EpisodeGridViewHolder(binding)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: EpisodeGridViewHolder, position: Int) {
        val binding = holder.binding
        val ep = arr[position]
        Picasso.get().load(ep.thumb?:media.cover).resize(0,300).into(binding.itemEpisodeImage)
        binding.itemEpisodeNumber.text = ep.number
        binding.itemEpisodeTitle.text = ep.title?:media.name
        if(ep.filler){
            binding.itemEpisodeFiller.visibility = View.VISIBLE
            binding.itemEpisodeFillerView.visibility = View.VISIBLE
        }
        if (media.userProgress!=null) {
            if (ep.number.toIntOrNull()?:9999<=media.userProgress!!) binding.root.alpha = 0.66f
        }
    }

    override fun getItemCount(): Int = arr.size

    inner class EpisodeGridViewHolder(val binding: ItemEpisodeGridBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            itemView.setOnClickListener {
                fragment.onEpisodeClick(media,arr[bindingAdapterPosition].number)
            }
        }
    }
}

class EpisodeListAdapter(
    private val media: Media,
    private val fragment: AnimeSourceFragment,
    private val arr: List<Episode>
): RecyclerView.Adapter<EpisodeListAdapter.EpisodeListViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EpisodeListViewHolder {
        val binding = ItemEpisodeListBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return EpisodeListViewHolder(binding)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: EpisodeListViewHolder, position: Int) {
        val binding = holder.binding
        val ep = arr[position]
        Picasso.get().load(ep.thumb?:media.cover).resize(0,300).into(binding.itemEpisodeImage)
        binding.itemEpisodeNumber.text = ep.number
        if(ep.filler){
            binding.itemEpisodeFiller.visibility = View.VISIBLE
            binding.itemEpisodeFillerView.visibility = View.VISIBLE
        }
        if (ep.desc==null) binding.itemEpisodeDesc.visibility = View.GONE
        binding.itemEpisodeDesc.text = ep.desc?:""
        binding.itemEpisodeTitle.text = ep.title?:media.userPreferredName
        if (media.userProgress!=null) {
            if (ep.number.toIntOrNull()?:9999<=media.userProgress!!) binding.root.alpha = 0.66f
        }
    }

    override fun getItemCount(): Int = arr.size

    inner class EpisodeListViewHolder(val binding: ItemEpisodeListBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            itemView.setOnClickListener {
                fragment.onEpisodeClick(media,arr[bindingAdapterPosition].number)
            }
            itemView.setOnLongClickListener {
                if(binding.itemEpisodeDesc.maxLines == 3)
                    binding.itemEpisodeDesc.maxLines = 100
                else
                    binding.itemEpisodeDesc.maxLines = 3
                return@setOnLongClickListener true
            }
        }
    }
}

