package ar.nacho.lineasagrada

import android.content.Context
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.File

/** Centro de operaciones: lee la galería, guarda el análisis y arma la Library. */
class Repo private constructor(private val ctx: Context) {
    companion object {
        @Volatile private var inst: Repo? = null
        fun get(ctx: Context): Repo = inst ?: synchronized(this) {
            inst ?: Repo(ctx.applicationContext).also { inst = it }
        }
    }

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val cities by lazy { Cities(ctx) }
    val edits = Edits(File(ctx.filesDir, "edits.json"))
    private val cacheFile = File(ctx.filesDir, "index.bin")
    private val mutex = Mutex()

    val items = MutableStateFlow<List<Media>>(emptyList())
    val library = MutableStateFlow<Library?>(null)
    val progress = MutableStateFlow(Progress.IDLE)

    /** Arranca (o retoma) el análisis si no está corriendo. */
    fun start() {
        if (mutex.isLocked) return
        scope.launch { refresh() }
    }

    suspend fun refresh(budgetMs: Long = Long.MAX_VALUE) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val deadline = if (budgetMs == Long.MAX_VALUE) Long.MAX_VALUE else System.currentTimeMillis() + budgetMs
            if (items.value.isEmpty()) progress.value = Progress("LEYENDO GALERÍA", 0, 0)
            val cached = CacheIO.load(cacheFile)
            val list = Scanner.query(ctx)
            for (m in list) {
                val c = cached[m.id]
                if (c != null && c.modified == m.modified && c.size == m.size) c.applyTo(m)
            }
            items.value = list
            recompute()
            try {
                process(list, deadline)
            } finally {
                progress.value = Progress.IDLE
                save()
                recompute()
            }
        }
    }

    private suspend fun process(list: List<Media>, deadline: Long) {
        val order = list.sortedByDescending { it.time }

        suspend fun stage(target: Int, label: String, work: suspend (Media) -> Unit): Boolean {
            val todo = order.filter { it.stage < target }
            var done = 0
            for (m in todo) {
                if (System.currentTimeMillis() > deadline) return false
                yield()
                try { work(m) } catch (e: Exception) { }
                m.stage = target
                done++
                if (done % 20 == 0) progress.value = Progress(label, done, todo.size)
                if (done % 500 == 0) { save(); recompute() }
            }
            if (todo.isNotEmpty()) { save(); recompute() }
            return true
        }

        if (!stage(Stage.LOC, "UBICANDO") { Scanner.readLocation(ctx, it) }) return
        if (!stage(Stage.HASH, "COMPARANDO") { m ->
                Scanner.thumb(ctx, m, 256)?.let { b ->
                    m.hash = Scanner.dHash(b)
                    if (!m.isVideo) m.sharp = Scanner.sharpness(b)
                }
            }
        ) return
        val rec = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            stage(Stage.TEXT, "LEYENDO TEXTO") { m -> if (!m.isVideo) Scanner.readText(ctx, m, rec) }
        } finally {
            rec.close()
        }
    }

    private fun save() = CacheIO.save(cacheFile, items.value)

    @Synchronized
    fun recompute() {
        val snap = synchronized(edits) { edits.snapshot() }
        val c = cities
        library.value = Library.build(items.value, c::nearest, c.available, snap, System.currentTimeMillis())
    }

    private fun edit(block: Edits.() -> Unit) {
        synchronized(edits) { edits.block(); edits.save() }
        scope.launch { recompute() }
    }

    fun rename(id: String, name: String) = edit { if (name.isBlank()) names.remove(id) else names[id] = name.trim() }
    fun mergeWithNext(a: Event, b: Event) = edit { merges.add("${a.id}|${b.id}") }
    fun splitAt(ts: Long) = edit { splits.add(ts) }
    fun dissolve(id: String) = edit { dissolved.add(id) }
    fun toggleStar(id: Long) = edit { if (!stars.remove(id)) stars.add(id) }
    fun markReviewed(id: String) = edit { reviewed.add(id) }
    fun markNotified(id: String) = edit { notified.add(id) }

    /** Saca de la vista lo que mandaste a la papelera. */
    fun removeIds(ids: Set<Long>) {
        items.value = items.value.filter { it.id !in ids }
        scope.launch { save(); recompute() }
    }
}
