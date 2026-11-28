package dev.sito8943.sitonotas.ui.elements

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Mismos cortes que `formatNoteDate` de la web. */
class RelativeAgeTest {

    private val minute = 60_000L
    private val hour = 60 * minute
    private val day = 24 * hour

    @Test
    fun `seconds are now and the largest reached unit wins`() {
        assertEquals(RelativeAge(RelativeAgeUnit.NOW, 0), relativeAge(59_999))
        assertEquals(RelativeAge(RelativeAgeUnit.MINUTE, 1), relativeAge(minute))
        assertEquals(RelativeAge(RelativeAgeUnit.MINUTE, 59), relativeAge(hour - 1))
        assertEquals(RelativeAge(RelativeAgeUnit.HOUR, 23), relativeAge(day - 1))
        assertEquals(RelativeAge(RelativeAgeUnit.DAY, 6), relativeAge(7 * day - 1))
    }

    @Test
    fun `a week or more shows the date and future times count as now`() {
        assertNull(relativeAge(7 * day))
        assertEquals(RelativeAge(RelativeAgeUnit.NOW, 0), relativeAge(-5 * minute))
    }
}
