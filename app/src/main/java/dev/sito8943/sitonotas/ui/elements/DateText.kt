package dev.sito8943.sitonotas.ui.elements

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Fecha corta de una nota: la hora si es de hoy, la fecha si no. Usa
 * `Locale.getDefault()`, que `AppLocaleProvider` alinea con el idioma elegido.
 */
fun formatNoteDate(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val dateTime = Instant.ofEpochMilli(epochMillis).atZone(zone)
    val locale = Locale.getDefault()
    return if (dateTime.toLocalDate() == LocalDate.now(zone)) {
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(dateTime)
    } else {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(dateTime)
    }
}

/** Fecha y hora, para "ultima sincronizacion". */
fun formatDateTime(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(Locale.getDefault())
        .format(Instant.ofEpochMilli(epochMillis).atZone(zone))
