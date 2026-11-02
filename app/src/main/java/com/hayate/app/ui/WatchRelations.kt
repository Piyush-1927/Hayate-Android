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
fun WatchRelations(m: AnimeMedia) {
    if (m.relations.isNotEmpty()) Column {
        Text("Connected Seasons & Relations", fontSize = 18.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 24.dp, bottom = 10.dp))
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
