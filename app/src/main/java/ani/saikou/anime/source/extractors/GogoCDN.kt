package ani.saikou.anime.source.extractors

import android.util.Base64
import ani.saikou.anime.Episode
import ani.saikou.anime.source.Extractor
import ani.saikou.getSize
import ani.saikou.logger
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.jsoup.Jsoup
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class GogoCDN: Extractor() {
    override fun getStreamLinks(name: String, url: String): Episode.StreamLinks {
        println(url)
        val list = arrayListOf<Episode.Quality>()

        val response = Jsoup.connect(url)
            .ignoreContentType(true)
            .ignoreHttpErrors(true)
            .get()

        val encrypted = response.select("script[data-name='crypto']").attr("data-value")
        val iv = response.select("script[data-name='ts']").attr("data-value").toByteArray()

        val id = Regex("(?<=id=).+?(?=&)").find(url)!!.value

        val secretKey = cryptoHandler(encrypted,iv,iv+iv,false)
        val encryptedId = cryptoHandler(id,"0000000000000000".toByteArray(),secretKey.toByteArray())

        val jsonResponse = Jsoup.connect("http://gogoplay.io/encrypt-ajax.php?id=$encryptedId&time=00000000000000000000")
            .ignoreHttpErrors(true).ignoreContentType(true)
            .header("X-Requested-With","XMLHttpRequest").get().body().text()

        Json.decodeFromString<JsonObject>(jsonResponse).jsonObject["source"]!!.jsonArray.forEach {
           val label = it.jsonObject["label"].toString().lowercase().trim('"')
            val fileURL = it.jsonObject["file"].toString().trim('"')
            if(label != "auto"){
                list.add(Episode.Quality(
                    fileURL,
                    label,
                    getSize(fileURL,"https://gogoanime.pe")
                ))
            }
        }

        logger(jsonResponse)

        return Episode.StreamLinks(name, list, "https://gogoplay1.com/")
    }

    private fun cryptoHandler(string:String,iv:ByteArray,secretKeyString:ByteArray,encrypt:Boolean=true) : String {
        val ivParameterSpec =  IvParameterSpec(iv)
        val secretKey =  SecretKeySpec(secretKeyString, "AES")
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        return if (!encrypt) {
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivParameterSpec)
            String(cipher.doFinal(Base64.decode(string,Base64.DEFAULT)))
        }
        else{
            cipher.init(Cipher.ENCRYPT_MODE,secretKey,ivParameterSpec)
            Base64.encodeToString(cipher.doFinal(string.toByteArray()),Base64.NO_WRAP)
        }
    }

}