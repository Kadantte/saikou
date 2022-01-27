package ani.saikou.media

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import ani.saikou.*
import ani.saikou.databinding.ActivityStudioBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class StudioActivity : AppCompatActivity() {
    private lateinit var binding: ActivityStudioBinding
    private val scope = CoroutineScope(Dispatchers.Default)
    private val model: OtherDetailsViewModel by viewModels()
    private lateinit var studio: Studio
    private var loaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStudioBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initActivity(this)
        this.window.statusBarColor = ContextCompat.getColor(this, R.color.nav_bg)

        binding.root.updateLayoutParams<ViewGroup.MarginLayoutParams> { topMargin += statusBarHeight }
        binding.studioRecycler.updatePadding(bottom = 64f.px + navBarHeight)
        binding.studioTitle.isSelected = true

        studio = intent.getSerializableExtra("studio") as Studio
        binding.studioTitle.text = studio.name

        binding.studioClose.setOnClickListener{
            onBackPressed()
        }

        model.getStudio().observe(this, {
            if (it != null) {
                studio = it
                loaded = true
                binding.studioProgressBar.visibility = View.GONE
                binding.studioRecycler.visibility = View.VISIBLE
                binding.studioRecycler.adapter = MediasWithTitleAdapter(studio.yearMedia!!,this)
                binding.studioRecycler.layoutManager = LinearLayoutManager(this)
            }
        })
        if(!loaded) scope.launch {
            model.loadStudio(studio)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onResume() {
        binding.studioProgressBar.visibility = if (!loaded) View.VISIBLE else View.GONE
        super.onResume()
    }
}