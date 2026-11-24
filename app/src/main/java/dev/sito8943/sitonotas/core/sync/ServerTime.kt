package dev.sito8943.sitonotas.core.sync

import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeParseException

/**
 * Fechas del backend: `LocalDateTime` sin zona. Se leen como UTC, igual que
 * la web (`parseApiDate` añade la `Z`).
 */
internal object ServerTime {

    fun parse(value: String?): LocalDateTime? = value?.takeIf { it.isNotBlank() }?.let {
        try {
            LocalDateTime.parse(it.removeSuffix("Z"))
        } catch (_: DateTimeParseException) {
            null
        }
    }

    fun toEpochMillis(value: String?): Long? =
        parse(value)?.toInstant(ZoneOffset.UTC)?.toEpochMilli()

    /** El mas reciente de los dos, comparando fechas y no texto. */
    fun max(a: String?, b: String?): String? {
        val pa = parse(a) ?: return b.takeIf { parse(it) != null }
        val pb = parse(b) ?: return a
        return if (pb.isAfter(pa)) b else a
    }
}
