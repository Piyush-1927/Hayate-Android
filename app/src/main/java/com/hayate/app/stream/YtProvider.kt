package com.hayate.app.stream

import android.content.Context
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import org.json.JSONObject

data class StreamResult(val url: String, val title: String, val referer: String = "", val subtitle: String = "")
data class Outcome(val stream: StreamResult?, val notice: String = "")

/** Port of desktop ytdlp_provider.py (official licensor channels). Blocking: run off the main thread. */
object YtProvider {
    private val OFFICIAL = listOf(
        "muse asia", "muse india", "muse malaysia", "muse indonesia", "muse vietnam", "muse thailand",
        "ani-one asia", "ani-one thailand", "ani-one vietnam", "ani-one china", "crunchyroll",
        "crunchyroll collection", "tms anime", "gundaminfo", "gundam.info", "pokemon asia official",
        "bilibili", "kadokawa anime", "toho animation", "aniplex", "bandai spirits",
        "muse india - best anime collection", "muse philippines", "muse vi\u1ec7t nam",
        "ani-one philippines", "ani-one indonesia", "ani-one india", "crunchyroll dubs",
        "gundam channel intl", "tms entertainment", "nozomi entertainment", "retrocrush",
        "toei animation", "\u6771\u6620\u52d5\u756b\u4e9e\u6d32 toei animation asia", "aniplus asia",
    )
    private val I = RegexOption.IGNORE_CASE
    private val MEMBERS = Regex("\\bultra\\b|ultra\\s*early|members?[- ]only|\u6703\u54e1|\u4f1a\u5458", I)
    private val HIN = Regex("hindi|\u0939\u093f\u0928\u094d\u0926\u0940|\u0939\u093f\u0902\u0926\u0940", I)
    private val HIN_SUB = Regex("hindi\\s*sub", I)
    private val ENG_DUB = Regex("english\\s*dub|eng\\s*dub|dubbed\\s*in\\s*english|english\\s*dubbed", I)
    private val ANY_DUB = Regex("\\bdub(bed|s)?\\b|\u914d\u97f3|\u5439\u304d\u66ff\u3048|\u5439\u66ff|dublado|doblaje|l\u1ed3ng ti\u1ebfng|\u0e1e\u0e32\u0e01\u0e22\u0e4c", I)
    private val BLOCKED = setOf("subscriber_only", "premium_only", "needs_auth")

    private var ready = false
    @Synchronized private fun init(ctx: Context) {
        if (ready) return
        YoutubeDL.getInstance().init(ctx); ready = true
    }

    private fun str(d: JSONObject, k: String) = if (d.isNull(k)) "" else d.optString(k)

    private fun clean(t: String) = t.replace(Regex("\\(.*?\\)|\\[.*?\\]"), "")
        .replace(Regex("[:\\-\u2013\u2014]"), " ").replace(Regex("\\s+"), " ").trim()

    private fun official(up: String, ch: String): Boolean {
        val u = up.trim().lowercase(); val c = ch.trim().lowercase()
        return OFFICIAL.any { it in u || it in c }
    }

    private fun matchesEp(title: String, ep: Int): Boolean {
        val t = title.lowercase(); val n = "0*$ep"
        return listOf("\\bep(isode)?\\.?\\s*$n\\b", "#$n\\b", "\\[$n\\]", "\\($n\\)").any { Regex(it).containsMatchIn(t) }
    }

    private fun usable(d: JSONObject, ep: Int): Boolean {
        val v = str(d, "title")
        if (!(official(str(d, "uploader"), str(d, "channel")) && matchesEp(v, ep))) return false
        return !(MEMBERS.containsMatchIn(v) || str(d, "availability") in BLOCKED)
    }

    private fun lang(d: JSONObject): String {
        val t = str(d, "title")
        val ch = str(d, "channel").ifEmpty { str(d, "uploader") }.lowercase()
        if ((HIN.containsMatchIn(t) && !HIN_SUB.containsMatchIn(t)) || "hindi dub" in ch) return "hin"
        if (ENG_DUB.containsMatchIn(t) || "dubs" in ch) return "eng"
        if (ANY_DUB.containsMatchIn(t)) return "dub"
        return "sub"
    }

    /** want: "sub" | "eng" | "hin" */
    private val cache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, Outcome>>()
    private const val TTL = 30 * 60 * 1000L

    fun warmUp(ctx: Context) = runCatching { init(ctx) }

    fun prefetch(ctx: Context, title: String, ep: Int, want: String) {
        Thread { runCatching { getStream(ctx.applicationContext, title, ep, want) } }.start()
    }

    fun getStream(ctx: Context, title: String, ep: Int, want: String): Outcome {
        val key = "${title.lowercase()}|$ep|$want"
        cache[key]?.let { if (System.currentTimeMillis() - it.first < TTL) return it.second }
        val r = fetch(ctx, title, ep, want)
        if (r.stream != null) cache[key] = System.currentTimeMillis() to r
        return r
    }

    private fun fetch(ctx: Context, title: String, ep: Int, want: String): Outcome {
        init(ctx)
        val suffix = when (want) { "hin" -> " hindi dub"; "eng" -> " english dub"; else -> "" }
        val scan = YoutubeDLRequest("ytsearch10:${clean(title)} episode $ep$suffix").apply {
            addOption("--flat-playlist"); addOption("--no-warnings"); addOption("-j")
        }
        val out = YoutubeDL.getInstance().execute(scan).out
        val found = mutableSetOf<String>()
        var target: String? = null; var mt = ""; var mu = ""
        for (line in out.lines()) {
            val d = try { JSONObject(line) } catch (e: Exception) { continue }
            if (!usable(d, ep)) continue
            val l = lang(d); found += l
            if (l == want && target == null) {
                target = str(d, "url").ifEmpty { "https://www.youtube.com/watch?v=" + str(d, "id") }
                mt = str(d, "title"); mu = str(d, "uploader").ifEmpty { str(d, "channel") }
            }
        }
        val t = target ?: run {
            val nm = mapOf("sub" to "Sub", "eng" to "English dub", "hin" to "Hindi dub", "dub" to "Another dub")
            val msg = if (found.isEmpty()) "Episode $ep isn't on the official channels."
            else "${nm[want]} isn't available for episode $ep. Available: " +
                listOf("sub", "eng", "hin", "dub").filter { it in found }.joinToString(", ") { nm[it]!! } + "."
            return Outcome(null, msg)
        }
        val req = YoutubeDLRequest(t).apply {
            addOption("--no-playlist"); addOption("--no-warnings"); addOption("--extractor-args", "youtube:player_client=android")
            addOption("-f", "best[ext=mp4]/best")
        }
        val url = YoutubeDL.getInstance().getInfo(req).url ?: return Outcome(null, "Couldn't resolve the stream.")
        return Outcome(StreamResult(url, "[$mu] $mt"))
    }
}
