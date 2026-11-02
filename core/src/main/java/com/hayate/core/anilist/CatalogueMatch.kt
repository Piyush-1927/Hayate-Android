package com.hayate.core.anilist

import org.json.JSONObject
import java.text.Normalizer

/** Title cleanup + AniList lookup for catalogue titles missing from the bundled seed. Mirrors desktop catalogue_view. */
object CatalogueMatch {
    private val I = RegexOption.IGNORE_CASE
    private val JUNK = Regex("highlight|recap|marathon|celebration|anniversary|ani-?mall|\\bfest\\b|\\bPV\\b|theme song|ani-one studio|預告|精華|best scene|in theaters|ani-news|ani-stars|digital live|pop-up|anime music|reveal|interview|messages from|promotion|after hours|新番|購物網|現場直擊|訂閱|quizknock|mecha-senku|science class", I)
    private val NOISE = Regex("multi[- ]?subs?|full episodes?|playlist|english sub(bed)?|eng sub|\\bsubbed\\b|\\bdubbed\\b", I)
    private val CJK = Regex("[\u3040-\u30ff\u3400-\u9fff]")
    private val LAT3 = Regex("[A-Za-z]{3}")
    private val WS = Regex("\\s+")
    private val TAIL = Regex("(?:\\s*[-\u2013|]\\s*(?:now on|streaming|available)|\\s*\\|\\s*(?:from|s\\d+ starting|starting))\\b.*$", I)
    private val SEASON = Regex("\\s*[-\u2013:]?\\s*\\b(?:(?:the\\s+)?(?:final|second|third|fourth|fifth|\\d+(?:st|nd|rd|th))\\s+(?:season|cour|part)|season\\s*\\d+|part\\s*\\d+|cour\\s*\\d+)\\b.*$", I)
    private val ROMAN = Regex("\\s+(?:[IVX]+|\\d+)$")
    private val SN = Regex("\\s+S\\d+$", I)
    private val ACT = Regex("\\s*\\bact\\s*[IVX\\d]+$", I)
    private val STRIP = charArrayOf(' ', '-', '\u2013', ':')

    private fun norm(t: String) = Normalizer.normalize(t, Normalizer.Form.NFKC).replace('\u02bc', '\'').replace('\u2019', '\'')

    /** Playlist title as it appears in the desktop/seed title list; null for promo reels. */
    fun channelTitle(e: String): String? {
        if (JUNK.containsMatchIn(e)) return null
        var t = e.replace(Regex("\\(.*?\\)|\\[.*?\\]"), "")
        t = WS.replace(NOISE.replace(t, ""), " ").trim(' ', '|', '\u00b7', '-')
        return t.takeIf { it.length > 2 }
    }

    fun clean(raw: String): String {
        var t = raw.replace(Regex("#ULTRAEarlyBird\\S*"), "").replace(Regex("\u3010[^\u3011]*\u3011"), "")
        for (m in Regex("\u300a([^\u300b]*)\u300b").findAll(t)) {
            val x = WS.replace(m.groupValues[1], " ").trim(' ', '|', '-')
            if (LAT3.containsMatchIn(x) && !CJK.containsMatchIn(x)) { t = x; break }
        }
        return TAIL.replace(t, "").trim()
    }

    fun hasLatin(clean: String) = LAT3.containsMatchIn(norm(clean))

    fun variants(raw: String): List<String> {
        val t = norm(raw)
        var a = SEASON.replace(t, "").trim(*STRIP)
        a = SN.replace(a, "").trim(*STRIP)
        a = ACT.replace(a, "").trim(*STRIP)
        val b = ROMAN.replace(t, "").trim(*STRIP)
        val c = ROMAN.replace(a, "").trim(*STRIP)
        val out = arrayListOf(t)
        for (v in listOf(a, b, c, t.substringBefore(":").trim())) if (v.length >= 3 && out.none { it.equals(v, true) }) out.add(v)
        return out
    }

    private fun searchIds(qs: List<String>): List<Int?> {
        val decl = qs.indices.joinToString(", ") { "\$q$it: String" }
        val body = qs.indices.joinToString("\n") {
            "  a$it: Page(page: 1, perPage: 1) { media(search: \$q$it, type: ANIME, sort: [SEARCH_MATCH, POPULARITY_DESC]) { id } }"
        }
        val vars = JSONObject().also { v -> qs.forEachIndexed { i, q -> v.put("q$i", q) } }
        val data = AniListClient.query("query ($decl) {\n$body\n}", vars).optJSONObject("data")
        return qs.indices.map { data?.optJSONObject("a$it")?.optJSONArray("media")?.optJSONObject(0)?.optInt("id") }
    }

    /** name -> AniList id, or null when nothing matched. Throws if AniList can't be reached (nothing is guessed). */
    fun resolve(names: List<String>): Map<String, Int?> {
        val lad = names.associateWith { variants(it) }
        val res = HashMap<String, Int?>(); val pending = names.toMutableList()
        for (k in 0 until (lad.values.maxOfOrNull { it.size } ?: 0)) {
            val qs = pending.filter { k < lad[it]!!.size }
            if (qs.isEmpty()) break
            for (chunk in qs.chunked(8)) {
                val ids = searchIds(chunk.map { lad[it]!![k] })
                chunk.forEachIndexed { i, t -> ids[i]?.let { res[t] = it; pending.remove(t) } }
                Thread.sleep(700)
            }
        }
        for (t in pending) res[t] = null
        return res
    }
}
