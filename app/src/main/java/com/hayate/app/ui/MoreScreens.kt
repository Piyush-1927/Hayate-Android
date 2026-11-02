package com.hayate.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
private fun Page(title: String, note: String) = Column(Modifier.fillMaxSize().padding(16.dp)) {
    Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    Text(note, color = HMuted, modifier = Modifier.padding(top = 8.dp))
}

