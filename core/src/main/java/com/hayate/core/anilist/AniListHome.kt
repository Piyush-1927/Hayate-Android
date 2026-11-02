package com.hayate.core.anilist

import org.json.JSONObject
import java.io.File
import java.util.Calendar

data class Shelves(
    val spotlight: List<AnimeMedia>,
    val trending: List<AnimeMedia>,
    val popular: List<AnimeMedia>,
    val newReleases: List<AnimeMedia>,
    val justFinished: List<AnimeMedia>,
    val upcoming: List<AnimeMedia>,
)

data class HomeResult(val shelves: Shelves, val stale: Boolean)

/** All calls are blocking: run them off the main thread. */
object AniListHome {
    private const val D = "$"
    const val UPCOMING_LIMIT = 32
    const val SHELF_LIMIT = 32
    const val FINISHED_LIMIT = 16
    const val SPOTLIGHT_LIMIT = 10
    private const val CACHE_TTL_MS = 20 * 60 * 1000L

    private fun fields() = AniListParser.MEDIA_FIELDS

    fun homeQuery(allow: Boolean): String {
        val a = AniListParser.adultArg(allow)
        val f = fields()
        return """
query (${D}monthAgo: FuzzyDateInt) {
  spotlight: Page(page: 1, perPage: 15) {
    media(type: ANIME, status: RELEASING, sort: TRENDING_DESC, format_in: [TV, ONA], $a) { $f }
  }
  trending: Page(page: 1, perPage: 45) {
    media(type: ANIME, sort: TRENDING_DESC, $a) { $f }
  }
  popular: Page(page: 1, perPage: 45) {
    media(type: ANIME, sort: POPULARITY_DESC, $a) { $f }
  }
  new_releases: Page(page: 1, perPage: 50) {
    media(type: ANIME, status: RELEASING, sort: START_DATE_DESC, format_in: [TV, ONA], $a) { $f }
  }
  just_finished: Page(page: 1, perPage: 30) {
    media(type: ANIME, status: FINISHED, sort: END_DATE_DESC, endDate_greater: ${D}monthAgo, format_in: [TV, ONA], $a) { $f }
  }
  upcoming: Page(page: 1, perPage: 50) {
    media(type: ANIME, status: NOT_YET_RELEASED, sort: POPULARITY_DESC, $a) { $f }
  }
}
"""
    }

    fun buildShelves(data: JSONObject, allow: Boolean): Shelves {
        fun raw(key: String) = data.optJSONObject(key)?.optJSONArray("media")
        fun list(key: String) = AniListParser.parseList(raw(key), allow)

        val spotAll = list("spotlight")
        var spot = spotAll.filter { it.bannerUrl.isNotEmpty() && it.bannerUrl != it.coverUrl }
        if (spot.size < 5) spot = spotAll
        spot = spot.take(SPOTLIGHT_LIMIT)
        val spotIds = spot.map { it.id }.toSet()

        return Shelves(
            spotlight = spot,
            trending = AniListParser.dedupe(list("trending"), spotIds).take(SHELF_LIMIT),
            popular = AniListParser.dedupe(list("popular")).take(SHELF_LIMIT),
            newReleases = list("new_releases").filter { !it.hasPrequel }.take(SHELF_LIMIT),
            justFinished = list("just_finished").take(FINISHED_LIMIT),
            upcoming = list("upcoming").filter { it.isDateConfirmed }.take(UPCOMING_LIMIT),
        )
    }

    private fun today(offsetDays: Int = 0): Triple<Int, Int, Int> {
        val c = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, offsetDays) }
        return Triple(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    /** Instant read of whatever is cached (any age) so the UI can paint at once while a refresh runs. */
    fun peekCache(allowAdult: Boolean, cacheFile: File): HomeResult? = try {
        val b = cacheFile.takeIf { it.exists() }?.let { JSONObject(it.readText()) }
        if (b != null && b.optBoolean("allow_adult") == allowAdult && b.optJSONObject("data") != null)
            HomeResult(buildShelves(b.getJSONObject("data"), allowAdult), stale = false) else null
    } catch (e: Exception) { null }

    /** Pass cacheFile = File(context.cacheDir, "home_cache.json") to get the 20 min cache + offline fallback. */
    fun getHomeShelves(
        allowAdult: Boolean = false,
        cacheFile: File? = null,
        useCache: Boolean = true,
    ): HomeResult {
        val q = homeQuery(allowAdult)
        val qsig = Integer.toHexString(q.hashCode())
        val (y, m, d) = today()
        val dateStr = "%04d-%02d-%02d".format(y, m, d)

        fun readBlob(): JSONObject? = try {
            cacheFile?.takeIf { it.exists() }?.let { JSONObject(it.readText()) }
        } catch (e: Exception) { null }

        if (useCache) {
            val b = readBlob()
            if (b != null &&
                System.currentTimeMillis() - b.optLong("fetched_at", 0) < CACHE_TTL_MS &&
                b.optString("qsig") == qsig &&
                b.optBoolean("allow_adult") == allowAdult &&
                b.optString("date") == dateStr &&
                b.optJSONObject("data") != null
            ) return HomeResult(buildShelves(b.getJSONObject("data"), allowAdult), stale = false)
        }

        val (my, mm, md) = today(-30)
        val monthAgo = my * 10000 + mm * 100 + md
        val res = try {
            AniListClient.query(q, JSONObject().put("monthAgo", monthAgo))
        } catch (e: Exception) {
            // offline / API down: serve the last good cache, however old
            val b = readBlob()
            if (b != null && b.optBoolean("allow_adult") == allowAdult && b.optJSONObject("data") != null)
                return HomeResult(buildShelves(b.getJSONObject("data"), allowAdult), stale = true)
            throw e
        }
        val data = res.optJSONObject("data") ?: JSONObject()
        try {
            cacheFile?.writeText(JSONObject()
                .put("fetched_at", System.currentTimeMillis())
                .put("allow_adult", allowAdult)
                .put("qsig", qsig)
                .put("date", dateStr)
                .put("data", data).toString())
        } catch (_: Exception) {}
        return HomeResult(buildShelves(data, allowAdult), stale = false)
    }

    /** Full upcoming list for a "see all" screen. Returns (items, hasNextPage). */
    fun getUpcoming(page: Int = 1, perPage: Int = 50, allowAdult: Boolean = false): Pair<List<AnimeMedia>, Boolean> {
        val a = AniListParser.adultArg(allowAdult)
        val q = """
query (${D}page: Int, ${D}perPage: Int) {
  Page(page: ${D}page, perPage: ${D}perPage) {
    pageInfo { hasNextPage }
    media(type: ANIME, status: NOT_YET_RELEASED, sort: POPULARITY_DESC, $a) { ${fields()} }
  }
}
"""
        val res = AniListClient.query(q, JSONObject().put("page", page).put("perPage", perPage))
        val pg = res.optJSONObject("data")?.optJSONObject("Page")
        val items = AniListParser.parseList(pg?.optJSONArray("media"), allowAdult).filter { it.isDateConfirmed }
        return items to (pg?.optJSONObject("pageInfo")?.optBoolean("hasNextPage") ?: false)
    }

    /** One page of a home shelf, for "View all". kind: trending|popular|new_releases|just_finished|upcoming */
    fun getShelfPage(kind: String, page: Int = 1, perPage: Int = 30, allowAdult: Boolean = false): Pair<List<AnimeMedia>, Boolean> {
        val a = AniListParser.adultArg(allowAdult)
        val filt = when (kind) {
            "trending" -> "sort: TRENDING_DESC"
            "popular" -> "sort: POPULARITY_DESC"
            "new_releases" -> "status: RELEASING, sort: START_DATE_DESC, format_in: [TV, ONA]"
            "just_finished" -> "status: FINISHED, sort: END_DATE_DESC, endDate_greater: ${D}monthAgo, format_in: [TV, ONA]"
            else -> "status: NOT_YET_RELEASED, sort: POPULARITY_DESC"
        }
        val extra = if (kind == "just_finished") ", ${D}monthAgo: FuzzyDateInt" else ""
        val q = """
query (${D}page: Int, ${D}perPage: Int$extra) {
  Page(page: ${D}page, perPage: ${D}perPage) {
    pageInfo { hasNextPage }
    media(type: ANIME, $filt, $a) { ${fields()} }
  }
}
"""
        val vars = JSONObject().put("page", page).put("perPage", perPage)
        if (kind == "just_finished") { val (y, m, d) = today(-30); vars.put("monthAgo", y * 10000 + m * 100 + d) }
        val res = AniListClient.query(q, vars)
        val pg = res.optJSONObject("data")?.optJSONObject("Page")
        var items = AniListParser.parseList(pg?.optJSONArray("media"), allowAdult)
        if (kind == "upcoming") items = items.filter { it.isDateConfirmed }
        if (kind == "new_releases") items = items.filter { !it.hasPrequel }
        return items to (pg?.optJSONObject("pageInfo")?.optBoolean("hasNextPage") ?: false)
    }
}
