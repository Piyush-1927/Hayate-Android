package com.hayate.app.ui

import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.SingletonImageLoader
import com.hayate.app.CloudSync
import com.hayate.app.GoogleDrive
import com.hayate.app.Prefs
import com.hayate.app.stream.Providers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private class OpenInDownloads : ActivityResultContracts.OpenDocument() {
    override fun createIntent(context: android.content.Context, input: Array<String>) =
        super.createIntent(context, input).apply {
            putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI,
                android.net.Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADownload"))
        }
}

@Composable
private fun SCard(content: @Composable ColumnScope.() -> Unit) =
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(14.dp)).background(HCard).padding(14.dp), content = content)

@Composable
private fun SBtn(text: String, onClick: () -> Unit) =
    Box(Modifier.clip(RoundedCornerShape(10.dp)).background(HBg).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp)) {
        Text(text, fontSize = 13.sp, color = HAccent)
    }

@Composable
private fun SCheck(label: String, v: Boolean, onChange: (Boolean) -> Unit) =
    Row(Modifier.fillMaxWidth().clickable { onChange(!v) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(v, onChange, colors = CheckboxDefaults.colors(checkedColor = HAccent, checkmarkColor = HOnAccent))
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }

@Composable
private fun SChip(text: String, on: Boolean, onClick: () -> Unit) =
    Box(Modifier.clip(RoundedCornerShape(10.dp)).background(if (on) HAccent else HBg).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp)) {
        Text(text, fontSize = 13.sp, color = if (on) HOnAccent else HText)
    }

@Composable
private fun CChip(text: String, on: Boolean, onClick: () -> Unit) =
    Box(Modifier.clip(RoundedCornerShape(10.dp)).background(if (on) HAccent else HBg).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp)) {
        Text(text, fontSize = 13.sp, color = if (on) HOnAccent else HText)
    }

@Composable
private fun CField(label: String, v: String, secret: Boolean, onChange: (String) -> Unit) =
    OutlinedTextField(v, onChange, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text(label, fontSize = 12.sp) }, singleLine = true,
        visualTransformation = if (secret) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None)

@Composable
private fun PChip(text: String, on: Boolean, onClick: () -> Unit) =
    Box(Modifier.clip(RoundedCornerShape(10.dp)).background(if (on) HAccent else HBg).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp)) {
        Text(text, fontSize = 13.sp, color = if (on) HOnAccent else HText)
    }

@Composable
private fun TChip(text: String, on: Boolean, onClick: () -> Unit) =
    Box(Modifier.clip(RoundedCornerShape(10.dp)).background(if (on) HAccent else HBg).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp)) {
        Text(text, fontSize = 13.sp, color = if (on) HOnAccent else HText)
    }

@Composable
fun SettingsScreen(query: String = "", onOpenLan: () -> Unit = {}) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var adult by remember { mutableStateOf(Prefs.allowAdult) }
    var blur by remember { mutableIntStateOf(Prefs.pageBlur) }
    var aud by remember { mutableStateOf(Prefs.preferredAudio) }
    var cBackend by remember { mutableStateOf(Prefs.cloudBackend) }
    var dUrl by remember { mutableStateOf(Prefs.davUrl) }
    var dUser by remember { mutableStateOf(Prefs.davUser) }
    var dPass by remember { mutableStateOf(Prefs.davPass) }
    var cAuto by remember { mutableStateOf(Prefs.cloudAuto) }
    var syncing by remember { mutableStateOf(false) }
    var gEmail by remember { mutableStateOf(Prefs.gEmail) }
    val gCtx = androidx.compose.ui.platform.LocalContext.current
    var cMsg by remember { mutableStateOf("") }
    var pStyle by remember { mutableStateOf(Prefs.pageStyle) }
    var pMs by remember { mutableIntStateOf(Prefs.pageMs) }
    var cacheOn by remember { mutableStateOf(Prefs.cacheOn) }
    var mb by remember { mutableIntStateOf(Prefs.cacheMb) }
    var menu by remember { mutableStateOf(false) }
    var cleared by remember { mutableStateOf("") }
    val provDir = remember { File(ctx.filesDir, "providers").apply { mkdirs() } }
    fun scan() = Providers.installed(ctx)
    var userProv by remember { mutableStateOf(scan()) }
    var sel by remember { mutableStateOf(Prefs.provider) }
    var confirmDel by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(OpenInDownloads()) { uri ->
        uri?.let {
            val name = ctx.contentResolver.query(it, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(c.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else null
            } ?: "provider.py"
            val dest = File(provDir, name)
            val msg = when {
                !name.endsWith(".js") -> "Providers must be .js files."
                dest.exists() -> "That provider is already installed."
                else -> try {
                    ctx.contentResolver.openInputStream(it)!!.use { i -> dest.outputStream().use { o -> i.copyTo(o) } }
                    val err = Providers.validate(ctx, dest)
                    if (err.isNotEmpty()) { dest.delete(); "Invalid provider: $err" } else { userProv = scan(); "Installed $name." }
                } catch (e: Exception) { "Copy failed: ${e.message}" }
            }
            Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
        }
    }
    fun show(k: String) = query.isBlank() || k.contains(query.trim(), ignoreCase = true)
    var pendingImport by remember { mutableStateOf<android.net.Uri?>(null) }
    val histOut = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let {
            val msg = try {
                ctx.contentResolver.openOutputStream(it, "wt")!!.use { o -> o.write(HistoryStore.exportJson().toByteArray()) }
                "History exported."
            } catch (e: Exception) { "Export failed: ${e.message}" }
            Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
        }
    }
    val histIn = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> pendingImport = uri }
    fun doImport(u: android.net.Uri, replace: Boolean) {
        val msg = try {
            val txt = ctx.contentResolver.openInputStream(u)!!.use { it.readBytes().toString(Charsets.UTF_8) }
            "Imported ${HistoryStore.importJson(txt, replace)} entries."
        } catch (e: Exception) { "Import failed: ${e.message}" }
        Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Settings", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 14.dp))

        if (show("providers provider import active refresh ytdlp youtube official licensors")) SCard {
            Text("Providers", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f, fill = false)) {
                    Box(Modifier.clip(RoundedCornerShape(10.dp)).background(HBg).clickable { menu = true }.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(userProv.firstOrNull { it.id == sel }?.let { "${it.label} (${it.id})" } ?: "Official Licensors (YouTube) (ytdlp)",
                                fontSize = 13.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            Text("  ▾", fontSize = 13.sp)
                        }
                    }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text("Official Licensors (YouTube) (ytdlp)", maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                            onClick = { Prefs.provider = "ytdlp"; sel = "ytdlp"; menu = false })
                        userProv.forEach { n ->
                            DropdownMenuItem(text = { Text("${n.label} (${n.id})", maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }, onClick = { Prefs.provider = n.id; sel = n.id; menu = false })
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                SBtn("Refresh") { userProv = scan(); Toast.makeText(ctx, "Providers refreshed", Toast.LENGTH_SHORT).show() }
            }
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SBtn("Import Provider…") { picker.launch(arrayOf("*/*")) }
                if (userProv.any { it.id == sel }) SBtn("Remove provider") { confirmDel = true }
            }
            if (confirmDel) {
                val cur = userProv.firstOrNull { it.id == sel }
                AlertDialog(
                    onDismissRequest = { confirmDel = false },
                    title = { Text("Remove provider") },
                    text = { Text("Remove ${cur?.label ?: sel}? The built-in provider will be used instead.") },
                    confirmButton = {
                        TextButton({
                            cur?.file?.delete()
                            Prefs.provider = "ytdlp"; sel = "ytdlp"; userProv = scan(); confirmDel = false
                            Toast.makeText(ctx, "Provider removed", Toast.LENGTH_SHORT).show()
                        }) { Text("Remove", color = HAccent) }
                    },
                    dismissButton = { TextButton({ confirmDel = false }) { Text("Cancel", color = HMuted) } },
                )
            }
        }

        if (show("content 18+ nsfw adult")) SCard {
            Text("Content", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            SCheck("Enable 18+ and NSFW Content", adult) { adult = it; Prefs.allowAdult = it; Repo.home = null }
        }

        if (show("transitions page animation duration slide fade")) SCard {
            Text("Transitions", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Page transition", fontSize = 13.sp)
                listOf("fade" to "Fade", "slide" to "Slide", "none" to "None").forEach { (k, l) ->
                    SChip(l, pStyle == k) { pStyle = k; Prefs.pageStyle = k }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Page duration", fontSize = 13.sp)
                Slider(pMs.toFloat(), { pMs = (it / 20f).roundToInt() * 20 }, Modifier.weight(1f).padding(horizontal = 8.dp),
                    valueRange = 100f..800f, onValueChangeFinished = { Prefs.pageMs = pMs },
                    colors = SliderDefaults.colors(thumbColor = HAccent, activeTrackColor = HAccent))
                Text("$pMs ms", fontSize = 13.sp, color = HAccent)
            }
        }

        if (show("history watch export import json backup")) SCard {
            Text("Watch history", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SBtn("Export History (JSON)") { histOut.launch("hayate_history.json") }
                SBtn("Import History (JSON)") { histIn.launch(arrayOf("*/*")) }
            }
            pendingImport?.let { u ->
                AlertDialog(
                    onDismissRequest = { pendingImport = null },
                    title = { Text("Import history") },
                    text = { Text("Merge keeps your current history and adds the file's entries. Replace overwrites it (a backup is kept).") },
                    confirmButton = { Row {
                        TextButton({ doImport(u, true); pendingImport = null }) { Text("Replace", color = HMuted) }
                        TextButton({ doImport(u, false); pendingImport = null }) { Text("Merge", color = HAccent) }
                    } },
                    dismissButton = { TextButton({ pendingImport = null }) { Text("Cancel", color = HMuted) } },
                )
            }
        }

        var linkStep by remember { mutableStateOf(0) }
        var linkRemote by remember { mutableStateOf<org.json.JSONObject?>(null) }
        fun startLink() {
            if (syncing) return
            syncing = true; cMsg = "Checking your cloud…"
            scope.launch {
                val pr = CloudSync.probe()
                if (pr.error != null) cMsg = pr.error
                else if (!(CloudSync.remoteHasData(pr.remote) && CloudSync.localHasData())) { CloudSync.markLinked(); cMsg = CloudSync.sync() }
                else { linkRemote = pr.remote; linkStep = 1; cMsg = "" }
                syncing = false
            }
        }
        if (linkStep == 1) androidx.compose.material3.AlertDialog(
            onDismissRequest = { linkStep = 0; cMsg = "Cloud sync is paused until you choose. Tap Sync now to continue." },
            containerColor = HCard,
            title = { Text("Connect cloud sync", color = HText) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("This device and your cloud both already have Hayate data. What should Hayate do?", color = HMuted, fontSize = 13.sp)
                SBtn("Merge both (recommended)") { linkStep = 0; syncing = true; cMsg = "Merging…"; scope.launch { cMsg = CloudSync.linkMerge(); syncing = false } }
                SBtn("Replace the cloud data with this device's") { linkStep = 2 }
                SBtn("Replace this device's data with the cloud's") { linkStep = 3 }
            } },
            confirmButton = {},
            dismissButton = { androidx.compose.material3.TextButton({ linkStep = 0; cMsg = "Cloud sync is paused until you choose. Tap Sync now to continue." }) { Text("Not now", color = HMuted) } })
        if (linkStep == 2 || linkStep == 3) androidx.compose.material3.AlertDialog(
            onDismissRequest = { linkStep = 1 },
            containerColor = HCard,
            title = { Text("Are you sure?", color = HText) },
            text = { Text(if (linkStep == 2)
                "This replaces the history and library stored in your cloud with this device's data. Anything that exists only in the cloud will be lost. Other devices that are already connected may add their data back the next time they sync."
                else "This deletes the history and library on this device and replaces them with what is in your cloud. Anything that exists only on this device will be lost.",
                color = HMuted, fontSize = 13.sp) },
            confirmButton = { androidx.compose.material3.TextButton({
                val up = linkStep == 2; val rem = linkRemote
                linkStep = 0; syncing = true; cMsg = "Working…"
                scope.launch { cMsg = if (up) CloudSync.linkUseThisDevice() else if (rem != null) CloudSync.linkUseCloud(rem) else ""; syncing = false }
            }) { Text("I'm sure", color = HText) } },
            dismissButton = { androidx.compose.material3.TextButton({ linkStep = 1 }) { Text("Go back", color = HMuted) } })

        if (show("cloud sync webdav backup nextcloud")) SCard {
            Text("Cloud sync", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("off" to "Off", "google" to "Google Drive", "webdav" to "WebDAV").forEach { (k, l) -> CChip(l, cBackend == k) { cBackend = k; Prefs.cloudBackend = k } }
            }
            if (cBackend == "google") {
                Text(if (gEmail.isEmpty()) "Not signed in" else "Signed in as $gEmail", color = HMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
                SCheck("Sync when the app starts", cAuto) { cAuto = it; Prefs.cloudAuto = it }
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (gEmail.isEmpty() && !com.hayate.app.GoogleCreds.configured) {
                        Text("Google sign-in isn't configured in this build. Use WebDAV, or add your own credentials (see local.properties.example).", color = HMuted, fontSize = 12.sp)
                    } else if (gEmail.isEmpty()) {
                        SBtn("Sign in with Google") { scope.launch { cMsg = "Opening browser…"; cMsg = GoogleDrive.connect(gCtx); gEmail = Prefs.gEmail; if (gEmail.isNotEmpty() && CloudSync.needsLink()) startLink() } }
                    } else {
                        SBtn(if (syncing) "Syncing…" else "Sync now") {
                            if (!syncing) { if (CloudSync.needsLink()) startLink() else { syncing = true; scope.launch { cMsg = CloudSync.sync(); syncing = false } } }
                        }
                        SBtn("Sign out") { scope.launch { GoogleDrive.disconnect(); CloudSync.unlink(); gEmail = ""; cMsg = "" } }
                    }
                }
                if (cMsg.isNotEmpty()) Text(cMsg, color = HMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
            if (cBackend == "webdav") {
                CField("WebDAV folder URL", dUrl, false) { dUrl = it; Prefs.davUrl = it }
                CField("Username", dUser, false) { dUser = it; Prefs.davUser = it }
                CField("Password", dPass, true) { dPass = it; Prefs.davPass = it }
                SCheck("Sync when the app starts", cAuto) { cAuto = it; Prefs.cloudAuto = it }
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    SBtn(if (syncing) "Syncing…" else "Sync now") {
                        if (!syncing) { if (CloudSync.needsLink()) startLink() else { syncing = true; scope.launch { cMsg = CloudSync.sync(); syncing = false } } }
                    }
                    if (cMsg.isNotEmpty()) Text(cMsg, color = HMuted, fontSize = 12.sp, modifier = Modifier.padding(start = 10.dp))
                }
            }
        }

        if (show("default audio language dub sub english hindi")) SCard {
            Text("Default audio", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("sub" to "Sub", "eng" to "English dub", "hin" to "Hindi dub").forEach { (k, l) ->
                    PChip(l, aud == k) { aud = k; Prefs.preferredAudio = k }
                }
            }
            Text("New anime start on this audio when available.", color = HMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        }

        if (show("appearance theme colors midnight forest daylight ember")) SCard {
            Text("Appearance", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Themes.all.forEach { p -> TChip(p.name, Themes.cur.name == p.name) { Themes.set(p.name) } }
            }
            val appearOpen = remember { mutableStateOf(false) }
            androidx.compose.material3.TextButton(onClick = { appearOpen.value = true }, Modifier.padding(top = 4.dp)) { Text("Customize colours…") }
            if (appearOpen.value) AppearanceDialog { appearOpen.value = false }
        }

        if (show("transition blur page animation")) SCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Transition blur", fontSize = 13.sp)
                Slider(blur.toFloat(), { blur = (it / 5f).roundToInt() * 5 }, Modifier.weight(1f).padding(horizontal = 8.dp),
                    valueRange = 0f..100f, onValueChangeFinished = { Prefs.pageBlur = blur },
                    colors = SliderDefaults.colors(thumbColor = HAccent, activeTrackColor = HAccent))
                Text("$blur%", fontSize = 13.sp, color = HAccent)
            }
            Text("Blurs pages while they change. Needs Android 12 or newer.", color = HMuted, fontSize = 11.sp)
        }

        if (show("lan media player network tailscale server host")) SCard {
            Text("LAN Media Player", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text("Play files from a Hayate server on your network.", color = HMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            Row(Modifier.padding(top = 10.dp)) { SBtn("Open LAN Media Player", onOpenLan) }
        }

        if (show("cache enable size clear image storage")) SCard {
            SCheck("Enable cache", cacheOn) { cacheOn = it; Prefs.cacheOn = it }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Cache size", fontSize = 13.sp)
                Slider(mb.toFloat(), { mb = (it / 50f).roundToInt() * 50 }, Modifier.weight(1f).padding(horizontal = 8.dp),
                    valueRange = 50f..1000f, onValueChangeFinished = { Prefs.cacheMb = mb },
                    colors = SliderDefaults.colors(thumbColor = HAccent, activeTrackColor = HAccent))
                Text("$mb MB", fontSize = 13.sp, color = HAccent)
            }
            Text("Cache changes apply after restarting the app.", color = HMuted, fontSize = 11.sp)
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                SBtn("Clear image cache") {
                    scope.launch {
                        val freed = withContext(Dispatchers.IO) {
                            val l = SingletonImageLoader.get(ctx)
                            val before = l.diskCache?.size ?: 0L
                            l.memoryCache?.clear(); l.diskCache?.clear()
                            before
                        }
                        cleared = "Cleared %.1f MB".format(freed / 1048576.0)
                    }
                }
                if (cleared.isNotEmpty()) Text(cleared, color = HMuted, fontSize = 12.sp, modifier = Modifier.padding(start = 10.dp))
            }
        }
    }
}
