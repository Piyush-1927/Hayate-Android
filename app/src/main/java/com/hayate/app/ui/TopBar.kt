package com.hayate.app.ui
import androidx.compose.ui.focus.onFocusChanged

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hayate.app.R
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import kotlinx.coroutines.delay

private val BarBg: Color get() = HCard.copy(alpha = 0.85f)
private val BarBorder: Color get() = HBorder
private val BtnShape = RoundedCornerShape(10.dp)
var searchHint by mutableStateOf("Search anime…")

@Composable
private fun RoundBtn(onClick: () -> Unit, content: @Composable () -> Unit) =
    Box(Modifier.size(40.dp).clip(BtnShape).background(BarBg).border(1.5.dp, BarBorder, BtnShape).clickable(onClick = onClick), Alignment.Center) { content() }

@Composable
private fun MenuIcon() = Canvas(Modifier.size(18.dp)) {
    val w = 2.dp.toPx()
    listOf(0.18f to 1f, 0.5f to 0.7f, 0.82f to 1f).forEach { (y, len) ->
        drawLine(HText, Offset(0f, size.height * y), Offset(size.width * len, size.height * y), w, StrokeCap.Round)
    }
}

@Composable
private fun SearchIcon() = Canvas(Modifier.size(20.dp)) {
    val w = 2.dp.toPx()
    drawCircle(HText, radius = size.minDimension * 0.32f, center = Offset(size.width * 0.42f, size.height * 0.42f), style = Stroke(w))
    drawLine(HText, Offset(size.width * 0.66f, size.height * 0.66f), Offset(size.width * 0.92f, size.height * 0.92f), w, StrokeCap.Round)
}

@Composable
private fun Logo() {
    Image(painterResource(R.drawable.hayate_logo), contentDescription = null,
        modifier = Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)))
}

@Composable
private fun GearIcon() = Canvas(Modifier.size(20.dp)) {
    val c = center; val r = size.minDimension / 2; val w = 2.dp.toPx()
    drawCircle(HText, r * 0.5f, c, style = Stroke(w))
    for (i in 0 until 8) {
        val a = Math.toRadians(i * 45.0); val dx = Math.cos(a).toFloat(); val dy = Math.sin(a).toFloat()
        drawLine(HText, Offset(c.x + dx * r * 0.66f, c.y + dy * r * 0.66f),
            Offset(c.x + dx * r * 0.97f, c.y + dy * r * 0.97f), w * 1.4f, StrokeCap.Round)
    }
}

@Composable
private fun CloseIcon() = Canvas(Modifier.size(12.dp)) {
    val w = 2.dp.toPx()
    drawLine(HText, Offset(0f, 0f), Offset(size.width, size.height), w, StrokeCap.Round)
    drawLine(HText, Offset(size.width, 0f), Offset(0f, size.height), w, StrokeCap.Round)
}

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit, onSubmit: () -> Unit, onClose: () -> Unit, onFocus: () -> Unit, autoFocus: Boolean) {
    val fr = remember { FocusRequester() }
    val fm = LocalFocusManager.current
    LaunchedEffect(Unit) { if (autoFocus) { delay(120); try { fr.requestFocus() } catch (_: Exception) {} } }
    Row(Modifier.fillMaxWidth().height(40.dp).clip(BtnShape).background(BarBg)
        .border(1.5.dp, BarBorder, BtnShape).padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.clickable { fm.clearFocus(); onSubmit() }) { SearchIcon() }
        Spacer(Modifier.width(10.dp))
        BasicTextField(query, onQuery, Modifier.weight(1f).focusRequester(fr).onFocusChanged { if (it.isFocused) onFocus() }, singleLine = true,
            textStyle = TextStyle(color = HText, fontSize = 15.sp), cursorBrush = SolidColor(HAccent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { fm.clearFocus(); onSubmit() }),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) Text(searchHint, color = HMuted, fontSize = 15.sp)
                    inner()
                }
            })
        Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).clickable { fm.clearFocus(); onClose() }, Alignment.Center) { CloseIcon() }
    }
}

@Composable
private fun Vis(
    visible: Boolean, modifier: Modifier = Modifier,
    enter: EnterTransition, exit: ExitTransition, content: @Composable () -> Unit,
) = AnimatedVisibility(visible, modifier, enter, exit) { content() }

@Composable
fun TopBar(
    searching: Boolean, query: String, onQuery: (String) -> Unit,
    onSearchOpen: () -> Unit, onSearchClose: () -> Unit, onSubmit: () -> Unit,
    onHome: () -> Unit, onSchedule: () -> Unit, onSettings: () -> Unit,
    onFocus: () -> Unit = {}, autoFocus: Boolean = true,
    onCatalogue: () -> Unit = {}, onHistory: () -> Unit = {}, onLibrary: () -> Unit = {},
) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box {
            RoundBtn({ menu = true }) { MenuIcon() }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false },
                shape = RoundedCornerShape(18.dp), containerColor = HCard) {
                DropdownMenuItem(text = { Text("Home") }, onClick = { onHome(); menu = false })
                DropdownMenuItem(text = { Text("Weekly Airing Schedule") }, onClick = { onSchedule(); menu = false })
                DropdownMenuItem(text = { Text("Catalogue") }, onClick = { onCatalogue(); menu = false })
                DropdownMenuItem(text = { Text("Watch History") }, onClick = { onHistory(); menu = false })
                DropdownMenuItem(text = { Text("Library") }, onClick = { onLibrary(); menu = false })
                DropdownMenuItem(text = { Text("Settings") }, onClick = { onSettings(); menu = false })
            }
        }
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f).height(40.dp)) {
            // logo + name get swept out to the left when search opens
            Vis(!searching, Modifier.align(Alignment.CenterStart),
                enter = slideInHorizontally(tween(380)) { -it } + fadeIn(tween(380)),
                exit = slideOutHorizontally(tween(380)) { -it } + fadeOut(tween(300))) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Logo(); Spacer(Modifier.width(10.dp))
                    Text("Hayate", fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
            Vis(!searching, Modifier.align(Alignment.CenterEnd), enter = fadeIn(), exit = fadeOut()) {
                RoundBtn(onSearchOpen) { SearchIcon() }
            }
            // search field grows from the right and fills the whole space
            Vis(searching, Modifier.fillMaxWidth(),
                enter = expandHorizontally(tween(380), expandFrom = Alignment.End) + fadeIn(tween(380)),
                exit = shrinkHorizontally(tween(300), shrinkTowards = Alignment.End) + fadeOut(tween(200))) {
                SearchField(query, onQuery, onSubmit, onSearchClose, onFocus, autoFocus)
            }
        }
    }
}
