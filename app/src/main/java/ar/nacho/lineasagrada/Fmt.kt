package ar.nacho.lineasagrada

import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

object Fmt {
    private val M = arrayOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")
    private val MF = arrayOf(
        "enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto",
        "septiembre", "octubre", "noviembre", "diciembre",
    )
    private val D = arrayOf("lun", "mar", "mié", "jue", "vie", "sáb", "dom")
    private val AR = Locale("es", "AR")

    fun z(ms: Long): ZonedDateTime = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
    fun monthYear(ms: Long): String = z(ms).let { "${M[it.monthValue - 1]} ${it.year}" }
    fun monthFull(ms: Long): String = z(ms).let { "${MF[it.monthValue - 1]} ${it.year}" }
    fun dayMonthYear(ms: Long): String = z(ms).let { "${it.dayOfMonth} ${M[it.monthValue - 1]} ${it.year}" }
    fun dayFull(ms: Long): String =
        z(ms).let { "${D[it.dayOfWeek.value - 1]} ${it.dayOfMonth} ${M[it.monthValue - 1]} ${it.year}" }
    fun dayShort(ms: Long): String =
        z(ms).let { "${D[it.dayOfWeek.value - 1]} ${it.dayOfMonth} ${M[it.monthValue - 1]}" }
    fun time(ms: Long): String = z(ms).let { String.format(AR, "%02d:%02d", it.hour, it.minute) }

    fun range(a: Long, b: Long): String {
        val x = z(a)
        val y = z(b)
        return when {
            x.toLocalDate() == y.toLocalDate() -> dayMonthYear(a)
            x.year == y.year && x.monthValue == y.monthValue -> "${x.dayOfMonth}–${y.dayOfMonth} ${M[y.monthValue - 1]} ${y.year}"
            x.year == y.year -> "${x.dayOfMonth} ${M[x.monthValue - 1]} – ${y.dayOfMonth} ${M[y.monthValue - 1]} ${y.year}"
            else -> "${dayMonthYear(a)} – ${dayMonthYear(b)}"
        }
    }

    fun days(a: Long, b: Long): Long =
        java.time.temporal.ChronoUnit.DAYS.between(z(a).toLocalDate(), z(b).toLocalDate()) + 1

    fun dayStart(ms: Long): Long = z(ms).toLocalDate().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    fun n(i: Number): String = NumberFormat.getIntegerInstance(AR).format(i)

    fun size(bytes: Long): String =
        if (bytes >= 1_000_000_000L) String.format(AR, "%.1f GB", bytes / 1e9)
        else String.format(AR, "%.0f MB", bytes / 1e6)

    fun duration(ms: Long): String {
        val s = (ms / 1000).toInt()
        return String.format(AR, "%d:%02d", s / 60, s % 60)
    }
}
