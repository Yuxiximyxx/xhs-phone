package com.xhsphone

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.xhsphone.data.XhsSession
import com.xhsphone.ui.theme.XhsPhoneTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * WebView 登录：加载小红书官方登录页（支持手机号+验证码/密码、扫码），
 * 登录成功后把 Cookie 同步到 OkHttp 并持久化。
 */
class LoginActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val session = XhsSession(applicationContext)
        val cookieManager = CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(null, true)
        }

        setContent {
            XhsPhoneTheme {
                val scope = rememberCoroutineScope()
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("登录小红书") },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.Default.Close, contentDescription = "关闭")
                                }
                            }
                        )
                    }
                ) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding)) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.userAgentString = XhsSession.UA
                                    webViewClient = object : WebViewClient() {
                                        override fun onPageFinished(view: WebView, url: String) {
                                            // 登录成功会跳回首页，检测登录态 Cookie
                                            val cookie = cookieManager.getCookie("https://www.xiaohongshu.com").orEmpty()
                                            if (cookie.contains("web_session") && cookie.contains("a1")) {
                                                scope.launch {
                                                    syncCookies(cookieManager, session)
                                                    withContext(Dispatchers.Main) {
                                                        setResult(RESULT_OK)
                                                        finish()
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    loadUrl("https://www.xiaohongshu.com/login")
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    private suspend fun syncCookies(cookieManager: CookieManager, session: XhsSession) {
        withContext(Dispatchers.IO) {
            listOf("www.xiaohongshu.com", "edith.xiaohongshu.com").forEach { host ->
                val raw = cookieManager.getCookie("https://$host").orEmpty()
                val cookies = raw.split(";").mapNotNull { part ->
                    val kv = part.trim().split("=", limit = 2)
                    if (kv.size == 2) {
                        okhttp3.Cookie.Builder()
                            .name(kv[0].trim()).value(kv[1])
                            .domain(host).path("/").build()
                    } else null
                }
                session.cookieJar.putAll(host, cookies)
            }
            session.saveCookies()
        }
    }
}

@Composable
fun LoginPlaceholder() = Unit
