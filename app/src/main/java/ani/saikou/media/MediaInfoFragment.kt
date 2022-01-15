package ani.saikou.media

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.text.HtmlCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import ani.saikou.R
import ani.saikou.databinding.FragmentMediaInfoBinding
import ani.saikou.recyclerViewListener

@SuppressLint("SetTextI18n")
class MediaInfoFragment : Fragment() {
    private var _binding: FragmentMediaInfoBinding? = null
    private val binding get() = _binding!!
    private var loaded = false
    private var type = "ANIME"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMediaInfoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView();_binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val screenWidth = resources.displayMetrics.run { widthPixels / density }
        binding.mediaInfoProgressBar.visibility = if (!loaded) View.VISIBLE else View.GONE
        binding.mediaInfoContainer.visibility = if (loaded) View.VISIBLE else View.GONE

        val model : MediaDetailsViewModel by activityViewModels()
        model.getMedia().observe(viewLifecycleOwner,{
            val media = it
            if(media!=null){
                loaded=true
                binding.mediaInfoProgressBar.visibility = View.GONE
                binding.mediaInfoContainer.visibility = View.VISIBLE
                binding.mediaInfoName.text = media.getMainName()
                if (media.name!="null")
                    binding.mediaInfoNameRomajiContainer.visibility = View.VISIBLE
                    binding.mediaInfoNameRomaji.text = media.nameRomaji
                binding.mediaInfoMeanScore.text = if(media.meanScore!=null) (media.meanScore/10.0).toString() else "??"
                binding.mediaInfoStatus.text = media.status
                binding.mediaInfoFormat.text = media.format
                binding.mediaInfoSource.text = media.source
                binding.mediaInfoStart.text = if (media.startDate.toString()!="") media.startDate.toString() else "??"
                binding.mediaInfoEnd.text = if (media.endDate.toString()!="") media.endDate.toString() else "??"
                if (media.anime!=null) {
                    binding.mediaInfoDuration.text = if (media.anime.episodeDuration!=null) media.anime.episodeDuration.toString() else "??"
                    binding.mediaInfoDurationContainer.visibility = View.VISIBLE
                    binding.mediaInfoSeasonContainer.visibility = View.VISIBLE
                    binding.mediaInfoSeason.text = media.anime.season?:"??" +" "+ media.anime.seasonYear
                    binding.mediaInfoStudioContainer.visibility = View.VISIBLE
                    binding.mediaInfoStudio.text = media.anime.mainStudioName?:"??"
                    binding.mediaInfoTotalTitle.setText(R.string.total_eps)
                    binding.mediaInfoTotal.text = if (media.anime.nextAiringEpisode!=null) (media.anime.nextAiringEpisode.toString()+" | "+(media.anime.totalEpisodes?:"~").toString()) else (media.anime.totalEpisodes?:"~").toString()
                }
                else if (media.manga!=null){
                    type = "MANGA"
                    binding.mediaInfoTotalTitle.setText(R.string.total_chaps)
                    binding.mediaInfoTotal.text = (media.manga.totalChapters?:"~").toString()
                }
                val desc = HtmlCompat.fromHtml((media.description?:"null").replace("\\n","<br>").replace("\\\"","\""), HtmlCompat.FROM_HTML_MODE_LEGACY)
                binding.mediaInfoDescription.text = "\t\t\t"+if (desc.toString()!="null") desc  else "No Description Available"
                binding.mediaInfoDescription.setOnClickListener{
                    if (binding.mediaInfoDescription.maxLines == 5){
                        ObjectAnimator.ofInt(binding.mediaInfoDescription,"maxLines",100).setDuration(950).start()
                    }
                    else{
                        ObjectAnimator.ofInt(binding.mediaInfoDescription,"maxLines",5).setDuration(400).start()
                    }
                }
                binding.mediaInfoRelationRecyclerView.adapter=MediaAdaptor(media.relations!!,requireActivity())
                binding.mediaInfoRelationRecyclerView.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
                binding.mediaInfoRelationRecyclerView.addOnItemTouchListener(recyclerViewListener)

                binding.mediaInfoGenresRecyclerView.adapter = GenreAdapter(media.genres!!,type,requireActivity())
                binding.mediaInfoGenresRecyclerView.layoutManager = GridLayoutManager(requireContext(), (screenWidth/156f).toInt())
                binding.mediaInfoGenresRecyclerView.addOnItemTouchListener(recyclerViewListener)

                binding.mediaInfoCharacterRecyclerView.adapter = CharacterAdapter(media.characters!!)
                binding.mediaInfoCharacterRecyclerView.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
                binding.mediaInfoCharacterRecyclerView.addOnItemTouchListener(recyclerViewListener)

                binding.mediaInfoRecommendedRecyclerView.adapter = MediaAdaptor(media.recommendations!!,requireActivity())
                binding.mediaInfoRecommendedRecyclerView.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
                binding.mediaInfoRecommendedRecyclerView.addOnItemTouchListener(recyclerViewListener)
            }
        })
        super.onViewCreated(view, null)
    }

    override fun onResume() {
        binding.mediaInfoProgressBar.visibility = if (!loaded) View.VISIBLE else View.GONE
        super.onResume()
    }
}
