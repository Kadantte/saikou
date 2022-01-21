package ani.saikou

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePaddingRelative
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ani.saikou.anilist.Anilist
import ani.saikou.anilist.AnilistSearch
import ani.saikou.anilist.SearchResults
import ani.saikou.databinding.ActivitySearchBinding
import ani.saikou.media.MediaAdaptor
import ani.saikou.media.MediaLargeAdaptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SearchActivity : AppCompatActivity() {
    private lateinit var binding : ActivitySearchBinding
    private val scope = CoroutineScope(Dispatchers.Default)
    private var screenWidth:Float = 0f
    private var search:SearchResults?=null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initActivity(this)

        var grid = loadData<Boolean>("searchGrid")?:false
        (if (grid) binding.searchResultGrid else binding.searchResultList).alpha = 1f
        (if (!grid) binding.searchResultGrid else binding.searchResultList).alpha = 0.33f

        screenWidth = resources.displayMetrics.run { widthPixels / density }
        binding.searchBar.updateLayoutParams<ViewGroup.MarginLayoutParams> { topMargin += statusBarHeight }
        binding.searchScrollContainer.updateLayoutParams<ViewGroup.MarginLayoutParams> { topMargin += statusBarHeight }
        binding.searchRecyclerView.updateLayoutParams{ height=resources.displayMetrics.heightPixels+navBarHeight }
        binding.searchProgress.updateLayoutParams<ViewGroup.MarginLayoutParams> { bottomMargin += navBarHeight }
        binding.searchRecyclerView.updatePaddingRelative(bottom = navBarHeight+80f.px)
        binding.searchRecyclerView.requestDisallowInterceptTouchEvent(false)

        binding.searchScrollView.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { v, _, _, _, _ ->
            if(!v.canScrollVertically(1)) {
                binding.searchRecyclerView.requestDisallowInterceptTouchEvent(false)
                window.statusBarColor = ContextCompat.getColor(this, R.color.bg)
            }
            if(!v.canScrollVertically(-1)) {
                binding.searchRecyclerView.requestDisallowInterceptTouchEvent(true)
                window.statusBarColor = ContextCompat.getColor(this, R.color.status)
            }
        })

        binding.searchGenre.setText(intent.getStringExtra("genre")?:"")
        binding.searchGenre.setAdapter(ArrayAdapter(this, R.layout.item_dropdown,(Anilist.genres?: mapOf()).keys.toTypedArray()))
        binding.searchSortBy.setText(intent.getStringExtra("sortBy")?:"")
        binding.searchSortBy.setAdapter(ArrayAdapter(this, R.layout.item_dropdown,Anilist.sortBy.keys.toTypedArray()))

        val model: AnilistSearch by viewModels()

        fun recycler(){
            if (search!=null) {
                val adapter = if(grid) MediaAdaptor(search!!.results, this, true) else MediaLargeAdaptor(search!!.results,this)
                var loading = false
                binding.searchRecyclerView.adapter = adapter
                binding.searchRecyclerView.layoutManager = GridLayoutManager(this, if (grid) (screenWidth / 124f).toInt() else 1)
                binding.searchProgress.visibility = View.GONE
                binding.searchRecyclerView.visibility = View.VISIBLE
                if (search!!.hasNextPage) {
                    binding.searchRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                        override fun onScrolled(v: RecyclerView, dx: Int, dy: Int) {
                            if (!v.canScrollVertically(1)) {
                                if (search!!.hasNextPage && !loading) {
                                    binding.searchProgress.visibility = View.VISIBLE
                                    scope.launch {
                                        if (!loading) {
                                            loading = true
                                            val get = model.loadNextPage(search!!)
                                            if(get!=null) {
                                                val a = search!!.results.size
                                                search!!.results.addAll(get.results)
                                                runOnUiThread {
                                                    adapter.notifyItemRangeInserted(
                                                        a,
                                                        get.results.size
                                                    )
                                                    binding.searchProgress.visibility = View.GONE
                                                }
                                                search!!.page = get.page
                                                search!!.hasNextPage = get.hasNextPage
                                                loading = false
                                            }
                                        }
                                    }
                                } else binding.searchProgress.visibility = View.GONE
                            }
                            if (!v.canScrollVertically(-1)){
                                binding.searchRecyclerView.requestDisallowInterceptTouchEvent(true)
                                window.statusBarColor = ContextCompat.getColor(this@SearchActivity, R.color.status)
                            }
                            super.onScrolled(v, dx, dy)
                        }
                    })
                }
            }
        }

        val type:String = intent.getStringExtra("type")!!
        binding.searchBar.hint = type

        model.getSearch().observe(this,{
            search=it
            recycler()
        })
        binding.searchBarText.requestFocusFromTouch()
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        val imm: InputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        fun searchTitle(){
            binding.searchRecyclerView.adapter=null
            binding.searchProgress.visibility = View.VISIBLE
            binding.searchRecyclerView.visibility = View.GONE
            binding.searchBarText.clearFocus()
            imm.hideSoftInputFromWindow(binding.searchBarText.windowToken, 0)
            val search = if (binding.searchBarText.text.toString()!="") binding.searchBarText.text.toString() else null
            val genre = if (binding.searchGenre.text.toString()!="") arrayListOf(binding.searchGenre.text.toString()) else null
            val sortBy = if (binding.searchSortBy.text.toString()!="") Anilist.sortBy[binding.searchSortBy.text.toString()] else null
            scope.launch {
                model.loadSearch(type,search,genre,sortBy?:"SEARCH_MATCH")
            }
        }

        binding.searchBarText.setOnEditorActionListener { _, actionId, _ ->
            return@setOnEditorActionListener when (actionId) {
                EditorInfo.IME_ACTION_SEARCH -> {
                    searchTitle()
                    true
                }
                else -> false
            }
        }
        binding.searchBar.setEndIconOnClickListener{ searchTitle() }
        binding.searchGenre.setOnItemClickListener { _, _, _, _ -> searchTitle() }
        binding.searchSortBy.setOnItemClickListener { _, _, _, _ -> searchTitle() }

        binding.searchClear.setOnClickListener {
            binding.searchGenre.setText("")
            binding.searchSortBy.setText("")
            searchTitle()
        }

        binding.searchResultGrid.setOnClickListener {
            it.alpha = 1f
            binding.searchResultList.alpha = 0.33f
            grid = true
            saveData("searchGrid",grid)
            recycler()
        }
        binding.searchResultList.setOnClickListener {
            it.alpha = 1f
            binding.searchResultGrid.alpha = 0.33f
            grid = false
            saveData("searchGrid",grid)
            recycler()
        }

        if(intent.getStringExtra("genre")!=null || intent.getStringExtra("sortBy")!=null) searchTitle()
    }
}