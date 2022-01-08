package ani.saikou

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.content.res.Resources.getSystem
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.text.InputFilter
import android.text.Spanned
import android.util.AttributeSet
import android.view.View
import android.view.ViewAnimationUtils
import android.widget.AutoCompleteTextView
import android.widget.DatePicker
import android.widget.ImageView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.DialogFragment
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator
import androidx.multidex.MultiDex
import androidx.multidex.MultiDexApplication
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import ani.saikou.media.Media
import ani.saikou.media.Source
import com.squareup.picasso.Picasso
import nl.joery.animatedbottombar.AnimatedBottomBar
import org.jsoup.Jsoup
import java.io.*
import java.util.*
import kotlin.math.max
import kotlin.math.min
import com.squareup.picasso.OkHttp3Downloader
import okhttp3.Cache
import okhttp3.OkHttpClient
import org.jsoup.Connection

const val STATE_RESUME_WINDOW = "resumeWindow"
const val STATE_RESUME_POSITION = "resumePosition"
const val STATE_PLAYER_FULLSCREEN = "playerFullscreen"
const val STATE_PLAYER_PLAYING = "playerOnPlay"
const val MAX_HEIGHT = 500
const val MAX_WIDTH = 900
const val buildDebug = true

var statusBarHeight  = 0
var navBarHeight = 0
val Int.dp: Float get() = (this / getSystem().displayMetrics.density)
val Float.px: Int get() = (this * getSystem().displayMetrics.density).toInt()

lateinit var bottomBar: AnimatedBottomBar
var selectedOption = 1

fun currActivity():Activity?{
    return App.currentActivity()
}

var loadMedia:Int?=null
var loadIsMAL=false



fun logger(e:Any?,print:Boolean=true){
    if(buildDebug && print)
        println(e)
}

fun saveData(fileName:String,data:Any,activity: Activity?=null){
    val a = activity?: currActivity()
    if (a!=null) {
        val fos: FileOutputStream = a.openFileOutput(fileName, Context.MODE_PRIVATE)
        val os = ObjectOutputStream(fos)
        os.writeObject(data)
        os.close()
        fos.close()
    }
}

@Suppress("UNCHECKED_CAST")
fun <T> loadData(fileName:String,activity: Activity?=null): T? {
    val a = activity?: currActivity()
    if (a?.fileList() != null)
        if (fileName in a.fileList()){
            val fileIS: FileInputStream = a.openFileInput(fileName)
            val objIS = ObjectInputStream(fileIS)
            val data = objIS.readObject() as T
            objIS.close()
            fileIS.close()
            return data
        }
    return null
}

fun initActivity(a: Activity,view:View?=null) {
    val window = a.window
    WindowCompat.setDecorFitsSystemWindows(window, false)
    if (view != null) {
        println("$view")
        val windowInsets = ViewCompat.getRootWindowInsets(window.decorView.findViewById(android.R.id.content))
        if (windowInsets!=null) {
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            statusBarHeight = insets.top
            navBarHeight = insets.bottom
            println("$insets")
        }
    }
}


fun isOnline(context: Context): Boolean {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
    if (capabilities != null) {
        when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                logger("Device on Cellular")
                return true
            }
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                logger("Device on Wifi")
                return true
            }
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> {
                logger("Device on Ethernet, TF man?")
                return true
            }
        }
    }
    return false
}

fun startMainActivity(activity: Activity){
    activity.finishAffinity()
    activity.startActivity(Intent(activity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK))
}

data class FuzzyDate(
    val year: Int?=null,
    val month: Int?=null,
    val day: Int?=null,
): Serializable{
    override fun toString():String{
        return (if (day!=null) "$day / " else "")+(if (month!=null) "$month / " else "")+(year?.toString() ?: "")
    }
    fun getToday():FuzzyDate{
        val cal = Calendar.getInstance()
        return FuzzyDate(cal.get(Calendar.YEAR),cal.get(Calendar.MONTH)+1,cal.get(Calendar.DAY_OF_MONTH))
    }
    fun getEpoch():Long{
        val cal = Calendar.getInstance()
        cal.set(year?:cal.get(Calendar.YEAR),month?:cal.get(Calendar.MONTH),day?:cal.get(Calendar.DAY_OF_MONTH))
        return cal.timeInMillis
    }
}

class DatePickerFragment(activity: Activity, var date: FuzzyDate=FuzzyDate().getToday()) : DialogFragment(), DatePickerDialog.OnDateSetListener {
    var dialog :DatePickerDialog
    init{
        val c = Calendar.getInstance()
        val year = date.year?:c.get(Calendar.YEAR)
        val month= if (date.month!=null) date.month!! -1 else c.get(Calendar.MONTH)
        val day = date.day?:c.get(Calendar.DAY_OF_MONTH)
        dialog = DatePickerDialog(activity, this, year, month, day)
    }

    override fun onDateSet(view: DatePicker, year: Int, month: Int, day: Int) {
        date = FuzzyDate(year,month+1,day)
    }
}

class InputFilterMinMax(private val min: Double, private val max: Double,private val status:AutoCompleteTextView?=null) : InputFilter {
    override fun filter(source: CharSequence, start: Int, end: Int, dest: Spanned, dstart: Int, dend: Int): CharSequence? {
        try {
            val input = (dest.toString() + source.toString()).toDouble()
            if (isInRange(min, max, input)) return null
        } catch (nfe: NumberFormatException) {
            nfe.printStackTrace()
        }
        return ""
    }

    @SuppressLint("SetTextI18n")
    private fun isInRange(a: Double, b: Double, c: Double): Boolean {
        if (c==b) {
            status?.setText("COMPLETED",false)
            status?.parent?.requestLayout()
        }
        return if (b > a) c in a..b else c in b..a
    }
}

fun getMalMedia(media:Media) : Media{
    try {
        if(media.anime!=null) {
            val res = Jsoup.connect("https://myanimelist.net/anime/${media.idMAL}").ignoreHttpErrors(true).get()
            val a = res.select(".title-english").text()
            media.nameMAL = if (a!="") a else res.select(".title-name").text()
            media.typeMAL = if(res.select("div.spaceit_pad > a").isNotEmpty()) res.select("div.spaceit_pad > a")[0].text() else null
        }else{
            val res = Jsoup.connect("https://myanimelist.net/manga/${media.idMAL}").ignoreHttpErrors(true).get()
            val b = res.select(".title-english").text()
            val a = res.select(".h1-title").text().removeSuffix(b)
            media.nameMAL = a
            media.typeMAL = if(res.select("div.spaceit_pad > a").isNotEmpty()) res.select("div.spaceit_pad > a")[0].text() else null
        }
    } catch (e:Exception){
        toastString(e.message)
    }
    return media
}

fun toastString(s: String?){
    currActivity()?.runOnUiThread { Toast.makeText(currActivity(), s, Toast.LENGTH_LONG).show() }
//    logger(s)
}

class ZoomOutPageTransformer(private val bottom:Boolean=false) : ViewPager2.PageTransformer {
    override fun transformPage(view: View, position: Float) {
        if (position == 0.0f) {
            var cy = 0
            if (bottom) cy = view.height
            val a = ViewAnimationUtils.createCircularReveal(view, view.width / 2, cy, 0f, max(view.height, view.width).toFloat())
            a.interpolator = LinearOutSlowInInterpolator()
            a.setDuration(400).start()
        }
    }
}

class FadingEdgeRecyclerView : RecyclerView {

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    override fun isPaddingOffsetRequired(): Boolean {
        return !clipToPadding
    }

    override fun getLeftPaddingOffset(): Int {
        return if (clipToPadding) 0 else -paddingLeft
    }

    override fun getTopPaddingOffset(): Int {
        return if (clipToPadding) 0 else -paddingTop
    }

    override fun getRightPaddingOffset(): Int {
        return if (clipToPadding) 0 else paddingRight
    }

    override fun getBottomPaddingOffset(): Int {
        return if (clipToPadding) 0 else paddingBottom
    }
}

fun levenshtein(lhs : CharSequence, rhs : CharSequence) : Int {
    if(lhs == rhs) { return 0 }
    if(lhs.isEmpty()) { return rhs.length }
    if(rhs.isEmpty()) { return lhs.length }

    val lhsLength = lhs.length + 1
    val rhsLength = rhs.length + 1

    var cost = Array(lhsLength) { it }
    var newCost = Array(lhsLength) { 0 }

    for (i in 1 until rhsLength) {
        newCost[0] = i

        for (j in 1 until lhsLength) {
            val match = if(lhs[j - 1] == rhs[i - 1]) 0 else 1

            val costReplace = cost[j - 1] + match
            val costInsert = cost[j] + 1
            val costDelete = newCost[j - 1] + 1

            newCost[j] = min(min(costInsert, costDelete), costReplace)
        }

        val swap = cost
        cost = newCost
        newCost = swap
    }

    return cost[lhsLength - 1]
}

fun ArrayList<Source>.sortByTitle(string: String){
    val temp : MutableMap<Int,Int> = mutableMapOf()
    for (i in 0 until this.size){
        temp[i] = levenshtein(string.lowercase(),this[i].name.lowercase())
    }
    val a = temp.toList().sortedBy { (_, value) -> value}.toMap().keys.toTypedArray()
    val temp2 = arrayListOf<Source>()
    temp2.addAll(this)
    this.clear()
    for (i in a.indices){
        this.add(temp2[a[i]])
    }
}

fun loadImage(url:String?,imageView: ImageView,referer:String?=null){
    if(referer==null) Picasso.get().load(url).into(imageView)
    else {
        val a = currActivity()
        if (a!=null && !a.isDestroyed) {
            val client = OkHttpClient.Builder()
                .cache(Cache(
                    File(a.cacheDir, "http_cache"),
                    50L * 1024L * 1024L
                ))
                .addInterceptor { chain ->
                    val newRequest = chain.request().newBuilder()
                        .addHeader("referer", referer)
                        .build()
                    chain.proceed(newRequest)
                }
                .build()

            Picasso.Builder(a)
                .downloader(OkHttp3Downloader(client))
                .build().load(url).into(imageView)
        }
    }
}

fun getSize(url: String,referer: String=""):Int?{
    return Jsoup.connect(url)
        .ignoreContentType(true)
        .ignoreHttpErrors(true)
        .header("referer",referer)
        .method(Connection.Method.HEAD)
        .execute().header("Content-Length")?.toInt()?.div(1048576)
}


class App: MultiDexApplication() {
    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        MultiDex.install(this)
    }

    init { instance = this }

    val mFTActivityLifecycleCallbacks = FTActivityLifecycleCallbacks()

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(mFTActivityLifecycleCallbacks)
    }

    companion object {
        private var instance: App? = null
        fun currentActivity(): Activity? {
            return instance!!.mFTActivityLifecycleCallbacks.currentActivity
        }
    }
}

class FTActivityLifecycleCallbacks: Application.ActivityLifecycleCallbacks {
    var currentActivity: Activity? = null
    override fun onActivityCreated(p0: Activity, p1: Bundle?) {}
    override fun onActivityStarted(p0: Activity) {}
    override fun onActivityResumed(p0: Activity) { currentActivity = p0 }
    override fun onActivityPaused(p0: Activity) {}
    override fun onActivityStopped(p0: Activity) {}
    override fun onActivitySaveInstanceState(p0: Activity, p1: Bundle) {}
    override fun onActivityDestroyed(p0: Activity) {}
}