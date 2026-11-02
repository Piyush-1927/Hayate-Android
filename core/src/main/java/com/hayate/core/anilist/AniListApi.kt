package com.hayate.core.anilist

import org.json.JSONObject

data class SearchResult(val items: List<AnimeMedia>, val hadAdultHits: Boolean)

/** All calls are blocking: run them off the main thread. */
object AniListApi {
    private const val D = "$"

    private val SEARCH_FIELDS =
        AniListParser.MEDIA_FIELDS.substringBefore("  relations {") + "  synonyms\n"

    private val SEARCH_QUERY = """
query (${D}query: String, ${D}page: Int, ${D}perPage: Int) {
  Page(page: ${D}page, perPage: ${D}perPage) {
    pageInfo { total perPage currentPage hasNextPage }
    media(search: ${D}query, type: ANIME, sort: [SEARCH_MATCH, POPULARITY_DESC]) {
      $SEARCH_FIELDS
    }
  }
}
"""

    private val BY_ID_QUERY = """
query (${D}id: Int) {
  Media(id: ${D}id, type: ANIME) {
    ${AniListParser.MEDIA_FIELDS}
  }
}
"""

    fun searchAnime(
        query: String,
        page: Int = 1,
        perPage: Int = 35,
        allowAdult: Boolean = false,
    ): SearchResult {
        val vars = JSONObject().put("query", query).put("page", page).put("perPage", perPage)
        val res = AniListClient.query(SEARCH_QUERY, vars)
        val raw = res.optJSONObject("data")?.optJSONObject("Page")?.optJSONArray("media")
        val all = AniListParser.parseList(raw, allowAdult = true)
        val shown = if (allowAdult) all else all.filter { !it.isAdult }
        return SearchResult(shown, hadAdultHits = !allowAdult && shown.size < all.size)
    }

    private val byIdCache = java.util.concurrent.ConcurrentHashMap<Int, AnimeMedia>()
    fun getAnimeById(id: Int): AnimeMedia? {
        byIdCache[id]?.let { return it }
        val res = AniListClient.query(BY_ID_QUERY, JSONObject().put("id", id))
        val node = res.optJSONObject("data")?.optJSONObject("Media") ?: return null
        return AniListParser.parseMediaNode(node)?.also { byIdCache[id] = it }
    }
}
