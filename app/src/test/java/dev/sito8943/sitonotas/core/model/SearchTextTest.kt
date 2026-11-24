package dev.sito8943.sitonotas.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTextTest {

    @Test
    fun `every word must appear, ignoring case and accents`() {
        assertTrue(SearchText.matches("Canción de cuna", "para dormir", "CANCION dormir"))
        assertFalse(SearchText.matches("Canción", "para dormir", "cancion comer"))
        assertTrue(SearchText.matches("x", "y", "   "))
    }

    @Test
    fun `markdown links count by their text, not the url`() {
        assertTrue(SearchText.matches("", "mira [la receta](https://ejemplo.com/pan)", "receta"))
        assertFalse(SearchText.matches("", "mira [la receta](https://ejemplo.com/pan)", "ejemplo"))
    }

    @Test
    fun `category names compare like the server`() {
        assertEquals(CategoryNames.key("  Trabájo   duro "), CategoryNames.key("trabajo DURO"))
        assertEquals(CategoryNames.MAX_LENGTH, CategoryNames.normalize("x".repeat(60)).length)
    }

    @Test
    fun `new categories get the least used color`() {
        assertEquals("#3B82F6", CategoryNames.pickColor(emptyList()))
        assertEquals("#22C55E", CategoryNames.pickColor(listOf("#3b82f6")))
    }
}
