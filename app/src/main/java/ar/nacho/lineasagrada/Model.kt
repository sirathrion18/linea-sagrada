package ar.nacho.lineasagrada

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore

const val HOUR = 3_600_000L
const val DAY = 24 * HOUR

/** De dónde vino el archivo, según la carpeta donde vive. */
enum class Origin(val label: String) {
    CAMARA("Cámara"),
    WHATSAPP("WhatsApp"),
    DESCARGAS("Descargas"),
    CAPTURAS("Capturas"),
    DRIVE("Drive"),
    OTRAS("Otras"),
}

fun originOf(path: String, bucket: String, name: String): Origin {
    val p = "$path/$bucket".lowercase()
    val n = name.lowercase()
    return when {
        "screenshot" in p || "captura" in p || n.startsWith("screenshot") -> Origin.CAPTURAS
        "whatsapp" in p -> Origin.WHATSAPP
        "drive" in p -> Origin.DRIVE
        "download" in p || "descarga" in p -> Origin.DESCARGAS
        p.startsWith("dcim/") || "camera" in p || "cámara" in p -> Origin.CAMARA
        else -> Origin.OTRAS
    }
}

/** Etapas de análisis: 0 = solo metadatos, 1 = ubicación, 2 = huella visual, 3 = texto. */
object Stage {
    const val META = 0
    const val LOC = 1
    const val HASH = 2
    const val TEXT = 3
}

class Media(
    val id: Long,
    val isVideo: Boolean,
    val time: Long,
    val modified: Long,
    val size: Long,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val path: String,
    val bucket: String,
    val name: String,
    val sysFavorite: Boolean,
) {
    @Volatile var lat: Double = Double.NaN
    @Volatile var lon: Double = Double.NaN
    @Volatile var hash: Long = 0L          // 0 = todavía no calculada
    @Volatile var sharp: Float = -1f       // -1 = no aplica / no calculada
    @Volatile var textLines: Int = -1
    @Volatile var transient: Boolean = false
    @Volatile var camExif: Boolean = false
    @Volatile var stage: Int = Stage.META

    val uri: Uri by lazy {
        ContentUris.withAppendedId(
            if (isVideo) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            else MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
            id,
        )
    }
    val origin: Origin by lazy { originOf(path, bucket, name) }
    val hasLoc: Boolean get() = !lat.isNaN()
    val pixels: Long get() = width.toLong() * height
}

/** "Documento": capturas de pantalla y fotos de papeles (mucho texto). */
fun isDocument(m: Media): Boolean =
    m.origin == Origin.CAPTURAS || (!m.isVideo && m.textLines >= 6)

enum class Filter(val label: String) {
    TODO("Todo"),
    CAMARA("Cámara"),
    WHATSAPP("WhatsApp"),
    DESCARGAS("Descargas y Drive"),
    CAPTURAS("Capturas"),
    DOCUMENTOS("Documentos"),
    VIDEOS("Videos"),
    OTRAS("Otras");

    fun ok(m: Media): Boolean = when (this) {
        TODO -> true
        CAMARA -> m.origin == Origin.CAMARA
        WHATSAPP -> m.origin == Origin.WHATSAPP
        DESCARGAS -> m.origin == Origin.DESCARGAS || m.origin == Origin.DRIVE
        CAPTURAS -> m.origin == Origin.CAPTURAS
        DOCUMENTOS -> isDocument(m)
        VIDEOS -> m.isVideo
        OTRAS -> m.origin == Origin.OTRAS
    }
}

data class Progress(val label: String, val done: Int, val total: Int) {
    val active: Boolean get() = label.isNotEmpty()

    companion object {
        val IDLE = Progress("", 0, 0)
    }
}
