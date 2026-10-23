package ar.nacho.lineasagrada

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/** Índice del análisis, para no repetir el trabajo cada vez que se abre la app. */
object CacheIO {
    private const val VERSION = 1

    class Row(
        val modified: Long, val size: Long, val lat: Double, val lon: Double, val hash: Long,
        val sharp: Float, val lines: Int, val transient: Boolean, val camExif: Boolean, val stage: Int,
    ) {
        fun applyTo(m: Media) {
            m.lat = lat; m.lon = lon; m.hash = hash; m.sharp = sharp; m.textLines = lines
            m.transient = transient; m.camExif = camExif; m.stage = stage
        }
    }

    fun load(f: File): HashMap<Long, Row> {
        val out = HashMap<Long, Row>()
        if (!f.exists()) return out
        try {
            DataInputStream(BufferedInputStream(FileInputStream(f))).use { d ->
                if (d.readInt() != VERSION) return out
                val n = d.readInt()
                repeat(n) {
                    val id = d.readLong()
                    out[id] = Row(
                        d.readLong(), d.readLong(), d.readDouble(), d.readDouble(), d.readLong(),
                        d.readFloat(), d.readInt(), d.readBoolean(), d.readBoolean(), d.readInt(),
                    )
                }
            }
        } catch (e: Exception) {
            out.clear()
        }
        return out
    }

    @Synchronized
    fun save(f: File, items: List<Media>) {
        val tmp = File(f.parentFile, f.name + ".tmp")
        try {
            DataOutputStream(BufferedOutputStream(FileOutputStream(tmp))).use { d ->
                d.writeInt(VERSION)
                d.writeInt(items.size)
                for (m in items) {
                    d.writeLong(m.id); d.writeLong(m.modified); d.writeLong(m.size)
                    d.writeDouble(m.lat); d.writeDouble(m.lon); d.writeLong(m.hash)
                    d.writeFloat(m.sharp); d.writeInt(m.textLines); d.writeBoolean(m.transient)
                    d.writeBoolean(m.camExif); d.writeInt(m.stage)
                }
            }
            tmp.renameTo(f)
        } catch (e: Exception) {
            tmp.delete()
        }
    }
}

/** Lo que vos corregís a mano: nombres, uniones, cortes, estrellas, eventos revisados. */
data class EditsSnap(
    val names: Map<String, String>,
    val merges: Set<String>,
    val splits: Set<Long>,
    val dissolved: Set<String>,
    val stars: Set<Long>,
    val reviewed: Set<String>,
    val notified: Set<String>,
    val installTime: Long,
)

class Edits(private val file: File) {
    val names = HashMap<String, String>()
    val merges = HashSet<String>()
    val splits = HashSet<Long>()
    val dissolved = HashSet<String>()
    val stars = HashSet<Long>()
    val reviewed = HashSet<String>()
    val notified = HashSet<String>()
    var installTime = 0L

    init {
        load()
        if (installTime == 0L) {
            installTime = System.currentTimeMillis()
            save()
        }
    }

    private fun strs(o: JSONObject, k: String, into: MutableSet<String>) {
        o.optJSONArray(k)?.let { a -> for (i in 0 until a.length()) into.add(a.getString(i)) }
    }

    private fun longs(o: JSONObject, k: String, into: MutableSet<Long>) {
        o.optJSONArray(k)?.let { a -> for (i in 0 until a.length()) into.add(a.getLong(i)) }
    }

    private fun load() {
        if (!file.exists()) return
        try {
            val o = JSONObject(file.readText())
            o.optJSONObject("names")?.let { n -> n.keys().forEach { k -> names[k] = n.getString(k) } }
            strs(o, "merges", merges)
            longs(o, "splits", splits)
            strs(o, "dissolved", dissolved)
            longs(o, "stars", stars)
            strs(o, "reviewed", reviewed)
            strs(o, "notified", notified)
            installTime = o.optLong("installTime", 0L)
        } catch (e: Exception) {
        }
    }

    fun save() {
        val o = JSONObject()
        val n = JSONObject()
        names.forEach { (k, v) -> n.put(k, v) }
        o.put("names", n)
        o.put("merges", JSONArray(merges.toList()))
        o.put("splits", JSONArray(splits.toList()))
        o.put("dissolved", JSONArray(dissolved.toList()))
        o.put("stars", JSONArray(stars.toList()))
        o.put("reviewed", JSONArray(reviewed.toList()))
        o.put("notified", JSONArray(notified.toList()))
        o.put("installTime", installTime)
        try {
            file.writeText(o.toString())
        } catch (e: Exception) {
        }
    }

    fun snapshot() = EditsSnap(
        HashMap(names), HashSet(merges), HashSet(splits), HashSet(dissolved),
        HashSet(stars), HashSet(reviewed), HashSet(notified), installTime,
    )
}
