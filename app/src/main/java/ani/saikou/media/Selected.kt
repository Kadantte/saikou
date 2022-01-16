package ani.saikou.media

import java.io.Serializable

data class Selected(
    var window:Int = 0,
    var recyclerStyle:Int = 0,
    var recyclerReversed:Boolean = false,
    var source:Int = 0,
    var stream:String? = null,
    var quality: Int=0,
    var chip:Int = 1,
):Serializable
