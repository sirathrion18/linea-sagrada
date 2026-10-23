package ar.nacho.lineasagrada.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ar.nacho.lineasagrada.Event
import ar.nacho.lineasagrada.Filter
import ar.nacho.lineasagrada.Fmt
import ar.nacho.lineasagrada.Library
import ar.nacho.lineasagrada.Media
import ar.nacho.lineasagrada.Repo
import ar.nacho.lineasagrada.countryName
import coil.compose.AsyncImage
import coil.request.ImageRequest

private sealed interface TRow {
    data class Year(val y: Int) : TRow
    data class Month(val key: Int, val label: String, val n: Int) : TRow
    data class Ev(val ev: Event, val n: Int, val cover: List<Media>) : TRow
    data class Ph(val m: Media, val idx: Int) : TRow
}

// ───────────────────────── FECHA ─────────────────────────

@Composable
fun Timeline(l: Library, filter: Filter, push: (Dest) -> Unit) {
    val data = remember(l, filter) {
        val out = ArrayList<TRow>()
        val photos = ArrayList<Media>()
        val vis = l.all.filter(filter::ok)
        val byMonth = vis.groupBy { val z = Fmt.z(it.time); z.year * 100 + z.monthValue }
        val shown = HashSet<String>()
        var year = -1
        for ((key, list) in byMonth) {
            if (key / 100 != year) { year = key / 100; out.add(TRow.Year(year)) }
            out.add(TRow.Month(key, Fmt.monthFull(list.first().time), list.size))
            for (m in list) {
                val ev = l.eventOf[m.id]
                if (ev != null) {
                    if (shown.add(ev.id)) {
                        val evVis = ev.items.filter(filter::ok)
                        val cover = if (evVis.size <= 3) evVis else listOf(evVis[evVis.size / 6], evVis[evVis.size / 2], evVis[evVis.size * 5 / 6])
                        out.add(TRow.Ev(ev, evVis.size, cover))
                    }
                } else {
                    out.add(TRow.Ph(m, photos.size))
                    photos.add(m)
                }
            }
        }
        out to photos
    }
    val (rows, photos) = data
    if (rows.isEmpty()) { Empty("No hay archivos con este filtro."); return }
    LazyVerticalGrid(GridCells.Fixed(4), Modifier.fillMaxSize()) {
        items(
            count = rows.size,
            key = { i ->
                when (val r = rows[i]) {
                    is TRow.Year -> "y${r.y}"
                    is TRow.Month -> "m${r.key}"
                    is TRow.Ev -> "ev${r.ev.id}"
                    is TRow.Ph -> r.m.id
                }
            },
            span = { i -> if (rows[i] is TRow.Ph) GridItemSpan(1) else GridItemSpan(maxLineSpan) },
        ) { i ->
            when (val r = rows[i]) {
                is TRow.Year -> Text(
                    "${r.y}",
                    style = Tva.title.copy(fontSize = 34.sp, color = Tva.Orange),
                    modifier = Modifier.padding(start = 12.dp, top = 20.dp),
                )
                is TRow.Month -> Row(Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(r.label.uppercase(), style = Tva.title.copy(fontSize = 15.sp))
                    Spacer(Modifier.weight(1f))
                    Label("${Fmt.n(r.n)} archivos")
                }
                is TRow.Ev -> EventCard(r.ev, r.n, r.cover, l) { push(Dest.Ev(r.ev.id)) }
                is TRow.Ph -> Thumb(r.m, r.m.id in l.stars, onClick = { push(Dest.View(photos, r.idx)) })
            }
        }
    }
}

@Composable
fun EventCard(ev: Event, n: Int, cover: List<Media>, l: Library, onClick: () -> Unit) {
    val ctx = LocalContext.current
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)
            .border(1.dp, Tva.Orange).background(Tva.Panel).clickable(onClick = onClick).padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Stamp(if (ev.isTrip) "Expediente · viaje" else "Expediente · evento")
            Spacer(Modifier.weight(1f))
            Label("${Fmt.n(n)} archivos")
        }
        Text(ev.name, style = Tva.h2, modifier = Modifier.padding(top = 6.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Label(Fmt.range(ev.start, ev.end) + " · " + Fmt.days(ev.start, ev.end) + " días", color = Tva.Ivory)
        if (ev.cities.size > 1 || (ev.cities.isNotEmpty() && ev.isTrip)) {
            Label(ev.cities.take(5).joinToString(" · ") { it.first }, color = Tva.Bronze)
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            cover.forEach { m ->
                AsyncImage(
                    model = ImageRequest.Builder(ctx).data(m.uri).size(300).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.weight(1f).height(90.dp).background(Tva.Black),
                )
            }
        }
    }
}

// ───────────────────────── LUGAR ─────────────────────────

private class CityRow(val cc: String, val city: String, val items: List<Media>, val inferred: Int)
private class CountryRow(val cc: String, val name: String, val total: Int, val cities: List<CityRow>)

@Composable
fun Places(l: Library, filter: Filter, push: (Dest) -> Unit) {
    val data = remember(l, filter) {
        val vis = l.all.filter(filter::ok)
        val known = vis.filter { it.id in l.place }
        val noPlace = vis.size - known.size
        val countries = known.groupBy { l.place[it.id]!!.cc }.map { (cc, list) ->
            val cities = list.groupBy { l.place[it.id]!!.city }.map { (city, items) ->
                CityRow(cc, city, items, items.count { l.place[it.id]!!.inferred })
            }.sortedByDescending { it.items.size }
            CountryRow(cc, countryName(cc), list.size, cities)
        }.sortedWith(compareByDescending<CountryRow> { it.cc == l.homeCc }.thenByDescending { it.total })
        countries to noPlace
    }
    val (countries, noPlace) = data
    if (!l.citiesAvailable) { Empty("Falta el mapa de ciudades en esta versión de la app."); return }
    if (countries.isEmpty() && noPlace == 0) { Empty("No hay archivos con este filtro."); return }
    LazyColumn(Modifier.fillMaxSize()) {
        countries.forEach { c ->
            item(key = "c${c.cc}") { SectionHeader(c.name, "${Fmt.n(c.total)} archivos · ${c.cities.size} ciudades") }
            items(c.cities, key = { "${it.cc}|${it.city}" }) { r -> PlaceRow(r.city, r.items, r.inferred) { push(Dest.CityD(r.cc, r.city)) } }
        }
        if (noPlace > 0) {
            item(key = "sin") {
                SectionHeader("Sin lugar", "${Fmt.n(noPlace)} archivos sin ubicación ni foto cercana en el tiempo")
            }
            item(key = "sinrow") {
                val list = l.all.filter { filter.ok(it) && it.id !in l.place }
                PlaceRow("Ver todos", list, 0) { push(Dest.NoPlace) }
            }
        }
    }
}

@Composable
private fun PlaceRow(title: String, items: List<Media>, inferred: Int, onClick: () -> Unit) {
    val ctx = LocalContext.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = ImageRequest.Builder(ctx).data(items.firstOrNull()?.uri).size(200).build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(56.dp).background(Tva.Panel),
        )
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(title, style = Tva.body.copy(fontSize = 16.sp))
            val last = items.firstOrNull()?.let { "Última: ${Fmt.dayMonthYear(it.time)}" } ?: ""
            Label(last + if (inferred > 0) " · ≈ ${Fmt.n(inferred)} ubicadas por fecha" else "")
        }
        Text(Fmt.n(items.size), style = Tva.title.copy(fontSize = 16.sp, color = Tva.Orange))
    }
}

// ───────────────────────── GRILLAS POR DÍA ─────────────────────────

@Composable
fun BackBar(title: String, sub: String?, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(end = 12.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = Tva.Orange) }
        Column(Modifier.weight(1f)) {
            Text(title, style = Tva.h2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub != null) Label(sub)
        }
    }
}

@Composable
fun DayGrid(
    list: List<Media>,
    l: Library,
    push: (Dest) -> Unit,
    dayLabel: (index: Int, dayStart: Long, items: List<Media>) -> String,
    dayAction: (@Composable (index: Int, dayStart: Long) -> Unit)? = null,
    header: LazyGridScope.() -> Unit = {},
) {
    val days = remember(list) { list.groupBy { Fmt.dayStart(it.time) }.entries.toList() }
    val index = remember(list) { list.withIndex().associate { it.value.id to it.index } }
    LazyVerticalGrid(GridCells.Fixed(4), Modifier.fillMaxSize()) {
        header()
        days.forEachIndexed { di, entry ->
            val start = entry.key
            val dayItems = entry.value
            item(key = "d$start", span = { GridItemSpan(maxLineSpan) }) {
                Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 14.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Label(dayLabel(di, start, dayItems), color = Tva.Ivory, modifier = Modifier.weight(1f))
                    dayAction?.invoke(di, start)
                }
            }
            items(count = dayItems.size, key = { dayItems[it].id }) { j ->
                val m = dayItems[j]
                Thumb(m, m.id in l.stars, onClick = { push(Dest.View(list, index[m.id] ?: 0)) })
            }
        }
    }
}

private fun cityOfDay(l: Library, items: List<Media>): String? =
    items.mapNotNull { l.place[it.id]?.city }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key

@Composable
fun CityScreen(l: Library, cc: String, city: String, filter: Filter, push: (Dest) -> Unit, onBack: () -> Unit) {
    val list = remember(l, filter, cc, city) {
        l.all.filter { filter.ok(it) && l.place[it.id]?.let { p -> p.cc == cc && p.city == city } == true }
    }
    Column(Modifier.fillMaxSize()) {
        BackBar(city, "${countryName(cc)} · ${Fmt.n(list.size)} archivos", onBack)
        DayGrid(list, l, push, dayLabel = { _, s, items ->
            val ev = l.eventOf[items.first().id]
            Fmt.dayFull(s) + " · ${items.size}" + (ev?.let { " · ${it.name}" } ?: "")
        })
    }
}

@Composable
fun NoPlaceScreen(l: Library, filter: Filter, push: (Dest) -> Unit, onBack: () -> Unit) {
    val list = remember(l, filter) { l.all.filter { filter.ok(it) && it.id !in l.place } }
    Column(Modifier.fillMaxSize()) {
        BackBar("Sin lugar", "${Fmt.n(list.size)} archivos", onBack)
        DayGrid(list, l, push, dayLabel = { _, s, items -> Fmt.dayFull(s) + " · ${items.size}" })
    }
}

// ───────────────────────── EVENTO ─────────────────────────

@Composable
fun EventScreen(l: Library, ev: Event, filter: Filter, push: (Dest) -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val repo = remember { Repo.get(ctx) }
    var renaming by remember { mutableStateOf(false) }
    var confirmDissolve by remember { mutableStateOf(false) }
    val list = remember(ev, filter) { ev.items.filter(filter::ok) }
    val next = remember(l, ev) { l.nextEvent(ev) }
    val cleanCount = remember(l, ev) { l.eventCleanup(ev).sumOf { it.items.size } }

    Column(Modifier.fillMaxSize()) {
        BackBar(ev.name, "${Fmt.range(ev.start, ev.end)} · ${Fmt.days(ev.start, ev.end)} días · ${Fmt.n(list.size)} archivos", onBack)
        DayGrid(
            list, l, push,
            dayLabel = { i, s, items ->
                "Día ${i + 1} · ${Fmt.dayShort(s)}" + (cityOfDay(l, items)?.let { " · $it" } ?: "") + " · ${items.size}"
            },
            dayAction = { i, s ->
                if (i > 0) TextButton(onClick = { repo.splitAt(s) }) { Label("Partir acá", color = Tva.Orange) }
            },
            header = {
                item(key = "hdr", span = { GridItemSpan(maxLineSpan) }) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Stamp(if (ev.isTrip) "Viaje" else "Evento")
                            Spacer(Modifier.width(8.dp))
                            if (ev.countries.isNotEmpty()) Label(ev.countries.joinToString(" · ") { countryName(it) }, color = Tva.Bronze)
                        }
                        if (ev.cities.isNotEmpty()) {
                            Row(Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState())) {
                                ev.cities.forEach { (c, n) -> Stamp("$c · $n", color = Tva.Ivory, modifier = Modifier.padding(end = 6.dp)) }
                            }
                        }
                        Row(Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TvaButton("Renombrar", filled = false) { renaming = true }
                            if (next != null) TvaButton("Unir con «${next.name}»", filled = false) { repo.mergeWithNext(ev, next) }
                            TvaButton("No es un evento", color = Tva.Gray, filled = false) { confirmDissolve = true }
                            if (cleanCount > 0) TvaButton("Podar · $cleanCount", color = Tva.Red) {
                                push(Dest.Review("Podar · ${ev.name}", "evento", ev.id))
                            }
                        }
                        Label("«Partir acá» corta el evento al empezar ese día.", modifier = Modifier.padding(top = 8.dp))
                    }
                }
            },
        )
    }

    if (renaming) {
        var text by remember { mutableStateOf(ev.name) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            containerColor = Tva.Panel,
            title = { Label("Renombrar expediente", color = Tva.Orange) },
            text = {
                OutlinedTextField(
                    value = text, onValueChange = { text = it }, singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Tva.Orange, unfocusedBorderColor = Tva.Gray,
                        focusedTextColor = Tva.Ivory, unfocusedTextColor = Tva.Ivory, cursorColor = Tva.Orange,
                    ),
                )
            },
            confirmButton = { TextButton(onClick = { repo.rename(ev.id, text); renaming = false }) { Label("Guardar", color = Tva.Orange) } },
            dismissButton = { TextButton(onClick = { renaming = false }) { Label("Cancelar") } },
        )
    }
    if (confirmDissolve) {
        AlertDialog(
            onDismissRequest = { confirmDissolve = false },
            containerColor = Tva.Panel,
            title = { Label("¿No es un evento?", color = Tva.Orange) },
            text = { Text("Las fotos vuelven a la línea de tiempo común. No se borra nada.", style = Tva.body) },
            confirmButton = { TextButton(onClick = { repo.dissolve(ev.id); confirmDissolve = false; onBack() }) { Label("Confirmar", color = Tva.Orange) } },
            dismissButton = { TextButton(onClick = { confirmDissolve = false }) { Label("Cancelar") } },
        )
    }
}

@Composable
fun Empty(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, style = Tva.body.copy(color = Tva.Gray))
    }
}
