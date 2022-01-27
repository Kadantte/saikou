package ani.saikou.media

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.math.MathUtils.clamp
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ani.saikou.*
import ani.saikou.databinding.ActivityCharacterBinding
import com.google.android.material.appbar.AppBarLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class CharacterDetailsActivity : AppCompatActivity(), AppBarLayout.OnOffsetChangedListener {
    private lateinit var binding: ActivityCharacterBinding
    private val scope = CoroutineScope(Dispatchers.Default)
    private val model: OtherDetailsViewModel by viewModels()
    private lateinit var character: Character
    private var loaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCharacterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initActivity(this)
        screenWidth = resources.displayMetrics.widthPixels.toFloat()
        this.window.statusBarColor = ContextCompat.getColor(this, R.color.nav_status)

        binding.characterBanner.updateLayoutParams{ height += statusBarHeight }
        binding.characterBannerStatus.updateLayoutParams{ height += statusBarHeight }
        binding.characterBanner.translationY = -statusBarHeight.toFloat()
        binding.characterClose.updateLayoutParams<ViewGroup.MarginLayoutParams> { topMargin += statusBarHeight }
        binding.characterAppBar.updateLayoutParams<ViewGroup.MarginLayoutParams> { topMargin += statusBarHeight }
        binding.characterCover.updateLayoutParams<ViewGroup.MarginLayoutParams> { topMargin += statusBarHeight }
        binding.characterRecyclerView.updatePadding(bottom = 64f.px + navBarHeight)
        binding.characterTitle.isSelected = true
        binding.characterAppBar.addOnOffsetChangedListener(this)

        binding.characterClose.setOnClickListener{
            onBackPressed()
        }
        character = intent.getSerializableExtra("character") as Character
        binding.characterTitle.text = character.name
        loadImage(character.banner,binding.characterBanner)
        loadImage(character.banner,binding.characterBannerStatus)
        loadImage(character.image,binding.characterCoverImage)
        binding.characterCoverImage.setOnClickListener{ openImage(character.image) }
        binding.characterBanner.setOnClickListener{ openImage(character.banner) }

        model.getCharacter().observe(this) {
            if (it != null) {
                character = it
                loaded = true
                binding.characterProgress.visibility = View.GONE
                binding.characterRecyclerView.visibility = View.VISIBLE

                val adapters: ArrayList<RecyclerView.Adapter<out RecyclerView.ViewHolder>> =
                    arrayListOf(CharacterDetailsAdapter(character, this))
                val perRow = clamp(resources.displayMetrics.widthPixels / 124f.px, 1, 4)
                val multiplier = min(perRow, character.roles!!.size)
                for (i in 0 until max(1, character.roles!!.size / perRow)) {
                    adapters.add(
                        MediaGridAdapter(
                            ArrayList(
                                character.roles!!.subList(
                                    i * multiplier,
                                    (i + 1) * multiplier
                                )
                            ), this
                        )
                    )
                }
                binding.characterRecyclerView.adapter = ConcatAdapter(adapters)
                binding.characterRecyclerView.layoutManager = LinearLayoutManager(this)
            }
        }
        if(!loaded) scope.launch {
            model.loadCharacter(character)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onResume() {
        binding.characterBannerStatus.visibility=if (!isCollapsed) View.VISIBLE else View.GONE
        binding.characterProgress.visibility=if (!loaded) View.VISIBLE else View.GONE
        super.onResume()
    }

    private var isCollapsed = false
    private val percent = 30
    private var mMaxScrollSize = 0
    private var screenWidth:Float = 0f

    override fun onOffsetChanged(appBar: AppBarLayout, i: Int) {
        if (mMaxScrollSize == 0) mMaxScrollSize = appBar.totalScrollRange
        val percentage = abs(i) * 100 / mMaxScrollSize
        val cap = clamp((percent - percentage) / percent.toFloat(), 0f, 1f)

        binding.characterCover.scaleX = 1f*cap
        binding.characterCover.scaleY = 1f*cap
        binding.characterCover.cardElevation = 32f*cap

        if (percentage >= percent && !isCollapsed) {
            isCollapsed = true
            binding.characterBannerStatus.visibility=View.GONE
            this.window.statusBarColor = ContextCompat.getColor(this, R.color.nav_bg)
        }
        if (percentage <= percent && isCollapsed) {
            isCollapsed = false
            binding.characterBannerStatus.visibility=View.VISIBLE
            this.window.statusBarColor = ContextCompat.getColor(this, R.color.nav_status)
        }
    }
}