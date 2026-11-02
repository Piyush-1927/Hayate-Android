package com.hayate.core.anilist

import org.json.JSONArray
import org.json.JSONObject

data class AnimeRelation(
    val relationType: String,
    val id: Int,
    val title: String,
    val format: String?,
    val coverUrl: String,
)

private fun JSONObject?.str(key: String): String? =
    this?.takeIf { it.has(key) && !it.isNull(key) }?.optString(key)?.takeIf { it.isNotEmpty() }

private fun JSONObject?.int(key: String): Int? =
    this?.takeIf { it.has(key) && !it.isNull(key) }?.optInt(key)

private fun JSONObject?.long(key: String): Long? =
    this?.takeIf { it.has(key) && !it.isNull(key) }?.optLong(key)

object AniListParser {
    const val MEDIA_FIELDS = """
  id
  title {
    romaji
    english
  }
  coverImage {
    large
  }
  bannerImage
  description
  format
  episodes
  averageScore
  idMal
  season
  seasonYear
  popularity
  genres
  isAdult
  status
  startDate { year month day }
  endDate { year month day }
  nextAiringEpisode {
    episode
    airingAt
  }
  relations {
    edges {
      relationType
      node {
        id
        title {
          romaji
          english
        }
        format
        coverImage {
          large
        }
      }
    }
  }
"""

    fun adultArg(allowAdult: Boolean): String =
        if (allowAdult) "" else "isAdult: false, genre_not_in: [\"Ecchi\", \"Hentai\"],"

    fun parseMediaNode(item: JSONObject): AnimeMedia {
        val t = item.optJSONObject("title")
        val title = t.str("english") ?: t.str("romaji") ?: "Unknown Title"
        val cover = item.optJSONObject("coverImage").str("large") ?: ""
        val banner = item.str("bannerImage") ?: cover

        val next = item.optJSONObject("nextAiringEpisode")
        var eps = item.int("episodes")
        if ((eps == null || eps == 0) && next != null) {
            val cur = next.int("episode")
            if (cur != null && cur > 1) eps = cur - 1
        }

        val rels = mutableListOf<AnimeRelation>()
        val edges = item.optJSONObject("relations")?.optJSONArray("edges")
        if (edges != null) for (i in 0 until edges.length()) {
            val edge = edges.optJSONObject(i) ?: continue
            val node = edge.optJSONObject("node") ?: continue
            val rt = node.optJSONObject("title")
            rels += AnimeRelation(
                relationType = edge.optString("relationType", ""),
                id = node.optInt("id"),
                title = rt.str("english") ?: rt.str("romaji") ?: "Unknown",
                format = node.str("format"),
                coverUrl = node.optJSONObject("coverImage").str("large") ?: "",
            )
        }

        val g = item.optJSONArray("genres")
        val genres = if (g == null) emptyList() else (0 until g.length()).map { g.getString(it) }
        val sd = item.optJSONObject("startDate")
        val ed = item.optJSONObject("endDate")

        return AnimeMedia(
            id = item.optInt("id"),
            title = title,
            coverUrl = cover,
            bannerUrl = banner,
            description = item.str("description") ?: "",
            format = item.str("format"),
            episodes = eps ?: 1,
            averageScore = item.int("averageScore"),
            idMal = item.int("idMal"),
            season = item.str("season"),
            seasonYear = item.int("seasonYear"),
            genres = genres,
            isAdult = item.optBoolean("isAdult", false),
            relations = rels,
            airingEpisode = next.int("episode"),
            airingTime = next.long("airingAt"),
            startYear = sd.int("year"),
            startMonth = sd.int("month"),
            startDay = sd.int("day"),
            endYear = ed.int("year"),
            endMonth = ed.int("month"),
            endDay = ed.int("day"),
            status = item.str("status"),
            popularity = item.int("popularity"),
            synonyms = item.optJSONArray("synonyms")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
        )
    }

    fun parseList(raw: JSONArray?, allowAdult: Boolean): List<AnimeMedia> {
        if (raw == null) return emptyList()
        val out = (0 until raw.length()).mapNotNull { raw.optJSONObject(it)?.let(::parseMediaNode) }
        return if (allowAdult) out else out.filter { !it.isAdult }
    }

    fun dedupe(items: List<AnimeMedia>, excludeIds: Set<Int> = emptySet()): List<AnimeMedia> {
        val seen = excludeIds.toMutableSet()
        return items.filter { seen.add(it.id) }
    }
}
