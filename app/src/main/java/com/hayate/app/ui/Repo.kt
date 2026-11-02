package com.hayate.app.ui

import com.hayate.app.Prefs

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.hayate.core.anilist.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object Repo {
    var home by mutableStateOf<HomeResult?>(null)
    var homeError by mutableStateOf<String?>(null)
    var schedule by mutableStateOf<Map<String, List<ScheduleEntry>>?>(null)
    var scheduleError by mutableStateOf<String?>(null)

    suspend fun loadHome(ctx: Context, force: Boolean = false) {
        if (home != null && !force) return
        withContext(Dispatchers.IO) {
            val f = File(ctx.cacheDir, "home_cache.json")
            if (home == null) AniListHome.peekCache(Prefs.allowAdult, f)?.let { home = it }
            try {
                home = AniListHome.getHomeShelves(Prefs.allowAdult, f, !force)
                homeError = null
            } catch (e: Exception) { if (home == null) homeError = e.message ?: "Network error" }
        }
    }

    suspend fun loadSchedule(ctx: Context, force: Boolean = false) {
        if (schedule != null && !force) return
        withContext(Dispatchers.IO) {
            try {
                schedule = AniListSchedule.getWeeklySchedule(cacheFile = File(ctx.cacheDir, "schedule_cache.json"), useCache = !force)
                scheduleError = null
            } catch (e: Exception) { scheduleError = e.message ?: "Network error" }
        }
    }
}
