package com.hayate.app.stream

import android.content.Context
import android.util.Log
import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.define
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import com.hayate.app.Prefs
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

interface JsHttp { fun get(url: String, headers: String): String; fun post(url: String, body: String, headers: String): String }
interface JsYt { fun resolve(url: String): String }
interface JsCache { fun get(k: String): String; fun put(k: String, v: String) }

/** Script (.js) providers: imported files are validated and run in QuickJS. No compile step. */
object Providers {
    data class Info(val id: String, val label: String, val file: File)

    private const val PRELUDE = """
var http = {
  get: function (u, h) { return _http.get(u, h ? JSON.stringify(h) : ""); },
  post: function (u, b, h) { return _http.post(u, typeof b === "string" ? b : JSON.stringify(b), h ? JSON.stringify(h) : ""); },
  json: function (u, h) { return JSON.parse(this.get(u, h)); }
};
var ytdlp = { resolve: function (u) { return _yt.resolve(u); } };
var cache = { get: function (k) { var s = _cache.get(k); if (!s) return null; var o = JSON.parse(s); return Date.now() - o.t < 1800000 ? o.v : null; },
              set: function (k, v) { _cache.put(k, JSON.stringify({ t: Date.now(), v: v })); } };
function __run(t, e, l) {
  var r = provider.getStream(t, e, l);
  return JSON.stringify(r === undefined ? null : r);
}
"""

    private val jsCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val outCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, Outcome>>()

    /** Parses an HLS master playlist into (height, variantUrl), highest first. Blocking. */
    fun variants(master: String, referer: String): List<Pair<Int, String>> = try {
        if (!master.contains(".m3u8")) emptyList() else {
            val c = URL(master).openConnection() as HttpURLConnection
            c.connectTimeout = 10000; c.readTimeout = 10000
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) Hayate")
            if (referer.isNotEmpty()) c.setRequestProperty("Referer", referer)
            val lines = c.inputStream.bufferedReader().use { it.readLines() }
            val out = ArrayList<Pair<Int, String>>()
            lines.forEachIndexed { i, l ->
                if (l.startsWith("#EXT-X-STREAM-INF") && i + 1 < lines.size) {
                    val h = Regex("RESOLUTION=\\d+x(\\d+)").find(l)?.groupValues?.get(1)?.toIntOrNull()
                    val n = lines[i + 1].trim()
                    if (h != null && n.isNotEmpty() && !n.startsWith("#")) out += h to URL(URL(master), n).toString()
                }
            }
            out.distinctBy { it.first }.sortedByDescending { it.first }
        }
    } catch (e: Exception) { emptyList() }

    fun dir(ctx: Context) = File(ctx.filesDir, "providers").apply { mkdirs() }

    private fun req(url: String, method: String, body: String?, headers: String): String {
        val t = System.currentTimeMillis()
        return try { reqRaw(url, method, body, headers) } finally { Log.d("HayateProv", "$method ${url.take(90)} ${System.currentTimeMillis() - t}ms") }
    }

    private fun reqRaw(url: String, method: String, body: String?, headers: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = method; c.connectTimeout = 15000; c.readTimeout = 20000
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) Hayate")
        if (headers.isNotBlank()) JSONObject(headers).let { j -> j.keys().forEach { k -> c.setRequestProperty(k, j.getString(k)) } }
        if (body != null) { c.doOutput = true; c.outputStream.use { it.write(body.toByteArray()) } }
        val st = if (c.responseCode >= 400) c.errorStream else c.inputStream
        return st?.bufferedReader()?.use { it.readText() } ?: ""
    }

    private fun <T> withJs(ctx: Context, script: String, block: suspend (QuickJs) -> T): T = runBlocking {
        val app = ctx.applicationContext
        val q = QuickJs.create(Dispatchers.IO)
        try {
            q.define("_http") {
                function("get") { a -> req(a[0] as String, "GET", null, a[1] as String) }
                function("post") { a -> req(a[0] as String, "POST", a[1] as String, a[2] as String) }
            }
            q.define("_yt") {
                function("resolve") { a ->
                    YtProvider.warmUp(app)
                    val r = YoutubeDLRequest(a[0] as String).apply {
                        addOption("--no-playlist"); addOption("--extractor-args", "youtube:player_client=android")
                        addOption("-f", "best[ext=mp4]/best")
                    }
                    YoutubeDL.getInstance().getInfo(r).url ?: ""
                }
            }
            q.define("_cache") {
                function("get") { a -> jsCache[a[0] as String] ?: "" }
                function("put") { a -> jsCache[a[0] as String] = a[1] as String; Unit }
            }
            q.evaluate<Any?>(PRELUDE)
            q.evaluate<Any?>(script)
            block(q)
        } finally { q.close() }
    }

    fun validate(ctx: Context, f: File): String = try {
        withJs(ctx, f.readText()) { q ->
            val n = q.evaluate<Any?>("provider.name") as? String
            val l = q.evaluate<Any?>("provider.label") as? String
            when {
                n.isNullOrBlank() -> "missing 'name' attribute"
                l.isNullOrBlank() -> "missing 'label' attribute"
                n == "ytdlp" -> "name 'ytdlp' is reserved"
                else -> ""
            }
        }
    } catch (e: Throwable) { e.message ?: "script error" }

    fun installed(ctx: Context): List<Info> =
        (dir(ctx).listFiles { f -> f.extension == "js" } ?: emptyArray()).sortedBy { it.name }.mapNotNull { f ->
            runCatching {
                withJs(ctx, f.readText()) { q ->
                    val n = q.evaluate<Any?>("provider.name") as? String
                    val l = q.evaluate<Any?>("provider.label") as? String
                    if (n.isNullOrBlank() || l.isNullOrBlank()) null else Info(n, l, f)
                }
            }.getOrNull()
        }

    /** Blocking: run off the main thread. Falls back to the built-in provider if the selected one is gone. */
    fun getStream(ctx: Context, title: String, ep: Int, want: String): Outcome {
        val key = "${Prefs.provider}|${title.lowercase()}|$ep|$want"
        outCache[key]?.let { if (System.currentTimeMillis() - it.first < 10 * 60 * 1000L) return it.second }
        val t = System.currentTimeMillis()
        val o = fetchStream(ctx, title, ep, want)
        Log.d("HayateProv", "getStream ep $ep total ${System.currentTimeMillis() - t}ms")
        if (o.stream != null) outCache[key] = System.currentTimeMillis() to o
        return o
    }

    fun prefetch(ctx: Context, title: String, ep: Int, want: String) {
        Thread { Thread.sleep(8000); runCatching { getStream(ctx.applicationContext, title, ep, want) } }.start()
    }

    private fun fetchStream(ctx: Context, title: String, ep: Int, want: String): Outcome {
        val id = Prefs.provider
        if (id == "ytdlp") return YtProvider.getStream(ctx, title, ep, want)
        val info = installed(ctx).firstOrNull { it.id == id } ?: return YtProvider.getStream(ctx, title, ep, want)
        return try {
            val raw = withJs(ctx, info.file.readText()) { q ->
                q.evaluate<Any?>("__run(${JSONObject.quote(title)}, $ep, ${JSONObject.quote(want)})") as? String
            } ?: "null"
            when (val v = JSONTokener(raw).nextValue()) {
                is JSONObject -> {
                    val u = v.optString("url")
                    if (u.isEmpty()) Outcome(null, v.optString("notice").ifEmpty { "Episode $ep isn't available from this provider." })
                    else Outcome(StreamResult(u, v.optString("title").ifEmpty { title }, v.optString("referer"), v.optString("subtitle")), "")
                }
                is String -> Outcome(StreamResult(v, title), "")
                else -> Outcome(null, "Episode $ep isn't available from this provider.")
            }
        } catch (e: Throwable) { Outcome(null, "Provider error: ${e.message}") }
    }
}
