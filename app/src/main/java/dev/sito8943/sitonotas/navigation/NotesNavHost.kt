package dev.sito8943.sitonotas.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dev.sito8943.sitonotas.R
import dev.sito8943.sitonotas.core.auth.SessionState
import dev.sito8943.sitonotas.core.di.AppContainer
import dev.sito8943.sitonotas.feature.auth.AuthViewModel
import dev.sito8943.sitonotas.feature.auth.ui.AuthScreen
import dev.sito8943.sitonotas.feature.categories.CategoriesViewModel
import dev.sito8943.sitonotas.feature.categories.ui.CategoriesScreen
import dev.sito8943.sitonotas.feature.notes.NoteEditorViewModel
import dev.sito8943.sitonotas.feature.notes.NotesViewModel
import dev.sito8943.sitonotas.feature.notes.ui.NoteEditorScreen
import dev.sito8943.sitonotas.feature.notes.ui.NotesScreen
import dev.sito8943.sitonotas.feature.settings.SettingsViewModel
import dev.sito8943.sitonotas.feature.settings.ui.SettingsScreen
import dev.sito8943.sitonotas.feature.trash.TrashViewModel
import dev.sito8943.sitonotas.feature.trash.ui.TrashScreen
import dev.sito8943.sitonotas.ui.theme.NotesTheme
import kotlin.reflect.KClass

private data class Tab(val route: Any, val type: KClass<*>, val icon: ImageVector, val label: Int)

private val tabs = listOf(
    Tab(NotesRoute, NotesRoute::class, Icons.AutoMirrored.Outlined.StickyNote2, R.string.tab_notes),
    Tab(CategoriesRoute, CategoriesRoute::class, Icons.AutoMirrored.Outlined.Label, R.string.tab_categories),
    Tab(TrashRoute, TrashRoute::class, Icons.Outlined.Delete, R.string.tab_trash),
    Tab(SettingsRoute, SettingsRoute::class, Icons.Outlined.Settings, R.string.tab_settings),
)

@Composable
fun NotesNavHost(
    container: AppContainer,
    navController: NavHostController = rememberNavController(),
) {
    // La sesion se restaura sincrona en Application.onCreate: el destino
    // inicial ya se conoce y no hay que componer el login para luego saltar.
    val startDestination: Any = remember {
        val session = container.sessionRepository
        if (session.state.value is SessionState.Authenticated || session.loginAnswered()) {
            NotesRoute
        } else {
            SignInRoute
        }
    }

    val backStack by navController.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val showTabs = tabs.any { tab -> destination?.hierarchy?.any { it.hasRoute(tab.type) } == true }

    Scaffold(
        containerColor = NotesTheme.colors.background,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (showTabs) {
                NavigationBar(containerColor = NotesTheme.colors.surface) {
                    tabs.forEach { tab ->
                        val selected = destination?.hierarchy?.any { it.hasRoute(tab.type) } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { androidx.compose.material3.Text(stringResource(tab.label)) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = NotesTheme.colors.accent,
                                selectedTextColor = NotesTheme.colors.accent,
                                unselectedIconColor = NotesTheme.colors.textMuted,
                                unselectedTextColor = NotesTheme.colors.textMuted,
                                indicatorColor = NotesTheme.colors.background,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .then(if (showTabs) Modifier.windowInsetsPadding(WindowInsets.statusBars) else Modifier),
        ) {
            NavHost(navController = navController, startDestination = startDestination) {
                composable<SignInRoute> {
                    SignInDestination(
                        container = container,
                        onDone = {
                            if (!navController.popBackStack()) {
                                navController.navigate(NotesRoute) {
                                    popUpTo<SignInRoute> { inclusive = true }
                                }
                            }
                        },
                        onContinueWithoutAccount = {
                            container.sessionRepository.continueAsGuest()
                            if (!navController.popBackStack()) {
                                navController.navigate(NotesRoute) {
                                    popUpTo<SignInRoute> { inclusive = true }
                                }
                            }
                        },
                    )
                }
                composable<NotesRoute> {
                    val viewModel: NotesViewModel = viewModel { container.createNotesViewModel() }
                    NotesScreen(
                        viewModel = viewModel,
                        onOpenNote = { navController.navigate(NoteEditorRoute(it)) },
                        onNewNote = { navController.navigate(NoteEditorRoute()) },
                    )
                }
                composable<NoteEditorRoute> { entry ->
                    val route = entry.toRoute<NoteEditorRoute>()
                    val viewModel: NoteEditorViewModel = viewModel {
                        container.createNoteEditorViewModel(route.noteId)
                    }
                    NoteEditorScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                }
                composable<CategoriesRoute> {
                    val viewModel: CategoriesViewModel = viewModel { container.createCategoriesViewModel() }
                    CategoriesScreen(viewModel = viewModel)
                }
                composable<TrashRoute> {
                    val viewModel: TrashViewModel = viewModel { container.createTrashViewModel() }
                    TrashScreen(viewModel = viewModel)
                }
                composable<SettingsRoute> {
                    val viewModel: SettingsViewModel = viewModel { container.createSettingsViewModel() }
                    SettingsScreen(viewModel = viewModel, onSignIn = { navController.navigate(SignInRoute) })
                }
            }
        }
    }
}

@Composable
private fun SignInDestination(
    container: AppContainer,
    onDone: () -> Unit,
    onContinueWithoutAccount: () -> Unit,
) {
    val viewModel: AuthViewModel = viewModel { container.createAuthViewModel() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val session by container.sessionRepository.state.collectAsStateWithLifecycle()

    // Se navega cuando la sesion pasa a autenticada: la unica fuente de verdad
    // de "hay sesion" es el repositorio.
    LaunchedEffect(session) {
        if (session is SessionState.Authenticated) onDone()
    }

    AuthScreen(
        state = state,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onSubmit = viewModel::signIn,
        onForgotPassword = viewModel::forgotPassword,
        onContinueWithoutAccount = onContinueWithoutAccount,
    )
}
