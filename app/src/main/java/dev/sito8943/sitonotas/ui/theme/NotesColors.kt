package dev.sito8943.sitonotas.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Paleta semantica de la app, portada de `foundations/colors.ts` y
 * `theme/constants.ts` (THEME_COLOR). Los nombres son intencionadamente
 * semanticos, no de Material: `textStrong`, no `onSurface`.
 *
 * Todo gira alrededor del azul de marca `#041E42`, que es el
 * `--color-primary` de `apps/notes/src/styles/global.css` (mismo que el wallet). De ese tono
 * (H 215) salen tambien `accent` y los azules del tema oscuro, subiendo la
 * luminosidad hasta que contrastan sobre su fondo.
 */
@Immutable
data class NotesColors(
    val background: Color,
    val surface: Color,
    val border: Color,
    val textStrong: Color,
    val textMuted: Color,
    val textSubtle: Color,
    val textInverted: Color,
    val positive: Color,
    val negative: Color,
    val warning: Color,
    val primary: Color,
    val accent: Color,
    val overlay: Color,
)

val lightColors = NotesColors(
    background = Color(0xFFF5F8FC),
    surface = Color(0xFFFFFFFF),
    border = Color(0xFFD6E0EB),
    textStrong = Color(0xFF041E42),
    textMuted = Color(0xFF4F627D),
    textSubtle = Color(0xFF74839A),
    textInverted = Color(0xFFFFFFFF),
    positive = Color(0xFF2E7D32),
    negative = Color(0xFFC62828),
    warning = Color(0xFFE65100),
    primary = Color(0xFF041E42),
    // El azul de marca a tono 40. `primary` es casi negro y no sirve para
    // marcar lo seleccionado: una pestaña activa en #041E42 no se distingue
    // del texto. Este contrasta 7,2:1 sobre blanco y sigue siendo el mismo
    // tono, H 215.
    accent = Color(0xFF0F53B3),
    // rgba(4, 30, 66, 0.4)
    overlay = Color(0x66041E42),
)

val darkColors = NotesColors(
    background = Color(0xFF020B16),
    surface = Color(0xFF061A33),
    border = Color(0xFF163B63),
    textStrong = Color(0xFFF5F8FC),
    textMuted = Color(0xFFC1D0E0),
    textSubtle = Color(0xFF90A5BF),
    textInverted = Color(0xFFFFFFFF),
    positive = Color(0xFF4CAF50),
    negative = Color(0xFFEF5350),
    warning = Color(0xFFFF6B35),
    primary = Color(0xFF3B82F6),
    // El mismo tono subido a 65 de luminosidad: 6,0:1 sobre `surface`.
    accent = Color(0xFF5598F6),
    // rgba(0, 0, 0, 0.6)
    overlay = Color(0x99000000),
)
