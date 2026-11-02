package com.hayate.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.hayate.core.anilist.AnimeMedia

@Composable
fun SuggestPanel(results: List<AnimeMedia>, busy: Boolean, onOpen: (AnimeMedia) -> Unit, modifier: Modifier = Modifier) {
    if (results.isEmpty() && !busy) return
    Column(modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)
        .clip(RoundedCornerShape(20.dp)).background(HCard).border(1.dp, HBorder, RoundedCornerShape(20.dp))) {
        if (results.isEmpty()) Text("Searching…", color = HMuted, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
        LazyColumn(Modifier.heightIn(max = 420.dp)) {
            items(results) { m ->
                Row(Modifier.fillMaxWidth().clickable { onOpen(m) }.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(model = m.coverUrl, contentDescription = null,
                        modifier = Modifier.size(42.dp, 58.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(m.displayTitle, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(meta(m), color = HMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
