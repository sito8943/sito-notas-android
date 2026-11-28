package dev.sito8943.sitonotas.core.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerTimeTest {

    @Test
    fun `reads the old local form as UTC`() {
        assertEquals(1_764_324_000_000L, ServerTime.toEpochMillis("2025-11-28T10:00:00"))
        assertEquals(1_764_324_000_123L, ServerTime.toEpochMillis("2025-11-28T10:00:00.123456"))
    }

    @Test
    fun `reads ISO instants with offset`() {
        assertEquals(1_764_324_000_000L, ServerTime.toEpochMillis("2025-11-28T10:00:00Z"))
        assertEquals(1_764_324_000_000L, ServerTime.toEpochMillis("2025-11-28T11:00:00+01:00"))
        assertEquals(1_764_324_000_123L, ServerTime.toEpochMillis("2025-11-28T10:00:00.123456Z"))
    }

    @Test
    fun `rejects garbage`() {
        assertNull(ServerTime.parse("ayer"))
        assertNull(ServerTime.parse(""))
        assertNull(ServerTime.parse(null))
    }

    @Test
    fun `max compares instants across forms and keeps the server text`() {
        assertEquals("2025-11-28T11:30:00+01:00", ServerTime.max("2025-11-28T10:00:00", "2025-11-28T11:30:00+01:00"))
        assertEquals("2025-11-28T10:00:00", ServerTime.max("2025-11-28T10:00:00", "2025-11-28T10:30:00+01:00"))
        assertEquals("2025-11-28T10:00:00Z", ServerTime.max(null, "2025-11-28T10:00:00Z"))
    }
}
