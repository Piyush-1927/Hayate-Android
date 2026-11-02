package com.hayate.app.ui
import com.hayate.app.stream.AniSkip
import com.hayate.app.stream.SkipSeg
import kotlin.math.abs
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.geometry.CornerRadius

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil3.compose.AsyncImage
import com.hayate.app.player.MpvPlayer
import com.hayate.app.player.PlayerState
import com.hayate.app.stream.Providers
import com.hayate.app.stream.StreamResult
import com.hayate.core.anilist.AnimeMedia
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val WBg: Color get() = androidx.compose.ui.graphics.lerp(HBg, if (Themes.cur.dark) Color.White else Color.Black, if (Themes.cur.dark) 0.06f else 0.05f)
private val WBorder: Color get() = HBorder
private val WPanel: Color get() = if (Themes.cur.dark) androidx.compose.ui.graphics.lerp(HBg, Color.Black, 0.35f) else HCard

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun fmt(sec: Double): String {
    val t = sec.toInt().coerceAtLeast(0); val h = t / 3600; val mi = (t % 3600) / 60; val s = t % 60
    return if (h > 0) "%d:%02d:%02d".format(h, mi, s) else "%d:%02d".format(mi, s)
}

@Composable private fun PlayIcon(modifier: Modifier = Modifier.size(22.dp), color: Color = Color.White) = Canvas(modifier) {
    val p = Path().apply { moveTo(size.width * 0.2f, size.height * 0.1f); lineTo(size.width * 0.9f, size.height * 0.5f); lineTo(size.width * 0.2f, size.height * 0.9f); close() }
    drawPath(p, color)
}

@Composable private fun PauseIcon(modifier: Modifier = Modifier.size(22.dp)) = Canvas(modifier) {
    drawRect(Color.White, Offset(size.width * 0.18f, size.height * 0.1f), Size(size.width * 0.22f, size.height * 0.8f))
    drawRect(Color.White, Offset(size.width * 0.6f, size.height * 0.1f), Size(size.width * 0.22f, size.height * 0.8f))
}

@Composable private fun SpeakerIcon(muted: Boolean, volume: Double = 100.0) = Canvas(Modifier.size(20.dp)) {
    val w = size.width; val h = size.height; val sw = 1.6.dp.toPx()
    val p = Path().apply { moveTo(w * 0.04f, h * 0.38f); lineTo(w * 0.24f, h * 0.38f); lineTo(w * 0.48f, h * 0.15f); lineTo(w * 0.48f, h * 0.85f); lineTo(w * 0.24f, h * 0.62f); lineTo(w * 0.04f, h * 0.62f); close() }
    drawPath(p, Color.White)
    if (muted) {
        drawLine(Color.White, Offset(w * 0.64f, h * 0.35f), Offset(w * 0.94f, h * 0.65f), sw, StrokeCap.Round)
        drawLine(Color.White, Offset(w * 0.94f, h * 0.35f), Offset(w * 0.64f, h * 0.65f), sw, StrokeCap.Round)
    } else {
        val n = if (volume > 66.0) 3 else if (volume > 33.0) 2 else 1
        val cx = w * 0.48f; val cy = h * 0.5f
        for (i in 1..n) {
            val r = w * (0.14f + 0.14f * i)
            drawArc(Color.White, -45f, 90f, false, Offset(cx - r, cy - r), Size(2 * r, 2 * r), style = Stroke(sw, cap = StrokeCap.Round))
        }
    }
}

@Composable private fun FullIcon() = Canvas(Modifier.size(20.dp).padding(3.dp)) {
    val s = size.width; val a = s * 0.34f; val w = 2.dp.toPx()
    fun corner(x: Float, y: Float, dx: Float, dy: Float) {
        drawLine(Color.White, Offset(x, y), Offset(x + dx * a, y), w, StrokeCap.Round)
        drawLine(Color.White, Offset(x, y), Offset(x, y + dy * a), w, StrokeCap.Round)
    }
    corner(0f, 0f, 1f, 1f); corner(s, 0f, -1f, 1f); corner(0f, s, 1f, -1f); corner(s, s, -1f, -1f)
}

@Composable
fun WatchScreen(m: AnimeMedia, startEp: Int = 0, restart: Boolean = false, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var p by remember { mutableStateOf<MpvPlayer?>(null) }
    LaunchedEffect(Unit) { p = withContext(Dispatchers.Default) { MpvPlayer(ctx) } }
    DisposableEffect(Unit) { onDispose { p?.release() } }
    PauseOnBackground { p }
    val pl = p
    if (pl == null) {
        BackHandler { onBack() }
        Box(Modifier.fillMaxSize().background(HBg), Alignment.Center) { CircularProgressIndicator(color = HAccent) }
    } else WatchContent(m, pl, startEp, restart, onBack)
}

@Composable
private fun WatchContent(m: AnimeMedia, player: MpvPlayer, startEp: Int, restart: Boolean, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val ps by player.state.collectAsState()
    val desc = remember(m.id) { HtmlCompat.fromHtml(m.description, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim() }
    var lang by remember { mutableStateOf(com.hayate.app.Prefs.preferredAudio) }
    var busy by remember { mutableIntStateOf(0) }
    var current by remember { mutableIntStateOf(0) }
    var fresh by remember { mutableStateOf(restart) }
    var notice by remember { mutableStateOf("") }
    var autoNext by remember { mutableStateOf(true) }
    var full by remember { mutableStateOf(false) }
    var descOpen by remember { mutableStateOf(false) }
    var showSet by remember { mutableStateOf(false) }
    var speedPct by remember { mutableIntStateOf(100) }
    var subsOn by remember { mutableStateOf(true) }
    var quals by remember { mutableStateOf<List<Pair<Int, String>>>(emptyList()) }
    var qual by remember { mutableIntStateOf(0) }
    var cur by remember { mutableStateOf<StreamResult?>(null) }
    val skipPrefs = remember { ctx.getSharedPreferences("hayate", android.content.Context.MODE_PRIVATE) }
    var autoSkip by remember { mutableStateOf(skipPrefs.getBoolean("auto_skip", false)) }
    fun setAutoSkip(v: Boolean) { autoSkip = v; skipPrefs.edit().putBoolean("auto_skip", v).apply() }
    var segs by remember { mutableStateOf<List<SkipSeg>>(emptyList()) }
    LaunchedEffect(current, m.idMal) {
        segs = emptyList()
        val id = m.idMal
        if (current >= 1 && id != null) segs = withContext(Dispatchers.IO) { AniSkip.get(id, current) }
    }
    val seg = segs.firstOrNull { ps.position >= it.start && ps.position < it.end - 0.5 }
    LaunchedEffect(ps.duration > 0, current) { if (ps.duration > 0 && tLoad > 0) android.util.Log.d("HAYATE_T", "mpv opened ep=$current ${android.os.SystemClock.elapsedRealtime() - tLoad}ms") }
    LaunchedEffect(seg?.start, autoSkip) { val sg = seg; if (autoSkip && sg != null) player.seekTo(sg.end) }
    val total = m.airingEpisode?.let { maxOf(1, it - 1) } ?: m.episodes
    var armed by remember(current) { mutableStateOf(false) }
    LaunchedEffect(current) { if (current >= 1) { kotlinx.coroutines.delay(6000); armed = true } }
    LaunchedEffect(armed, ps.duration > 0) { if (armed && ps.duration > 0) { HistoryStore.markWatched(m, current, total); com.hayate.app.CloudSync.auto() } }
    LaunchedEffect(armed, (ps.position / 5).toInt()) {
        if (armed && !ps.loading && ps.duration > 0) HistoryStore.setProgress(m.id, current, ps.position, ps.duration)
    }
    fun play(ep: Int) {
        if (busy != 0) return
        busy = ep; notice = ""
        scope.launch {
            try {
                val t0 = android.os.SystemClock.elapsedRealtime()
                val o = withContext(Dispatchers.IO) { Providers.getStream(ctx.applicationContext, m.displayTitle, ep, lang) }
                android.util.Log.d("HAYATE_T", "getStream ep=$ep ${android.os.SystemClock.elapsedRealtime() - t0}ms")
                val s = o.stream
                if (s != null) {
                    val startAt = if (fresh) 0.0 else ((HistoryStore.progress(m.id, ep)?.first ?: 0.0) - 3.0).coerceAtLeast(0.0)
                    fun bestOf(v: List<Pair<Int, String>>) = v.filter { it.first <= 720 }.maxByOrNull { it.first } ?: v.maxByOrNull { it.first }
                    val guess = if (s.url.endsWith("/master.m3u8")) s.url.removeSuffix("master.m3u8") + "720/index.m3u8" else null
                    var vs0: List<Pair<Int, String>> = emptyList()
                    val target = guess ?: run {
                        vs0 = withContext(Dispatchers.IO) { Providers.variants(s.url, s.referer) }
                        bestOf(vs0)?.second ?: s.url
                    }
                    current = ep; fresh = false; tLoad = android.os.SystemClock.elapsedRealtime()
                    player.load(target, s.referer, "", startAt)
                    cur = s; quals = vs0; qual = if (guess != null) 720 else (bestOf(vs0)?.first ?: 0)
                    if (ep < total) Providers.prefetch(ctx, m.displayTitle, ep + 1, lang)
                    if (guess != null) scope.launch {
                        val vs = withContext(Dispatchers.IO) { Providers.variants(s.url, s.referer) }
                        if (current != ep) return@launch
                        quals = vs
                        if (vs.isNotEmpty() && vs.none { it.first == 720 }) {
                            val b = bestOf(vs)
                            if (b != null) { player.load(b.second, s.referer, "", ps.position); qual = b.first }
                        }
                    }
                    if (s.subtitle.isNotEmpty()) scope.launch {
                        kotlinx.coroutines.delay(1500)
                        if (current == ep) player.addSub(s.subtitle)
                    }
                } else notice = o.notice
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { notice = e.message ?: "Failed to load stream"
            } finally { busy = 0 }
        }
    }
    fun setQuality(h: Int) {
        val c = cur ?: return
        val url = if (h == 0) c.url else quals.firstOrNull { it.first == h }?.second ?: return
        qual = h
        player.load(url, c.referer, c.subtitle, ps.position)
    }
    LaunchedEffect(Unit) { if (startEp > 0) play(startEp) }
    LaunchedEffect(ps.ended) { if (ps.ended) com.hayate.app.CloudSync.auto(); if (ps.ended && autoNext && current in 1 until total) play(current + 1) }
    // pre-leave-sync: save the final position and sync when the player screen is left (back, arrows, any exit)
    val curNow by androidx.compose.runtime.rememberUpdatedState(current)
    val armedNow by androidx.compose.runtime.rememberUpdatedState(armed)
    DisposableEffect(Unit) {
        onDispose {
            val st = player.state.value
            if (armedNow && curNow >= 1 && st.duration > 0) {
                HistoryStore.setProgress(m.id, curNow, st.position, st.duration)
                com.hayate.app.CloudSync.auto()
            }
        }
    }
    BackHandler(full) { full = false }
    BackHandler(!full) { onBack() }
    val wasFull = remember { booleanArrayOf(false) }
    DisposableEffect(full) {
        val act = ctx.findActivity()
        val ctl = act?.let { WindowCompat.getInsetsController(it.window, it.window.decorView) }
        if (full) {
            wasFull[0] = true
            act?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            ctl?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            ctl?.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            if (wasFull[0]) act?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            restoreBars(ctl, ctx)
        }
        onDispose {
            act?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED; restoreBars(ctl, ctx)
            applyBrightness(act, android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
        }
    }
    if (showSet) AlertDialog(
        onDismissRequest = { showSet = false }, containerColor = WPanel,
        confirmButton = { TextButton({ showSet = false }) { Text("Done", color = HAccent) } },
        title = { Text("Player settings") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            SetRow("Quality", listOf(0 to "Auto") + quals.map { it.first to "${it.first}p" }, qual) { setQuality(it) }
            if (quals.isEmpty()) Text("Other qualities aren't available for this stream.", color = HMuted, fontSize = 11.sp)
            SetRow("Subtitles", listOf(1 to "On", 0 to "Off"), if (subsOn) 1 else 0) { subsOn = it == 1; player.setSubs(subsOn) }
            SetRow("Speed", listOf(50 to "0.5x", 75 to "0.75x", 100 to "1x", 125 to "1.25x", 150 to "1.5x", 200 to "2x"), speedPct) {
                speedPct = it; player.setSpeed(it / 100.0)
            }
        } },
    )
    val header: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(WBg).border(1.5.dp, WBorder, RoundedCornerShape(10.dp))
                .clickable { onBack() }, Alignment.Center) { Text("←", color = HText, fontSize = 18.sp) }
            Spacer(Modifier.width(12.dp))
            Text(m.displayTitle, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    val panel: @Composable (Modifier) -> Unit = { mod -> Column(mod.verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (autoNext) "✓ Auto Next" else "○ Auto Next", fontSize = 12.sp, color = if (autoNext) HAccent else HMuted,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { autoNext = !autoNext }.padding(8.dp))
                Text(if (autoSkip) "✓ Auto Skip" else "○ Auto Skip", fontSize = 12.sp, color = if (autoSkip) HAccent else HMuted,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { setAutoSkip(!autoSkip) }.padding(8.dp))
                Spacer(Modifier.weight(1f))
                Text("‹ Prev", fontSize = 12.sp, color = if (current > 1) HText else HMuted,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(enabled = current > 1) { play(current - 1) }.padding(8.dp))
                Text("Next ›", fontSize = 12.sp, color = if (current in 1 until total) HText else HMuted,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(enabled = current in 1 until total) { play(current + 1) }.padding(8.dp))
            }
            Box(Modifier.fillMaxWidth().padding(vertical = 8.dp).clip(RoundedCornerShape(10.dp)).background(WPanel)
                .border(1.dp, WBorder, RoundedCornerShape(10.dp)).padding(14.dp), Alignment.Center) {
                Text(buildAnnotatedString {
                    if (current > 0) {
                        append("You're watching ")
                        withStyle(SpanStyle(color = HAccent, fontWeight = FontWeight.Bold)) { append("Episode $current") }
                        append(". If the current option doesn't work, try another one below.")
                    } else append("Pick an episode below to start watching.")
                }, fontSize = 13.sp, color = HMuted, textAlign = TextAlign.Center)
            }
            if (notice.isNotEmpty()) Text(notice, color = HMuted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp))
            Text(when (lang) { "sub" -> "SUB"; "eng" -> "ENGLISH DUB"; else -> "HINDI DUB" }, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                color = HMuted, modifier = Modifier.padding(top = 6.dp))
            listOf("sub" to "Sub", "eng" to "English dub", "hin" to "Hindi dub").chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (k, l) ->
                        val on = lang == k
                        Box(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (on) HAccent else HCard)
                            .border(1.dp, if (on) HAccent else WBorder, RoundedCornerShape(10.dp))
                            .clickable { lang = k; if (current > 0) play(current) }.padding(vertical = 12.dp), Alignment.Center) {
                            Text(l, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (on) HOnAccent else HText)
                        }
                    }
                    if (row.size < 2) Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(14.dp))
            EpisodePanel(total, current, busy) { play(it) }
            Row(Modifier.fillMaxWidth().padding(top = 20.dp)) {
                AsyncImage(model = m.coverUrl, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.size(100.dp, 144.dp).clip(RoundedCornerShape(10.dp)))
                Column(Modifier.padding(start = 14.dp)) {
                    Text(m.displayTitle, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(meta(m), color = HMuted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp))
                    Box(Modifier.padding(bottom = 8.dp)) { LibraryBar(m) }
                    Text(desc, fontSize = 13.sp, lineHeight = 19.sp, color = HMuted,
                        maxLines = if (descOpen) Int.MAX_VALUE else 6, overflow = TextOverflow.Ellipsis)
                    Text(if (descOpen) "less" else "more", fontSize = 12.sp, color = HAccent,
                        modifier = Modifier.clickable { descOpen = !descOpen }.padding(vertical = 6.dp))
                }
            }
            WatchRelations(m)
            Spacer(Modifier.navigationBarsPadding().height(28.dp))
        }
    }
    val isLand = androidx.compose.ui.platform.LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE && !full
    if (isLand) androidx.compose.foundation.layout.BoxWithConstraints(
        Modifier.fillMaxSize().background(HBg).windowInsetsPadding(WindowInsets.displayCutout)) {
        val paneH = maxHeight - 44.dp - 16.dp
        val playerH = minOf(((maxWidth - 36.dp) * 0.7f) * 9f / 16f, paneH)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(WBg).border(1.5.dp, WBorder, RoundedCornerShape(10.dp))
                    .clickable { onBack() }, Alignment.Center) { Text("←", color = HText, fontSize = 16.sp) }
                Spacer(Modifier.width(10.dp))
                Text(m.displayTitle + if (current > 0) "  —  EP $current" else "", fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(Modifier.fillMaxWidth().height(playerH).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(0.7f).fillMaxHeight(), Alignment.CenterStart) {
                    Box(Modifier.fillMaxHeight().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp))) {
                PlayerBox(m, player, ps, busy != 0, full, { full = !full }, { showSet = true },
                        Modifier.fillMaxSize())
            if (seg != null && !autoSkip) SkipPill(skipLabel(seg.type)) { player.seekTo(seg.end) }
        }
                }
                Column(Modifier.weight(0.3f).fillMaxHeight()) {
                    var langMenu by remember { mutableStateOf(false) }
                    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Episodes", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Box {
                            Text(when (lang) { "sub" -> "Sub"; "eng" -> "Eng Dub"; else -> "Hin Dub" } + " ▾", fontSize = 12.sp, color = HText,
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(HCard)
                                    .border(1.dp, WBorder, RoundedCornerShape(8.dp))
                                    .clickable { langMenu = true }.padding(horizontal = 10.dp, vertical = 6.dp))
                            androidx.compose.material3.DropdownMenu(expanded = langMenu, onDismissRequest = { langMenu = false }) {
                                listOf("sub" to "Sub", "eng" to "English dub", "hin" to "Hindi dub").forEach { (k, l) ->
                                    androidx.compose.material3.DropdownMenuItem(text = { Text(l) },
                                        onClick = { langMenu = false; lang = k; if (current > 0) play(current) })
                                }
                            }
                        }
                    }
                    if (notice.isNotEmpty()) Text(notice, color = HMuted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        EpisodePanel(total, current, busy) { play(it) }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (autoNext) "✓ Auto Next" else "○ Auto Next", fontSize = 12.sp, color = if (autoNext) HAccent else HMuted,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { autoNext = !autoNext }.padding(8.dp))
                Text(if (autoSkip) "✓ Auto Skip" else "○ Auto Skip", fontSize = 12.sp, color = if (autoSkip) HAccent else HMuted,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { setAutoSkip(!autoSkip) }.padding(8.dp))
                Spacer(Modifier.weight(1f))
                Text("‹ Prev", fontSize = 12.sp, color = if (current > 1) HText else HMuted,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(enabled = current > 1) { play(current - 1) }.padding(8.dp))
                Text("Next ›", fontSize = 12.sp, color = if (current in 1 until total) HText else HMuted,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(enabled = current in 1 until total) { play(current + 1) }.padding(8.dp))
            }
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 20.dp)) {
                AsyncImage(model = m.coverUrl, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.size(110.dp, 158.dp).clip(RoundedCornerShape(10.dp)))
                Column(Modifier.padding(start = 14.dp)) {
                    Text(m.displayTitle, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(meta(m), color = HMuted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp))
                    Box(Modifier.padding(bottom = 8.dp)) { LibraryBar(m) }
                    Text(desc, fontSize = 13.sp, lineHeight = 19.sp, color = HMuted,
                        maxLines = if (descOpen) Int.MAX_VALUE else 5, overflow = TextOverflow.Ellipsis)
                    Text(if (descOpen) "less" else "more", fontSize = 12.sp, color = HAccent,
                        modifier = Modifier.clickable { descOpen = !descOpen }.padding(vertical = 6.dp))
                }
            }
            Box(Modifier.padding(horizontal = 12.dp)) { WatchRelations(m) }
            Spacer(Modifier.navigationBarsPadding().height(24.dp))
        }
    } else Column(Modifier.fillMaxSize().background(HBg)) {
        if (!full) header()
        Box(if (full) Modifier.fillMaxWidth().weight(1f) else Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            PlayerBox(m, player, ps, busy != 0, full, { full = !full }, { showSet = true },
            Modifier.fillMaxSize())
            if (seg != null && !autoSkip) SkipPill(skipLabel(seg.type)) { player.seekTo(seg.end) }
        }
        if (!full) panel(Modifier.weight(1f))
    }
}

@Composable
private fun EpisodePanel(total: Int, current: Int, busy: Int, onPick: (Int) -> Unit) {
    val ranges = remember(total) { (1..total).chunked(100).map { it.first()..it.last() } }
    var rangeIdx by remember(total) { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    val shown = remember(total, rangeIdx, query) {
        if (query.isNotBlank()) (1..total).filter { it.toString().contains(query) } else ranges.getOrNull(rangeIdx)?.toList() ?: emptyList()
    }
    val ls = rememberLazyListState()
    LaunchedEffect(current, shown) { val i = shown.indexOf(current); if (i >= 0) ls.animateScrollToItem(i) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(WPanel).border(1.dp, WBorder, RoundedCornerShape(12.dp))) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                val r = ranges.getOrNull(rangeIdx)
                Box(Modifier.clip(RoundedCornerShape(8.dp)).background(WBg).border(1.dp, WBorder, RoundedCornerShape(8.dp))
                    .clickable(enabled = ranges.size > 1) { menu = true }.padding(horizontal = 12.dp, vertical = 9.dp)) {
                    Text((if (r != null) "%03d-%03d".format(r.first, r.last) else "—") + if (ranges.size > 1) "  ▾" else "", fontSize = 12.sp, color = HMuted)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = HCard) {
                    ranges.forEachIndexed { i, rr ->
                        DropdownMenuItem(text = { Text("%03d-%03d".format(rr.first, rr.last)) }, onClick = { rangeIdx = i; menu = false })
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            BasicTextField(query, { query = it.filter(Char::isDigit).take(4) }, Modifier.weight(1f), singleLine = true,
                textStyle = TextStyle(color = HText, fontSize = 13.sp), cursorBrush = SolidColor(HAccent),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                decorationBox = { inner ->
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(WBg).border(1.dp, WBorder, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 9.dp)) {
                        if (query.isEmpty()) Text("Find number", color = HMuted, fontSize = 13.sp)
                        inner()
                    }
                })
        }
        LazyColumn(Modifier.fillMaxWidth().height(320.dp), state = ls) {
            items(shown, key = { it }) { ep ->
                val on = ep == current
                Row(Modifier.fillMaxWidth().background(if (on) HAccent else Color.Transparent).clickable { onPick(ep) }
                    .padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("$ep", fontSize = 12.sp, color = if (on) HOnAccent else HMuted, modifier = Modifier.width(34.dp))
                    Text("Episode $ep", fontSize = 14.sp, color = if (on) HOnAccent else HText,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.weight(1f))
                    if (busy == ep) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = if (on) HOnAccent else HAccent)
                    else if (on) PlayIcon(Modifier.size(14.dp), HOnAccent)
                }
            }
        }
    }
}

@Composable
private fun CtlBtn(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(8.dp), Alignment.Center) { content() }
}

@Composable
private fun SeekIcon(forward: Boolean) = Canvas(Modifier.size(20.dp)) {
    val w = size.width; val h = size.height; val sw = 1.8.dp.toPx(); val dir = if (forward) 1f else -1f
    fun chevron(x: Float) {
        val p = Path().apply { moveTo(x - dir * w * 0.2f, h * 0.15f); lineTo(x + dir * w * 0.2f, h * 0.5f); lineTo(x - dir * w * 0.2f, h * 0.85f) }
        drawPath(p, Color.White, style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
    chevron(w * 0.28f); chevron(w * 0.70f)
}

@Composable
private fun SeekBar(fraction: Float, onChange: (Float) -> Unit, onDone: () -> Unit) {
    val change by rememberUpdatedState(onChange)
    val done by rememberUpdatedState(onDone)
    Canvas(Modifier.fillMaxWidth().height(22.dp)
        .pointerInput(Unit) { detectTapGestures { o -> change((o.x / size.width).coerceIn(0f, 1f)); done() } }
        .pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragStart = { o -> change((o.x / size.width).coerceIn(0f, 1f)) },
                onDragEnd = { done() }, onDragCancel = { done() },
            ) { c, _ -> c.consume(); change((c.position.x / size.width).coerceIn(0f, 1f)) }
        }) {
        val cy = size.height / 2; val th = 3.dp.toPx()
        drawRoundRect(Color(0x55FFFFFF), Offset(0f, cy - th / 2), Size(size.width, th), CornerRadius(th / 2))
        drawRoundRect(HAccent, Offset(0f, cy - th / 2), Size(size.width * fraction, th), CornerRadius(th / 2))
        drawCircle(HAccent, 5.dp.toPx(), Offset(size.width * fraction, cy))
    }
}

private fun readBrightness(a: Activity?): Float {
    val w = a?.window?.attributes?.screenBrightness ?: -1f
    if (w >= 0f) return w
    val cr = a?.contentResolver ?: return 0.5f
    return try { android.provider.Settings.System.getInt(cr, android.provider.Settings.System.SCREEN_BRIGHTNESS) / 255f } catch (_: Exception) { 0.5f }
}

private fun applyBrightness(a: Activity?, v: Float) {
    val w = a?.window ?: return
    w.attributes = w.attributes.also { it.screenBrightness = v }
}

@Composable
internal fun PlayerBox(m: AnimeMedia?, player: MpvPlayer, ps: PlayerState, busy: Boolean, full: Boolean, onFull: () -> Unit, onSettings: (() -> Unit)?, modifier: Modifier) {
    var controls by remember { mutableStateOf(true) }
    var drag by remember { mutableStateOf<Float?>(null) }
    var hud by remember { mutableStateOf<String?>(null) }
    var hudTick by remember { mutableIntStateOf(0) }
    val act = LocalContext.current.findActivity()
    val psNow by rememberUpdatedState(ps)
    val downPos = remember { floatArrayOf(0f, 0f) }
    LaunchedEffect(controls, ps.paused, drag) {
        if (controls && !ps.paused && drag == null) { delay(3000); controls = false }
    }
    LaunchedEffect(hudTick) { if (hud != null) { delay(900); hud = null } }
    Box(modifier.background(Color.Black)
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { controls = !controls }
        .then(if (full) Modifier.pointerInput(Unit) {
            awaitEachGesture {
                val dn = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                downPos[0] = dn.position.x; downPos[1] = dn.position.y
            }
        } else Modifier)
        .then(if (full) Modifier.pointerInput(Unit) {
            var mode = 0 // 0 undecided, 1 seek, 2 brightness, 3 volume
            var sx = 0f; var sy = 0f; var accX = 0f; var startX = 0f; var blocked = false
            var bright = 0.5f; var vol = 100.0; var startPos = 0.0; var delta = 0.0
            detectDragGestures(
                onDragStart = { o -> mode = 0; sx = 0f; sy = 0f; accX = 0f; delta = 0.0; startX = o.x; startPos = psNow.position
                    val em = 28.dp.toPx(); val x = downPos[0]; val y = downPos[1]
                    blocked = x < em || y < em || x > size.width - em || y > size.height - em ||
                        (abs(x - size.width / 2f) < size.width * 0.15f && abs(y - size.height / 2f) < size.height * 0.35f) },
                onDragEnd = { if (mode == 1) player.seekTo(startPos + delta); hudTick++ },
                onDragCancel = { hudTick++ },
            ) { c, d ->
                if (blocked) return@detectDragGestures
                c.consume()
                var dx = d.x; var dy = d.y
                if (mode == 0) {
                    sx += d.x; sy += d.y
                    if (abs(sx) < 10f && abs(sy) < 10f) return@detectDragGestures
                    mode = if (abs(sx) > abs(sy)) 1 else if (startX < size.width / 2f) 2 else 3
                    if (mode == 2) bright = readBrightness(act) else if (mode == 3) vol = psNow.volume
                    dx = sx; dy = sy
                }
                when (mode) {
                    1 -> {
                        accX += dx
                        val dur = psNow.duration.coerceAtLeast(0.0)
                        delta = (accX / size.width * 120.0).coerceIn(-startPos, (dur - startPos).coerceAtLeast(0.0))
                        val a = abs(delta).toInt()
                        hud = (if (delta < 0) "-" else "+") + (if (a >= 60) fmt(a.toDouble()) else "${a}s") + "  ·  " + fmt(startPos + delta)
                    }
                    2 -> { bright = (bright - dy / size.height * 1.5f).coerceIn(0.02f, 1f); applyBrightness(act, bright); hud = "Brightness  ${(bright * 100).toInt()}%" }
                    3 -> { vol = (vol - dy / size.height * 150.0).coerceIn(0.0, 100.0); player.setVolume(vol)
                        if (psNow.muted && vol > 0) player.setMuted(false); hud = "Volume  ${vol.toInt()}%" }
                }
                hudTick++
            }
        } else Modifier)) {
        AndroidView(factory = { c ->
            SurfaceView(c).apply {
                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(h: SurfaceHolder) { player.attach(h.surface) }
                    override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, hh: Int) { player.surfaceSize(w, hh) }
                    override fun surfaceDestroyed(h: SurfaceHolder) { player.detach() }
                })
            }
        }, modifier = Modifier.fillMaxSize())
        if (!ps.active) {
            AsyncImage(model = m?.bannerUrl ?: m?.coverUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Color(0x99000000)))
        }
        if (ps.loading || ps.buffering || busy) CircularProgressIndicator(Modifier.align(Alignment.Center), color = HAccent)
        hud?.let { Text(it, color = Color.White, fontSize = 13.sp, modifier = Modifier.align(Alignment.TopCenter).padding(top = 24.dp)
            .clip(RoundedCornerShape(20.dp)).background(Color(0xCC12121C)).padding(horizontal = 16.dp, vertical = 8.dp)) }
        AnimatedVisibility(controls && ps.active, Modifier.fillMaxSize(), enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x66000000), Color.Transparent, Color(0xCC000000))))) {
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 12.dp)) {
                    val dur = ps.duration.coerceAtLeast(1.0)
                    val shownPos = drag?.toDouble() ?: ps.position
                    SeekBar((shownPos / dur).toFloat().coerceIn(0f, 1f), { drag = (it * dur).toFloat() },
                        { drag?.let { player.seekTo(it.toDouble()) }; drag = null })
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CtlBtn({ player.seekBy(-10.0) }) { SeekIcon(false) }
                        CtlBtn({ player.togglePause() }) { if (ps.paused) PlayIcon(Modifier.size(20.dp)) else PauseIcon(Modifier.size(20.dp)) }
                        CtlBtn({ player.seekBy(10.0) }) { SeekIcon(true) }
                        Text("${fmt(shownPos)} / ${fmt(ps.duration)}", color = HMuted, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
                        Spacer(Modifier.weight(1f))
                        Box(Modifier.clickable { player.toggleMute() }.padding(8.dp)) { SpeakerIcon(ps.muted || ps.volume <= 0.0, ps.volume) }
                        if (onSettings != null) CtlBtn({ onSettings() }) { GearIcon() }
                        CtlBtn({ onFull() }) { FullIcon() }
                    }
                }
            }
        }
    }
}

@Composable
private fun SetRow(title: String, opts: List<Pair<Int, String>>, sel: Int, onPick: (Int) -> Unit) {
    Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = HMuted, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        opts.forEach { (v, l) ->
            val on = v == sel
            Box(Modifier.clip(RoundedCornerShape(8.dp)).background(if (on) HAccent else WBg)
                .border(1.dp, if (on) HAccent else WBorder, RoundedCornerShape(8.dp))
                .clickable { onPick(v) }.padding(horizontal = 14.dp, vertical = 9.dp)) {
                Text(l, fontSize = 13.sp, color = if (on) HOnAccent else HText)
            }
        }
    }
}

@Composable
private fun GearIcon() = Canvas(Modifier.size(20.dp)) {
    val c = Offset(size.width / 2, size.height / 2)
    val r1 = size.width * 0.34f; val r2 = size.width * 0.48f
    fun pt(deg: Double, r: Float) = Offset(c.x + (Math.cos(Math.toRadians(deg)) * r).toFloat(), c.y + (Math.sin(Math.toRadians(deg)) * r).toFloat())
    val path = Path().apply {
        fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
        for (k in 0 until 8) {
            val a = k * 45.0
            val pts = listOf(pt(a - 17, r1) to false, pt(a - 9, r2) to true, pt(a + 9, r2) to true, pt(a + 17, r1) to false)
            pts.forEachIndexed { j, (o, _) -> if (k == 0 && j == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) }
        }
        close()
        addOval(androidx.compose.ui.geometry.Rect(c, size.width * 0.15f))
    }
    drawPath(path, Color.White)
}

private fun restoreBars(ctl: WindowInsetsControllerCompat?, ctx: android.content.Context) {
    val land = ctx.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    if (land) ctl?.hide(WindowInsetsCompat.Type.systemBars()) else ctl?.show(WindowInsetsCompat.Type.systemBars())
}

private fun skipLabel(t: String) = when (t) {
    "op", "mixed-op" -> "Skip Opening"
    "ed", "mixed-ed" -> "Skip Ending"
    else -> "Skip Recap"
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.SkipPill(label: String, onClick: () -> Unit) {
    Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = androidx.compose.ui.graphics.Color.White,
        modifier = Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 64.dp)
            .clip(RoundedCornerShape(10.dp)).background(androidx.compose.ui.graphics.Color(0xCC000000))
            .border(1.dp, androidx.compose.ui.graphics.Color(0x66FFFFFF), RoundedCornerShape(10.dp))
            .clickable { onClick() }.padding(horizontal = 14.dp, vertical = 9.dp))
}

@Composable
private fun CtlChip(text: String, on: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Text(text, fontSize = 13.sp, color = if (on) HAccent else if (enabled) HText else HMuted,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(HCard)
            .border(1.dp, if (on) HAccent else WBorder, RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp))
}

private var tLoad = 0L
