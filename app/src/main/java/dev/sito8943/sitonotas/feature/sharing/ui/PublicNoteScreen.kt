package dev.sito8943.sitonotas.feature.sharing.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.sito8943.sitonotas.R
import dev.sito8943.sitonotas.feature.sharing.PublicNoteRedirect
import dev.sito8943.sitonotas.feature.sharing.PublicNoteViewModel
import dev.sito8943.sitonotas.ui.elements.NotesText
import dev.sito8943.sitonotas.ui.elements.formatNoteDate
import dev.sito8943.sitonotas.ui.foundations.spacing
import dev.sito8943.sitonotas.ui.theme.NotesTheme

/** A note opened from its public link (web `PublicNote`): read only, no account needed. */
@Composable
fun PublicNoteScreen(
    viewModel: PublicNoteViewModel,
    onBack: () -> Unit,
    onRedirect: (PublicNoteRedirect) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = NotesTheme.colors

    LaunchedEffect(state.redirect) {
        state.redirect?.let {
            viewModel.consumeRedirect()
            onRedirect(it)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .safeDrawingPadding(),
    ) {
        SharingHeader(title = stringResource(R.string.app_name), onBack = onBack)
        when {
            state.loading -> Box(Modifier.weight(1f))
            state.notFound -> SharingErrorState(
                error = state.error!!,
                onRetry = null,
                text = stringResource(R.string.public_note_not_found),
            )
            state.error != null -> SharingErrorState(state.error!!, onRetry = viewModel::load)
            else -> Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = spacing(4))
                    .padding(top = spacing(6), bottom = spacing(10)),
                verticalArrangement = Arrangement.spacedBy(spacing(6)),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(spacing(2))) {
                    NotesText(
                        state.title.ifBlank { stringResource(R.string.note_untitled) },
                        style = NotesTheme.typography.display.copy(fontSize = 30.sp, lineHeight = 38.sp, color = colors.textStrong),
                    )
                    val by = stringResource(R.string.public_note_by, state.ownerName)
                    val meta = state.updatedAt?.let { "$by · ${stringResource(R.string.public_note_updated, formatNoteDate(it))}" } ?: by
                    NotesText(meta, style = NotesTheme.typography.small, color = colors.textMuted)
                }
                ReadOnlyMarkdown(state.content)
            }
        }
    }
}
