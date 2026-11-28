package dev.sito8943.sitonotas.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTextTest {

    @Test
    fun `every word must appear, ignoring case and accents`() {
        assertTrue(SearchText.matches("Canción de cuna", "para dormir", "CANCION dormir"))
        assertFalse(SearchText.matches("Receta", "para dormir", "cancion comer"))
        assertTrue(SearchText.matches("x", "y", "   "))
    }

    @Test
    fun `similarity is the dice coefficient of string-similarity`() {
        assertEquals(0.8, SearchText.similarity("healed", "sealed"), 1e-9)
        assertEquals(0.5, SearchText.similarity("aaaa", "aa"), 1e-9)
        assertEquals(1.0, SearchText.similarity("a b", "ab"), 1e-9)
        assertEquals(0.0, SearchText.similarity("a", "ab"), 1e-9)
    }

    @Test
    fun `a title close to the whole query matches despite typos`() {
        // "cancion" frente a "cancion comer": 0,705 > 0,3, como en la web.
        assertTrue(SearchText.matches("Canción", "para dormir", "cancion comer"))
        assertTrue(SearchText.matches("Lista de la compra", "", "lista de la conpra"))
        assertFalse(SearchText.matches("Lista de la compra", "", "zzz"))
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
