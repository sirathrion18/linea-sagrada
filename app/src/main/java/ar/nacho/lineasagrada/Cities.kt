package ar.nacho.lineasagrada

import android.content.Context
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

class City(val name: String, val cc: String, val lat: Double, val lon: Double)

/**
 * Buscador de ciudades sin internet: usa una lista de GeoNames (ciudades de más de
 * 5.000 habitantes) que viene adentro de la app. Las coordenadas nunca salen del celu.
 */
class Cities(ctx: Context) {
    private val list = ArrayList<City>()
    private val grid = HashMap<Int, MutableList<City>>()
    val available: Boolean get() = list.isNotEmpty()

    init {
        try {
            ctx.assets.open("cities.tsv").bufferedReader().useLines { lines ->
                lines.forEach { l ->
                    val p = l.split('\t')
                    if (p.size >= 4) {
                        val c = City(p[0], p[1], p[2].toDouble(), p[3].toDouble())
                        list.add(c)
                        grid.getOrPut(key(floor(c.lat).toInt(), floor(c.lon).toInt())) { ArrayList() }.add(c)
                    }
                }
            }
        } catch (e: Exception) {
        }
    }

    private fun key(la: Int, lo: Int): Int {
        val w = ((lo + 180) % 360 + 360) % 360
        return (la + 90) * 400 + w
    }

    fun nearest(lat: Double, lon: Double): City? {
        if (list.isEmpty()) return null
        val la = floor(lat).toInt()
        val lo = floor(lon).toInt()
        var best: City? = null
        var bd = Double.MAX_VALUE
        for (dla in -1..1) for (dlo in -1..1) {
            grid[key(la + dla, lo + dlo)]?.forEach { c ->
                val d = distKm(lat, lon, c.lat, c.lon)
                if (d < bd) { bd = d; best = c }
            }
        }
        return if (bd <= 60.0) best else null
    }
}

fun distKm(a: Double, b: Double, c: Double, d: Double): Double {
    val r = 6371.0
    val dLat = Math.toRadians(c - a)
    val dLon = Math.toRadians(d - b)
    val h = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(a)) * cos(Math.toRadians(c)) * sin(dLon / 2) * sin(dLon / 2)
    return 2 * r * asin(sqrt(h.coerceIn(0.0, 1.0)))
}

private val countryCache = HashMap<String, String>()
private val ES = Locale("es", "AR")

fun countryName(cc: String): String {
    if (cc.isEmpty()) return "Sin lugar"
    return synchronized(countryCache) {
        countryCache.getOrPut(cc) {
            Locale("", cc).getDisplayCountry(ES).replaceFirstChar { it.uppercase() }.ifEmpty { cc }
        }
    }
}

val EUROPE = setOf(
    "ES", "FR", "IT", "PT", "DE", "GB", "IE", "NL", "BE", "LU", "CH", "AT", "CZ", "PL", "HU",
    "SK", "SI", "HR", "GR", "DK", "SE", "NO", "FI", "IS", "EE", "LV", "LT", "RO", "BG", "RS",
    "BA", "ME", "MK", "AL", "MT", "MC", "SM", "VA", "AD", "LI", "CY", "UA", "MD",
)
