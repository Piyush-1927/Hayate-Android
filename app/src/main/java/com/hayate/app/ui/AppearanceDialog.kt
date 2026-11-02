package com.hayate.app.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private fun HPalette.edit(k: String, v: Long): HPalette {
    val b = if (k == "bg") v else bg
    return HPalette("Custom", b, if (k == "card") v else card, if (k == "border") v else border,
        if (k == "text") v else text, if (k == "muted") v else muted, if (k == "accent") v else accent,
        Color(b).luminance() < 0.5f)
}

@Composable
private fun ColorRow(label: String, value: Long, onChange: (Long) -> Unit) {
    var txt by remember(value) { mutableStateOf("%06X".format(value and 0xFFFFFFL)) }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 13.sp, color = HText)
        Box(Modifier.size(28.dp).background(Color(value), RoundedCornerShape(8.dp)).border(1.dp, HBorder, RoundedCornerShape(8.dp)))
        Spacer(Modifier.width(10.dp))
        OutlinedTextField(
            txt, { s ->
                val t = s.removePrefix("#").take(6).uppercase(); txt = t
                if (t.length == 6) t.toLongOrNull(16)?.let { onChange(it or 0xFF000000L) }
            },
            Modifier.width(130.dp), singleLine = true,
            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp), prefix = { Text("#") })
    }
}

@Composable
fun AppearanceDialog(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            val ok = try { ctx.contentResolver.openOutputStream(uri)?.use { it.write(Themes.toJson(Themes.cur).toByteArray()) }; true } catch (e: Exception) { false }
            Toast.makeText(ctx, if (ok) "Theme exported" else "Export failed", Toast.LENGTH_SHORT).show()
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val p = try { ctx.contentResolver.openInputStream(uri)?.use { Themes.parseCustom(it.readBytes().decodeToString()) } } catch (e: Exception) { null }
            if (p != null) Themes.setCustom(p) else Toast.makeText(ctx, "Not a valid theme file", Toast.LENGTH_SHORT).show()
        }
    }
    fun upd(k: String, v: Long) = Themes.setCustom(Themes.cur.edit(k, v))
    val c = Themes.cur
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = HBg) {
            Column(Modifier.statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onClose) { Text("← Back") }
                    Text("Appearance", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = HText)
                }
                Text("Colour scheme", Modifier.padding(top = 12.dp, bottom = 8.dp), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = HText)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Themes.all.forEach { p ->
                        val sel = c.name == p.name
                        Box(Modifier.background(Color(p.card), RoundedCornerShape(10.dp))
                            .border(if (sel) 2.dp else 1.dp, if (sel) HAccent else Color(p.border), RoundedCornerShape(10.dp))
                            .clickable { Themes.set(p.name) }.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Text(p.name, fontSize = 12.sp, color = Color(p.text))
                        }
                    }
                }
                Text("Custom colour scheme", Modifier.padding(top = 20.dp, bottom = 4.dp), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = HText)
                Text("Editing any colour switches to Custom.", fontSize = 12.sp, color = HMuted)
                Text("Accent colour", Modifier.padding(top = 12.dp, bottom = 6.dp), fontSize = 13.sp, color = HText)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(0xFF38BDF8L, 0xFFA78BFAL, 0xFF34D399L, 0xFFFB923CL, 0xFFF472B6L).forEach { v ->
                        Box(Modifier.size(34.dp).background(Color(v), RoundedCornerShape(10.dp))
                            .border(if (c.accent == v) 2.dp else 1.dp, if (c.accent == v) HText else HBorder, RoundedCornerShape(10.dp))
                            .clickable { upd("accent", v) })
                    }
                }
                Text("All colours", Modifier.padding(top = 16.dp, bottom = 4.dp), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = HText)
                ColorRow("App background", c.bg) { upd("bg", it) }
                ColorRow("Card background", c.card) { upd("card", it) }
                ColorRow("Card border", c.border) { upd("border", it) }
                ColorRow("Text primary", c.text) { upd("text", it) }
                ColorRow("Text secondary", c.muted) { upd("muted", it) }
                ColorRow("Accent", c.accent) { upd("accent", it) }
                Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { import.launch(arrayOf("*/*")) }, Modifier.weight(1f)) { Text("Import") }
                    OutlinedButton(onClick = { export.launch("hayate-theme.json") }, Modifier.weight(1f)) { Text("Export") }
                }
            }
        }
    }
}
