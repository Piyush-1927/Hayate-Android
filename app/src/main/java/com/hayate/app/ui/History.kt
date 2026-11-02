package com.hayate.app.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.hayate.core.anilist.AnimeMedia
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class HistEntry(val id: Int, val title: String, val cover: String, val watched: Int, val last: Int,
                     val total: Int, val updated: Long, val percent: Float)

object HistoryStore {
    private lateinit var file: File
    private var data = JSONObject()
    var items by mutableStateOf<List<HistEntry>>(emptyList())
        private set

    fun init(c: Context) {
        if (::file.isInitialized) return
        file = File(c.applicationContext.filesDir, "history.json")
        try { data = JSONObject(file.readText()) }
        catch (e: Exception) { if (file.exists()) file.renameTo(File(file.parentFile, "history.json.corrupt")) }
        refresh()
    }

    private fun toEntry(o: JSONObject): HistEntry {
        val w = o.optJSONArray("watched_episodes")
        val watched = if (w == null) emptyList() else (0 until w.length()).map { w.optInt(it) }
        val total = o.optInt("total_episodes", 0)
        val last = o.optInt("last_episode", 0)
        val pr = o.optJSONObject("progress")?.optJSONObject(last.toString())
        val inProg = last != 0 && pr != null && pr.optDouble("dur") > 0
        val done = if (watched.isNotEmpty()) watched.size - (if (inProg && last in watched) 1 else 0) else maxOf(last - 1, 0)
        val frac = if (inProg) minOf(pr!!.optDouble("pos") / pr.optDouble("dur"), 1.0) else 0.0
        val pct = ((done + frac) / maxOf(total, last, 1)).coerceIn(0.0, 1.0)
        return HistEntry(o.optInt("id"), o.optString("title"), o.optString("cover_url"), watched.size, last, total,
            o.optLong("updated_at"), pct.toFloat())
    }

    private fun refresh() {
        items = data.keys().asSequence().map { toEntry(data.getJSONObject(it)) }.sortedByDescending { it.updated }.toList()
    }

    private fun save() {
        val tmp = File(file.parentFile, "history.json.tmp")
        tmp.writeText(data.toString(2)); tmp.renameTo(file)
    }

    private fun backup() { try { if (file.exists()) file.copyTo(File(file.parentFile, "history.json.bak"), overwrite = true) } catch (e: Exception) { } }

    private fun addEp(e: JSONObject, ep: Int) {
        val w = e.optJSONArray("watched_episodes") ?: JSONArray().also { e.put("watched_episodes", it) }
        val set = sortedSetOf<Int>()
        for (i in 0 until w.length()) set.add(w.optInt(i))
        if (set.add(ep)) e.put("watched_episodes", JSONArray(set.toList()))
    }

    fun markWatched(m: AnimeMedia, ep: Int, total: Int) {
        val key = m.id.toString()
        val e = data.optJSONObject(key) ?: JSONObject().put("id", m.id).put("watched_episodes", JSONArray()).put("last_episode", ep)
        addEp(e, ep)
        e.put("last_episode", ep).put("title", m.displayTitle)
        if (m.coverUrl.isNotEmpty()) e.put("cover_url", m.coverUrl)
        if (m.bannerUrl.isNotEmpty()) e.put("banner_url", m.bannerUrl)
        if (total > 0) e.put("total_episodes", total)
        e.put("updated_at", System.currentTimeMillis() / 1000)
        data.put(key, e); save(); refresh()
    }

    fun setProgress(id: Int, ep: Int, pos: Double, dur: Double) {
        val e = data.optJSONObject(id.toString()) ?: return
        if (dur <= 0 || pos < 0) return
        val prog = e.optJSONObject("progress") ?: JSONObject().also { e.put("progress", it) }
        val k = ep.toString()
        if (pos >= dur * 0.9) { prog.remove(k); e.put("done_ep", ep); addEp(e, ep) }
        else if (pos >= 10) {
            prog.put(k, JSONObject().put("pos", Math.round(pos * 10) / 10.0).put("dur", Math.round(dur * 10) / 10.0))
            if (e.optInt("done_ep", -1) == ep) e.remove("done_ep")
        } else return
        save(); refresh()
    }

    fun progress(id: Int, ep: Int): Pair<Double, Double>? {
        val p = data.optJSONObject(id.toString())?.optJSONObject("progress")?.optJSONObject(ep.toString()) ?: return null
        return if (p.optDouble("dur") > 0 && p.optDouble("pos") > 0) p.optDouble("pos") to p.optDouble("dur") else null
    }

    /** Episode to resume for a title, and whether to restart it from 0:00. */
    fun resume(id: Int, fallbackTotal: Int = 0): Pair<Int, Boolean> {
        val e = items.firstOrNull { it.id == id } ?: return 1 to false
        if (e.last < 1) return 1 to false
        val pr = progress(id, e.last)
        val done = pr != null && pr.second > 0 && pr.first / pr.second >= 0.99
        if (!done) return e.last to false
        val total = if (e.total > 0) e.total else fallbackTotal
        return if (total <= 0 || e.last < total) (e.last + 1) to false else e.last to true
    }

    fun exportJson(): String = data.toString(2)
    fun importJson(text: String, replace: Boolean): Int {
        val src = JSONObject(text)
        backup()
        if (replace) data = JSONObject()
        var n = 0
        for (k in src.keys().asSequence().toList()) {
            val inc = src.optJSONObject(k) ?: continue
            val cur = data.optJSONObject(k)
            if (cur == null) { data.put(k, inc); n++; continue }
            inc.optJSONArray("watched_episodes")?.let { w -> for (i in 0 until w.length()) addEp(cur, w.getInt(i)) }
            inc.optJSONObject("progress")?.let { ip ->
                val cp = cur.optJSONObject("progress") ?: JSONObject().also { cur.put("progress", it) }
                for (ek in ip.keys().asSequence().toList()) if (!cp.has(ek)) cp.put(ek, ip.get(ek))
            }
            n++
        }
        save(); refresh()
        return n
    }
    fun remove(id: Int) { backup(); data.remove(id.toString()); save(); refresh() }
    fun exportData(): JSONObject = JSONObject(data.toString())
    private fun score(e: JSONObject): DoubleArray {
        val last = e.optInt("last_episode", 0)
        val done = if (last > 0 && e.optInt("done_ep", -1) == last) 1.0 else 0.0
        val pos = e.optJSONObject("progress")?.optJSONObject(last.toString())?.optDouble("pos", 0.0) ?: 0.0
        return doubleArrayOf(last.toDouble(), done, pos, e.optLong("updated_at").toDouble())
    }
    private fun moreProgress(a: JSONObject, b: JSONObject): Boolean {
        val x = score(a); val y = score(b)
        for (i in x.indices) if (x[i] != y[i]) return x[i] > y[i]
        return false
    }
    fun mergeRemote(src: JSONObject): Int {
        val keys = src.keys().asSequence().toList()
        if (keys.isEmpty()) return 0
        backup()
        var n = 0
        for (k in keys) {
            val inc = src.optJSONObject(k) ?: continue
            val cur = data.optJSONObject(k)
            if (cur == null) { data.put(k, inc); n++; continue }
            var ch = false
            val before = cur.optJSONArray("watched_episodes")?.length() ?: 0
            inc.optJSONArray("watched_episodes")?.let { w -> for (i in 0 until w.length()) addEp(cur, w.getInt(i)) }
            if ((cur.optJSONArray("watched_episodes")?.length() ?: 0) != before) ch = true
            val incWins = moreProgress(inc, cur)
            inc.optJSONObject("progress")?.let { ip ->
                val cp = cur.optJSONObject("progress") ?: JSONObject().also { cur.put("progress", it) }
                for (ek in ip.keys().asSequence().toList()) {
                    val iv = ip.optJSONObject(ek) ?: continue
                    val old = cp.optJSONObject(ek)
                    if (old == null || iv.optDouble("pos") > old.optDouble("pos")) { cp.put(ek, iv); ch = true }
                }
            }
            if (incWins) {
                for (f in listOf("title", "cover_url", "banner_url", "last_episode", "updated_at", "total_episodes"))
                    if (inc.has(f)) cur.put(f, inc.get(f))
                if (inc.has("done_ep")) cur.put("done_ep", inc.get("done_ep")) else cur.remove("done_ep")
                val d = cur.optInt("done_ep", -1)
                if (d > 0) cur.optJSONObject("progress")?.remove(d.toString())
                ch = true
            }
            if (ch) n++
        }
        save(); refresh()
        return n
    }
    fun replaceAll(src: JSONObject) { backup(); data = JSONObject(src.toString()); save(); refresh() }
    fun clear() { backup(); data = JSONObject(); save(); refresh() }
}

private fun dayKey(ts: Long): Long = (ts * 1000 + TimeZone.getDefault().getOffset(ts * 1000)) / 86_400_000L

@Composable
private fun HistCard(e: HistEntry, onOpen: () -> Unit, onRemove: (() -> Unit)?) {
    Column(Modifier.clickable(onClick = onOpen)) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp)).background(HCard)) {
            AsyncImage(model = e.cover, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            if (onRemove != null) Text("✕", color = Color.White, fontSize = 11.sp,
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).background(Color(0xAA000000), CircleShape)
                    .clickable(onClick = onRemove).padding(horizontal = 8.dp, vertical = 4.dp))
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color(0x66000000))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(e.percent).background(HAccent))
            }
        }
        Text(e.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = HText, modifier = Modifier.padding(top = 6.dp))
        Text(if (e.total > 0) "Ep ${e.last} of ${e.total}" else "Ep ${e.last}", fontSize = 10.sp, color = HMuted)
    }
}

@Composable
fun HistoryScreen(query: String, onOpenId: (Int) -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    val all = HistoryStore.items
    val q = query.trim()
    val list = if (q.isEmpty()) all else all.filter { it.title.contains(q, ignoreCase = true) }
    val today = dayKey(System.currentTimeMillis() / 1000)
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Watch History", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("${all.size} anime · ${all.sumOf { it.watched }} episodes", color = HMuted, fontSize = 11.sp, modifier = Modifier.padding(start = 10.dp).weight(1f))
            if (all.isNotEmpty()) Text("Clear all", color = HAccent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { confirm = true }.padding(6.dp))
        }
        if (list.isEmpty()) Text(if (all.isEmpty()) "Nothing watched yet." else "No matches.", color = HMuted)
        LazyVerticalGrid(GridCells.Adaptive(110.dp), Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            list.groupBy { dayKey(it.updated) }.forEach { (k, es) ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(when (k) { today -> "Today"; today - 1 -> "Yesterday"
                        else -> SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(es.first().updated * 1000)) },
                        fontSize = 14.sp, fontWeight = FontWeight.Bold, color = HAccent)
                }
                gridItems(es) { e -> HistCard(e, { onOpenId(e.id) }, { HistoryStore.remove(e.id) }) }
            }
        }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, containerColor = HCard,
        title = { Text("Clear all history?") }, text = { Text("A backup copy is kept.", color = HMuted) },
        confirmButton = { TextButton({ HistoryStore.clear(); confirm = false }) { Text("Clear", color = HAccent) } },
        dismissButton = { TextButton({ confirm = false }) { Text("Cancel", color = HMuted) } })
}

@Composable
fun ContinueShelf(onOpenId: (Int) -> Unit, onViewAll: () -> Unit) {
    val list = HistoryStore.items.take(10)
    if (list.isEmpty()) return
    Column(Modifier.padding(top = 16.dp)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(3.dp).height(16.dp).background(HAccent))
            Text("Continue Watching", fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp).weight(1f))
            Text("View all ›", color = HAccent, fontSize = 12.sp, modifier = Modifier.clickable(onClick = onViewAll))
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(list) { e -> Box(Modifier.width(110.dp)) { HistCard(e, { onOpenId(e.id) }, null) } }
        }
    }
}
