package com.hayate.app.ui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.core.content.edit
import android.content.Context
import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import android.util.Base64
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.hayate.app.player.MpvPlayer
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder

sealed class ScanResult {
    data class Ok(val base: String, val files: List<String>) : ScanResult()
    data class Denied(val code: Int) : ScanResult()
    data class Error(val msg: String) : ScanResult()
}

object LanClient {
    private val VIDEO = setOf("mkv", "mp4", "avi", "webm", "mov", "m4v", "ts", "flv", "wmv", "mpg", "mpeg")
    private val HREF = Regex("""href\s*=\s*"([^"]*)"""", RegexOption.IGNORE_CASE)

    fun normalize(t: String): String {
        val h = t.trim().trimEnd('/')
        if (h.isEmpty()) return h
        return if (h.startsWith("http://") || h.startsWith("https://")) h else "http://$h"
    }
    fun authHeader(pw: String?): String? =
        if (pw.isNullOrEmpty()) null else "Basic " + Base64.encodeToString("hayate:$pw".toByteArray(), Base64.NO_WRAP)
    fun decode(p: String): String = try { URLDecoder.decode(p.replace("+", "%2B"), "UTF-8") } catch (_: Exception) { p }

    private fun get(url: String, pw: String?): Pair<Int, String> {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 2500; c.readTimeout = 4000
        authHeader(pw)?.let { c.setRequestProperty("Authorization", it) }
        return try {
            val code = c.responseCode
            code to (if (code == 200) c.inputStream.bufferedReader().readText() else "")
        } finally { c.disconnect() }
    }

    private fun tree(base: String, pw: String?, alive: () -> Boolean): ScanResult {
        val out = ArrayList<String>()
        val queue = ArrayDeque<Pair<String, Int>>()
        queue.add("" to 0)
        while (queue.isNotEmpty() && alive() && out.size < 5000) {
            val (dir, depth) = queue.removeFirst()
            val (code, body) = try { get("$base/$dir", pw) } catch (_: Exception) { -1 to "" }
            if (dir.isEmpty()) {
                if (code == 401 || code == 429) return ScanResult.Denied(code)
                if (code != 200) return ScanResult.Error("Can't reach $base")
            } else if (code != 200) continue
            for (m in HREF.findAll(body)) {
                val h = m.groupValues[1].replace("&amp;", "&")
                if (h.isEmpty() || h[0] in "?#/" || h.startsWith("http") || h.startsWith("..") || h.startsWith("mailto")) continue
                if (h.endsWith("/")) { if (depth < 10) queue.add((dir + h) to depth + 1) }
                else if (decode(h).substringAfterLast('.', "").lowercase() in VIDEO) out.add(dir + h)
            }
        }
        return ScanResult.Ok(base, out.sortedBy { decode(it).lowercase() })
    }

    fun scan(host: String, pw: String?, alive: () -> Boolean): ScanResult {
        return try {
        val hosts = if (URL(host).port == -1) listOf("$host:8080", host) else listOf(host)
        var last: ScanResult = ScanResult.Error("Can't reach $host")
        for (c in hosts) { val r = tree(c, pw, alive); if (r !is ScanResult.Error) return r; last = r }
        last
        } catch (e: Exception) { ScanResult.Error(e.message ?: "Invalid host") }
    }
}

class LanPrefs(c: Context) {
    private val sp = c.applicationContext.getSharedPreferences("hayate_lan", Context.MODE_PRIVATE)
    var mode: String
        get() = sp.getString("mode", "local") ?: "local"
        set(v) = sp.edit { putString("mode", v) }
    fun host(m: String): String? = sp.getString("host_$m", null)
    fun setHost(m: String, h: String) = sp.edit { putString("host_$m", h) }
    fun pw(h: String): String? = sp.getString("pw_$h", null)
    fun setPw(h: String, p: String) = sp.edit { putString("pw_$h", p) }
    fun last(): Pair<String, String>? {
        val m = sp.getString("last_mode", null); val h = sp.getString("last_host", null)
        return if (m != null && h != null) m to h else null
    }
    fun setLast(m: String, h: String) = sp.edit { putString("last_mode", m); putString("last_host", h) }
}

private fun modeLabel(m: String) = if (m == "tailscale") "Internet LAN" else "Local LAN"
private val Border: Color get() = HBorder
@Composable
private fun LBtn(text: String, onClick: () -> Unit, accent: Boolean = false, small: Boolean = false) =
    Box(Modifier.clip(RoundedCornerShape(10.dp)).background(if (accent) HAccent else HCard).clickable(onClick = onClick)
        .padding(horizontal = if (small) 12.dp else 16.dp, vertical = if (small) 8.dp else 10.dp)) {
        Text(text, fontSize = if (small) 12.sp else 13.sp, fontWeight = FontWeight.SemiBold, color = if (accent) HOnAccent else HText)
    }

@Composable
private fun LField(v: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier, password: Boolean = false) =
    Box(modifier.clip(RoundedCornerShape(10.dp)).background(HBg).border(1.dp, Border, RoundedCornerShape(10.dp))
        .padding(horizontal = 12.dp, vertical = 11.dp)) {
        BasicTextField(v, onChange, singleLine = true, textStyle = TextStyle(color = HText, fontSize = 13.sp),
            cursorBrush = SolidColor(HAccent),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            decorationBox = { inner ->
                if (v.isEmpty()) Text(hint, color = HMuted, fontSize = 13.sp)
                inner()
            })
    }

@Composable
fun LanPlayerScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { LanPrefs(ctx) }
    val scope = rememberCoroutineScope()
    val player = remember { MpvPlayer(ctx) }
    DisposableEffect(Unit) { onDispose { player.release() } }
    PauseOnBackground { player }
    val ps by player.state.collectAsState()
    var mode by remember { mutableStateOf(prefs.mode) }
    var host by remember { mutableStateOf(prefs.host(prefs.mode) ?: "") }
    var files by remember { mutableStateOf<List<String>>(emptyList()) }
    var base by remember { mutableStateOf("") }
    var activePw by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var askPw by remember { mutableStateOf(false) }
    var pwErr by remember { mutableStateOf(false) }
    var current by remember { mutableStateOf<String?>(null) }
    var cwd by remember { mutableStateOf("") }
    var fs by remember { mutableStateOf(false) }
    var showSet by remember { mutableStateOf(false) }
    var speed by remember { mutableDoubleStateOf(1.0) }
    BackHandler { if (fs) fs = false else if (cwd.isNotEmpty()) cwd = upDir(cwd) else onBack() }

    val act = ctx as? Activity
    DisposableEffect(fs) {
        val ic = act?.window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        val on = fs
        if (on) {
            act?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            ic?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            ic?.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            if (on) { act?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT; ic?.show(WindowInsetsCompat.Type.systemBars()) }
        }
    }

    fun scan(pwOverride: String? = null) {
        val h = LanClient.normalize(host)
        if (h.isEmpty()) { status = "Enter a host URL"; return }
        if (busy) return
        busy = true; status = "Scanning…"; files = emptyList(); cwd = ""
        scope.launch {
            val pw = pwOverride ?: prefs.pw(h)
            val r = withContext(Dispatchers.IO) { LanClient.scan(h, pw) { isActive } }
            busy = false
            when (r) {
                is ScanResult.Ok -> {
                    base = r.base; files = r.files; activePw = pw
                    prefs.setHost(mode, r.base); prefs.setLast(mode, r.base); host = r.base
                    status = if (r.files.isEmpty()) "No media files found" else "${r.files.size} files"
                }
                is ScanResult.Denied ->
                    if (r.code == 429) status = "Too many attempts. Try again later."
                    else { pwErr = pw != null; askPw = true; status = "Password required" }
                is ScanResult.Error -> status = r.msg
            }
        }
    }

    fun play(f: String) {
        current = f
        player.setAuth(LanClient.authHeader(activePw))
        player.load("$base/$f")
    }

    if (askPw) {
        var pw by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { askPw = false }, containerColor = HCard,
            title = { Text("Server password") },
            text = {
                Column {
                    if (pwErr) Text("Wrong password", color = HMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                    LField(pw, { pw = it }, "Password", Modifier.fillMaxWidth(), password = true)
                }
            },
            confirmButton = { TextButton({ prefs.setPw(LanClient.normalize(host), pw); askPw = false; scan(pw) }) { Text("Connect", color = HAccent) } },
            dismissButton = { TextButton({ askPw = false }) { Text("Cancel", color = HMuted) } })
    }

    if (showSet) AlertDialog(onDismissRequest = { showSet = false }, containerColor = HCard,
        title = { Text("Settings") },
        text = {
            Column {
                Text("Playback speed", color = HMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0.5, 0.75, 1.0, 1.25, 1.5, 2.0).forEach { v ->
                        LBtn(if (v == v.toInt().toDouble()) "${v.toInt()}x" else "${v}x", { speed = v; player.setSpeed(v) }, accent = v == speed, small = true)
                    }
                }
            }
        },
        confirmButton = { TextButton({ showSet = false }) { Text("Done", color = HAccent) } })

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(if (fs) 0.dp else 14.dp)) {
        if (!fs) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LBtn("←  Back to Settings", onBack, small = true)
                Text("LAN Media Player", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp))
            }
            Row(Modifier.padding(top = 10.dp).clip(RoundedCornerShape(10.dp)).background(HCard).border(1.dp, Border, RoundedCornerShape(10.dp))) {
                listOf("local", "tailscale").forEach { k ->
                    val on = mode == k
                    Box(Modifier.clip(RoundedCornerShape(10.dp)).background(if (on) HAccent else HCard)
                        .clickable { if (!on) { prefs.setHost(mode, host.trim()); mode = k; prefs.mode = k; host = prefs.host(k) ?: "" } }
                        .padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(modeLabel(k), fontSize = 12.sp, color = if (on) HOnAccent else HMuted)
                    }
                }
            }
            prefs.last()?.let { (m, h) ->
                Box(Modifier.padding(top = 8.dp).clip(RoundedCornerShape(8.dp)).border(1.dp, Border, RoundedCornerShape(8.dp))
                    .clickable { mode = m; prefs.mode = m; host = h }.padding(horizontal = 10.dp, vertical = 5.dp)) {
                    Text("Last used: ${modeLabel(m)} · ${h.removePrefix("http://")}", color = HMuted, fontSize = 11.sp)
                }
            }
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Host URL:", color = HMuted, fontSize = 12.sp)
                LField(host, { host = it }, if (mode == "tailscale") "http://100.x.y.z" else "http://192.168.x.x:8080",
                    Modifier.weight(1f).padding(horizontal = 8.dp))
                LBtn("Scan Files", { scan() }, accent = true, small = true)
            }
            Spacer(Modifier.height(10.dp))
        }

        PlayerBox(null, player, ps, false, fs, { fs = !fs }, { showSet = true },
            Modifier.fillMaxWidth().then(if (fs) Modifier.weight(1f) else Modifier.aspectRatio(16f / 9f)))

        if (!fs) {
            Row(Modifier.padding(top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Available Files", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                if (status.isNotEmpty()) Text(status, color = HMuted, fontSize = 12.sp, modifier = Modifier.padding(start = 10.dp))
            }
            val inDir = files.filter { it.startsWith(cwd) }
            val subs = inDir.asSequence().map { it.removePrefix(cwd) }.filter { it.contains('/') }.map { it.substringBefore('/') }
                .distinct().sortedBy { LanClient.decode(it).lowercase() }.toList()
            val here = inDir.filter { !it.removePrefix(cwd).contains('/') }
            LazyColumn(Modifier.weight(1f).fillMaxWidth().border(1.dp, Border, RoundedCornerShape(14.dp)).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (cwd.isNotEmpty()) item {
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(HCard)
                        .clickable { cwd = upDir(cwd) }.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("←", color = HAccent, fontSize = 14.sp)
                        Text(LanClient.decode(cwd.trimEnd('/')), fontSize = 12.sp, color = HMuted, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 10.dp))
                    }
                }
                items(subs) { d ->
                    val n = inDir.count { it.removePrefix(cwd).startsWith("$d/") }
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(HCard)
                        .clickable { cwd = "$cwd$d/" }.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("📁", fontSize = 15.sp)
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(LanClient.decode(d), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(if (n == 1) "1 video" else "$n videos", fontSize = 10.sp, color = HMuted)
                        }
                        Text("›", color = HMuted, fontSize = 18.sp)
                    }
                }
                items(here) { f ->
                    Text(LanClient.decode(f).substringAfterLast('/'), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = if (f == current) HAccent else HText,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(HCard)
                            .clickable { play(f) }.padding(horizontal = 12.dp, vertical = 12.dp))
                }
            }
        }
    }
}

private fun upDir(c: String): String = c.trimEnd('/').substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
