package dev.sito8943.sitonotas.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Paleta semantica de la app, portada de los tokens de la web
 * (`apps/notes/src/styles/global.css`, la misma paleta que Sito Wallet).
 * Los nombres son semanticos, no de Material: `textStrong`, no `onSurface`.
 *
 * Equivalencias con la web:
 * - `background` = `--color-base` (pagina), `surface` = `--color-base-light`
 *   (tarjetas, barra inferior), `surfaceRecessed` = `--color-base-dark`
 *   (bloques de codigo, fondos hundidos).
 * - `primary` = `--color-primary`: relleno de marca (botones, pestaña activa).
 * - `accent` = `--color-text-primary`: la marca usada como texto o icono sobre
 *   la pagina (enlaces, iconos activos, foco). En oscuro el relleno es
 *   demasiado oscuro para leerse sobre el fondo, por eso son dos tokens.
 * - `textInverted` = `--color-on-brand`, texto sobre `primary`.
 */
@Immutable
data class NotesColors(
    val background: Color,
    val surface: Color,
    val surfaceRecessed: Color,
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
    background = Color(0xFFF2F2F2),
    surface = Color(0xFFFFFFFF),
    surfaceRecessed = Color(0xFFE6E6E6),
    border = Color(0xFFCCCCCC),
    textStrong = Color(0xFF0D0D0D),
    textMuted = Color(0xFF4D4D4D),
    // La web no tiene un tercer nivel; este queda entre muted y el borde y
    // sigue pasando AA (5,7:1 sobre blanco) para fechas y metadatos.
    textSubtle = Color(0xFF666666),
    textInverted = Color(0xFFFFFFFF),
    positive = Color(0xFF1D9A5C),
    negative = Color(0xFFC14444),
    warning = Color(0xFFFFA500),
    primary = Color(0xFF041E42),
    accent = Color(0xFF041E42),
    overlay = Color(0x66000000),
)

val darkColors = NotesColors(
    background = Color(0xFF0F141C),
    surface = Color(0xFF1A212C),
    surfaceRecessed = Color(0xFF0B0F16),
    border = Color(0xFF343D4B),
    textStrong = Color(0xFFC9CED8),
    textMuted = Color(0xFF8A92A0),
    textSubtle = Color(0xFF8A92A0),
    textInverted = Color(0xFFF1F5FB),
    positive = Color(0xFF1F8A55),
    negative = Color(0xFFC24A4A),
    warning = Color(0xFFE8A33A),
    primary = Color(0xFF1F4F99),
    accent = Color(0xFF8AB4F8),
    overlay = Color(0x99000000),
)
