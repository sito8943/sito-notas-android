package dev.sito8943.sitonotas.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

/**
 * Portado de `foundations/typography.ts`. Los tamanos vienen de la misma escala
 * de 4 (`spacing(8) == 32`), expresados en sp porque son texto y deben escalar
 * con la preferencia de tamano de fuente del sistema.
 *
 * La familia es la del sistema a proposito: en la app RN solo el wordmark de
 * marca usa Poppins, el resto usa la fuente nativa de la plataforma.
 */
@Immutable
data class NotesTypography(
    val display: TextStyle = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold),
    val title: TextStyle = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
    val body: TextStyle = TextStyle(fontSize = 16.sp),
    val bodyStrong: TextStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold),
    val caption: TextStyle = TextStyle(fontSize = 12.sp),
    // `textTransform: uppercase` en RN; en Compose se aplica al texto, no al
    // estilo, asi que lo resuelve el elemento Typography al renderizar.
    val label: TextStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold),
    val subtle: TextStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold),
    val fontFamily: FontFamily = FontFamily.Default,
    val defaultAlign: TextAlign = TextAlign.Start,
)
