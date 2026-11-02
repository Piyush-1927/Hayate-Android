package com.hayate.core.anilist

import org.json.JSONArray
import org.json.JSONObject

object CatalogueApi {
    private val Q = """
query (${'$'}ids: [Int]) {
  Page(page: 1, perPage: 50) {
    media(id_in: ${'$'}ids, type: ANIME) {
      ${AniListParser.MEDIA_FIELDS}
    }
  }
}
"""

    /** One request for up to 50 ids; returns raw media nodes so callers can cache them. */
    fun rawByIds(ids: List<Int>): JSONArray {
        val res = AniListClient.query(Q, JSONObject().put("ids", JSONArray(ids)))
        return res.optJSONObject("data")?.optJSONObject("Page")?.optJSONArray("media") ?: JSONArray()
    }

    fun parse(raw: JSONArray): List<AnimeMedia> = AniListParser.parseList(raw, allowAdult = true)
}
