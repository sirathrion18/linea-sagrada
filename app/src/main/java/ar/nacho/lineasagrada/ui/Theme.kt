@file:OptIn(ExperimentalFoundationApi::class)

package ar.nacho.lineasagrada.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Typeface
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ar.nacho.lineasagrada.Fmt
import ar.nacho.lineasagrada.Media
import coil.compose.AsyncImage
import coil.request.ImageRequest

/** Paleta y tipografía del manual de estilo TVA. */
object Tva {
    val Orange = Color(0xFFFF6A00)
    val Black = Color(0xFF0E0E0E)
    val Gray = Color(0xFF5A5A5A)
    val Ivory = Color(0xFFEDEAE4)
    val Green = Color(0xFF4F6B58)
    val Bronze = Color(0xFFB38040)
    val Red = Color(0xFFD6452F)
    val Panel = Color(0xFF181818)
    val Line = Color(0xFF2A2A2A)

    val Cond = FontFamily(Typeface(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.NORMAL)))
    val CondBold = FontFamily(Typeface(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD)))

    val title = TextStyle(fontFamily = CondBold, fontSize = 22.sp, letterSpacing = 3.sp, color = Ivory)
    val h2 = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Ivory)
    val body = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, color = Ivory, lineHeight = 20.sp)
    val label = TextStyle(fontFamily = Cond, fontSize = 11.sp, letterSpacing = 2.sp, color = Gray)
}

@Composable
fun LineaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Tva.Orange,
            onPrimary = Tva.Black,
            secondary = Tva.Bronze,
            background = Tva.Black,
            onBackground = Tva.Ivory,
            surface = Tva.Black,
            onSurface = Tva.Ivory,
            surfaceVariant = Tva.Panel,
            onSurfaceVariant = Tva.Ivory,
            surfaceContainer = Tva.Panel,
            error = Tva.Red,
            outline = Tva.Gray,
        ),
        content = content,
    )
}

@Composable
fun Label(text: String, color: Color = Tva.Gray, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = Tva.label.copy(color = color), modifier = modifier, maxLines = 2, overflow = TextOverflow.Ellipsis)
}

/** Recuadro tipo sello o credencial. */
@Composable
fun Stamp(text: String, color: Color = Tva.Orange, modifier: Modifier = Modifier) {
    Box(modifier.border(1.dp, color).padding(horizontal = 6.dp, vertical = 2.dp)) {
        Text(text.uppercase(), style = Tva.label.copy(color = color, fontSize = 10.sp))
    }
}

@Composable
fun TvaButton(text: String, color: Color = Tva.Orange, filled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .then(if (filled) Modifier.background(color) else Modifier.border(1.dp, color))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text.uppercase(),
            style = Tva.label.copy(color = if (filled) Tva.Black else color, fontSize = 13.sp, fontFamily = Tva.CondBold),
        )
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(end = 6.dp)
            .then(if (selected) Modifier.background(Tva.Orange) else Modifier.border(1.dp, Tva.Gray))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(text.uppercase(), style = Tva.label.copy(color = if (selected) Tva.Black else Tva.Ivory, fontSize = 11.sp))
    }
}

@Composable
fun Banner(text: String, action: String? = null, color: Color = Tva.Bronze, onAction: () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp).border(1.dp, color).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = Tva.body.copy(fontSize = 13.sp), modifier = Modifier.weight(1f))
        if (action != null) {
            Spacer(Modifier.width(8.dp))
            TvaButton(action, color = color, filled = false, onClick = onAction)
        }
    }
}

@Composable
fun Thumb(
    m: Media,
    starred: Boolean,
    onClick: () -> Unit,
    onLong: () -> Unit = onClick,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val ctx = LocalContext.current
    Box(
        Modifier
            .aspectRatio(1f)
            .padding(1.dp)
            .background(Tva.Panel)
            .combinedClickable(onClick = onClick, onLongClick = onLong),
    ) {
        AsyncImage(
            model = ImageRequest.Builder(ctx).data(m.uri).size(300).build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (m.isVideo) {
            Row(
                Modifier.align(Alignment.BottomStart).background(Tva.Black.copy(alpha = 0.6f)).padding(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.PlayArrow, null, tint = Tva.Ivory, modifier = Modifier.size(12.dp))
                Text(Fmt.duration(m.durationMs), style = Tva.label.copy(color = Tva.Ivory, fontSize = 10.sp, letterSpacing = 0.sp))
            }
        }
        if (starred || m.sysFavorite) {
            Icon(Icons.Filled.Star, null, tint = Tva.Orange, modifier = Modifier.align(Alignment.TopEnd).padding(3.dp).size(14.dp))
        }
        overlay()
    }
}

@Composable
fun SectionHeader(title: String, sub: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 6.dp)) {
        Text(title.uppercase(), style = Tva.title.copy(fontSize = 16.sp, color = Tva.Orange))
        if (sub != null) Label(sub)
    }
}
