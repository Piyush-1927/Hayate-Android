package com.hayate.app

import android.util.Base64
import com.hayate.app.ui.HistoryStore
import com.hayate.app.ui.LibraryStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** WebDAV sync of hayate_sync.json, same envelope as the desktop app (format "hayate-sync"). */
object CloudSync {
    private const val FORMAT = "hayate-sync"
    private const val FILE = "hayate_sync.json"
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private fun isLocal(h: String): Boolean {
        val s = h.lowercase()
        if (s == "localhost" || s.endsWith(".local")) return true
        val p = s.split(".").mapNotNull { it.toIntOrNull() }
        if (p.size != 4) return false
        return p[0] == 10 || p[0] == 127 || (p[0] == 192 && p[1] == 168) ||
            (p[0] == 172 && p[1] in 16..31) || (p[0] == 100 && p[1] in 64..127)
    }

    private fun target(): URL {
        val base = Prefs.davUrl.trim().trimEnd('/')
        val u = try { URL(base) } catch (e: Exception) { null }
        if (u == null || (u.protocol != "http" && u.protocol != "https") || u.host.isNullOrEmpty())
            throw IllegalStateException("Enter the full WebDAV folder URL, starting with https://")
        if (u.protocol == "http" && !isLocal(u.host))
            throw IllegalStateException("Use https:// for a WebDAV server outside your network.")
        return URL("$base/$FILE")
    }

    private fun open(method: String): HttpURLConnection {
        val c = target().openConnection() as HttpURLConnection
        c.requestMethod = method; c.connectTimeout = 20000; c.readTimeout = 20000
        if (Prefs.davUser.isNotEmpty())
            c.setRequestProperty("Authorization", "Basic " +
                Base64.encodeToString("${Prefs.davUser}:${Prefs.davPass}".toByteArray(), Base64.NO_WRAP))
        return c
    }

    private fun check(code: Int, put: Boolean) {
        if (code == 401 || code == 403) throw IllegalStateException("WebDAV login was rejected.")
        if (put && (code == 404 || code == 409)) throw IllegalStateException("WebDAV folder not found. Create it first, then check the URL.")
        if (code !in 200..299) throw IllegalStateException("WebDAV error $code")
    }

    private fun pull(): JSONObject? {
        val c = open("GET")
        try {
            val code = c.responseCode
            if (code == 404) return null
            check(code, false)
            val txt = c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            return try { JSONObject(txt) } catch (e: Exception) {
                throw IllegalStateException("The sync file on the WebDAV server is unreadable (left untouched).")
            }
        } finally { c.disconnect() }
    }

    private fun push(p: JSONObject) {
        val c = open("PUT"); c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        try {
            val b = p.toString().toByteArray(Charsets.UTF_8)
            c.setFixedLengthStreamingMode(b.size)
            c.outputStream.use { it.write(b) }
            check(c.responseCode, true)
        } finally { c.disconnect() }
    }

    private fun payload(): JSONObject {
        val now = System.currentTimeMillis() / 1000
        val hist = JSONObject().put("format", "hayate-history").put("version", 1)
            .put("exported_at", now).put("history", HistoryStore.exportData())
        val lib = JSONObject().put("version", 1).put("library", LibraryStore.exportRows())
        return JSONObject().put("format", FORMAT).put("version", 1).put("saved_at", now)
            .put("history", hist).put("library", lib)
    }

    private fun merge(r: JSONObject): Int {
        if (r.optString("format") != FORMAT) throw IllegalStateException("That file is not a Hayate sync file.")
        var n = 0
        r.optJSONObject("history")?.let { h -> n += HistoryStore.mergeRemote(h.optJSONObject("history") ?: h) }
        r.optJSONObject("library")?.optJSONArray("library")?.let { n += LibraryStore.mergeRows(it) }
        return n
    }

    /** pull -> merge (main) -> push. Returns a user-facing message; never throws. */
    suspend fun sync(): String = mutex.withLock {
        try {
            if (Prefs.cloudBackend != "webdav" && Prefs.cloudBackend != "google") "Cloud sync is off."
            else {
                val remote = withContext(Dispatchers.IO) { if (Prefs.cloudBackend == "google") GoogleDrive.read() else pull() }
                val changed = if (remote != null) withContext(Dispatchers.Main) { merge(remote) } else 0
                val p = withContext(Dispatchers.Main) { payload() }
                withContext(Dispatchers.IO) { if (Prefs.cloudBackend == "google") GoogleDrive.write(p) else push(p) }
                "Synced. $changed entries updated from the cloud."
            }
        } catch (e: Exception) { e.message ?: e.toString() }
    }

    private val lock = Any()
    private var running = false
    private var again = false

    /** Fire-and-forget sync if a backend is on and auto-sync is enabled. Overlapping calls coalesce into one re-run. */
    fun auto() {
        if (Prefs.cloudBackend == "off" || !Prefs.cloudAuto || needsLink()) return
        synchronized(lock) { if (running) { again = true; return }; running = true }
        scope.launch {
            while (true) {
                sync()
                synchronized(lock) { if (!again) { running = false; return@launch }; again = false }
            }
        }
    }

    // ---- connect-time choice ----
    class Probe(val remote: JSONObject?, val error: String?)

    private fun identity(): String = when (Prefs.cloudBackend) {
        "google" -> "google:" + Prefs.gEmail
        "webdav" -> "webdav:" + Prefs.davUrl.trim().trimEnd('/')
        else -> ""
    }
    fun needsLink(): Boolean = Prefs.cloudBackend != "off" && Prefs.cloudLinked != identity()
    fun markLinked() { Prefs.cloudLinked = identity() }
    fun unlink() { Prefs.cloudLinked = "" }

    private fun pullAny(): JSONObject? = if (Prefs.cloudBackend == "google") GoogleDrive.read() else pull()
    private fun pushAny(p: JSONObject) { if (Prefs.cloudBackend == "google") GoogleDrive.write(p) else push(p) }

    suspend fun probe(): Probe = withContext(Dispatchers.IO) {
        try { Probe(pullAny(), null) } catch (e: Exception) { Probe(null, e.message ?: e.toString()) }
    }

    fun remoteHasData(r: JSONObject?): Boolean {
        if (r == null || r.optString("format") != FORMAT) return false
        val h = r.optJSONObject("history")
        val hh = h?.optJSONObject("history") ?: h
        val hist = hh != null && hh.keys().asSequence().any { hh.optJSONObject(it) != null }
        val lib = (r.optJSONObject("library")?.optJSONArray("library")?.length() ?: 0) > 0
        return hist || lib
    }
    fun localHasData(): Boolean = HistoryStore.exportData().length() > 0 || LibraryStore.exportRows().length() > 0

    suspend fun linkMerge(): String { markLinked(); return sync() }

    suspend fun linkUseThisDevice(): String = mutex.withLock {
        try {
            val p = withContext(Dispatchers.Main) { payload() }
            withContext(Dispatchers.IO) { pushAny(p) }
            markLinked()
            "Cloud data replaced with this device's data."
        } catch (e: Exception) { e.message ?: e.toString() }
    }

    suspend fun linkUseCloud(remote: JSONObject): String = mutex.withLock {
        try {
            if (remote.optString("format") != FORMAT) throw IllegalStateException("That file is not a Hayate sync file.")
            withContext(Dispatchers.Main) {
                remote.optJSONObject("history")?.optJSONObject("history")?.let { HistoryStore.replaceAll(it) }
                remote.optJSONObject("library")?.optJSONArray("library")?.let { LibraryStore.replaceRows(it) }
            }
            markLinked()
            "This device now matches your cloud."
        } catch (e: Exception) { e.message ?: e.toString() }
    }

    fun autoOnStart() = auto()
}
