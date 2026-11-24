package dev.sito8943.sitonotas.core.settings

import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/**
 * Idioma elegido, visible desde cualquier composicion **incluidas las de los
 * dialogos**.
 *
 * Hace falta porque un dialogo de Compose se compone en su propia ventana y ahi
 * `LocalContext` y `LocalConfiguration` se vuelven a proveer desde cero, asi
 * que pierden la configuracion que [AppLocaleProvider] puso. Los locals
 * propios si viajan, y por eso el dialogo puede leer este y volver a aplicar el
 * idioma; lo hace `NotesDialog`.
 */
val LocalAppLanguage = compositionLocalOf<AppLanguage?> { null }

/** Idiomas que ofrece el ajuste. Nulo = el del sistema. */
enum class AppLanguage(val tag: String) {
    SPANISH("es"),
    ENGLISH("en"),
    ;

    val locale: Locale get() = Locale.forLanguageTag(tag)

    companion object {
        fun fromTag(tag: String?): AppLanguage? =
            tag?.let { value -> entries.firstOrNull { it.tag == value } }
    }
}

/**
 * Aplica el idioma elegido a todo el arbol de composicion.
 *
 * Se resuelve con un `Context` de configuracion propia en vez de con
 * `AppCompatDelegate.setApplicationLocales`, que exige la dependencia de
 * AppCompat entera, o con `LocaleManager`, que es de API 33 y aqui el minSdk es
 * 26. Esto funciona en todas las versiones y no añade dependencias
 * (ARCHITECTURE_RULES regla 9).
 *
 * Ademas fija `Locale.setDefault`. Hace falta porque quien formatea dinero y
 * fechas no lee de la composicion: `formatMoney` y `NumberFormat` usan
 * `Locale.getDefault()`. Sin esto la pantalla saldria en español con los
 * numeros en formato ingles, que es exactamente el defecto que este ajuste
 * viene a arreglar. Es una mutacion global del proceso a proposito: es lo mismo
 * que hace por dentro el idioma por app de Android.
 */
@Composable
fun AppLocaleProvider(language: AppLanguage?, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val systemConfiguration = LocalConfiguration.current

    if (language == null) {
        // De vuelta al sistema: se restaura por si una eleccion anterior dejo
        // otro Locale por defecto en este mismo proceso.
        remember(systemConfiguration) { Locale.setDefault(systemConfiguration.locales[0]) }
        CompositionLocalProvider(LocalAppLanguage provides null, content = content)
        return
    }

    val localized = remember(context, language) {
        Locale.setDefault(language.locale)
        val configuration = Configuration(context.resources.configuration).apply {
            setLocales(LocaleList(language.locale))
        }
        LocalizedContext(base = context, localized = context.createConfigurationContext(configuration))
    }

    CompositionLocalProvider(
        LocalContext provides localized,
        LocalConfiguration provides localized.resources.configuration,
        LocalAppLanguage provides language,
        content = content,
    )
}

/**
 * El contexto con el idioma aplicado, pero **sin romper la cadena hasta la
 * Activity**.
 *
 * Hace falta porque `createConfigurationContext` devuelve un contexto suelto,
 * no un envoltorio del original, y media librería de AndroidX encuentra la
 * Activity subiendo por `getBaseContext()`. Al proveer el contexto suelto como
 * `LocalContext`, esa busqueda se queda sin nada:
 *
 *     IllegalStateException: No ActivityResultRegistryOwner was provided
 *         via LocalActivityResultRegistryOwner
 *
 * que es lo que reventaba al abrir Ajustes en cuanto la pantalla pidio un
 * `rememberLauncherForActivityResult` para elegir el fichero. Lo mismo le
 * pasaria a cualquier cosa que busque la Activity: permisos, `LocalActivity`,
 * un `ViewModelStoreOwner`.
 *
 * Asi que la base sigue siendo la Activity y solo se desvian los recursos, que
 * es lo unico que el idioma tiene que cambiar.
 */
internal class LocalizedContext(base: Context, private val localized: Context) :
    ContextWrapper(base) {

    override fun getResources(): Resources = localized.resources
    override fun getAssets(): AssetManager = localized.assets
    override fun getTheme(): Resources.Theme = localized.theme
}
