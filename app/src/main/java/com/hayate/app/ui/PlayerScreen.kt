package com.hayate.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.hayate.app.stream.StreamResult

@Composable
fun PlayerScreen(s: StreamResult, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val exo = remember(s.url) {
        ExoPlayer.Builder(ctx).build().apply { setMediaItem(MediaItem.fromUri(s.url)); prepare(); playWhenReady = true }
    }
    DisposableEffect(exo) { onDispose { exo.release() } }
    Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding()) {
        Text("←  Back", color = Color.White, modifier = Modifier.clickable { onBack() }.padding(16.dp))
        AndroidView(factory = { PlayerView(it).apply { player = exo } },
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f))
        Text(s.title, color = HMuted, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
    }
}
