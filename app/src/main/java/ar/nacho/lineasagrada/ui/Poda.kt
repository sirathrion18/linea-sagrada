package ar.nacho.lineasagrada.ui

import android.app.Activity
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ar.nacho.lineasagrada.CleanupGroup
import ar.nacho.lineasagrada.Fmt
import ar.nacho.lineasagrada.Library
import ar.nacho.lineasagrada.Media
import ar.nacho.lineasagrada.Progress
import ar.nacho.lineasagrada.Repo

private const val MAX_PER_REQUEST = 400

@Composable
fun CleanupHome(l: Library, progress: Progress, push: (Dest) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        item {
            Column(Modifier.padding(vertical = 12.dp)) {
                Text("PODA", style = Tva.title.copy(color = Tva.Orange, fontSize = 28.sp))
                Text(
                    "Te propongo qué borrar; vos sacás lo que querés salvar y confirmás. " +
                        "Lo que mandes a la papelera queda 30 días en la papelera de Android.",
                    style = Tva.body.copy(color = Tva.Ivory.copy(alpha = 0.8f)),
                )
                Label("Las favoritas y las que marcás con estrella nunca aparecen acá.", modifier = Modifier.padding(top = 6.dp))
                if (progress.active) {
                    Banner("Sigo analizando la galería (${progress.label.lowercase()}). Los números van a crecer.")
                }
            }
        }
        if (l.closed.isNotEmpty()) {
            item { Label("Expedientes cerrados", color = Tva.Orange, modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)) }
            items(l.closed, key = { "cl${it.id}" }) { ev ->
                val n = remember(l, ev) { l.eventCleanup(ev).sumOf { it.items.size } }
                Card(
                    title = ev.name,
                    sub = "${Fmt.range(ev.start, ev.end)} · terminó · $n para revisar",
                    stamp = "Cierre",
                    color = Tva.Orange,
                ) { push(Dest.Review("Podar · ${ev.name}", "evento", ev.id)) }
            }
        }
        if (l.cleanup.isNotEmpty()) {
            item { Label("Toda la galería", color = Tva.Orange, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)) }
            items(l.cleanup, key = { it.key }) { g ->
                Card(
                    title = g.title,
                    sub = "${Fmt.n(g.items.size)} archivos · ${Fmt.size(g.bytes)}\n${g.desc}",
                    stamp = null,
                    color = Tva.Red,
                ) { push(Dest.Review(g.title, g.key, null)) }
            }
        }
        if (l.cleanup.isEmpty() && l.closed.isEmpty()) {
            item { Banner("Nada para podar por ahora.", color = Tva.Green) }
        }
        item { Spacer(Modifier.padding(24.dp)) }
    }
}

@Composable
private fun Card(title: String, sub: String, stamp: String?, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).border(1.dp, color).background(Tva.Panel)
            .clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            if (stamp != null) Stamp(stamp, color = color, modifier = Modifier.padding(bottom = 4.dp))
            Text(title, style = Tva.title.copy(fontSize = 17.sp))
            Text(sub, style = Tva.body.copy(fontSize = 13.sp, color = Tva.Ivory.copy(alpha = 0.75f)))
        }
        Text("›", style = Tva.title.copy(color = color, fontSize = 28.sp))
    }
}

private sealed interface RRow {
    data class Head(val g: CleanupGroup) : RRow
    data class Keep(val m: Media) : RRow
    data class Cand(val m: Media) : RRow
}

@Composable
fun ReviewScreen(title: String, groups: List<CleanupGroup>, l: Library, eventId: String?, push: (Dest) -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val repo = remember { Repo.get(ctx) }
    val all = remember(groups) { groups.flatMap { g -> g.items.map { it.m } } }
    val selected = remember(groups) { mutableStateMapOf<Long, Boolean>().apply { all.forEach { put(it.id, true) } } }
    var pending by remember { mutableStateOf<List<Long>>(emptyList()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            repo.removeIds(pending.toSet())
            if (eventId != null && all.size <= pending.size) repo.markReviewed(eventId)
        }
        pending = emptyList()
    }
    val rows = remember(groups) {
        val out = ArrayList<RRow>()
        for (g in groups) {
            out.add(RRow.Head(g))
            if (g.key == "repetidas") {
                g.items.groupBy { it.keep?.id }.values.forEach { cands ->
                    cands.first().keep?.let { out.add(RRow.Keep(it)) }
                    cands.forEach { out.add(RRow.Cand(it.m)) }
                }
            } else {
                g.items.forEach { out.add(RRow.Cand(it.m)) }
            }
        }
        out
    }
    val viewList = remember(rows) { rows.mapNotNull { (it as? RRow.Cand)?.m ?: (it as? RRow.Keep)?.m } }
    val chosen = all.filter { selected[it.id] == true }
    val bytes = chosen.sumOf { it.size }

    Column(Modifier.fillMaxSize()) {
        BackBar(title, "Tocá para salvar · mantené apretado para ver", onBack)
        if (groups.isEmpty()) {
            Box(Modifier.weight(1f)) { Empty("Nada para podar acá.") }
        } else {
            LazyVerticalGrid(GridCells.Fixed(4), Modifier.weight(1f)) {
                items(
                    count = rows.size,
                    span = { i -> if (rows[i] is RRow.Head) GridItemSpan(maxLineSpan) else GridItemSpan(1) },
                ) { i ->
                    when (val r = rows[i]) {
                        is RRow.Head -> Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 6.dp)) {
                            Text(r.g.title, style = Tva.title.copy(fontSize = 15.sp, color = Tva.Orange))
                            Label(r.g.desc)
                        }
                        is RRow.Keep -> Thumb(r.m, r.m.id in l.stars, onClick = {
                            push(Dest.View(viewList, viewList.indexOf(r.m).coerceAtLeast(0)))
                        }) {
                            Box(Modifier.fillMaxSize().border(2.dp, Tva.Green))
                            Text(
                                "QUEDA",
                                style = Tva.label.copy(color = Tva.Ivory, fontSize = 9.sp),
                                modifier = Modifier.align(Alignment.BottomEnd).background(Tva.Green).padding(horizontal = 4.dp, vertical = 1.dp),
                            )
                        }
                        is RRow.Cand -> {
                            val on = selected[r.m.id] == true
                            Thumb(
                                r.m, r.m.id in l.stars,
                                onClick = { selected[r.m.id] = !on },
                                onLong = { push(Dest.View(viewList, viewList.indexOf(r.m).coerceAtLeast(0))) },
                            ) {
                                if (on) {
                                    Box(Modifier.fillMaxSize().background(Tva.Red.copy(alpha = 0.30f)).border(2.dp, Tva.Red))
                                    Icon(Icons.Filled.Delete, null, tint = Tva.Ivory, modifier = Modifier.align(Alignment.TopStart).padding(3.dp).size(14.dp))
                                } else {
                                    Text(
                                        "SE SALVA",
                                        style = Tva.label.copy(color = Tva.Black, fontSize = 9.sp),
                                        modifier = Modifier.align(Alignment.BottomEnd).background(Tva.Ivory).padding(horizontal = 4.dp, vertical = 1.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().background(Tva.Black).navigationBarsPadding().padding(12.dp)) {
            Label("${Fmt.n(chosen.size)} marcadas · ${Fmt.size(bytes)}" + if (chosen.size > MAX_PER_REQUEST) " · van de a $MAX_PER_REQUEST" else "", color = Tva.Ivory)
            Row(Modifier.padding(top = 8.dp)) {
                TvaButton(
                    "A la papelera",
                    color = if (chosen.isEmpty()) Tva.Gray else Tva.Red,
                    modifier = Modifier.weight(1f),
                ) {
                    if (chosen.isNotEmpty()) {
                        val batch = chosen.take(MAX_PER_REQUEST)
                        try {
                            val pi = MediaStore.createTrashRequest(ctx.contentResolver, batch.map { it.uri }, true)
                            pending = batch.map { it.id }
                            launcher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                        } catch (e: Exception) {
                            pending = emptyList()
                        }
                    }
                }
                Spacer(Modifier.size(8.dp))
                TvaButton("Listo, no borro nada", filled = false) {
                    if (eventId != null) repo.markReviewed(eventId)
                    onBack()
                }
            }
        }
    }
}
