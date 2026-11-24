package dev.sito8943.sitonotas.core.data

import java.util.concurrent.atomic.AtomicLong

/**
 * Version monotona para `local_version`. Parte del reloj para que sobreviva a
 * reiniciar el proceso (las versiones ya guardadas son menores) y nunca repite
 * aunque dos ediciones caigan en el mismo milisegundo.
 */
class VersionClock(private val now: () -> Long = System::currentTimeMillis) {
    private val last = AtomicLong(0)

    fun next(): Long = last.updateAndGet { previous -> maxOf(previous + 1, now()) }
}
