package com.hayate.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom

/** Google Drive appDataFolder sync: same client, scope and file as the desktop (core/cloud.py). */
object GoogleDrive {
    private const val FILE = "hayate_sync.json"
    private const val AUTH = "https://accounts.google.com/o/oauth2/v2/auth"
    private const val TOKEN = "https://oauth2.googleapis.com/token"
    private const val SCOPE = "openid email https://www.googleapis.com/auth/drive.appdata"
    private const val FILES = "https://www.googleapis.com/drive/v3/files"
    private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3/files"
    private val FORM = mapOf("Content-Type" to "application/x-www-form-urlencoded")

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    private fun rand(n: Int) = b64(ByteArray(n).also { SecureRandom().nextBytes(it) })
    private fun form(m: Map<String, String>) = m.entries.joinToString("&") {
        URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8")
    }

    private fun req(method: String, url: String, headers: Map<String, String> = emptyMap(), body: ByteArray? = null): Pair<Int, ByteArray> {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method; c.connectTimeout = 20000; c.readTimeout = 30000
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            if (body != null) { c.doOutput = true; c.setFixedLengthStreamingMode(body.size); c.outputStream.use { it.write(body) } }
            val code = c.responseCode
            val s = if (code in 200..299) c.inputStream else c.errorStream
            return code to (s?.use { it.readBytes() } ?: ByteArray(0))
        } finally { c.disconnect() }
    }

    /** Browser sign-in (PKCE, loopback redirect). Blocks up to 3 min. Returns the account email. */
    private fun signIn(ctx: Context): String {
        val verifier = rand(48)
        val challenge = b64(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
        val state = rand(16)
        val srv = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        srv.soTimeout = 1000
        val redirect = "http://127.0.0.1:${srv.localPort}"
        val url = AUTH + "?" + form(mapOf(
            "client_id" to GoogleCreds.ID, "redirect_uri" to redirect, "response_type" to "code",
            "scope" to SCOPE, "code_challenge" to challenge, "code_challenge_method" to "S256",
            "state" to state, "access_type" to "offline", "prompt" to "consent"))
        var code = ""; var gotState = ""; var err = ""
        try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            val end = System.currentTimeMillis() + 180_000
            while (code.isEmpty() && err.isEmpty() && System.currentTimeMillis() < end) {
                val s = try { srv.accept() } catch (e: SocketTimeoutException) { continue }
                s.use { sock ->
                    sock.soTimeout = 5000
                    val line = try { sock.getInputStream().bufferedReader().readLine() ?: "" } catch (e: Exception) { "" }
                    val path = line.split(" ").getOrNull(1) ?: ""
                    val ok = path.startsWith("/?")
                    if (ok) {
                        val q = path.substring(2).split("&").mapNotNull {
                            val i = it.indexOf('='); if (i < 0) null
                            else it.substring(0, i) to URLDecoder.decode(it.substring(i + 1), "UTF-8")
                        }.toMap()
                        code = q["code"] ?: ""; gotState = q["state"] ?: ""; err = q["error"] ?: ""
                    }
                    val html = if (ok) "<html><body style='font-family:sans-serif;text-align:center;margin-top:20vh'><h2>Hayate is connected.</h2><p>You can return to the app.</p></body></html>" else ""
                    val b = html.toByteArray()
                    sock.getOutputStream().apply {
                        write(("HTTP/1.1 ${if (ok) "200 OK" else "404 Not Found"}\r\nContent-Type: text/html; charset=utf-8\r\nConnection: close\r\nContent-Length: ${b.size}\r\n\r\n").toByteArray())
                        write(b); flush()
                    }
                }
            }
        } finally { srv.close() }
        if (err.isNotEmpty() || code.isEmpty()) throw IllegalStateException("Sign-in was cancelled or timed out.")
        if (gotState != state) throw IllegalStateException("Sign-in failed (state mismatch).")

        val (rc, rb) = req("POST", TOKEN, FORM, form(mapOf(
            "client_id" to GoogleCreds.ID, "client_secret" to GoogleCreds.SECRET, "code" to code,
            "code_verifier" to verifier, "grant_type" to "authorization_code", "redirect_uri" to redirect)).toByteArray())
        if (rc != 200) throw IllegalStateException("Google rejected the sign-in ($rc).")
        val t = JSONObject(String(rb, Charsets.UTF_8))
        val refresh = t.optString("refresh_token")
        if (refresh.isEmpty()) throw IllegalStateException("Google didn't return a refresh token. Remove Hayate under your Google account's third-party access, then sign in again.")
        var email = ""
        try {
            val (ec, eb) = req("GET", "https://openidconnect.googleapis.com/v1/userinfo",
                mapOf("Authorization" to "Bearer ${t.getString("access_token")}"))
            if (ec == 200) email = JSONObject(String(eb, Charsets.UTF_8)).optString("email")
        } catch (_: Exception) {}
        Prefs.gRefresh = refresh
        Prefs.gAccess = t.getString("access_token")
        Prefs.gExpiry = System.currentTimeMillis() + t.optLong("expires_in", 3600) * 1000 - 60_000
        Prefs.gEmail = email.ifEmpty { "Google account" }
        return Prefs.gEmail
    }

    private fun token(): String {
        if (Prefs.gAccess.isNotEmpty() && Prefs.gExpiry > System.currentTimeMillis()) return Prefs.gAccess
        if (Prefs.gRefresh.isEmpty()) throw IllegalStateException("Sign in with Google first.")
        val (code, b) = req("POST", TOKEN, FORM, form(mapOf(
            "client_id" to GoogleCreds.ID, "client_secret" to GoogleCreds.SECRET,
            "refresh_token" to Prefs.gRefresh, "grant_type" to "refresh_token")).toByteArray())
        if (code == 400 || code == 401) throw IllegalStateException("Google sign-in expired. Sign in again.")
        if (code !in 200..299) throw IllegalStateException("Google error $code")
        val t = JSONObject(String(b, Charsets.UTF_8))
        Prefs.gAccess = t.getString("access_token")
        Prefs.gExpiry = System.currentTimeMillis() + t.optLong("expires_in", 3600) * 1000 - 60_000
        return Prefs.gAccess
    }

    private fun check(code: Int) {
        if (code == 401) throw IllegalStateException("Google sign-in expired. Sign in again.")
        if (code == 403) throw IllegalStateException("Google Drive refused access (is the Drive API enabled for this app?).")
        if (code !in 200..299) throw IllegalStateException("Google Drive error $code")
    }

    private fun find(auth: Map<String, String>): String? {
        val (code, b) = req("GET", FILES + "?" + form(mapOf(
            "spaces" to "appDataFolder", "pageSize" to "1", "fields" to "files(id)",
            "q" to "name = '$FILE' and trashed = false")), auth)
        check(code)
        val arr = JSONObject(String(b, Charsets.UTF_8)).optJSONArray("files")
        return if (arr != null && arr.length() > 0) arr.getJSONObject(0).getString("id") else null
    }

    /** Blocking: call off the main thread. */
    fun read(): JSONObject? {
        val auth = mapOf("Authorization" to "Bearer ${token()}")
        val id = find(auth) ?: return null
        val (code, b) = req("GET", "$FILES/$id?alt=media", auth)
        check(code)
        return try { JSONObject(String(b, Charsets.UTF_8)) } catch (e: Exception) {
            throw IllegalStateException("The sync file in Google Drive is unreadable (left untouched).")
        }
    }

    /** Blocking: call off the main thread. */
    fun write(p: JSONObject) {
        val auth = mapOf("Authorization" to "Bearer ${token()}")
        val body = p.toString().toByteArray(Charsets.UTF_8)
        val id = find(auth)
        val (code, _) = if (id != null) {
            req("POST", "$UPLOAD/$id?uploadType=media",
                auth + mapOf("Content-Type" to "application/json", "X-HTTP-Method-Override" to "PATCH"), body)
        } else {
            val meta = JSONObject().put("name", FILE).put("parents", JSONArray().put("appDataFolder")).toString()
            val b = "hayate" + rand(8)
            val mp = ("--$b\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$meta\r\n--$b\r\nContent-Type: application/json\r\n\r\n").toByteArray() +
                body + "\r\n--$b--".toByteArray()
            req("POST", "$UPLOAD?uploadType=multipart", auth + mapOf("Content-Type" to "multipart/related; boundary=$b"), mp)
        }
        check(code)
    }

    suspend fun connect(ctx: Context): String = withContext(Dispatchers.IO) {
        val app = ctx.applicationContext
        SignInService.start(app)
        try { delay(300); "Connected as ${signIn(app)}" } catch (e: Exception) { e.message ?: e.toString() }
        finally { SignInService.stop(app) }
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        val r = Prefs.gRefresh
        Prefs.gRefresh = ""; Prefs.gAccess = ""; Prefs.gExpiry = 0L; Prefs.gEmail = ""
    }
}
