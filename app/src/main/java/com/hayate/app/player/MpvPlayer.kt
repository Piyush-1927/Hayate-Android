package com.hayate.app.player

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.Surface
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.text.CueGroup
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
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

/** UI-free Media3 ExoPlayer wrapper (class name kept so UI code is unchanged). */
@androidx.annotation.OptIn(UnstableApi::class)
class MpvPlayer(context: Context, @Suppress("UNUSED_PARAMETER") hwdec: String = "") : Player.Listener {
    private val main = Handler(Looper.getMainLooper())
    private val http = DefaultHttpDataSource.Factory()
        .setUserAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36")
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(15_000).setReadTimeoutMs(15_000)
    private val msf = DefaultMediaSourceFactory(context.applicationContext).setDataSourceFactory(http)
    private val exo: ExoPlayer = ExoPlayer.Builder(
            context.applicationContext,
            androidx.media3.exoplayer.DefaultRenderersFactory(context.applicationContext)
                .setEnableDecoderFallback(true)
                .setMediaCodecSelector { mime, secure, tunneling ->
                    val all = androidx.media3.exoplayer.mediacodec.MediaCodecUtil.getDecoderInfos(mime, secure, tunneling)
                    all.filterNot { it.name.contains("goldfish", true) }.ifEmpty { all }
                })
        .setLooper(Looper.getMainLooper())
        .setMediaSourceFactory(msf)
        .setLoadControl(DefaultLoadControl.Builder().setBufferDurationsMs(8_000, 12_000, 1_500, 3_000).build())
        .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
        .setHandleAudioBecomingNoisy(true)
        .build()
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()
    private val _sub = MutableStateFlow("")
    /** Current subtitle text (ExoPlayer can't draw subs onto a raw Surface; UI overlays this). */
    val subtitle: StateFlow<String> = _sub.asStateFlow()

    private val _aspect = MutableStateFlow(0f)
    /** Video width/height (0 until known); UI sizes the SurfaceView with it. */
    val aspect: StateFlow<Float> = _aspect.asStateFlow()

    @Volatile private var released = false
    @Volatile private var loadT = 0L
    private var referer = ""
    private var auth: String? = null
    private var curUrl = ""
    private var curAudio: String? = null
    private val curSubs = mutableListOf<String>()
    private var vol = 100.0
    private var muted = false

    private val tick = object : Runnable {
        override fun run() {
            if (released) return
            val st = exo.playbackState
            if (st == Player.STATE_READY || st == Player.STATE_BUFFERING) {
                val p = exo.currentPosition.coerceAtLeast(0) / 1000.0
                _state.update { it.copy(position = p) }
            }
            main.postDelayed(this, 250)
        }
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }
    private fun live(block: () -> Unit) { if (!released) onMain { if (!released) block() } }

    init {
        onMain { exo.addListener(this); main.post(tick) }
    }

    private fun applyHeaders() {
        val h = HashMap<String, String>()
        if (referer.isNotEmpty()) h["Referer"] = referer
        auth?.let { h["Authorization"] = it }
        http.setDefaultRequestProperties(h)
    }
    private fun applyVol() { exo.volume = if (muted) 0f else (vol / 100.0).toFloat() }
    private fun subMime(u: String): String {
        val l = u.substringBefore('?').lowercase()
        return when {
            l.endsWith(".srt") -> MimeTypes.APPLICATION_SUBRIP
            l.endsWith(".ass") || l.endsWith(".ssa") -> MimeTypes.TEXT_SSA
            else -> MimeTypes.TEXT_VTT
        }
    }
    private fun buildItem(): MediaItem {
        val b = MediaItem.Builder().setUri(curUrl)
        if (curUrl.contains(".m3u8", true)) b.setMimeType(MimeTypes.APPLICATION_M3U8)
        if (curSubs.isNotEmpty()) b.setSubtitleConfigurations(curSubs.mapIndexed { i, u ->
            MediaItem.SubtitleConfiguration.Builder(Uri.parse(u)).setMimeType(subMime(u)).setLanguage("und")
                .setSelectionFlags(if (i == curSubs.lastIndex) C.SELECTION_FLAG_DEFAULT else 0).build()
        })
        return b.build()
    }

    private fun setSource(startMs: Long) {
        val item = buildItem(); val au = curAudio
        if (au == null) exo.setMediaItem(item, startMs)
        else exo.setMediaSource(MergingMediaSource(msf.createMediaSource(item),
            ProgressiveMediaSource.Factory(http).createMediaSource(MediaItem.fromUri(au))), startMs)
    }
    fun attach(s: Surface) = live { exo.setVideoSurface(s) }
    fun surfaceSize(w: Int, h: Int) {}
    fun detach() = live { exo.clearVideoSurface() }
    fun load(url: String, referer: String = "", sub: String = "", start: Double = 0.0, audio: String? = null) = live {
        loadT = System.currentTimeMillis()
        this.referer = referer; applyHeaders()
        curUrl = url; curAudio = audio; curSubs.clear(); if (sub.isNotEmpty()) curSubs.add(sub)
        android.util.Log.d("HayateProv", "sub='$sub'")
        _sub.value = ""
        _state.update { it.copy(loading = true, active = true, ended = false, paused = false, position = 0.0, duration = 0.0) }
        setSource((start * 1000).toLong())
        exo.prepare(); exo.playWhenReady = true
    }
    fun setAuth(h: String?) = live { auth = h; applyHeaders() }
    fun setVolume(v: Int) = setVolume(v.toDouble())
    fun setVolume(v: Double) = live { vol = v.coerceIn(0.0, 100.0); applyVol(); _state.update { it.copy(volume = vol) } }
    fun setPaused(p: Boolean) = live {
        if (!p && _state.value.ended) exo.seekTo(0)
        exo.playWhenReady = !p
        _state.update { it.copy(paused = p) }
    }
    fun togglePause() = setPaused(!_state.value.paused)
    fun pauseIfPlaying() = live { if (_state.value.active && !_state.value.paused) setPaused(true) }
    fun toggleMute() = setMuted(!_state.value.muted)
    fun setMuted(m: Boolean) = live { muted = m; applyVol(); _state.update { it.copy(muted = m) } }
    fun seekTo(sec: Double) = live {
        val ms = (sec * 1000).toLong().coerceAtLeast(0)
        exo.seekTo(ms); _state.update { it.copy(position = ms / 1000.0) }
    }
    fun seekBy(sec: Double) = live {
        var ms = (exo.currentPosition + sec * 1000).toLong().coerceAtLeast(0)
        if (exo.duration != C.TIME_UNSET) ms = ms.coerceAtMost(exo.duration)
        exo.seekTo(ms); _state.update { it.copy(position = ms / 1000.0) }
    }
    fun setSpeed(v: Double) = live { exo.setPlaybackSpeed(v.toFloat()) }
    private val _heights = kotlinx.coroutines.flow.MutableStateFlow<List<Int>>(emptyList())
    val heights: kotlinx.coroutines.flow.StateFlow<List<Int>> = _heights
    private val _playH = kotlinx.coroutines.flow.MutableStateFlow(0)
    val playH: kotlinx.coroutines.flow.StateFlow<Int> = _playH
    /** HLS: cap the variant height (0 = adaptive). Switches at the next segment, no restart. */
    fun setMaxHeight(h: Int) = live {
        android.util.Log.d("HayateQ", "cap=$h")
        val b = exo.trackSelectionParameters.buildUpon()
        if (h > 0) b.setMaxVideoSize(Int.MAX_VALUE, h).setForceHighestSupportedBitrate(true)
        else b.clearVideoSizeConstraints().setForceHighestSupportedBitrate(false)
        exo.trackSelectionParameters = b.build()
    }
    fun setSubs(on: Boolean) = live {
        exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !on).build()
    }
    fun addSub(url: String) = live {
        if (curUrl.isEmpty()) return@live
        curSubs.add(url)
        val pos = exo.currentPosition; val play = exo.playWhenReady
        setSource(pos); exo.prepare(); exo.playWhenReady = play
    }
    fun release() {
        if (released) return
        released = true
        onMain { main.removeCallbacks(tick); exo.removeListener(this); exo.release() }
    }

    override fun onPlaybackStateChanged(st: Int) {
        val dur = exo.duration
        _state.update {
            val s = if (dur != C.TIME_UNSET && dur > 0) it.copy(duration = dur / 1000.0) else it
            if (st == Player.STATE_READY && s.loading)
                android.util.Log.d("HayateProv", "exo ready after ${System.currentTimeMillis() - loadT}ms")
            s.copy(
                buffering = st == Player.STATE_BUFFERING && !s.loading,
                loading = if (st == Player.STATE_READY || st == Player.STATE_ENDED) false else s.loading,
                ended = st == Player.STATE_ENDED,
                paused = if (st == Player.STATE_ENDED) true else s.paused,
            )
        }
    }
    override fun onPlayWhenReadyChanged(play: Boolean, reason: Int) {
        _state.update { it.copy(paused = !play) }
    }
    override fun onCues(cueGroup: CueGroup) {
        _sub.value = cueGroup.cues.mapNotNull { it.text?.toString() }.joinToString("\n")
    }
    override fun onTracksChanged(t: androidx.media3.common.Tracks) {
        val hs = t.groups.filter { it.type == C.TRACK_TYPE_VIDEO }.flatMap { g -> (0 until g.length).map { g.getTrackFormat(it).height } }
        android.util.Log.d("HayateQ", "video track heights=$hs")
        _heights.value = hs.filter { it > 0 }.distinct().sorted()
    }
    override fun onVideoSizeChanged(v: VideoSize) {
        android.util.Log.d("HayateQ", "playing height=${v.height}")
        _playH.value = v.height
        if (v.width > 0 && v.height > 0) _aspect.value = v.width * v.pixelWidthHeightRatio / v.height
    }
    override fun onPlayerError(error: PlaybackException) {
        android.util.Log.e("HayateProv", "exo error: ${error.errorCodeName}", error)
        _state.update { it.copy(loading = false, buffering = false) }
    }
}
