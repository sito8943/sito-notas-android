package dev.sito8943.sitonotas.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import dev.sito8943.sitonotas.R

/**
 * Tipografia de la web (`global.css`): todo el texto en Roboto 500 y los
 * titulos (`h1`-`h3`) en Poppins 700. Roboto es la fuente del sistema en
 * Android; Poppins Bold va empaquetada en `res/font` (SIL OFL 1.1).
 *
 * Tamaños en sp para que escalen con el tamaño de letra del sistema.
 */
val Poppins = FontFamily(Font(R.font.poppins_bold, FontWeight.Bold))

@Immutable
data class NotesTypography(
    /** Titulo de pagina (`text-3xl md:text-2xl` en movil). */
    val display: TextStyle = TextStyle(
        fontFamily = Poppins,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.Bold,
    ),
    /** Titulo de seccion y de dialogo (`text-xl`). */
    val title: TextStyle = TextStyle(
        fontFamily = Poppins,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Bold,
    ),
    /** Titulo de tarjeta de nota (`h3` a `1rem`). */
    val cardTitle: TextStyle = TextStyle(
        fontFamily = Poppins,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Bold,
    ),
    val body: TextStyle = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
    val bodyStrong: TextStyle = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    /** `text-sm`: chips de filtro, descripciones, migas. */
    val small: TextStyle = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    val caption: TextStyle = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    val label: TextStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold),
    val subtle: TextStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold),
    val button: TextStyle = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    val fontFamily: FontFamily = FontFamily.Default,
    val defaultAlign: TextAlign = TextAlign.Start,
)
