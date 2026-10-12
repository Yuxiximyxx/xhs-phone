package com.xhsphone

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.xhsphone.data.FeedNote
import com.xhsphone.data.XhsApi
import com.xhsphone.data.XhsSession
import com.xhsphone.ui.theme.XhsPhoneTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val loginLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) recreate()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val session = XhsSession(applicationContext)
        val api = XhsApi(session)
        setContent {
            XhsPhoneTheme {
                var loggedIn by remember { mutableStateOf<Boolean?>(null) }
                LaunchedEffect(Unit) {
                    val ok = withContext(Dispatchers.IO) {
                        if (!session.restoreCookies()) return@withContext false
                        api.selfInfo() != null
                    }
                    loggedIn = ok
                    if (ok == false) {
                        loginLauncher.launch(Intent(this@MainActivity, LoginActivity::class.java))
                    }
                }
                when (loggedIn) {
                    null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    true -> MainScreen(api = api, onLogout = {
                        loginLauncher.launch(Intent(this@MainActivity, LoginActivity::class.java))
                    })
                    false -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("正在跳转登录…")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(api: XhsApi, onLogout: () -> Unit) {
    var tab by remember { mutableStateOf(0) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("小红书", fontSize = 18.sp) },
                actions = {
                    IconButton(onClick = { /* TODO: 搜索 */ }) {
                        Icon(Icons.Default.Search, contentDescription = "搜索")
                    }
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Default.Person, contentDescription = "我的")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0, onClick = { tab = 0 },
                    icon = { Text("🏠") }, label = { Text("首页") }
                )
                NavigationBarItem(
                    selected = tab == 1, onClick = { tab = 1 },
                    icon = { Text("🔍") }, label = { Text("发现") }
                )
                NavigationBarItem(
                    selected = tab == 2, onClick = { tab = 2 },
                    icon = { Text("👤") }, label = { Text("我") }
                )
            }
        }
    ) { padding ->
        when (tab) {
            0 -> FeedScreen(api = api, modifier = Modifier.padding(padding))
            else -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { Text("即将上线") }
        }
    }
}

@Composable
fun FeedScreen(api: XhsApi, modifier: Modifier = Modifier) {
    val notes = remember { mutableStateListOf<FeedNote>() }
    var cursor by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var hasMore by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyStaggeredGridState()

    fun loadMore() {
        if (loading || !hasMore) return
        loading = true
        scope.launch(Dispatchers.IO) {
            val (list, next) = runCatching { api.homeFeed(cursor) }
                .getOrElse { emptyList<FeedNote>() to "" }
            withContext(Dispatchers.Main) {
                notes.addAll(list)
                cursor = next
                hasMore = next.isNotEmpty() || list.isNotEmpty()
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) { loadMore() }

    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        state = gridState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalItemSpacing = 8.dp
    ) {
        items(notes, key = { it.displayId + it.xsecToken }) { note ->
            NoteCard(note = note, onClick = { /* TODO: 详情 */ })
        }
        if (loading || hasMore) {
            item {
                LaunchedEffect(Unit) { loadMore() }
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                }
            }
        }
    }
}

@Composable
fun NoteCard(note: FeedNote, onClick: () -> Unit) {
    val cover = note.imageList.firstOrNull()
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column {
            if (cover != null && cover.width > 0 && cover.height > 0) {
                AsyncImage(
                    model = cover.thumbUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth()
                        .aspectRatio(cover.width.toFloat() / cover.height.toFloat()),
                    contentScale = ContentScale.Crop
                )
            }
            Column(Modifier.padding(8.dp)) {
                Text(
                    text = note.title.ifEmpty { note.desc }.take(60),
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = note.user?.avatarUrl,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = note.user?.nickname.orEmpty(),
                        fontSize = 11.sp,
                        color = Color.Gray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(Icons.Default.Favorite, contentDescription = null,
                        tint = Color.Gray, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(2.dp))
                    Text(note.interact?.likeCount ?: "0", fontSize = 11.sp, color = Color.Gray)
                }
            }
        }
    }
}
