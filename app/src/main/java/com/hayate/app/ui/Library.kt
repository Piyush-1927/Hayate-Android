package com.hayate.app.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hayate.core.anilist.AnimeMedia
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class LibEntry(val id: Int, val title: String, val cover: String, val totalEps: Int?,
                    val status: String?, val favorite: Boolean, val added: Long, val updated: Long)

private val LIB_TABS = listOf("Watching" to "watching", "Plan to Watch" to "plan_to_watch", "Watch Later" to "watch_later",
    "Completed" to "completed", "On Hold" to "on_hold", "Dropped" to "dropped", "♥ Favorites" to "fav")
private val LIB_LABELS = LIB_TABS.toMap().entries.associate { it.value to it.key }

object LibraryStore {
    private lateinit var file: File
    var items by mutableStateOf<List<LibEntry>>(emptyList())
        private set

    fun init(c: Context) {
        if (::file.isInitialized) return
        file = File(c.applicationContext.filesDir, "library.json")
        try {
            val a = JSONArray(file.readText())
            items = (0 until a.length()).map { a.getJSONObject(it) }.map { o ->
                LibEntry(o.getInt("anilist_id"), o.optString("title"), o.optString("cover_url"),
                    if (o.isNull("total_eps")) null else o.optInt("total_eps"),
                    if (o.isNull("status")) null else o.optString("status"),
                    o.optInt("favorite", 0) != 0, o.optLong("added_at"), o.optLong("updated_at"))
            }
        } catch (e: Exception) { }
    }

    private fun save() {
        val a = JSONArray()
        items.forEach { e ->
            a.put(JSONObject().put("anilist_id", e.id).put("title", e.title).put("cover_url", e.cover)
                .put("total_eps", e.totalEps ?: JSONObject.NULL).put("status", e.status ?: JSONObject.NULL)
                .put("favorite", if (e.favorite) 1 else 0).put("added_at", e.added).put("updated_at", e.updated))
        }
        val tmp = File(file.parentFile, "library.json.tmp")
        tmp.writeText(a.toString()); tmp.renameTo(file)
    }

    fun exportRows(): JSONArray {
        val a = JSONArray()
        items.filter { it.title.isNotBlank() }.forEach { e ->
            a.put(JSONObject().put("anilist_id", e.id).put("title", e.title).put("cover_url", e.cover)
                .put("total_eps", e.totalEps ?: JSONObject.NULL).put("status", e.status ?: JSONObject.NULL)
                .put("favorite", if (e.favorite) 1 else 0).put("added_at", e.added).put("updated_at", e.updated))
        }
        return a
    }
    fun mergeRows(a: JSONArray): Int {
        var n = 0
        val list = items.toMutableList()
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            if (!o.has("anilist_id") || o.optString("title").isBlank()) continue
            val id = o.optInt("anilist_id")
            val upd = o.optDouble("updated_at", 0.0).toLong()
            val cur = list.firstOrNull { it.id == id }
            if (cur != null && upd <= cur.updated) continue
            val e = LibEntry(id, o.optString("title"), if (o.isNull("cover_url")) "" else o.optString("cover_url"),
                if (o.isNull("total_eps")) null else o.optInt("total_eps"),
                if (o.isNull("status")) null else o.optString("status"),
                o.optInt("favorite", 0) != 0, o.optDouble("added_at", 0.0).toLong(), upd)
            list.removeAll { it.id == id }; list.add(0, e); n++
        }
        if (n > 0) { items = list; save() }
        return n
    }
    fun replaceRows(a: JSONArray) {
        items = emptyList()
        val r = JSONArray(); for (i in a.length() - 1 downTo 0) r.put(a.get(i))
        mergeRows(r); save()
    }
    fun get(id: Int) = items.find { it.id == id }

    /** null status/favorite keep the existing value (same as desktop set_entry). */
    fun set(m: AnimeMedia, status: String? = null, favorite: Boolean? = null) {
        val now = System.currentTimeMillis() / 1000
        val old = get(m.id)
        val e = LibEntry(m.id, m.displayTitle, m.coverUrl, m.episodes, status ?: old?.status,
            favorite ?: old?.favorite ?: false, old?.added ?: now, now)
        items = listOf(e) + items.filter { it.id != m.id }
        save()
    }

    fun clearStatus(id: Int) {
        val now = System.currentTimeMillis() / 1000
        items = items.map { if (it.id == id) it.copy(status = null, updated = now) else it }
            .filter { it.status != null || it.favorite }
        save()
    }
}

@Composable
fun LibraryBar(m: AnimeMedia) {
    val e = LibraryStore.get(m.id)
    var menu by remember { mutableStateOf(false) }
    val has = e?.status != null
    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box {
            Box(Modifier.clip(RoundedCornerShape(10.dp)).background(if (has) HAccent else HCard)
                .clickable { menu = true }.padding(horizontal = 14.dp, vertical = 9.dp)) {
                Text(if (has) "✓ ${LIB_LABELS[e!!.status]} ▾" else "+ Add to Library ▾", fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold, color = if (has) HOnAccent else HText)
            }
            DropdownMenu(menu, { menu = false }, containerColor = HCard) {
                LIB_TABS.filter { it.second != "fav" }.forEach { (l, k) ->
                    DropdownMenuItem(text = { Text(l) }, onClick = { LibraryStore.set(m, status = k); menu = false })
                }
                if (has) DropdownMenuItem(text = { Text("Remove from Library") }, onClick = { LibraryStore.clearStatus(m.id); menu = false })
            }
        }
        Text(if (e?.favorite == true) "♥" else "♡", fontSize = 22.sp, color = if (e?.favorite == true) HAccent else HMuted,
            modifier = Modifier.padding(start = 14.dp).clickable { LibraryStore.set(m, favorite = !(e?.favorite ?: false)) })
    }
}

@Composable
fun LibraryScreen(onOpenId: (Int) -> Unit) {
    var tab by remember { mutableStateOf("plan_to_watch") }
    val all = LibraryStore.items
    val list = if (tab == "fav") all.filter { it.favorite } else all.filter { it.status == tab }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text("Library", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 10.dp)) {
            LIB_TABS.forEach { (l, k) ->
                val on = tab == k
                Box(Modifier.padding(end = 8.dp).clip(RoundedCornerShape(20.dp)).background(if (on) HAccent else HCard)
                    .clickable { tab = k }.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Text(l, fontSize = 12.sp, color = if (on) HOnAccent else HMuted)
                }
            }
        }
        if (list.isEmpty()) Text("Nothing here yet.", color = HMuted, modifier = Modifier.padding(top = 8.dp))
        LazyVerticalGrid(GridCells.Adaptive(110.dp), Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            gridItems(list) { e -> Poster(AnimeMedia(id = e.id, title = e.title, coverUrl = e.cover), { onOpenId(e.id) }) }
        }
    }
}
