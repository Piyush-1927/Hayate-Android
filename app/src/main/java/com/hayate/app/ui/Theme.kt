package com.hayate.app.ui

import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.hayate.app.Prefs

// Presets copied from desktop core/theme.py
class HPalette(val name: String, val bg: Long, val card: Long, val border: Long,
               val text: Long, val muted: Long, val accent: Long, val dark: Boolean = true)

object Themes {
    val all = listOf(
        HPalette("Midnight", 0xFF171722, 0xFF1E1E2D, 0xFF2F2F44, 0xFFFFFFFF, 0xFF94A3B8, 0xFF38BDF8),
        HPalette("Forest", 0xFF101412, 0xFF1A201C, 0xFF2C3A31, 0xFFFFFFFF, 0xFF94A3B8, 0xFF34D399),
        HPalette("Daylight", 0xFFF4F6FA, 0xFFFFFFFF, 0xFFD5DBE6, 0xFF1B2433, 0xFF5B6779, 0xFF2563EB, dark = false),
        HPalette("Ember", 0xFF17110E, 0xFF231A15, 0xFF4A3326, 0xFFFFFFFF, 0xFF94A3B8, 0xFFFB923C),
    )
    var cur by mutableStateOf(all[0])
        private set
    fun parseCustom(js: String): HPalette? = try {
        val o = org.json.JSONObject(js)
        fun c(k: String): Long = o.getString(k).removePrefix("#").toLong(16) or 0xFF000000L
        val bg = c("bg")
        HPalette("Custom", bg, c("card"), c("border"), c("text"), c("muted"), c("accent"), Color(bg).luminance() < 0.5f)
    } catch (e: Exception) { null }
    fun toJson(p: HPalette): String {
        fun h(v: Long) = "#%06X".format(v and 0xFFFFFFL)
        return org.json.JSONObject().put("name", "Custom").put("bg", h(p.bg)).put("card", h(p.card))
            .put("border", h(p.border)).put("text", h(p.text)).put("muted", h(p.muted)).put("accent", h(p.accent)).toString(2)
    }
    fun load() {
        cur = all.firstOrNull { it.name == Prefs.theme }
            ?: (if (Prefs.theme == "Custom") parseCustom(Prefs.customTheme) else null) ?: all[0]
    }
    fun set(name: String) { Prefs.theme = name; load() }
    fun setCustom(p: HPalette) { Prefs.customTheme = toJson(p); Prefs.theme = "Custom"; load() }
}

val HBg: Color get() = Color(Themes.cur.bg)
val HPanel: Color get() = Color(Themes.cur.card)
val HCard: Color get() = Color(Themes.cur.card)
val HBorder: Color get() = Color(Themes.cur.border)
val HAccent: Color get() = Color(Themes.cur.accent)
val HOnAccent: Color get() = if (Color(Themes.cur.accent).luminance() > 0.35f) Color(0xFF090A0F) else Color.White
val HText: Color get() = Color(Themes.cur.text)
val HMuted: Color get() = Color(Themes.cur.muted)

@Composable
fun HayateTheme(content: @Composable () -> Unit) {
    val view = androidx.compose.ui.platform.LocalView.current
    val p = Themes.cur
    androidx.compose.runtime.SideEffect {
        (view.context as? android.app.Activity)?.window?.let { w ->
            w.navigationBarColor = p.bg.toInt()
            androidx.core.view.WindowCompat.getInsetsController(w, view).isAppearanceLightNavigationBars = !p.dark
        }
    }
    HayateMaterial(content)
}

@Composable
private fun HayateMaterial(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = if (Themes.cur.dark) darkColorScheme(
        primary = HAccent, onPrimary = HOnAccent, background = HBg, surface = HPanel,
        onBackground = HText, onSurface = HText, surfaceVariant = HCard, onSurfaceVariant = HMuted, outline = HBorder,
    ) else lightColorScheme(
        primary = HAccent, onPrimary = HOnAccent, background = HBg, surface = HPanel,
        onBackground = HText, onSurface = HText, surfaceVariant = HCard, onSurfaceVariant = HMuted, outline = HBorder,
    ),
    typography = androidx.compose.material3.Typography(
        bodyLarge = androidx.compose.ui.text.TextStyle(
            fontSize = 16.sp,
            lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified,
            letterSpacing = 0.sp,
        ),
    ),
    content = content,
)
