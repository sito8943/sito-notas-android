package dev.sito8943.sitonotas.ui.foundations

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Escala de espaciado en base 4, portada de `foundations/spacing.ts`.
 * `spacing(4) == 16.dp`, igual que en la app React Native.
 */
fun spacing(step: Int): Dp = (step * BASE_SPACING).dp

private const val BASE_SPACING = 4
