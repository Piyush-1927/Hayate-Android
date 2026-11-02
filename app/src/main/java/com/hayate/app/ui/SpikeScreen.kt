package com.hayate.app.ui

import android.content.Context
import android.view.ViewGroup
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private var ytReady = false

private fun resolve(ctx: Context, url: String): String {
    if (!ytReady) {
        YoutubeDL.getInstance().init(ctx)
        FFmpeg.getInstance().init(ctx)
        ytReady = true
    }
    val req = YoutubeDLRequest(url).apply { addOption("-f", "best[ext=mp4]/best") }
    return YoutubeDL.getInstance().getInfo(req).url ?: error("no stream url")
}

@Composable
fun SpikeScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var link by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Paste a YouTube link") }
    val player = remember { ExoPlayer.Builder(ctx).build() }
    DisposableEffect(Unit) { onDispose { player.release() } }

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
        OutlinedTextField(link, { link = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("YouTube URL") })
        Spacer(Modifier.height(8.dp))
        Button(onClick = {
            status = "Resolving..."
            scope.launch {
                try {
                    val u = withContext(Dispatchers.IO) { resolve(ctx.applicationContext, link.trim()) }
                    player.setMediaItem(MediaItem.fromUri(u)); player.prepare(); player.play()
                    status = "Playing"
                } catch (e: Throwable) { status = "Error: ${e.message}" }
            }
        }) { Text("Play") }
        Text(status, Modifier.padding(vertical = 8.dp))
        AndroidView(
            factory = { PlayerView(it).apply { this.player = player
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT) } },
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
    }
}
