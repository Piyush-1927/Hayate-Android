package com.hayate.app.ui

import androidx.compose.foundation.*
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.hayate.core.anilist.AnimeMedia
import kotlinx.coroutines.delay

fun meta(m: AnimeMedia): String = listOfNotNull(
    m.format, m.seasonYear?.toString(), m.averageScore?.let { "★ ${it / 10.0}" },
    m.genres.take(3).joinToString(" · ").ifEmpty { null },
).joinToString("  •  ")

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Poster(m: AnimeMedia, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var preview by remember { mutableStateOf(false) }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    if (preview) PosterPreview(m, onDismiss = { preview = false }, onPlay = { preview = false; onClick() })
    Column(modifier.combinedClickable(onClick = onClick, onLongClick = {
        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress); preview = true
    })) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp)).background(HCard)) {
            AsyncImage(model = m.coverUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            m.averageScore?.let {
                Text("★ ${it / 10.0}", color = Color.White, fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
                        .background(Color(0xAA000000), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
            }
        }
        Text(m.displayTitle, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 13.sp,
            lineHeight = 16.sp, fontWeight = FontWeight.Medium,
            color = HText, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
fun Shelf(title: String, items: List<AnimeMedia>, onOpen: (AnimeMedia) -> Unit, onViewAll: (() -> Unit)? = null) {
    if (items.isEmpty()) return
    Column {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 4.dp, height = 20.dp).clip(RoundedCornerShape(50)).background(HAccent))
            Spacer(Modifier.width(8.dp))
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = HText)
            Spacer(Modifier.weight(1f))
            if (onViewAll != null) Text("View all ›", color = HAccent, fontSize = 12.sp, modifier = Modifier.clickable(onClick = onViewAll))
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(items) { Poster(it, { onOpen(it) }, Modifier.width(120.dp)) }
        }
    }
}

@Composable
fun Spotlight(items: List<AnimeMedia>, onOpen: (AnimeMedia) -> Unit, onPlay: (AnimeMedia) -> Unit = onOpen) {
    if (items.isEmpty()) return
    val pager = rememberPagerState { items.size }
    LaunchedEffect(items) {
        while (true) { delay(5000); pager.animateScrollToPage((pager.currentPage + 1) % items.size) }
    }
    HorizontalPager(pager, Modifier.fillMaxWidth().height(270.dp)) { i ->
        val m = items[i]
        Box(Modifier.fillMaxSize().clickable { onOpen(m) }) {
            AsyncImage(model = m.bannerUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, 0.45f to Color(0x22000000), 1f to (if (Themes.cur.dark) HBg else Color(0xE6000000)))))
            Text("#${i + 1} SPOTLIGHT", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.5.sp, color = HAccent,
                modifier = Modifier.align(Alignment.TopStart).padding(start = 16.dp, top = 14.dp)
                    .background(Color(0x99000000), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp))
            Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                Text(m.displayTitle, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                Text(meta(m), fontSize = 12.sp, color = Color(0xCCFFFFFF), modifier = Modifier.padding(top = 4.dp))
                Button(onClick = { onPlay(m) }, shape = RoundedCornerShape(10.dp), modifier = Modifier.padding(top = 10.dp)) { Text("Watch Now") }
            }
        }
    }
}

@Composable
fun Loading() = Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = HAccent) }

@Composable
fun ErrorBox(msg: String, onRetry: () -> Unit) = Box(Modifier.fillMaxSize(), Alignment.Center) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
        Text(msg, color = HMuted); Spacer(Modifier.height(12.dp))
        Button(onClick = onRetry) { Text("Retry") }
    }
}


@Composable
private fun PosterPreview(m: AnimeMedia, onDismiss: () -> Unit, onPlay: () -> Unit) {
    val plain = remember(m.id) { m.description.replace(Regex("<br\\s*/?>"), " ").replace(Regex("<[^>]*>"), "").trim() }
    val badge = listOfNotNull(m.averageScore?.let { "★ ${it / 10.0}" }, m.format, m.seasonYear?.toString()).joinToString("  ·  ")
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.width(300.dp).clip(RoundedCornerShape(14.dp)).background(HCard)) {
            AsyncImage(model = m.bannerUrl.ifEmpty { m.coverUrl }, contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(150.dp), contentScale = ContentScale.Crop)
            Column(Modifier.padding(14.dp)) {
                if (badge.isNotEmpty()) Text(badge, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF38BDF8))
                Text(m.displayTitle, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = HText,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                if (m.genres.isNotEmpty()) Text(m.genres.take(3).joinToString(" · "), fontSize = 11.sp, color = HMuted,
                    modifier = Modifier.padding(top = 4.dp))
                if (plain.isNotEmpty()) Text(plain, fontSize = 12.sp, lineHeight = 17.sp, color = HMuted,
                    maxLines = 5, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                Button(onClick = onPlay, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Text("Play") }
            }
        }
    }
}
