package ar.nacho.lineasagrada

import kotlin.math.floor

class PlaceInfo(val city: String, val cc: String, val inferred: Boolean)

class Event(
    val id: String,
    val name: String,
    val start: Long,
    val end: Long,
    val items: List<Media>,          // de más viejo a más nuevo
    val isTrip: Boolean,
    val cities: List<Pair<String, Int>>,
    val countries: List<String>,
)

class Candidate(val m: Media, val keep: Media?)

class CleanupGroup(val key: String, val title: String, val desc: String, val items: List<Candidate>) {
    val bytes: Long get() = items.sumOf { it.m.size }
}

/** Foto del estado de la galería: lugares, eventos y sugerencias de limpieza. */
class Library(
    val all: List<Media>,                 // más nuevo primero
    val place: Map<Long, PlaceInfo>,
    val events: List<Event>,              // más nuevo primero
    val eventOf: Map<Long, Event>,
    val homeCc: String?,
    val stars: Set<Long>,
    val cleanup: List<CleanupGroup>,
    val closed: List<Event>,
    val citiesAvailable: Boolean,
    val now: Long,
) {
    fun isProtected(m: Media) = m.sysFavorite || m.id in stars
    fun eventCleanup(ev: Event): List<CleanupGroup> = Cleanup.forEvent(ev.items, ::isProtected)

    /** El evento que viene justo después (más nuevo). */
    fun nextEvent(ev: Event): Event? {
        val i = events.indexOfFirst { it.id == ev.id }
        return if (i > 0) events[i - 1] else null
    }

    companion object {
        const val AWAY_KM = 80.0
        const val TRIP_GAP = 3 * DAY
        const val LOCAL_GAP = 3 * HOUR
        const val LOCAL_MIN = 30

        private fun cellKey(lat: Double, lon: Double): Long =
            (floor(lat * 2).toLong() + 400) * 10_000 + (floor(lon * 2).toLong() + 400)

        private fun lowerBound(t: LongArray, x: Long): Int {
            var lo = 0
            var hi = t.size
            while (lo < hi) {
                val mid = (lo + hi) ushr 1
                if (t[mid] < x) lo = mid + 1 else hi = mid
            }
            return lo
        }

        fun build(
            items: List<Media>,
            nearest: (Double, Double) -> City?,
            citiesAvailable: Boolean,
            e: EditsSnap,
            now: Long,
        ): Library {
            val asc = items.sortedBy { it.time }
            val t = LongArray(asc.size) { asc[it].time }
            val locAsc = asc.filter { it.hasLoc }

            // 1. Casa = la zona donde más días distintos sacaste fotos.
            val cellDays = HashMap<Long, HashSet<Long>>()
            for (m in locAsc) cellDays.getOrPut(cellKey(m.lat, m.lon)) { HashSet() }.add(m.time / DAY)
            val homeCell = cellDays.maxByOrNull { it.value.size }?.key
            var homeLat = Double.NaN
            var homeLon = Double.NaN
            if (homeCell != null) {
                val inCell = locAsc.filter { cellKey(it.lat, it.lon) == homeCell }
                homeLat = inCell.map { it.lat }.sorted()[inCell.size / 2]
                homeLon = inCell.map { it.lon }.sorted()[inCell.size / 2]
            }
            val homeCc = if (!homeLat.isNaN()) nearest(homeLat, homeLon)?.cc else null
            fun away(m: Media) = !homeLat.isNaN() && distKm(m.lat, m.lon, homeLat, homeLon) > AWAY_KM

            // 2. Ciudad de cada foto con coordenadas.
            val cityOf = HashMap<Long, City>()
            for (m in locAsc) nearest(m.lat, m.lon)?.let { cityOf[m.id] = it }

            // 3. Viajes: fotos lejos de casa, seguidas, sin una foto en casa en el medio.
            val ranges = ArrayList<Pair<Long, Long>>()
            var s = -1L
            var last = -1L
            var count = 0
            fun close() {
                if (s >= 0 && (last - s >= 12 * HOUR || count >= 15)) ranges.add(s to last)
                s = -1L
            }
            for (m in locAsc) {
                if (away(m)) {
                    if (s >= 0 && m.time - last <= TRIP_GAP) {
                        last = m.time; count++
                    } else {
                        close(); s = m.time; last = m.time; count = 1
                    }
                } else {
                    close()
                }
            }
            close()

            // Grupos como rangos de índices sobre la lista ordenada.
            val groups = ArrayList<Pair<IntRange, Boolean>>()
            val inTrip = BooleanArray(asc.size)
            for ((a, b) in ranges) {
                val lo = lowerBound(t, a)
                val hi = lowerBound(t, b + 1) - 1
                if (hi >= lo) {
                    groups.add(lo..hi to true)
                    for (i in lo..hi) inTrip[i] = true
                }
            }
            // 4. Eventos en casa: muchas fotos seguidas (cumpleaños, recital, partido).
            var runStart = -1
            fun flush(endIdx: Int) {
                if (runStart >= 0) {
                    val n = endIdx - runStart + 1
                    if (n >= LOCAL_MIN && t[endIdx] - t[runStart] <= 18 * HOUR) groups.add(runStart..endIdx to false)
                }
                runStart = -1
            }
            for (i in asc.indices) {
                if (inTrip[i]) { flush(i - 1); continue }
                if (runStart >= 0 && t[i] - t[i - 1] > LOCAL_GAP) flush(i - 1)
                if (runStart < 0) runStart = i
            }
            flush(asc.size - 1)
            groups.sortBy { it.first.first }

            fun idOf(r: IntRange) = "e${asc[r.first].time}_${asc[r.first].id}"

            // 5. Tus correcciones: cortes, uniones y "no es un evento".
            val cuts = e.splits.sorted()
            val cutGroups = ArrayList<Pair<IntRange, Boolean>>()
            for ((r, trip) in groups) {
                var from = r.first
                for (c in cuts) {
                    if (c > t[r.first] && c <= t[r.last]) {
                        val at = lowerBound(t, c).coerceIn(from, r.last + 1)
                        if (at > from) { cutGroups.add(from until at to trip); from = at }
                    }
                }
                if (from <= r.last) cutGroups.add(from..r.last to trip)
            }
            val merged = ArrayList<Pair<IntRange, Boolean>>()
            for ((r, trip) in cutGroups) {
                val prev = merged.lastOrNull()
                if (prev != null && "${idOf(prev.first)}|${idOf(r)}" in e.merges) {
                    merged[merged.size - 1] = (prev.first.first..r.last) to (prev.second || trip)
                } else {
                    merged.add(r to trip)
                }
            }
            val finalGroups = merged.filter { idOf(it.first) !in e.dissolved }

            // 6. Lugar de cada foto (las que no tienen coordenadas, por cercanía en el tiempo).
            val place = HashMap<Long, PlaceInfo>()
            val withCity = asc.filter { it.id in cityOf }
            val wt = LongArray(withCity.size) { withCity[it].time }
            for (m in asc) cityOf[m.id]?.let { place[m.id] = PlaceInfo(it.name, it.cc, false) }

            val events = ArrayList<Event>()
            val eventOf = HashMap<Long, Event>()
            for ((r, trip) in finalGroups) {
                val list = asc.subList(r.first, r.last + 1)
                val located = list.filter { it.id in cityOf }
                // Lugar inferido dentro del evento: la foto con ciudad más cercana en el tiempo, del mismo evento.
                if (located.isNotEmpty()) {
                    val lt = LongArray(located.size) { located[it].time }
                    for (m in list) if (m.id !in place) {
                        val j = lowerBound(lt, m.time)
                        val cand = listOfNotNull(located.getOrNull(j), located.getOrNull(j - 1))
                            .minByOrNull { kotlin.math.abs(it.time - m.time) }
                        cand?.let { c -> cityOf[c.id]?.let { place[m.id] = PlaceInfo(it.name, it.cc, true) } }
                    }
                }
                val cityCounts = located.groupingBy { cityOf[it.id]!!.name }.eachCount()
                    .entries.sortedByDescending { it.value }.map { it.key to it.value }
                val ccs = located.groupingBy { cityOf[it.id]!!.cc }.eachCount()
                    .entries.sortedByDescending { it.value }.map { it.key }
                val start = list.first().time
                val label = if (trip) {
                    when {
                        ccs.size >= 2 && ccs.all { it in EUROPE } -> "Europa"
                        ccs.size == 1 && ccs[0] != homeCc -> countryName(ccs[0])
                        ccs.size == 1 -> cityCounts.firstOrNull()?.first ?: "Viaje"
                        ccs.size >= 2 -> ccs.take(3).joinToString(", ") { countryName(it) }
                        else -> "Viaje"
                    }
                } else {
                    cityCounts.firstOrNull()?.first ?: "Evento"
                }
                val auto = if (trip) "$label · ${Fmt.monthYear(start)}" else "$label · ${Fmt.dayMonthYear(start)}"
                val id = idOf(r)
                val ev = Event(id, e.names[id] ?: auto, start, list.last().time, list, trip, cityCounts, ccs)
                events.add(ev)
                for (m in list) eventOf[m.id] = ev
            }
            // Fuera de eventos: se infiere solo si hay una foto con ciudad a menos de 6 horas.
            for (m in asc) if (m.id !in place && m.id !in eventOf) {
                val j = lowerBound(wt, m.time)
                val cand = listOfNotNull(withCity.getOrNull(j), withCity.getOrNull(j - 1))
                    .minByOrNull { kotlin.math.abs(it.time - m.time) }
                if (cand != null && kotlin.math.abs(cand.time - m.time) <= 6 * HOUR) {
                    cityOf[cand.id]?.let { place[m.id] = PlaceInfo(it.name, it.cc, true) }
                }
            }
            events.reverse()

            val prot = { m: Media -> m.sysFavorite || m.id in e.stars }
            val cleanup = Cleanup.global(asc, prot, eventOf, now)
            // "Al cerrar un evento": terminó hace más de 48 h, es posterior a la instalación y no lo revisaste.
            val closed = events.filter { ev ->
                ev.end < now - 2 * DAY && ev.end > e.installTime - 7 * DAY && ev.id !in e.reviewed &&
                    Cleanup.forEvent(ev.items, prot).isNotEmpty()
            }
            return Library(
                all = asc.asReversed().toList(),
                place = place,
                events = events,
                eventOf = eventOf,
                homeCc = homeCc,
                stars = e.stars,
                cleanup = cleanup,
                closed = closed,
                citiesAvailable = citiesAvailable,
                now = now,
            )
        }
    }
}

object Cleanup {
    const val BLUR = 15f

    /** Repetidas y ráfagas: se agrupan las casi iguales y queda la mejor de cada grupo. */
    fun repeats(items: List<Media>, prot: (Media) -> Boolean): List<Candidate> {
        val ms = items.filter { it.stage >= Stage.HASH && it.hash != 0L }.sortedBy { it.time }
        val n = ms.size
        val parent = IntArray(n) { it }
        fun find(x: Int): Int {
            var a = x
            while (parent[a] != a) { parent[a] = parent[parent[a]]; a = parent[a] }
            return a
        }
        fun union(a: Int, b: Int) { parent[find(a)] = find(b) }
        // Ráfagas: casi iguales y sacadas con menos de 2 minutos de diferencia.
        for (i in 0 until n) {
            var j = i + 1
            while (j < n && ms[j].time - ms[i].time <= 120_000) {
                if (ms[i].isVideo == ms[j].isVideo && Scanner.hamming(ms[i].hash, ms[j].hash) <= 8) union(i, j)
                j++
            }
        }
        // Copias exactas en cualquier fecha (ej.: la misma foto reenviada por WhatsApp).
        ms.indices.groupBy { ms[it].hash to ms[it].isVideo }.values.filter { it.size > 1 }
            .forEach { g -> g.drop(1).forEach { union(g[0], it) } }

        val out = ArrayList<Candidate>()
        ms.indices.groupBy { find(it) }.values.filter { it.size > 1 }.forEach { g ->
            val members = g.map { ms[it] }
            val keep = members.sortedWith(
                compareByDescending<Media> { prot(it) }
                    .thenByDescending { it.origin == Origin.CAMARA }
                    .thenByDescending { it.sharp * kotlin.math.ln(it.pixels.toDouble() + 2) }
                    .thenByDescending { it.size },
            ).first()
            members.filter { it !== keep && !prot(it) }.forEach { out.add(Candidate(it, keep)) }
        }
        return out
    }

    private class Acc {
        val used = HashSet<Long>()
        val out = ArrayList<CleanupGroup>()
        fun add(key: String, title: String, desc: String, c: List<Candidate>) {
            val f = c.filter { used.add(it.m.id) }
            if (f.isNotEmpty()) out.add(CleanupGroup(key, title, desc, f))
        }
    }

    private fun basics(acc: Acc, items: List<Media>, prot: (Media) -> Boolean) {
        acc.add(
            "repetidas", "REPETIDAS Y RÁFAGAS",
            "Fotos casi iguales. De cada grupo queda la mejor (marcada QUEDA).",
            repeats(items, prot),
        )
        val free = items.filter { !prot(it) }
        acc.add(
            "movidas", "MOVIDAS O BORROSAS",
            "Fotos de la cámara con muy poca nitidez.",
            free.filter { !it.isVideo && it.origin == Origin.CAMARA && it.sharp >= 0f && it.sharp < BLUR }
                .map { Candidate(it, null) },
        )
        acc.add(
            "videos", "VIDEOS ACCIDENTALES",
            "Videos de menos de 2 segundos.",
            free.filter { it.isVideo && it.durationMs in 1 until 2000 }.map { Candidate(it, null) },
        )
    }

    fun forEvent(items: List<Media>, prot: (Media) -> Boolean): List<CleanupGroup> {
        val acc = Acc()
        basics(acc, items, prot)
        return acc.out
    }

    fun global(all: List<Media>, prot: (Media) -> Boolean, eventOf: Map<Long, Event>, now: Long): List<CleanupGroup> {
        val acc = Acc()
        basics(acc, all, prot)
        val free = all.filter { !prot(it) }
        acc.add(
            "unsolouso", "DE UN SOLO USO",
            "Comprobantes, códigos, turnos y envíos de hace más de 2 meses (detectados por el texto).",
            free.filter { it.transient && now - it.time > 60 * DAY }.map { Candidate(it, null) },
        )
        acc.add(
            "capturas", "CAPTURAS VIEJAS",
            "Capturas de pantalla de hace más de 3 meses.",
            free.filter { it.origin == Origin.CAPTURAS && now - it.time > 90 * DAY }.map { Candidate(it, null) },
        )
        acc.add(
            "whatsapp", "RECIBIDAS POR WHATSAPP",
            "Memes, cadenas y reenvíos de hace más de 2 meses. Quedan afuera los que caen dentro de un evento.",
            free.filter {
                it.origin == Origin.WHATSAPP && !it.camExif && !isDocument(it) &&
                    it.id !in eventOf && now - it.time > 60 * DAY
            }.map { Candidate(it, null) },
        )
        return acc.out
    }
}
