package com.xhsphone.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

private val Context.sessionStore by preferencesDataStore(name = "xhs_session")

/**
 * Cookie 持久化：登录后把 Cookie 存进 DataStore，App 重启后恢复登录态。
 */
class XhsSession(private val context: Context) {

    private val cookieKey = stringPreferencesKey("cookies_json")

    val cookieJar = object : CookieJar {
        private val memory = mutableMapOf<String, List<Cookie>>()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            memory[url.host] = cookies
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            return memory[url.host].orEmpty()
        }

        fun putAll(host: String, cookies: List<Cookie>) {
            memory[host] = cookies
        }

        fun all(): Map<String, List<Cookie>> = memory.toMap()

        fun clear() = memory.clear()
    }

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("User-Agent", UA)
                    .header("Referer", "https://www.xiaohongshu.com/")
                    .header("Origin", "https://www.xiaohongshu.com")
                    .build()
                chain.proceed(req)
            }
            .build()
    }

    suspend fun saveCookies() {
        val flat = cookieJar.all().flatMap { (host, list) ->
            list.map { "$host|${it.name}=${it.value}; domain=${it.domain}; path=${it.path}" }
        }
        context.sessionStore.edit { it[cookieKey] = flat.joinToString("\n") }
    }

    suspend fun restoreCookies(): Boolean {
        val raw = context.sessionStore.data.map { it[cookieKey].orEmpty() }.first()
        if (raw.isBlank()) return false
        var count = 0
        raw.lineSequence().forEach { line ->
            val host = line.substringBefore("|")
            val cookieStr = line.substringAfter("|")
            val cookie = Cookie.parse(HttpUrl.get("https://$host/"), cookieStr)
            if (cookie != null) {
                val prev = cookieJar.loadForRequest(HttpUrl.get("https://$host/")).toMutableList()
                prev.removeAll { it.name == cookie.name }
                prev.add(cookie)
                cookieJar.putAll(host, prev)
                count++
            }
        }
        return count > 0
    }

    suspend fun clear() {
        cookieJar.clear()
        context.sessionStore.edit { it.remove(cookieKey) }
    }

    companion object {
        const val UA =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
        const val API_BASE = "https://edith.xiaohongshu.com"
    }
}
