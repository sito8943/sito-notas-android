package dev.sito8943.sitonotas.core.sync

fun interface SyncStep {
    suspend fun run(userId: Long)
}
