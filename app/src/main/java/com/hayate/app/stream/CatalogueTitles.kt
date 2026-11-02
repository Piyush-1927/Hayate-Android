package com.hayate.app.stream

import android.content.Context
import com.hayate.core.anilist.CatalogueMatch
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import org.json.JSONObject
import java.io.File

/** Series titles from the official channels' playlists, cached 24h (same source as desktop). */
object CatalogueTitles {
    private val CHANNELS = listOf("https://www.youtube.com/@MuseAsia", "https://www.youtube.com/@AniOneAsia")
    private const val TTL = 24 * 3600 * 1000L

    fun get(ctx: Context): List<String> {
        val f = File(ctx.filesDir, "catalogue_titles.json")
        val prev = try { JSONObject(f.readText()) } catch (e: Exception) { null }
        val old = prev?.optJSONArray("titles")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
        if (prev != null && old.isNotEmpty() && System.currentTimeMillis() - prev.optLong("t") < TTL) return old
        YtProvider.warmUp(ctx)
        val keys = HashSet<String>(); val out = ArrayList<String>(); var failed = false
        for (url in CHANNELS) {
            try {
                val req = YoutubeDLRequest("$url/playlists").apply { addOption("--flat-playlist"); addOption("-J"); addOption("--no-warnings") }
                val entries = JSONObject(YoutubeDL.getInstance().execute(req).out).optJSONArray("entries") ?: continue
                for (i in 0 until entries.length()) {
                    val e = entries.optJSONObject(i) ?: continue
                    val t = CatalogueMatch.channelTitle(if (e.isNull("title")) "" else e.optString("title")) ?: continue
                    if (keys.add(t.lowercase())) out.add(t)
                }
            } catch (e: Exception) { failed = true }
        }
        if (failed) for (t in old) if (keys.add(t.lowercase())) out.add(t)  // keep known titles if a channel failed
        if (out.isNotEmpty() && !(failed && old.isNotEmpty() && out.size <= old.size)) {
            f.writeText(JSONObject().put("t", System.currentTimeMillis()).put("titles", org.json.JSONArray(out)).toString())
        }
        return if (out.isNotEmpty()) out else old
    }
}
