package dev.sito8943.sitonotas.ui.elements

import android.icu.text.RelativeDateTimeFormatter
import android.icu.util.ULocale
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Edad de una nota en la unidad mas grande que alcanza, como `formatNoteDate` de la web. */
data class RelativeAge(val unit: RelativeAgeUnit, val amount: Long)

enum class RelativeAgeUnit { NOW, MINUTE, HOUR, DAY }

private const val MINUTE_MS = 60_000L
private const val HOUR_MS = 60 * MINUTE_MS
private const val DAY_MS = 24 * HOUR_MS

/** Desde 7 dias se muestra la fecha (`RELATIVE_DATE_MAX_DAYS` de la web). */
private const val RELATIVE_MAX_MS = 7 * DAY_MS

/** @return la edad relativa, o nula si ya toca mostrar la fecha. */
fun relativeAge(elapsedMillis: Long): RelativeAge? {
    val elapsed = elapsedMillis.coerceAtLeast(0)
    return when {
        elapsed >= RELATIVE_MAX_MS -> null
        elapsed >= DAY_MS -> RelativeAge(RelativeAgeUnit.DAY, elapsed / DAY_MS)
        elapsed >= HOUR_MS -> RelativeAge(RelativeAgeUnit.HOUR, elapsed / HOUR_MS)
        elapsed >= MINUTE_MS -> RelativeAge(RelativeAgeUnit.MINUTE, elapsed / MINUTE_MS)
        else -> RelativeAge(RelativeAgeUnit.NOW, 0)
    }
}

/**
 * "hace 5 minutos", "ayer"... para menos de una semana y la fecha corta
 * despues, como `formatNoteDate` de la web (`Intl.RelativeTimeFormat` con
 * `numeric: "auto"`). Usa el idioma de la app (`Locale.getDefault()`, que
 * `AppLocaleProvider` alinea con el idioma elegido).
 */
fun formatRelativeNoteDate(
    epochMillis: Long,
    nowMillis: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault(),
): String {
    val locale = Locale.getDefault()
    val age = relativeAge(nowMillis - epochMillis)
        ?: return DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
            .format(Instant.ofEpochMilli(epochMillis).atZone(zone))
    val formatter = RelativeDateTimeFormatter.getInstance(ULocale.forLocale(locale))
    val numeric = { unit: RelativeDateTimeFormatter.RelativeUnit ->
        formatter.format(age.amount.toDouble(), RelativeDateTimeFormatter.Direction.LAST, unit)
    }
    return when (age.unit) {
        RelativeAgeUnit.NOW ->
            formatter.format(RelativeDateTimeFormatter.Direction.PLAIN, RelativeDateTimeFormatter.AbsoluteUnit.NOW)
        RelativeAgeUnit.MINUTE -> numeric(RelativeDateTimeFormatter.RelativeUnit.MINUTES)
        RelativeAgeUnit.HOUR -> numeric(RelativeDateTimeFormatter.RelativeUnit.HOURS)
        RelativeAgeUnit.DAY -> {
            // "ayer" / "anteayer" donde el idioma tiene palabra, como numeric: "auto".
            val direction = when (age.amount) {
                1L -> RelativeDateTimeFormatter.Direction.LAST
                2L -> RelativeDateTimeFormatter.Direction.LAST_2
                else -> null
            }
            direction
                ?.let { formatter.format(it, RelativeDateTimeFormatter.AbsoluteUnit.DAY) }
                ?.takeIf { it.isNotBlank() }
                ?: numeric(RelativeDateTimeFormatter.RelativeUnit.DAYS)
        }
    }
}
