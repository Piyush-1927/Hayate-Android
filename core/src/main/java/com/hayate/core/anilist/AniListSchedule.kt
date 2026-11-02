package com.hayate.core.anilist

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class ScheduleEntry(
    val id: Int,
    val title: String,
    val episode: Int,
    val airingAt: Long,
    val time: String,
    val coverUrl: String,
    val format: String?,
    val isAdult: Boolean,
)

private fun JSONObject?.sOrNull(key: String): String? =
    if (this == null || isNull(key)) null else optString(key).ifEmpty { null }

/** All calls are blocking: run them off the main thread. */
object AniListSchedule {
    private const val D = "$"
    private const val CACHE_TTL_MS = 30 * 60 * 1000L
    val DAYS = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    private val FORMATS = setOf("TV", "TV_SHORT", "ONA")

    private val QUERY = """
query (${D}start: Int, ${D}end: Int, ${D}page: Int) {
  Page(page: ${D}page, perPage: 50) {
    pageInfo { hasNextPage }
    airingSchedules(airingAt_greater: ${D}start, airingAt_lesser: ${D}end, sort: TIME) {
      airingAt
      episode
      media {
        id
        title { romaji english }
        coverImage { large }
        format
        isAdult
        episodes
      }
    }
  }
}
"""

    /** Unix seconds for Monday 00:00 (local) to the following Monday. */
    fun weekBounds(nowMs: Long = System.currentTimeMillis()): Pair<Long, Long> {
        val c = Calendar.getInstance().apply {
            timeInMillis = nowMs
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        c.add(Calendar.DAY_OF_YEAR, -((c.get(Calendar.DAY_OF_WEEK) + 5) % 7))
        val start = c.timeInMillis / 1000
        c.add(Calendar.DAY_OF_YEAR, 7)
        return start to c.timeInMillis / 1000
    }

    fun buildSchedule(entries: List<JSONObject>, allowAdult: Boolean): Map<String, List<ScheduleEntry>> {
        val out = DAYS.associateWith { mutableListOf<ScheduleEntry>() }
        val seen = mutableSetOf<Pair<Int, Int>>()
        val fmt = SimpleDateFormat("HH:mm", Locale.US)
        for (e in entries) {
            val media = e.optJSONObject("media") ?: continue
            val format = media.sOrNull("format")
            if (format !in FORMATS) continue
            val adult = media.optBoolean("isAdult", false)
            if (!allowAdult && adult) continue
            val id = media.optInt("id")
            val ep = e.optInt("episode")
            if (!seen.add(id to ep)) continue
            if (e.isNull("airingAt")) continue
            val at = e.optLong("airingAt")
            val cal = Calendar.getInstance().apply { timeInMillis = at * 1000 }
            val day = DAYS[(cal.get(Calendar.DAY_OF_WEEK) + 5) % 7]
            val t = media.optJSONObject("title")
            out.getValue(day) += ScheduleEntry(
                id = id,
                title = t.sOrNull("english") ?: t.sOrNull("romaji") ?: "Unknown",
                episode = ep,
                airingAt = at,
                time = fmt.format(Date(at * 1000)),
                coverUrl = media.optJSONObject("coverImage").sOrNull("large") ?: "",
                format = format,
                isAdult = adult,
            )
        }
        return out.mapValues { (_, v) -> v.sortedBy { it.airingAt } }
    }

    private fun toJson(m: Map<String, List<ScheduleEntry>>) = JSONObject().also { o ->
        m.forEach { (day, list) ->
            o.put(day, JSONArray().also { a ->
                list.forEach { s ->
                    a.put(JSONObject().put("id", s.id).put("title", s.title).put("episode", s.episode)
                        .put("airingAt", s.airingAt).put("time", s.time).put("cover", s.coverUrl)
                        .put("format", s.format ?: JSONObject.NULL).put("adult", s.isAdult))
                }
            })
        }
    }

    private fun fromJson(o: JSONObject): Map<String, List<ScheduleEntry>> = DAYS.associateWith { day ->
        val a = o.optJSONArray(day) ?: JSONArray()
        (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map {
            ScheduleEntry(it.optInt("id"), it.optString("title"), it.optInt("episode"),
                it.optLong("airingAt"), it.optString("time"), it.optString("cover"),
                it.sOrNull("format"), it.optBoolean("adult"))
        }
    }

    /** Pass cacheFile = File(context.cacheDir, "schedule_cache.json") for the 30 min cache. */
    fun getWeeklySchedule(
        startTimestamp: Long? = null,
        allowAdult: Boolean = false,
        cacheFile: File? = null,
        useCache: Boolean = true,
    ): Map<String, List<ScheduleEntry>> {
        val (ws, we) = if (startTimestamp == null) weekBounds() else startTimestamp to startTimestamp + 7 * 86400

        if (useCache && cacheFile?.exists() == true) {
            try {
                val b = JSONObject(cacheFile.readText())
                if (b.optLong("week_start") == ws && b.optBoolean("allow_adult") == allowAdult &&
                    System.currentTimeMillis() - b.optLong("fetched_at") < CACHE_TTL_MS
                ) return fromJson(b.getJSONObject("data"))
            } catch (_: Exception) {}
        }

        val entries = mutableListOf<JSONObject>()
        for (page in 1..20) {
            val res = AniListClient.query(
                QUERY, JSONObject().put("start", ws - 1).put("end", we).put("page", page))
            val pg = res.optJSONObject("data")?.optJSONObject("Page")
            val arr = pg?.optJSONArray("airingSchedules")
            if (arr != null) for (i in 0 until arr.length()) arr.optJSONObject(i)?.let(entries::add)
            if (pg?.optJSONObject("pageInfo")?.optBoolean("hasNextPage") != true) break
            Thread.sleep(300)
        }

        val data = buildSchedule(entries, allowAdult)
        try {
            cacheFile?.writeText(JSONObject().put("week_start", ws).put("allow_adult", allowAdult)
                .put("fetched_at", System.currentTimeMillis()).put("data", toJson(data)).toString())
        } catch (_: Exception) {}
        return data
    }
}
