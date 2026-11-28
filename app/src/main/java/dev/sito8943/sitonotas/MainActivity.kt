package dev.sito8943.sitonotas

import android.graphics.Color as AndroidColor
import android.content.Intent
import android.os.Bundle
import dev.sito8943.sitonotas.feature.sharing.ShareLinks
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.sito8943.sitonotas.core.settings.AppLanguage
import dev.sito8943.sitonotas.core.settings.AppLocaleProvider
import dev.sito8943.sitonotas.feature.sync.AccountSwitchDialog
import dev.sito8943.sitonotas.navigation.NotesNavHost
import dev.sito8943.sitonotas.ui.theme.NotesTheme
import dev.sito8943.sitonotas.ui.theme.ResolvedTheme
import dev.sito8943.sitonotas.ui.theme.ThemePreference

/** Unica Activity: raiz de composicion, tema, idioma y navegacion. */
class MainActivity : ComponentActivity() {

    private val container get() = (application as SitoNotasApplication).container

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val languageTag by container.preferences.languageTag
                .collectAsStateWithLifecycle(initialValue = null)
            val themeName by container.preferences.themeName
                .collectAsStateWithLifecycle(initialValue = null)

            AppLocaleProvider(language = AppLanguage.fromTag(languageTag)) {
                NotesTheme(preference = ThemePreference.fromName(themeName)) {
                    SyncSystemBarIcons(NotesTheme.resolved)
                    val publicToken by openedPublicToken.collectAsStateWithLifecycle()
                    NotesNavHost(
                        container = container,
                        publicNoteToken = publicToken,
                        onPublicNoteOpened = { openedPublicToken.value = null },
                    )
                    AccountSwitchDialog(container.syncCoordinator)
                }
            }
        }
        if (savedInstanceState == null) readPublicLink(intent)
    }

    /** Token de un enlace publico abierto con la app, hasta que la navegacion lo consume. */
    private val openedPublicToken = MutableStateFlow<String?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readPublicLink(intent)
    }

    private fun readPublicLink(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val url = intent.dataString ?: return
        ShareLinks.tokenOf(url, BuildConfig.SHARE_BASE_URL)?.let { openedPublicToken.value = it }
    }

    /** Volver a primer plano es un disparador de sincronizacion. */
    override fun onStart() {
        super.onStart()
        container.syncCoordinator.requestSync()
        container.realtimeClient.setForeground(true)
    }

    /** En segundo plano no hay socket: se reconecta al volver. */
    override fun onStop() {
        container.realtimeClient.setForeground(false)
        super.onStop()
    }

    /** Iconos de las barras del sistema segun el tema de la app, no el del telefono. */
    @Composable
    private fun SyncSystemBarIcons(resolved: ResolvedTheme) {
        LaunchedEffect(resolved) {
            val style = SystemBarStyle.auto(
                lightScrim = AndroidColor.TRANSPARENT,
                darkScrim = AndroidColor.TRANSPARENT,
            ) { resolved == ResolvedTheme.DARK }
            enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        }
    }
}
