package com.hayate.app

import android.content.Context
import android.content.SharedPreferences
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.CachePolicy
import okio.Path.Companion.toOkioPath

object Prefs {
    private lateinit var sp: SharedPreferences
    fun init(c: Context) { if (!::sp.isInitialized) sp = c.applicationContext.getSharedPreferences("hayate", Context.MODE_PRIVATE) }
    var provider: String
        get() = sp.getString("provider", "ytdlp") ?: "ytdlp"
        set(v) = sp.edit().putString("provider", v).apply()
    var allowAdult: Boolean
        get() = sp.getBoolean("adult", false)
        set(v) = sp.edit().putBoolean("adult", v).apply()
    var cacheOn: Boolean
        get() = sp.getBoolean("cache_on", true)
        set(v) = sp.edit().putBoolean("cache_on", v).apply()
    var pageStyle: String
        get() = sp.getString("page_style", "fade") ?: "fade"
        set(v) = sp.edit().putString("page_style", v).apply()
    var pageMs: Int
        get() = sp.getInt("page_ms", 260)
        set(v) = sp.edit().putInt("page_ms", v).apply()
    var cloudBackend: String
        get() = sp.getString("cloud_backend", "off") ?: "off"
        set(v) = sp.edit().putString("cloud_backend", v).apply()
    var davUrl: String
        get() = sp.getString("dav_url", "") ?: ""
        set(v) = sp.edit().putString("dav_url", v).apply()
    var davUser: String
        get() = sp.getString("dav_user", "") ?: ""
        set(v) = sp.edit().putString("dav_user", v).apply()
    var davPass: String
        get() = sp.getString("dav_pass", "") ?: ""
        set(v) = sp.edit().putString("dav_pass", v).apply()
    var cloudAuto: Boolean
        get() = sp.getBoolean("cloud_auto", true)
        set(v) = sp.edit().putBoolean("cloud_auto", v).apply()
    var gRefresh: String
        get() = sp.getString("g_refresh", "") ?: ""
        set(v) = sp.edit().putString("g_refresh", v).apply()
    var gAccess: String
        get() = sp.getString("g_access", "") ?: ""
        set(v) = sp.edit().putString("g_access", v).apply()
    var gExpiry: Long
        get() = sp.getLong("g_expiry", 0L)
        set(v) = sp.edit().putLong("g_expiry", v).apply()
    var gEmail: String
        get() = sp.getString("g_email", "") ?: ""
        set(v) = sp.edit().putString("g_email", v).apply()
    var cloudLinked: String
        get() = sp.getString("cloud_linked", "") ?: ""
        set(v) = sp.edit().putString("cloud_linked", v).apply()
    var preferredAudio: String
        get() = sp.getString("preferred_audio", "sub") ?: "sub"
        set(v) = sp.edit().putString("preferred_audio", v).apply()
    var theme: String
        get() = sp.getString("theme", "Midnight") ?: "Midnight"
        set(v) = sp.edit().putString("theme", v).apply()
    var customTheme: String
        get() = sp.getString("customTheme", "") ?: ""
        set(v) = sp.edit().putString("customTheme", v).apply()
    var pageBlur: Int
        get() = sp.getInt("page_blur", 0)
        set(v) = sp.edit().putInt("page_blur", v).apply()
    var cacheMb: Int
        get() = sp.getInt("cache_mb", 300)
        set(v) = sp.edit().putInt("cache_mb", v).apply()
}

fun installImageLoader(ctx: Context) {
    SingletonImageLoader.setSafe { c ->
        ImageLoader.Builder(c)
            .memoryCache { MemoryCache.Builder().maxSizePercent(c, 0.25).build() }
            .diskCache {
                DiskCache.Builder().directory(c.cacheDir.resolve("image_cache").toOkioPath())
                    .maxSizeBytes(Prefs.cacheMb * 1024L * 1024L).build()
            }
            .diskCachePolicy(if (Prefs.cacheOn) CachePolicy.ENABLED else CachePolicy.DISABLED)
            .build()
    }
}
