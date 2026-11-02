package com.hayate.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hayate.app.Prefs
import com.hayate.core.anilist.AniListHome
import com.hayate.core.anilist.AnimeMedia
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ShelfPage(kind: String, title: String, onBack: () -> Unit, onOpen: (AnimeMedia) -> Unit) {
    val scope = rememberCoroutineScope()
    val list = remember(kind) { mutableStateListOf<AnimeMedia>() }
    var page by remember(kind) { mutableIntStateOf(1) }
    var more by remember(kind) { mutableStateOf(true) }
    var busy by remember(kind) { mutableStateOf(false) }
    var err by remember(kind) { mutableStateOf<String?>(null) }
    val grid = rememberLazyGridState()

    fun load() {
        if (busy || !more) return
        busy = true; err = null
        scope.launch {
            try {
                val (got, next) = withContext(Dispatchers.IO) { AniListHome.getShelfPage(kind, page, 30, Prefs.allowAdult) }
                val have = list.map { it.id }.toSet()
                list.addAll(got.filter { it.id !in have })
                page += 1; more = next
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { err = e.message ?: "Network error"
            } finally { busy = false }
        }
    }
    val near by remember { derivedStateOf {
        val l = grid.layoutInfo
        (l.visibleItemsInfo.lastOrNull()?.index ?: 0) >= l.totalItemsCount - 6
    } }
    LaunchedEffect(kind) { load() }
    LaunchedEffect(near, list.size, page) { if (near && err == null) load() }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("←", fontSize = 22.sp, color = HText, modifier = Modifier.clickable(onClick = onBack).padding(end = 14.dp))
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = HText)
        }
        LazyVerticalGrid(GridCells.Adaptive(110.dp), Modifier.fillMaxSize(), state = grid,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(list, key = { it.id }) { Poster(it, { onOpen(it) }) }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) {
                    when {
                        busy -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = HAccent)
                        err != null -> Text("$err  ·  Tap to retry", color = HMuted, fontSize = 12.sp, modifier = Modifier.clickable { err = null; load() })
                        !more && list.isEmpty() -> Text("Nothing found.", color = HMuted)
                    }
                }
            }
        }
    }
}
