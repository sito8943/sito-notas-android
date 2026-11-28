package dev.sito8943.sitonotas.core.sync

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeParseException

/**
 * Fechas del backend. Dos formas:
 * - la nueva, ISO-8601 con offset (`2026-11-28T10:00:00Z`, `...+01:00`);
 * - la anterior, `LocalDateTime` sin zona, que se lee como UTC igual que la
 *   web (`parseApiDate` añade la `Z`).
 *
 * Los cursores se guardan como el texto que dio el servidor y se le devuelven
 * tal cual: el reloj que manda es el suyo.
 */
internal object ServerTime {

    fun parse(value: String?): Instant? {
        val text = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return try {
            OffsetDateTime.parse(text).toInstant()
        } catch (_: DateTimeParseException) {
            try {
                LocalDateTime.parse(text).toInstant(ZoneOffset.UTC)
            } catch (_: DateTimeParseException) {
                null
            }
        }
    }

    fun toEpochMillis(value: String?): Long? = parse(value)?.toEpochMilli()

    /** El mas reciente de los dos, comparando fechas y no texto. */
    fun max(a: String?, b: String?): String? {
        val pa = parse(a) ?: return b.takeIf { parse(it) != null }
        val pb = parse(b) ?: return a
        return if (pb.isAfter(pa)) b else a
    }
}
