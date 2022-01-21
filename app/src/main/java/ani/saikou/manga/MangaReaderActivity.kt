package ani.saikou.manga

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import ani.saikou.databinding.ActivityMangaReaderBinding
import ani.saikou.initActivity
import ani.saikou.media.Media
import ani.saikou.toastString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MangaReaderActivity : AppCompatActivity() {
    private lateinit var binding : ActivityMangaReaderBinding
    private val scope = CoroutineScope(Dispatchers.Default)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMangaReaderBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initActivity(this)

        val a = intent.getSerializableExtra("media")
        if(a!=null) {
            var media = a as Media
            val model: MangaChapterViewModel by viewModels()
            model.getMangaChapMedia().observe(this, {
                if (it != null) {
                    media = it
                    val chapImages = media.manga!!.chapters!![media.manga!!.selectedChapter]?.images
                    val referer = media.manga!!.chapters!![media.manga!!.selectedChapter]?.referer
                    if (chapImages != null) {
                        binding.mangaReaderRecyclerView.adapter = ImageAdapter(chapImages, referer)
                        binding.mangaReaderRecyclerView.layoutManager =
                            LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
//                    binding.mangaReaderRecyclerView.recycledViewPool.setMaxRecycledViews(1,0)
                    }
                }
            })
            scope.launch { model.loadChapMedia(media) }
        }
        else{
            toastString("Please Reload.")
        }
    }
}