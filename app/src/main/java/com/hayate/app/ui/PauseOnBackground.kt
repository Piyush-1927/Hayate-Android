package com.hayate.app.ui

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.hayate.app.player.MpvPlayer

/** Pauses the player whenever the app leaves the foreground (home, app switch, screen off). */
@Composable
internal fun PauseOnBackground(player: () -> MpvPlayer?) {
    val get by rememberUpdatedState(player)
    var c: Context? = LocalContext.current
    while (c is ContextWrapper && c !is LifecycleOwner) c = c.baseContext
    val owner = c as? LifecycleOwner ?: return
    DisposableEffect(owner) {
        val ob = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_STOP) get()?.pauseIfPlaying() }
        owner.lifecycle.addObserver(ob)
        onDispose { owner.lifecycle.removeObserver(ob) }
    }
}
