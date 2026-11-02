package com.hayate.app
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDp
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.core.view.WindowCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.hayate.app.stream.StreamResult
import com.hayate.app.ui.*
import com.hayate.core.anilist.AniListApi
import com.hayate.core.anilist.AnimeMedia
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onStop() { super.onStop(); com.hayate.app.CloudSync.auto() }

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyImmersive()
        Thread { android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND); Thread.sleep(4000); com.hayate.app.stream.YtProvider.warmUp(applicationContext) }.start()
        Prefs.init(this); com.hayate.app.ui.Themes.load(); LibraryStore.init(this); HistoryStore.init(this); installImageLoader(this); CloudSync.autoOnStart()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.navigationBarColor = 0xFF171722.toInt()
        setContent { HayateTheme {
            val sysDark = com.hayate.app.ui.Themes.cur.dark
            val strip = if (sysDark) Color.Black else Color.White
            androidx.compose.runtime.SideEffect {
                window.statusBarColor = if (sysDark) android.graphics.Color.BLACK else android.graphics.Color.WHITE
                WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = !sysDark
            }
            Box(Modifier.fillMaxSize().background(strip)) {
                Surface(Modifier.fillMaxSize().statusBarsPadding(), color = HBg) { App() } } } }
    }

    private fun applyImmersive() {
        val landscape = resources.configuration.orientation ==
            android.content.res.Configuration.ORIENTATION_LANDSCAPE
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode = if (landscape)
                android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            else android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
            window.attributes = lp
        }
        val c = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        c.systemBarsBehavior =
            androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        val bars = androidx.core.view.WindowInsetsCompat.Type.systemBars()
        if (landscape) c.hide(bars) else c.show(bars)
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        applyImmersive()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyImmersive()
    }
}

@Composable
fun App() {
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var sugg by remember { mutableStateOf<List<AnimeMedia>>(emptyList()) }
    var suggBusy by remember { mutableStateOf(false) }
    var suggOpen by remember { mutableStateOf(true) }
    var barVisible by remember { mutableStateOf(true) }
    var wantFocus by remember { mutableStateOf(false) }
    val barConn = remember { object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (available.y < -2f) barVisible = false else if (available.y > 2f) barVisible = true
            return Offset.Zero
        }
    } }
    LaunchedEffect(tab, searching) { barVisible = true }
    androidx.compose.runtime.SideEffect { com.hayate.app.ui.searchHint = when (tab) { 3 -> "Search settings…"; 5 -> "Search history…"; else -> "Search anime…" } }
    val focusMgr = LocalFocusManager.current
    LaunchedEffect(query, searching, tab) {
        if (!searching || query.isBlank() || tab == 3 || tab == 5) { sugg = emptyList(); suggBusy = false; return@LaunchedEffect }
        delay(350); suggBusy = true
        try { sugg = withContext(Dispatchers.IO) { AniListApi.searchAnime(query.trim(), 1, 8, allowAdult = Prefs.allowAdult).items } }
        catch (e: CancellationException) { throw e } catch (e: Exception) { }
        finally { suggBusy = false }
    }
    var detail by remember { mutableStateOf<AnimeMedia?>(null) }
    var lanOpen by remember { mutableStateOf(false) }
    var stream by remember { mutableStateOf<StreamResult?>(null) }
    BackHandler(enabled = stream != null || detail != null) { if (stream != null) stream = null else detail = null }
    BackHandler(enabled = searching && stream == null && detail == null) { searching = false; query = ""; if (tab == 2) tab = 0 }
    var shelf by remember { mutableStateOf<Pair<String, String>?>(null) }
    BackHandler(enabled = shelf != null && detail == null && stream == null) { shelf = null }
    var resumeFor by remember { mutableStateOf<Triple<Int, Int, Boolean>?>(null) }
    LaunchedEffect(detail) { if (detail == null) resumeFor = null }
    fun openId(id: Int, resume: Boolean = false) {
        scope.launch {
            val m = withContext(Dispatchers.IO) { try { AniListApi.getAnimeById(id) } catch (e: Exception) { null } }
            if (m != null) {
                resumeFor = if (resume) HistoryStore.resume(id, m.episodes).let { Triple(id, it.first, it.second) } else null
                detail = m
            }
        }
    }
    AnimatedContent(
        targetState = NavState(lanOpen, detail, shelf),
        transitionSpec = {
            val fwd = targetState.depth() >= initialState.depth()
            val e = FastOutSlowInEasing
            if (fwd) (slideInHorizontally(tween(320, easing = e)) { it / 4 } + fadeIn(tween(280)))
                .togetherWith(slideOutHorizontally(tween(320, easing = e)) { -it / 6 } + fadeOut(tween(200)))
            else (slideInHorizontally(tween(320, easing = e)) { -it / 6 } + fadeIn(tween(280)))
                .togetherWith(slideOutHorizontally(tween(320, easing = e)) { it / 4 } + fadeOut(tween(200)))
        },
        contentKey = { it.key() }, label = "nav",
    ) { nav ->
    val d = nav.detail
    val backCtx = androidx.compose.ui.platform.LocalContext.current
    val lastBack = remember { longArrayOf(0L, 0L) } // [0]=any back press, [1]=home toast armed
    val tabStack = remember { mutableStateListOf(0) }
    var tabBackNav by remember { mutableStateOf(false) }
    LaunchedEffect(tab) {
        if (tabBackNav) tabBackNav = false
        else if (tabStack.lastOrNull() != tab) { tabStack.add(tab); if (tabStack.size > 20) tabStack.removeAt(0) }
    }
    val canTabBack = !searching && !nav.lan && nav.shelf == null && detail == null && stream == null
    BackHandler(enabled = tab == 0 && canTabBack) {
        val now = System.currentTimeMillis()
        if (now - lastBack[0] < 500 || now - lastBack[1] < 2000) (backCtx as? android.app.Activity)?.finish()
        else { lastBack[1] = now; android.widget.Toast.makeText(backCtx, "Tap back again to exit", android.widget.Toast.LENGTH_SHORT).show() }
        lastBack[0] = now
    }
    BackHandler(enabled = tab != 0 && canTabBack) {
        val now = System.currentTimeMillis()
        if (now - lastBack[0] < 500) (backCtx as? android.app.Activity)?.finish()
        else {
            if (tabStack.size > 1) tabStack.removeAt(tabStack.lastIndex)
            tabBackNav = true
            tab = tabStack.lastOrNull() ?: 0
        }
        lastBack[0] = now
    }
    val sh = nav.shelf
    Box(Modifier.fillMaxSize().background(HBg)) {
    when {
        nav.lan -> LanPlayerScreen { lanOpen = false }
        d != null && d.status == "NOT_YET_RELEASED" -> DetailScreen(d, onBack = { detail = null }, onStream = {})
        d != null -> { val r = resumeFor?.takeIf { it.first == d.id }; WatchScreen(d, r?.second ?: 0, r?.third ?: false) { detail = null } }
        sh != null -> ShelfPage(sh.first, sh.second, onBack = { shelf = null }) { detail = it }
        else -> {
            var menu by remember { mutableStateOf(false) }
            val topPad = 56.dp
            Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().nestedScroll(barConn)) {
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            suggOpen = false; focusMgr.clearFocus()
                        }
                    }) {
                    AnimatedContent(tab, transitionSpec = {
                        val ms = Prefs.pageMs
                        when (Prefs.pageStyle) {
                            "none" -> androidx.compose.animation.EnterTransition.None togetherWith androidx.compose.animation.ExitTransition.None
                            "slide" -> {
                                val dir = if (targetState >= initialState) 1 else -1
                                (slideInHorizontally(tween(ms)) { it / 6 * dir } + fadeIn(tween(ms))) togetherWith
                                    (slideOutHorizontally(tween(ms)) { -it / 6 * dir } + fadeOut(tween(ms)))
                            }
                            else -> fadeIn(tween(ms)) togetherWith fadeOut(tween(ms))
                        }
                    }, label = "page") { t ->
                    val blurR by transition.animateDp(label = "blur") { s -> if (s == androidx.compose.animation.EnterExitState.Visible) 0.dp else (Prefs.pageBlur * 0.2f).dp }
                    Box(Modifier.fillMaxSize().blur(blurR)) {
                    when (t) {
                        0 -> HomeScreen(onOpenId = { openId(it, true) }, onHistory = { tab = 5 }, onShelf = { k, t -> shelf = k to t }) { detail = it }
                        1 -> Box(Modifier.padding(top = topPad)) { ScheduleScreen { openId(it) } }
                        2 -> Box(Modifier.padding(top = topPad)) { SearchScreen(query) { detail = it } }
                        4 -> Box { CatalogueScreen(query, topPad) { openId(it) } }
                        5 -> Box(Modifier.padding(top = topPad)) { HistoryScreen(query) { openId(it, true) } }
                        6 -> Box(Modifier.padding(top = topPad)) { LibraryScreen { openId(it) } }
                        else -> Box(Modifier.padding(top = topPad)) { SettingsScreen(query) { lanOpen = true } }
                    }
                    }
                    }
                    }
                    if (searching && tab != 2 && tab != 3 && tab != 5 && query.isNotBlank() && suggOpen)
                        SuggestPanel(sugg, suggBusy, { detail = it }, Modifier.align(Alignment.TopCenter).padding(top = topPad))
                }
                Column(Modifier.align(Alignment.TopCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(HBg.copy(alpha = 0.6f), HBg.copy(alpha = 0.3f), Color.Transparent)))) {
                AnimatedVisibility(barVisible, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                TopBar(searching, query, { query = it; suggOpen = true }, { searching = true; suggOpen = true; wantFocus = true },
                    { searching = false; query = ""; if (tab == 2) tab = 0 },
                    { if (query.isNotBlank() && tab != 3 && tab != 5) tab = 2 },
                    { searching = false; query = ""; tab = 0 }, { searching = false; query = ""; tab = 1 },
                    { searching = false; query = ""; tab = 3 }, onFocus = { suggOpen = true; wantFocus = false }, autoFocus = wantFocus,
                    onCatalogue = { searching = false; query = ""; tab = 4 },
                    onHistory = { searching = false; query = ""; tab = 5 },
                    onLibrary = { searching = false; query = ""; tab = 6 }
                )
                }
                }
            }
        }
    }
    }
    }
}

private data class NavState(val lan: Boolean, val detail: AnimeMedia?, val shelf: Pair<String, String>?) {
    fun depth() = if (detail != null) 2 else if (lan || shelf != null) 1 else 0
    fun key() = when { lan -> "lan"; detail != null -> "d${detail.id}"; shelf != null -> "s"; else -> "main" }
}
