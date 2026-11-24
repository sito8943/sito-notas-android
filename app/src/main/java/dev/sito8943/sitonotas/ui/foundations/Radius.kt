package dev.sito8943.sitonotas.ui.foundations

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Radios de la web: `rounded` (4), `rounded-lg` (8, chips y campos),
 * `rounded-xl` (12, tarjetas), `--sito-ui-radius-dialog` (16) y pastilla
 * (`--sito-ui-radius-pill`, botones y buscador).
 */
object Radius {
    val sm = 4.dp
    val md = 8.dp
    val lg = 12.dp
    val xl = 16.dp
    val full = 999.dp

    val smShape = RoundedCornerShape(sm)
    val mdShape = RoundedCornerShape(md)
    val lgShape = RoundedCornerShape(lg)
    val xlShape = RoundedCornerShape(xl)
    val fullShape = RoundedCornerShape(full)
}
