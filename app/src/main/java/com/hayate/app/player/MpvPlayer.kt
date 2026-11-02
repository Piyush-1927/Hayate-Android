package com.hayate.app.player

import android.content.Context
import android.view.Surface
import dev.jdtech.mpv.MPVLib
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class PlayerState(
    val position: Double = 0.0, val duration: Double = 0.0,
    val paused: Boolean = true, val buffering: Boolean = false,
    val loading: Boolean = false, val active: Boolean = false,
    val ended: Boolean = false, val muted: Boolean = false, val volume: Double = 100.0,
)

/** UI-free libmpv wrapper. All controls live in Compose; mpv only draws video. */
class MpvPlayer(context: Context, hwdec: String = "mediacodec-copy") : MPVLib.EventObserver {
    private val mpv: MPVLib = MPVLib.create(context.applicationContext) ?: error("libmpv failed to initialise")
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()
    @Volatile private var released = false
    @Volatile private var loadT = 0L
    @Volatile private var pendingSeek = 0.0
    @Volatile private var pendingSub = ""

    init {
        mpv.setOptionString("vo", "gpu")
        mpv.setOptionString("gpu-context", "android")
        mpv.setOptionString("opengl-es", "yes")
        mpv.setOptionString("hwdec", hwdec)            // use "no" on the emulator
        mpv.setOptionString("ao", "audiotrack,opensles")
        mpv.setOptionString("tls-verify", "no")        // no CA bundle shipped yet; tighten later
        mpv.setOptionString("keep-open", "yes")
        mpv.setOptionString("force-window", "no")
        mpv.setOptionString("ytdl", "no")
        mpv.setOptionString("demuxer-lavf-probesize", "524288")
        mpv.setOptionString("demuxer-lavf-analyzeduration", "1")
        mpv.setOptionString("cache", "yes")
        mpv.setOptionString("demuxer-max-bytes", "64MiB")
        mpv.setOptionString("demuxer-readahead-secs", "30")
        mpv.setOptionString("network-timeout", "15")
        mpv.init()
        mpv.observeProperty("time-pos", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
        mpv.observeProperty("duration", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
        mpv.observeProperty("pause", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
        mpv.observeProperty("paused-for-cache", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
        mpv.observeProperty("eof-reached", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
        mpv.observeProperty("mute", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
        mpv.observeProperty("volume", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
        mpv.addObserver(this)
    }

    private inline fun live(block: () -> Unit) { if (!released) block() }

    fun attach(s: Surface) = live {
        mpv.attachSurface(s); mpv.setOptionString("force-window", "yes"); mpv.setPropertyString("vo", "gpu")
    }
    fun surfaceSize(w: Int, h: Int) = live { mpv.setPropertyString("android-surface-size", "${w}x$h") }
    fun detach() = live {
        mpv.setPropertyString("vo", "null"); mpv.setOptionString("force-window", "no"); mpv.detachSurface()
    }
    fun load(url: String, referer: String = "", sub: String = "", start: Double = 0.0) = live {
        loadT = System.currentTimeMillis(); pendingSeek = start
        mpv.setPropertyString("referrer", referer)
        pendingSub = sub; android.util.Log.d("HayateProv", "sub='$sub'")
        mpv.setPropertyBoolean("pause", false); mpv.command(arrayOf("loadfile", url, "replace"))
    }
    fun setAuth(h: String?) = live { mpv.setPropertyString("http-header-fields", if (h == null) "" else "Authorization: $h") }
    fun setVolume(v: Int) = live { mpv.setPropertyString("volume", v.toString()) }
    fun setPaused(p: Boolean) = live { mpv.setPropertyBoolean("pause", p) }
    fun togglePause() = setPaused(!_state.value.paused)
    fun pauseIfPlaying() = live { if (_state.value.active && !_state.value.paused) mpv.setPropertyBoolean("pause", true) }
    fun toggleMute() = live { mpv.setPropertyBoolean("mute", !_state.value.muted) }
    fun setMuted(m: Boolean) = live { mpv.setPropertyBoolean("mute", m) }
    fun setVolume(v: Double) = live { mpv.setPropertyDouble("volume", v.coerceIn(0.0, 100.0)) }
    fun seekTo(sec: Double) = live { mpv.command(arrayOf("seek", sec.toString(), "absolute")) }
    fun seekBy(sec: Double) = live { mpv.command(arrayOf("seek", sec.toString(), "relative")) }
    fun setSpeed(v: Double) = live { mpv.setPropertyDouble("speed", v) }
    fun setSubs(on: Boolean) = live { mpv.setPropertyString("sid", if (on) "auto" else "no") }
    fun addSub(url: String) = live { mpv.command(arrayOf("sub-add", url, "select")) }
    fun release() {
        if (released) return
        released = true; mpv.removeObserver(this); mpv.destroy()
    }

    override fun eventProperty(property: String) {}
    override fun eventProperty(property: String, value: Long) {}
    override fun eventProperty(property: String, value: String) {}
    override fun eventProperty(property: String, value: Double) {
        when (property) {
            "time-pos" -> _state.update { it.copy(position = value) }
            "duration" -> _state.update { it.copy(duration = value) }
            "volume" -> _state.update { it.copy(volume = value) }
        }
    }
    override fun eventProperty(property: String, value: Boolean) {
        when (property) {
            "pause" -> _state.update { it.copy(paused = value) }
            "paused-for-cache" -> _state.update { it.copy(buffering = value) }
            "eof-reached" -> _state.update { it.copy(ended = value) }
            "mute" -> _state.update { it.copy(muted = value) }
        }
    }
    override fun event(eventId: Int) {
        when (eventId) {
            MPVLib.MpvEvent.MPV_EVENT_START_FILE -> _state.update { it.copy(loading = true, active = true, ended = false, position = 0.0, duration = 0.0) }
            MPVLib.MpvEvent.MPV_EVENT_FILE_LOADED, MPVLib.MpvEvent.MPV_EVENT_END_FILE -> {
                android.util.Log.d("HayateProv", "mpv loaded after ${System.currentTimeMillis() - loadT}ms")
                if (eventId == MPVLib.MpvEvent.MPV_EVENT_FILE_LOADED && pendingSub.isNotEmpty()) { runCatching { mpv.command(arrayOf("sub-add", pendingSub, "select")) }; pendingSub = "" }
                if (eventId == MPVLib.MpvEvent.MPV_EVENT_FILE_LOADED && pendingSeek > 0) { mpv.command(arrayOf("seek", pendingSeek.toString(), "absolute")); pendingSeek = 0.0 }
                _state.update { it.copy(loading = false) }
            }
        }
    }
}
