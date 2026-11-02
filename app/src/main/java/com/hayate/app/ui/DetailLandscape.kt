package com.hayate.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.text.HtmlCompat
import coil3.compose.AsyncImage
import com.hayate.app.Prefs
import com.hayate.app.stream.StreamResult
import com.hayate.app.stream.YtProvider
import com.hayate.core.anilist.*
import java.util.Calendar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DetailLandscape(
    m: AnimeMedia, desc: String, total: Int, unreleased: Boolean,
    lang: String, onLang: (String) -> Unit, busy: Int, notice: String,
    onBack: () -> Unit, onPlay: (Int) -> Unit,
) {
    var expanded by remember(m.id) { mutableStateOf(false) }
    val metaLine = listOfNotNull(
        m.format, (m.seasonYear ?: m.startYear)?.toString(),
        m.averageScore?.let { "★ %.1f".format(it / 10f) },
        m.status?.replace("_", " ")
    ).joinToString("  •  ")
    Box(Modifier.fillMaxSize().background(HBg)) {
        AsyncImage(model = m.bannerUrl.ifEmpty { m.coverUrl }, contentDescription = null,
            modifier = Modifier.fillMaxSize().blur(28.dp), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(HBg.copy(alpha = 0.85f)))
        Row(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout)) {
            // LEFT: fixed identity pane
            Column(Modifier.weight(0.32f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(16.dp)) {
                Text("←  Back", color = HText, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { onBack() }.padding(bottom = 12.dp))
                AsyncImage(model = m.coverUrl, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth(0.6f).aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp)))
                Text(m.displayTitle, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
                Text(metaLine, color = HMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                if (m.genres.isNotEmpty())
                    Text(m.genres.joinToString(" · "), color = HAccent, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
            // RIGHT: scrolling content pane
            Column(Modifier.weight(0.68f).fillMaxHeight().verticalScroll(rememberScrollState())
                .padding(top = 16.dp, end = 16.dp, bottom = 16.dp)) {
                if (unreleased) Box(Modifier.padding(bottom = 8.dp).clip(RoundedCornerShape(6.dp)).background(Color(0x33F59E0B))
                    .padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Text("UPCOMING • NOT YET AIRED", color = Color(0xFFF59E0B), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Text(desc, fontSize = 13.sp, lineHeight = 19.sp, maxLines = if (expanded) Int.MAX_VALUE else 4,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.clickable { expanded = !expanded })
                LibraryBar(m)
                if (unreleased) {
                    Column(Modifier.padding(top = 16.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(HCard).padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Broadcasting Schedule Pending", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("Episodes and streaming channels will appear here once official release dates begin.",
                            color = HMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                } else {
                    Row(Modifier.padding(top = 16.dp, bottom = 8.dp).horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("Episodes", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 16.dp))
                        listOf("sub" to "Sub", "eng" to "English dub", "hin" to "Hindi dub").forEach { (k, l) ->
                            val on = lang == k
                            Box(Modifier.padding(end = 8.dp).clip(RoundedCornerShape(20.dp)).background(if (on) HAccent else HCard)
                                .clickable { onLang(k) }.padding(horizontal = 14.dp, vertical = 8.dp)) {
                                Text(l, fontSize = 12.sp, color = if (on) HOnAccent else HMuted)
                            }
                        }
                    }
                    if (notice.isNotEmpty()) Text(notice, color = HMuted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 6.dp))
                    (1..total).chunked(8).forEach { row ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            row.forEach { ep ->
                                Box(Modifier.weight(1f).padding(2.dp).clip(RoundedCornerShape(14.dp)).background(HCard)
                                    .clickable { onPlay(ep) }.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                                    if (busy == ep) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = HAccent)
                                    else Text("$ep", fontSize = 14.sp)
                                }
                            }
                            repeat(8 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                if (m.relations.isNotEmpty()) {
                    Text("Connected Seasons & Relations", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 20.dp, bottom = 10.dp))
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        m.relations.forEach { r ->
                            Column(Modifier.padding(end = 10.dp).size(110.dp, 215.dp).clip(RoundedCornerShape(12.dp))
                                .background(HCard).padding(6.dp)) {
                                AsyncImage(model = r.coverUrl, contentDescription = null, contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(98.dp, 140.dp).clip(RoundedCornerShape(8.dp)))
                                Text(r.relationType.replace("_", " "), color = HAccent, fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                                Text(r.title, fontSize = 11.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}
