package com.hayate.core.kitsu

import com.hayate.core.anilist.AnimeMedia
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/** Kitsu browse for the Global catalogue. Chunk c covers raw positions [c*LIMIT, c*LIMIT+LIMIT). */
object Kitsu {
    const val LIMIT = 20
    private const val BASE = "https://kitsu.io/api/edge/anime"
    private const val TTL_MS = 10 * 60 * 1000L

    class Chunk(val items: List<AnimeMedia?>, val total: Int)

    private val cache = ConcurrentHashMap<String, Pair<Long, Chunk>>()

    fun getChunk(c: Int, text: String = "", cats: List<String> = emptyList()): Chunk {
        val key = "$c|${text.lowercase()}|${cats.joinToString(",")}"
        cache[key]?.let { if (System.currentTimeMillis() - it.first < TTL_MS) return it.second }
        val ch = fetch(c * LIMIT, text, cats)
        if (cache.size > 400) cache.clear()
        cache[key] = System.currentTimeMillis() to ch
        return ch
    }

    private fun JSONObject.str(k: String): String? = if (isNull(k)) null else optString(k).ifEmpty { null }

    private fun fetch(offset: Int, text: String, cats: List<String>): Chunk {
        val p = linkedMapOf(
            "page[limit]" to "$LIMIT", "page[offset]" to "$offset", "include" to "mappings",
            "fields[anime]" to "canonicalTitle,titles,posterImage,mappings,ageRating,nsfw,synopsis,episodeCount,startDate,subtype,averageRating",
            "fields[mappings]" to "externalSite,externalId",
        )
        if (text.isEmpty()) p["sort"] = "-userCount" else p["filter[text]"] = text
        if (cats.isNotEmpty()) p["filter[categories]"] = cats.joinToString(",")
        val q = p.entries.joinToString("&") { URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8") }
        val con = URL("$BASE?$q").openConnection() as HttpURLConnection
        con.connectTimeout = 12000; con.readTimeout = 12000
        con.setRequestProperty("Accept", "application/vnd.api+json")
        try {
            if (con.responseCode != 200) throw IOException("Kitsu HTTP ${con.responseCode}")
            val d = JSONObject(con.inputStream.bufferedReader().readText())
            val al = HashMap<String, String>()
            d.optJSONArray("included")?.let { inc ->
                for (i in 0 until inc.length()) {
                    val m = inc.getJSONObject(i); val a = m.optJSONObject("attributes") ?: continue
                    if (a.str("externalSite") == "anilist/anime") a.str("externalId")?.let { al[m.optString("id")] = it }
                }
            }
            val out = ArrayList<AnimeMedia?>()
            val data = d.optJSONArray("data")
            if (data != null) for (i in 0 until data.length()) {
                val a = data.getJSONObject(i); val at = a.optJSONObject("attributes") ?: JSONObject()
                val refs = a.optJSONObject("relationships")?.optJSONObject("mappings")?.optJSONArray("data")
                var aid: String? = null
                if (refs != null) for (j in 0 until refs.length()) {
                    val r = al[refs.getJSONObject(j).optString("id")]; if (r != null) { aid = r; break }
                }
                val poster = at.optJSONObject("posterImage")?.str("small")
                if (aid == null || poster == null || at.optBoolean("nsfw") || at.str("ageRating") == "R18") { out.add(null); continue }
                val t = at.optJSONObject("titles")
                val sd = at.str("startDate") ?: ""
                out.add(AnimeMedia(
                    id = aid.toInt(),
                    title = t?.str("en") ?: at.str("canonicalTitle") ?: t?.str("en_jp") ?: "Unknown Title",
                    coverUrl = poster, description = at.str("synopsis") ?: "",
                    format = at.str("subtype")?.uppercase(),
                    episodes = if (at.isNull("episodeCount")) 1 else at.optInt("episodeCount", 1),
                    averageScore = at.str("averageRating")?.toDoubleOrNull()?.roundToInt(),
                    seasonYear = sd.take(4).toIntOrNull(),
                ))
            }
            return Chunk(out, d.optJSONObject("meta")?.optInt("count", 0) ?: 0)
        } finally { con.disconnect() }
    }
}
