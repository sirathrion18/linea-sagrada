package ar.nacho.lineasagrada

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.provider.BaseColumns
import android.provider.MediaStore
import android.util.Size
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognizer
import kotlinx.coroutines.tasks.await
import java.text.Normalizer
import java.time.LocalDate
import java.time.ZoneId

object Scanner {
    private val nameDate = Regex("(20\\d{2})[-_]?([01]\\d)[-_]?([0-3]\\d)")
    private val iso6709 = Regex("([+-]\\d+(?:\\.\\d+)?)([+-]\\d+(?:\\.\\d+)?)")

    /** Lista todo lo que hay en la galería (fotos y videos) sin abrir los archivos. */
    fun query(ctx: Context): List<Media> {
        val out = ArrayList<Media>()
        val cr = ctx.contentResolver
        for (video in listOf(false, true)) {
            val coll = if (video) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            else MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            val proj = mutableListOf(
                BaseColumns._ID,
                MediaStore.MediaColumns.DATE_TAKEN,
                MediaStore.MediaColumns.DATE_MODIFIED,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.WIDTH,
                MediaStore.MediaColumns.HEIGHT,
                MediaStore.MediaColumns.RELATIVE_PATH,
                MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.IS_FAVORITE,
            )
            if (video) proj.add(MediaStore.MediaColumns.DURATION)
            try {
                cr.query(coll, proj.toTypedArray(), null, null, null)?.use { c ->
                    val iId = c.getColumnIndexOrThrow(BaseColumns._ID)
                    val iTaken = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
                    val iMod = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                    val iSize = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                    val iW = c.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
                    val iH = c.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
                    val iPath = c.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
                    val iBucket = c.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                    val iName = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                    val iFav = c.getColumnIndexOrThrow(MediaStore.MediaColumns.IS_FAVORITE)
                    val iDur = if (video) c.getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION) else -1
                    while (c.moveToNext()) {
                        val name = c.getString(iName) ?: ""
                        val taken = c.getLong(iTaken)
                        val modified = c.getLong(iMod)
                        // WhatsApp suele borrar la fecha de la foto: la sacamos del nombre (IMG-20250805-WA0012).
                        val time = if (taken > 0) taken else timeFromName(name) ?: (modified * 1000)
                        out.add(
                            Media(
                                id = c.getLong(iId),
                                isVideo = video,
                                time = time,
                                modified = modified,
                                size = c.getLong(iSize),
                                width = c.getInt(iW),
                                height = c.getInt(iH),
                                durationMs = if (iDur >= 0) c.getLong(iDur) else 0L,
                                path = c.getString(iPath) ?: "",
                                bucket = c.getString(iBucket) ?: "",
                                name = name,
                                sysFavorite = c.getInt(iFav) == 1,
                            ),
                        )
                    }
                }
            } catch (e: Exception) {
            }
        }
        return out
    }

    fun timeFromName(name: String): Long? {
        val mm = nameDate.find(name) ?: return null
        val (y, mo, d) = mm.destructured
        return try {
            LocalDate.of(y.toInt(), mo.toInt(), d.toInt()).atTime(12, 0)
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (e: Exception) {
            null
        }
    }

    /** Lee las coordenadas guardadas en la foto o el video. */
    fun readLocation(ctx: Context, m: Media) {
        val cr = ctx.contentResolver
        val orig = try { MediaStore.setRequireOriginal(m.uri) } catch (e: Exception) { m.uri }
        if (!m.isVideo) {
            val exif = try {
                cr.openInputStream(orig)?.use { ExifInterface(it) }
            } catch (e: Exception) {
                null
            } ?: try {
                cr.openInputStream(m.uri)?.use { ExifInterface(it) }
            } catch (e: Exception) {
                null
            }
            if (exif != null) {
                val ll = exif.latLong
                if (ll != null && !(ll[0] == 0.0 && ll[1] == 0.0)) {
                    m.lat = ll[0]; m.lon = ll[1]
                }
                m.camExif = exif.getAttribute(ExifInterface.TAG_MAKE) != null
            }
        } else {
            val r = MediaMetadataRetriever()
            try {
                try { r.setDataSource(ctx, orig) } catch (e: Exception) { r.setDataSource(ctx, m.uri) }
                val loc = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION)
                if (loc != null) {
                    iso6709.find(loc)?.let { mm ->
                        val la = mm.groupValues[1].toDouble()
                        val lo = mm.groupValues[2].toDouble()
                        if (!(la == 0.0 && lo == 0.0)) { m.lat = la; m.lon = lo }
                    }
                }
                m.camExif = m.origin == Origin.CAMARA
            } catch (e: Exception) {
            } finally {
                try { r.release() } catch (e: Exception) { }
            }
        }
    }

    fun thumb(ctx: Context, m: Media, px: Int): Bitmap? = try {
        val b = ctx.contentResolver.loadThumbnail(m.uri, Size(px, px), null)
        if (b.config == Bitmap.Config.HARDWARE) b.copy(Bitmap.Config.ARGB_8888, false) else b
    } catch (e: Exception) {
        null
    }

    private fun lum(c: Int) =
        ((c shr 16 and 0xFF) * 299 + (c shr 8 and 0xFF) * 587 + (c and 0xFF) * 114) / 1000

    /** Huella visual de 64 bits: dos fotos casi iguales tienen huellas casi iguales. */
    fun dHash(b: Bitmap): Long {
        val s = Bitmap.createScaledBitmap(b, 9, 8, true)
        var h = 0L
        var bit = 0
        for (y in 0 until 8) for (x in 0 until 8) {
            if (lum(s.getPixel(x, y)) > lum(s.getPixel(x + 1, y))) h = h or (1L shl bit)
            bit++
        }
        return if (h == 0L) 1L else h
    }

    /** Nitidez (varianza del laplaciano): números bajos = foto movida o borrosa. */
    fun sharpness(b: Bitmap): Float {
        val w = b.width
        val h = b.height
        if (w < 3 || h < 3) return 0f
        val px = IntArray(w * h)
        b.getPixels(px, 0, w, 0, 0, w, h)
        val g = IntArray(w * h) { lum(px[it]) }
        var sum = 0.0
        var sq = 0.0
        var n = 0
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x
            val l = 4 * g[i] - g[i - 1] - g[i + 1] - g[i - w] - g[i + w]
            sum += l; sq += l.toDouble() * l; n++
        }
        val mean = sum / n
        return (sq / n - mean * mean).toFloat()
    }

    fun hamming(a: Long, b: Long) = java.lang.Long.bitCount(a xor b)

    // Palabras de comprobantes y cosas de un solo uso. El texto se lee y se descarta: no se guarda.
    private val transientRx = Regex(
        "comprobante|transferencia|operacion|\\bcae\\b|ticket|factura|codigo de (verificacion|seguridad|reserva|retiro)" +
            "|\\bturno\\b|vencimiento|\\btoken\\b|seguimiento|tracking|de pedido|\\benvio\\b|\\bcbu\\b|\\bcvu\\b" +
            "|mercado pago|\\bqr\\b|pagaste|recibiste|aprobad[oa]",
    )
    private val marks = Regex("\\p{M}+")

    suspend fun readText(ctx: Context, m: Media, rec: TextRecognizer) {
        val b = thumb(ctx, m, 1024)
        if (b == null) { m.textLines = 0; return }
        try {
            val r = rec.process(InputImage.fromBitmap(b, 0)).await()
            m.textLines = r.textBlocks.sumOf { it.lines.size }
            val t = Normalizer.normalize(r.text.lowercase(), Normalizer.Form.NFD).replace(marks, "")
            m.transient = m.textLines >= 2 && transientRx.containsMatchIn(t)
        } catch (e: Exception) {
            m.textLines = 0
        }
    }
}
