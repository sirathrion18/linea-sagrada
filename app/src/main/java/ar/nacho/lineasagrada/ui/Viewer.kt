package ar.nacho.lineasagrada.ui

import android.app.Activity
import android.content.Intent
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ar.nacho.lineasagrada.Fmt
import ar.nacho.lineasagrada.Library
import ar.nacho.lineasagrada.Media
import ar.nacho.lineasagrada.Repo
import ar.nacho.lineasagrada.countryName
import coil.compose.AsyncImage
import coil.request.ImageRequest

@Composable
fun Viewer(list: List<Media>, start: Int, l: Library, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val repo = remember { Repo.get(ctx) }
    if (list.isEmpty()) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val pager = rememberPagerState(initialPage = start.coerceIn(0, list.size - 1)) { list.size }
    var pendingId by remember { mutableStateOf<Long?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        val id = pendingId
        if (r.resultCode == Activity.RESULT_OK && id != null) {
            repo.removeIds(setOf(id))
            onBack()
        }
        pendingId = null
    }

    Box(Modifier.fillMaxSize().background(Tva.Black)) {
        HorizontalPager(pager, Modifier.fillMaxSize(), key = { list[it].id }) { p ->
            val m = list[p]
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                AsyncImage(
                    model = ImageRequest.Builder(ctx).data(m.uri).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
                if (m.isVideo) {
                    Box(
                        Modifier.size(72.dp).background(Tva.Orange, CircleShape).clickable {
                            try {
                                ctx.startActivity(
                                    Intent(Intent.ACTION_VIEW).setDataAndType(m.uri, "video/*")
                                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                                )
                            } catch (e: Exception) {
                            }
                        },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.PlayArrow, "Reproducir", tint = Tva.Black, modifier = Modifier.size(40.dp)) }
                }
            }
        }

        val m = list[pager.currentPage.coerceIn(0, list.size - 1)]
        val starred = m.id in l.stars
        val place = l.place[m.id]
        val ev = l.eventOf[m.id]

        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.Close, "Cerrar", tint = Tva.Ivory) }
            Spacer(Modifier.weight(1f))
            Label("${pager.currentPage + 1} / ${Fmt.n(list.size)}", color = Tva.Ivory, modifier = Modifier.padding(end = 12.dp))
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Tva.Black.copy(alpha = 0.78f))
                .navigationBarsPadding().padding(12.dp),
        ) {
            Text("${Fmt.dayFull(m.time)} · ${Fmt.time(m.time)}", style = Tva.body)
            val where = place?.let { (if (it.inferred) "≈ " else "") + "${it.city}, ${countryName(it.cc)}" } ?: "Sin lugar"
            Label(where + " · " + m.origin.label + if (m.sysFavorite) " · favorita del sistema" else "", color = Tva.Ivory)
            if (ev != null) Label("Expediente: ${ev.name}", color = Tva.Orange)
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TvaButton(if (starred) "Con estrella" else "Estrella", filled = starred) { repo.toggleStar(m.id) }
                Icon(
                    if (starred) Icons.Filled.Star else Icons.Outlined.Star, null,
                    tint = Tva.Orange, modifier = Modifier.padding(start = 6.dp).size(18.dp),
                )
                Spacer(Modifier.weight(1f))
                TvaButton("Papelera", color = Tva.Red, filled = false) {
                    try {
                        val pi = MediaStore.createTrashRequest(ctx.contentResolver, listOf(m.uri), true)
                        pendingId = m.id
                        launcher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                    } catch (e: Exception) {
                    }
                }
                Icon(Icons.Filled.Delete, null, tint = Tva.Red, modifier = Modifier.padding(start = 6.dp).size(18.dp))
            }
        }
    }
}
