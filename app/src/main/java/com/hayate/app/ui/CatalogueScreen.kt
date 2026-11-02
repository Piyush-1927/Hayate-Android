package com.hayate.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hayate.core.anilist.AnimeMedia
import com.hayate.core.kitsu.Kitsu
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import com.hayate.app.Prefs
import com.hayate.core.anilist.CatalogueApi
import com.hayate.core.anilist.CatalogueMatch
import com.hayate.app.stream.CatalogueTitles
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val COLS = 3
private const val PER_PAGE = 8 * COLS

@Composable
fun CatalogueScreen(query: String, topPad: Dp = 0.dp, onOpenId: (Int) -> Unit) {
    var mode by rememberSaveable { mutableIntStateOf(0) } // 0 In-App, 1 Global
    var applied by remember { mutableStateOf("") }
    LaunchedEffect(query) { kotlinx.coroutines.delay(450); applied = query.trim() }
    val genres = remember { mutableStateListOf<Set<String>>(emptySet(), emptySet()) } // kept per mode
    var panel by remember { mutableStateOf(false) }
    val header: @Composable () -> Unit = { Column {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Catalogue", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = HText, modifier = Modifier.weight(1f))
            val n = genres[mode].size
            val hot = n > 0 || panel
            Text(if (n > 0) "Filter · $n" else "Filter", fontSize = 13.sp, fontWeight = FontWeight.Medium,
                color = if (hot) HOnAccent else HMuted,
                modifier = Modifier.padding(end = 8.dp).clip(RoundedCornerShape(10.dp))
                    .background(if (hot) HAccent else HMuted.copy(alpha = 0.15f))
                    .clickable { panel = !panel }.padding(horizontal = 12.dp, vertical = 7.dp))
            ModeToggle(mode) { mode = it }
        }
        if (panel) GenrePanel(genres[mode]) { genres[mode] = it }
    } }
    if (mode == 1) GlobalCatalogue(applied, genres[1], header, topPad, onOpenId)
    else InAppCatalogue(applied, genres[0], header, topPad, onOpenId)
}

@Composable
private fun ModeToggle(sel: Int, onPick: (Int) -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(10.dp)).background(HMuted.copy(alpha = 0.15f)).padding(3.dp)) {
        listOf("In-App", "Global").forEachIndexed { i, t ->
            val on = sel == i
            Text(t, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (on) HOnAccent else HMuted,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (on) HAccent else Color.Transparent)
                    .clickable { onPick(i) }.padding(horizontal = 14.dp, vertical = 6.dp))
        }
    }
}

private val GENRES = listOf(
    "Action" to "action", "Adventure" to "adventure", "Comedy" to "comedy", "Drama" to "drama",
    "Ecchi" to "ecchi", "Fantasy" to "fantasy", "Horror" to "horror", "Mahou Shoujo" to "magical-girl",
    "Mecha" to "mecha", "Music" to "music", "Mystery" to "mystery", "Psychological" to "psychological",
    "Romance" to "romance", "Sci-Fi" to "science-fiction", "Slice of Life" to "slice-of-life",
    "Sports" to "sports", "Supernatural" to "supernatural", "Thriller" to "thriller",
)

@Composable
private fun GenrePanel(sel: Set<String>, onChange: (Set<String>) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        GENRES.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { (name, _) ->
                    val on = name in sel
                    Text(name, fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 1,
                        color = if (on) HOnAccent else HText,
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                            .background(if (on) HAccent else HMuted.copy(alpha = 0.15f))
                            .clickable { onChange(if (on) sel - name else sel + name) }.padding(vertical = 7.dp))
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Text("Clear all", fontSize = 12.sp, color = if (sel.isEmpty()) HMuted.copy(alpha = 0.5f) else HAccent,
            modifier = Modifier.clickable(enabled = sel.isNotEmpty()) { onChange(emptySet()) }.padding(vertical = 6.dp))
    }
}

@Composable
private fun GlobalCatalogue(q: String, genres: Set<String>, header: @Composable () -> Unit, topPad: Dp, onOpenId: (Int) -> Unit) {
    val filled = remember(q, genres) { mutableStateListOf<AnimeMedia>() }
    var next by remember(q, genres) { mutableIntStateOf(0) }
    var total by remember(q, genres) { mutableIntStateOf(-1) }
    var page by remember(q, genres) { mutableIntStateOf(1) }
    var busy by remember(q, genres) { mutableStateOf(false) }
    var err by remember(q, genres) { mutableStateOf<String?>(null) }
    var retry by remember(q, genres) { mutableIntStateOf(0) }
    val grid = rememberLazyGridState()
    val cats = genres.mapNotNull { g -> GENRES.firstOrNull { it.first == g }?.second }.sorted()
    val exhausted = total >= 0 && next * Kitsu.LIMIT >= total

    // Fill the page from further down the results: titles without an AniList mapping are skipped, not left as gaps.
    LaunchedEffect(q, genres, page, retry) {
        busy = true; err = null
        try {
            while (filled.size < page * PER_PAGE && !(total >= 0 && next * Kitsu.LIMIT >= total)) {
                val ch = withContext(Dispatchers.IO) { Kitsu.getChunk(next, q, cats) }
                total = ch.total; next += 1
                val have = filled.map { it.id }.toSet()
                filled.addAll(ch.items.filterNotNull().filter { it.id !in have })
            }
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) { err = e.message ?: "Network error"
        } finally { busy = false }
    }
    LaunchedEffect(page) { grid.scrollToItem(0) }

    val shown = filled.drop((page - 1) * PER_PAGE).take(PER_PAGE)
    val hasNext = filled.size > page * PER_PAGE || !exhausted
    LazyVerticalGrid(GridCells.Fixed(COLS), Modifier.fillMaxSize(), state = grid,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topPad + 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) { header() }
        items(shown, key = { it.id }) { Poster(it, { onOpenId(it.id) }) }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                when {
                    busy -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = HAccent)
                    err != null -> Text("$err  ·  Tap to retry", color = HMuted, fontSize = 12.sp, modifier = Modifier.clickable { retry++ })
                    shown.isEmpty() -> Text("Nothing found.", color = HMuted)
                }
                Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("‹ Prev", color = if (page > 1) HAccent else HMuted.copy(alpha = 0.4f), fontSize = 14.sp,
                        modifier = Modifier.clickable(enabled = page > 1) { page-- }.padding(12.dp))
                    Text("Page $page", color = HText, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text("Next ›", color = if (hasNext && !busy) HAccent else HMuted.copy(alpha = 0.4f), fontSize = 14.sp,
                        modifier = Modifier.clickable(enabled = hasNext && !busy) { page++ }.padding(12.dp))
                }
            }
        }
    }
}


/** In-App catalogue: ids come from the bundled seed (desktop's yt-dlp channel titles matched to AniList). */
private object InAppData {
    val all = mutableStateListOf<AnimeMedia>()
    var done by mutableStateOf(false)
    var err by mutableStateOf<String?>(null)
    private var running = false
    private const val TTL = 24 * 3600 * 1000L

    private fun seedIds(ctx: Context): List<Int> {
        val o = JSONObject(ctx.assets.open("catalogue_seed.json").bufferedReader().use { it.readText() })
        val titles = o.getJSONArray("titles"); val ids = o.getJSONObject("ids")
        val seen = LinkedHashSet<Int>()
        for (i in 0 until titles.length()) { val t = titles.getString(i); if (ids.has(t) && !ids.isNull(t)) seen.add(ids.getInt(t)) }
        return seen.toList()
    }

    private fun readCache(f: File, n: Int): Pair<Boolean, JSONArray>? = try {
        val o = JSONObject(f.readText())
        if (o.optInt("n") != n) null else (System.currentTimeMillis() - o.optLong("t") < TTL) to o.getJSONArray("media")
    } catch (e: Exception) { null }

    private fun add(raw: JSONArray, order: Map<Int, Int>) {
        val have = all.map { it.id }.toSet()
        all.addAll(CatalogueApi.parse(raw).filter { (Prefs.allowAdult || !it.isAdult) && it.id !in have }
            .sortedBy { order[it.id] ?: Int.MAX_VALUE })
    }

    private var extending = false
    private var extended = false

    private suspend fun fetchNew(ids: List<Int>) {
        for (c in ids.chunked(50)) add(withContext(Dispatchers.IO) { CatalogueApi.rawByIds(c) }, emptyMap())
    }

    /** Titles missing from the bundled seed: look them up on AniList once, remember the result on the device. */
    suspend fun extend(ctx: Context) {
        if (extending || extended) return
        extending = true
        try {
            val seed = withContext(Dispatchers.IO) {
                JSONObject(ctx.assets.open("catalogue_seed.json").bufferedReader().use { it.readText() }).getJSONObject("ids")
            }
            val titles = withContext(Dispatchers.IO) { CatalogueTitles.get(ctx) }
            val mf = File(ctx.filesDir, "catalogue_matches.json")
            val matches = withContext(Dispatchers.IO) { try { JSONObject(mf.readText()) } catch (e: Exception) { JSONObject() } }
            val known = all.map { it.id }.toMutableSet()
            fun fresh(v: Collection<Int>) = v.filter { it > 0 && known.add(it) }
            fetchNew(fresh(matches.keys().asSequence().map { matches.getInt(it) }.toList()))
            val groups = LinkedHashMap<String, MutableList<String>>()
            for (t in titles) {
                if (seed.has(t) || matches.has(t)) continue
                val c = CatalogueMatch.clean(t)
                if (!CatalogueMatch.hasLatin(c)) matches.put(t, -1) else groups.getOrPut(c) { mutableListOf() }.add(t)
            }
            for (chunk in groups.keys.toList().chunked(24)) {
                val res = withContext(Dispatchers.IO) { CatalogueMatch.resolve(chunk) }
                for (c in chunk) for (t in groups[c]!!) matches.put(t, res[c] ?: -1)
                withContext(Dispatchers.IO) { mf.writeText(matches.toString()) }
                fetchNew(fresh(chunk.mapNotNull { res[it] }))
            }
            withContext(Dispatchers.IO) { mf.writeText(matches.toString()) }
            extended = true
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) { /* offline: keep what we have, retried next time */
        } finally { extending = false }
    }

    suspend fun load(ctx: Context) {
        if (running || done) return
        running = true; err = null
        try {
            val ids = withContext(Dispatchers.IO) { seedIds(ctx) }
            val order = ids.withIndex().associate { it.value to it.index }
            val f = File(ctx.cacheDir, "catalogue_inapp.json")
            val cached = withContext(Dispatchers.IO) { readCache(f, ids.size) }
            all.clear()
            if (cached != null && cached.first) { add(cached.second, order); done = true; return }
            val acc = JSONArray()
            try {
                for (chunk in ids.chunked(50)) {          // 50 ids per request, cards appear as each chunk lands
                    val raw = withContext(Dispatchers.IO) { CatalogueApi.rawByIds(chunk) }
                    for (i in 0 until raw.length()) acc.put(raw.get(i))
                    add(raw, order)
                }
                withContext(Dispatchers.IO) {
                    f.writeText(JSONObject().put("t", System.currentTimeMillis()).put("n", ids.size).put("media", acc).toString())
                }
                done = true
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                if (cached != null) { all.clear(); add(cached.second, order); done = true }  // offline: stale cache
                else throw e
            }
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) { err = e.message ?: "Network error"
        } finally { running = false }
    }
}

@Composable
private fun InAppCatalogue(q: String, genres: Set<String>, header: @Composable () -> Unit, topPad: Dp, onOpenId: (Int) -> Unit) {
    val ctx = LocalContext.current.applicationContext
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(retry) { InAppData.load(ctx) }
    LaunchedEffect(InAppData.done) { if (InAppData.done) InAppData.extend(ctx) }
    var page by remember(q, genres) { mutableIntStateOf(1) }
    val grid = rememberLazyGridState()
    val ql = q.lowercase()
    val count = InAppData.all.size
    val filtered = remember(q, genres, count) {
        InAppData.all.filter { m ->
            (genres.isEmpty() || genres.all { it in m.genres }) &&
                (ql.isEmpty() || m.title.lowercase().contains(ql) || m.synonyms.any { it.lowercase().contains(ql) })
        }
    }
    LaunchedEffect(page) { grid.scrollToItem(0) }
    val shown = filtered.drop((page - 1) * PER_PAGE).take(PER_PAGE)
    val loading = !InAppData.done && InAppData.err == null
    val hasNext = filtered.size > page * PER_PAGE
    LazyVerticalGrid(GridCells.Fixed(COLS), Modifier.fillMaxSize(), state = grid,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topPad + 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) { header() }
        items(shown, key = { it.id }) { Poster(it, { onOpenId(it.id) }) }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                when {
                    loading -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = HAccent)
                    InAppData.err != null -> Text("${InAppData.err}  ·  Tap to retry", color = HMuted, fontSize = 12.sp, modifier = Modifier.clickable { retry++ })
                    shown.isEmpty() -> Text("Nothing found.", color = HMuted)
                }
                Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("‹ Prev", color = if (page > 1) HAccent else HMuted.copy(alpha = 0.4f), fontSize = 14.sp,
                        modifier = Modifier.clickable(enabled = page > 1) { page-- }.padding(12.dp))
                    Text("Page $page", color = HText, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text("Next ›", color = if (hasNext) HAccent else HMuted.copy(alpha = 0.4f), fontSize = 14.sp,
                        modifier = Modifier.clickable(enabled = hasNext) { page++ }.padding(12.dp))
                }
            }
        }
    }
}
