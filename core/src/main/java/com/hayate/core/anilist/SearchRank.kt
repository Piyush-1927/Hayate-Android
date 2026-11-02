package com.hayate.core.anilist

import java.text.Normalizer
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/** Port of difflib.SequenceMatcher.ratio() (no junk heuristics). */
internal object Difflib {
    fun ratio(a: String, b: String): Double {
        val total = a.length + b.length
        if (total == 0) return 1.0
        var m = 0
        val st = ArrayDeque<IntArray>()
        st.add(intArrayOf(0, a.length, 0, b.length))
        while (st.isNotEmpty()) {
            val (alo, ahi, blo, bhi) = st.removeLast()
            var bi = alo; var bj = blo; var bk = 0
            var prev = IntArray(b.length + 1)
            for (i in alo until ahi) {
                val cur = IntArray(b.length + 1)
                for (j in blo until bhi) if (a[i] == b[j]) {
                    val k = prev[j] + 1
                    cur[j + 1] = k
                    if (k > bk) { bi = i - k + 1; bj = j - k + 1; bk = k }
                }
                prev = cur
            }
            if (bk > 0) {
                m += bk
                if (alo < bi && blo < bj) st.add(intArrayOf(alo, bi, blo, bj))
                if (bi + bk < ahi && bj + bk < bhi) st.add(intArrayOf(bi + bk, ahi, bj + bk, bhi))
            }
        }
        return 2.0 * m / total
    }
}

object SearchRank {
    private val STOP = setOf("the", "a", "an", "of", "no", "wa", "to", "and", "in", "on", "ni", "wo")
    private val STRIP = Regex("[^a-z0-9\\u3040-\\u30ff\\u4e00-\\u9fff ]+")

    fun norm(s: String?): String =
        Normalizer.normalize(s ?: "", Normalizer.Form.NFKD).lowercase()
            .replace(STRIP, " ").split(" ").filter { it.isNotEmpty() }.joinToString(" ")

    fun tokens(q: String): List<String> = norm(q).split(" ").filter { it.isNotEmpty() && it !in STOP && it.length > 1 }

    private fun close(k: String, w: String): Boolean {
        if (w.startsWith(k)) return true
        if (k.length < 4) return false
        return Difflib.ratio(k, w.take(k.length + 1)) >= 0.78
    }

    fun typoVariants(q: String): List<String> {
        val toks = tokens(q).sortedByDescending { it.length }
        if (toks.isEmpty() || toks[0].length < 4) return emptyList()
        val t = toks[0]
        return listOf(t.substring(1), t.substring(0, t.length - 1))
    }

    fun titleScore(m: AnimeMedia, q: String): Double {
        val nq = norm(q); val toks = tokens(q)
        var best = 0.0
        for (raw in listOf(m.title) + m.synonyms) {
            val t = norm(raw)
            if (t.isEmpty()) continue
            val words = t.split(" ")
            val s: Double = when {
                t == nq -> 1000.0
                t.startsWith(nq) -> 800.0
                nq in t -> 600.0
                else -> {
                    val hit = toks.count { k -> words.any { w -> close(k, w) } }
                    if (hit == 0) continue
                    100.0 + 300.0 * hit / toks.size + (if (hit == toks.size) 100.0 else 0.0)
                }
            }
            best = maxOf(best, s)
        }
        return best
    }

    class Group(val items: List<AnimeMedia>, val base: Int, val step: Int, val need: Boolean = false)

    fun mergeRank(q: String, groups: List<Group>): List<AnimeMedia> {
        val best = LinkedHashMap<Int, Pair<Double, AnimeMedia>>()
        for (g in groups) for ((i, m) in g.items.withIndex()) {
            val ts = titleScore(m, q)
            if (g.need && ts == 0.0) continue
            val sc = ts + maxOf(0, g.base - g.step * i) + (m.averageScore ?: 0) / 100.0
            val cur = best[m.id]
            if (cur == null || sc > cur.first) best[m.id] = sc to m
        }
        return best.values.sortedByDescending { it.first }.map { it.second }
    }
}

/** Blocking: run off the main thread. */
object AniListSmartSearch {
    fun search(query: String, page: Int = 1, perPage: Int = 35, allowAdult: Boolean = false, maxTokens: Int = 3): SearchResult {
        val q = query.trim()
        val first = AniListApi.searchAnime(q, page, perPage, allowAdult)
        var adult = first.hadAdultHits
        val groups = mutableListOf(SearchRank.Group(first.items, 300, 6, false))
        if (page == 1) {
            val toks = SearchRank.tokens(q).distinct().sortedByDescending { it.length }.take(maxTokens)
            val jobs = mutableListOf<Pair<String, SearchRank.Group?>>()
            val n = first.items.size
            if (n < 15 && (toks.size > 1 || (toks.isNotEmpty() && toks[0] != SearchRank.norm(q))))
                toks.forEach { jobs += it to SearchRank.Group(emptyList(), 60, 2, false) }
            if (n < 4) SearchRank.typoVariants(q).forEach { jobs += it to SearchRank.Group(emptyList(), 0, 0, true) }
            if (jobs.isNotEmpty()) {
                val ex = Executors.newFixedThreadPool(jobs.size)
                try {
                    val futs = jobs.map { (text, _) ->
                        ex.submit(Callable<SearchResult?> {
                            try { AniListApi.searchAnime(text, 1, maxOf(10, perPage / 2), allowAdult) } catch (e: Exception) { null }
                        })
                    }
                    for ((idx, f) in futs.withIndex()) {
                        val r = f.get() ?: continue
                        adult = adult || r.hadAdultHits
                        val g = jobs[idx].second!!
                        groups += SearchRank.Group(r.items, g.base, g.step, g.need)
                    }
                } finally { ex.shutdown() }
            }
        }
        return SearchResult(SearchRank.mergeRank(q, groups), adult)
    }
}
