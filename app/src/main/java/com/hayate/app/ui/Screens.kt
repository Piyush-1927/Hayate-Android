package com.hayate.app.ui

import com.hayate.app.Prefs

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.text.HtmlCompat
import coil3.compose.AsyncImage
import com.hayate.core.anilist.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.hayate.app.stream.StreamResult
import com.hayate.app.stream.YtProvider
import java.util.Calendar

@Composable
fun HomeScreen(onOpenId: (Int) -> Unit = {}, onHistory: () -> Unit = {}, onShelf: (String, String) -> Unit = { _, _ -> }, onOpen: (AnimeMedia) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { Repo.loadHome(ctx) }
    val h = Repo.home
    when {
        h == null && Repo.homeError != null ->
            ErrorBox(Repo.homeError!!) { scope.launch { Repo.homeError = null; Repo.loadHome(ctx, true) } }
        h == null -> Loading()
        else -> LazyColumn(Modifier.fillMaxSize()) {
            val s = h.shelves
            item { Spacer(Modifier.height(56.dp)) }
            item { Spotlight(s.spotlight, onOpen, onPlay = { onOpenId(it.id) }) }
            item { ContinueShelf(onOpenId, onHistory) }
            if (h.stale) item { Text("Offline: showing cached data", color = HMuted, fontSize = 12.sp, modifier = Modifier.padding(16.dp, 8.dp)) }
            item { Shelf("Trending Now", s.trending, onOpen) { onShelf("trending", "Trending Now") } }
            item { Shelf("Popular", s.popular, onOpen) { onShelf("popular", "Popular") } }
            item { Shelf("New Releases", s.newReleases, onOpen) { onShelf("new_releases", "New Releases") } }
            item { Shelf("Just Finished", s.justFinished, onOpen) { onShelf("just_finished", "Just Finished") } }
            item { Shelf("Upcoming", s.upcoming, onOpen) { onShelf("upcoming", "Upcoming") } }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
fun ScheduleScreen(onOpenId: (Int) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { Repo.loadSchedule(ctx) }
    val sch = Repo.schedule
    var sel by remember { mutableIntStateOf((Calendar.getInstance().get(Calendar.DAY_OF_WEEK) + 5) % 7) }
    if (sch == null) {
        if (Repo.scheduleError != null)
            ErrorBox(Repo.scheduleError!!) { scope.launch { Repo.scheduleError = null; Repo.loadSchedule(ctx, true) } }
        else Loading()
        return
    }
    Column(Modifier.fillMaxSize()) {
        Text("Schedule", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
            AniListSchedule.DAYS.forEachIndexed { i, d ->
                val on = i == sel
                Box(Modifier.weight(1f).padding(2.dp).clip(RoundedCornerShape(20.dp))
                    .background(if (on) HAccent else HCard).clickable { sel = i }.padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center) {
                    Text(d.take(3), color = if (on) HOnAccent else HMuted, fontSize = 12.sp)
                }
            }
        }
        val list = sch[AniListSchedule.DAYS[sel]].orEmpty()
        if (list.isEmpty()) Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Nothing airing", color = HMuted) }
        else LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(list) { e ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(HCard).border(1.dp, HBorder, RoundedCornerShape(20.dp))
                    .clickable { onOpenId(e.id) }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(model = e.coverUrl, contentDescription = null,
                        modifier = Modifier.size(52.dp, 74.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(e.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("Episode ${e.episode}", color = HMuted, fontSize = 12.sp)
                    }
                    Text(e.time, color = HAccent, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun SearchScreen(query: String, onOpen: (AnimeMedia) -> Unit) {
    var res by remember { mutableStateOf<List<AnimeMedia>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf(false) }
    LaunchedEffect(query) {
        if (query.isBlank()) { res = emptyList(); err = null; done = false; return@LaunchedEffect }
        busy = true; done = false
        try {
            res = withContext(Dispatchers.IO) { AniListSmartSearch.search(query, allowAdult = Prefs.allowAdult).items }; err = null; done = true
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) { err = e.message ?: "Search failed"
        } finally { busy = false }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp), color = HAccent)
        err?.let { Text(it, color = HMuted, modifier = Modifier.padding(top = 8.dp)) }
        if (done && res.isEmpty()) Text("No results for \"$query\"", color = HMuted, modifier = Modifier.padding(top = 12.dp))
        LazyVerticalGrid(GridCells.Adaptive(110.dp), Modifier.fillMaxSize().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            gridItems(res) { Poster(it, { onOpen(it) }) }
        }
    }
}

@Composable
fun DetailScreen(m: AnimeMedia, onBack: () -> Unit, onStream: (StreamResult) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val desc = remember(m.id) { HtmlCompat.fromHtml(m.description, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim() }
    var lang by remember { mutableStateOf(com.hayate.app.Prefs.preferredAudio) }
    var busy by remember { mutableIntStateOf(0) }
    var notice by remember { mutableStateOf("") }
    val total = m.airingEpisode?.let { maxOf(1, it - 1) } ?: m.episodes
    val unreleased = m.status == "NOT_YET_RELEASED"
    fun play(ep: Int) {
        if (busy != 0) return
        busy = ep; notice = ""
        scope.launch {
            try {
                val o = withContext(Dispatchers.IO) { YtProvider.getStream(ctx.applicationContext, m.displayTitle, ep, lang) }
                val s = o.stream
                if (s != null) onStream(s) else notice = o.notice
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { notice = e.message ?: "Failed to load stream"
            } finally { busy = 0 }
        }
    }
    if (androidx.compose.ui.platform.LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
        DetailLandscape(m, desc, total, unreleased, lang, { lang = it }, busy, notice, onBack, { play(it) })
        return
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(250.dp)) {
            AsyncImage(model = m.bannerUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x66000000), HBg))))
            Text("←  Back", color = Color.White, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.statusBarsPadding().clickable { onBack() }.padding(16.dp))
        }
        Column(Modifier.padding(16.dp)) {
            if (unreleased) Box(Modifier.padding(bottom = 8.dp).clip(RoundedCornerShape(6.dp)).background(Color(0x33F59E0B))
                .padding(horizontal = 8.dp, vertical = 4.dp)) {
                Text("UPCOMING • NOT YET AIRED", color = Color(0xFFF59E0B), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Text(m.displayTitle, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(meta(m), color = HMuted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp))
            Text(desc, fontSize = 14.sp, lineHeight = 21.sp, maxLines = 6, overflow = TextOverflow.Ellipsis)
            LibraryBar(m)
            if (unreleased) {
                Column(Modifier.padding(top = 20.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(HCard).padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Broadcasting Schedule Pending", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("Episodes and streaming channels will appear here once official release dates begin.",
                        color = HMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            } else {
            Text("Episodes", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
            Row(Modifier.padding(bottom = 8.dp)) {
                listOf("sub" to "Sub", "eng" to "English dub", "hin" to "Hindi dub").forEach { (k, l) ->
                    val on = lang == k
                    Box(Modifier.padding(end = 8.dp).clip(RoundedCornerShape(20.dp)).background(if (on) HAccent else HCard)
                        .clickable { lang = k }.padding(horizontal = 14.dp, vertical = 8.dp)) {
                        Text(l, fontSize = 12.sp, color = if (on) HOnAccent else HMuted)
                    }
                }
            }
            if (notice.isNotEmpty()) Text(notice, color = HMuted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 6.dp))
            (1..total).chunked(5).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    row.forEach { ep ->
                        Box(Modifier.weight(1f).padding(2.dp).clip(RoundedCornerShape(16.dp)).background(HCard)
                            .clickable { play(ep) }.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                            if (busy == ep) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = HAccent)
                            else Text("$ep", fontSize = 14.sp)
                        }
                    }
                    repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            }
            if (m.relations.isNotEmpty()) {
                Text("Connected Seasons & Relations", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 24.dp, bottom = 10.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    m.relations.forEach { r ->
                        Column(Modifier.padding(end = 10.dp).size(110.dp, 215.dp).clip(RoundedCornerShape(12.dp))
                            .background(HCard).padding(6.dp)) {
                            AsyncImage(model = r.coverUrl, contentDescription = null, contentScale = ContentScale.Crop,
                                modifier = Modifier.size(98.dp, 140.dp).clip(RoundedCornerShape(8.dp)))
                            Text(r.relationType.replace("_", " "), color = HAccent, fontSize = 9.sp,
                                fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                            Text(r.title, fontSize = 11.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
