package dev.sito8943.sitonotas.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.annotation.DrawableRes
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
import dev.sito8943.sitonotas.feature.sharing.PublicNoteRedirect
import dev.sito8943.sitonotas.feature.sharing.PublicNoteViewModel
import dev.sito8943.sitonotas.feature.sharing.ShareNoteViewModel
import dev.sito8943.sitonotas.feature.sharing.SharedNoteViewModel
import dev.sito8943.sitonotas.feature.sharing.SharedNotesViewModel
import dev.sito8943.sitonotas.feature.sharing.ui.PublicNoteScreen
import dev.sito8943.sitonotas.feature.sharing.ui.ShareNoteScreen
import dev.sito8943.sitonotas.feature.sharing.ui.SharedNoteScreen
import dev.sito8943.sitonotas.feature.sharing.ui.SharedNotesScreen
import dev.sito8943.sitonotas.feature.trash.TrashViewModel
import dev.sito8943.sitonotas.feature.trash.ui.TrashScreen
import dev.sito8943.sitonotas.ui.elements.FaIcon
import dev.sito8943.sitonotas.ui.theme.NotesTheme
import kotlin.reflect.KClass

private data class Tab(val route: Any, val type: KClass<*>, @param:DrawableRes val icon: Int, val label: Int)

// Mismos iconos que `AppBottomNavigation` / `AppSidebar` de la web.
private val tabs = listOf(
    Tab(NotesRoute, NotesRoute::class, R.drawable.fa_note_sticky, R.string.tab_notes),
    Tab(SharedRoute, SharedRoute::class, R.drawable.fa_user_group, R.string.tab_shared),
    Tab(CategoriesRoute, CategoriesRoute::class, R.drawable.fa_tag, R.string.tab_categories),
    Tab(TrashRoute, TrashRoute::class, R.drawable.fa_trash, R.string.tab_trash),
    Tab(SettingsRoute, SettingsRoute::class, R.drawable.fa_gear, R.string.tab_settings),
)

@Composable
fun NotesNavHost(
    container: AppContainer,
    navController: NavHostController = rememberNavController(),
    /** Token of a public note link opened with the app (deep link). */
    publicNoteToken: String? = null,
    onPublicNoteOpened: () -> Unit = {},
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
    val unseenShared by container.unseenShares.count.collectAsStateWithLifecycle()

    // Badge of "Compartidas": refreshed on every screen change (best effort, online).
    LaunchedEffect(destination?.route) { container.unseenShares.refresh() }

    LaunchedEffect(publicNoteToken) {
        if (publicNoteToken != null) {
            navController.navigate(PublicNoteRoute(publicNoteToken))
            onPublicNoteOpened()
        }
    }

    Scaffold(
        containerColor = NotesTheme.colors.background,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (showTabs) {
                // Barra de la web (`bottom-nav`: superficie elevada, icono activo
                // en la marca) con las etiquetas e indicador de Android.
                NavigationBar(containerColor = NotesTheme.colors.surface, tonalElevation = 0.dp) {
                    tabs.forEach { tab ->
                        val selected = destination?.hierarchy?.any { it.hasRoute(tab.type) } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navController.navigateToTab(tab.route) },
                            icon = {
                                BadgedBox(
                                    badge = {
                                        if (tab.route == SharedRoute && unseenShared > 0) {
                                            Badge(containerColor = NotesTheme.colors.accent) {
                                                androidx.compose.material3.Text(unseenShared.toString())
                                            }
                                        }
                                    },
                                ) {
                                    FaIcon(
                                        tab.icon,
                                        contentDescription = null,
                                        tint = if (selected) NotesTheme.colors.accent else NotesTheme.colors.textMuted,
                                        size = 20.dp,
                                    )
                                }
                            },
                            label = {
                                androidx.compose.material3.Text(
                                    stringResource(tab.label),
                                    style = NotesTheme.typography.caption,
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = NotesTheme.colors.accent,
                                selectedTextColor = NotesTheme.colors.accent,
                                unselectedIconColor = NotesTheme.colors.textMuted,
                                unselectedTextColor = NotesTheme.colors.textMuted,
                                indicatorColor = NotesTheme.colors.accent.copy(alpha = 0.12f),
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
                        onManageCategories = { navController.navigateToTab(CategoriesRoute) },
                    )
                }
                composable<NoteEditorRoute> { entry ->
                    val route = entry.toRoute<NoteEditorRoute>()
                    val viewModel: NoteEditorViewModel = viewModel {
                        container.createNoteEditorViewModel(route.noteId)
                    }
                    NoteEditorScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                        imageLoader = container.imageLoader,
                        onShare = { navController.navigate(ShareNoteRoute(it)) },
                    )
                }
                composable<ShareNoteRoute> { entry ->
                    val route = entry.toRoute<ShareNoteRoute>()
                    val viewModel: ShareNoteViewModel = viewModel { container.createShareNoteViewModel(route.noteId) }
                    ShareNoteScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                }
                composable<SharedRoute> {
                    val viewModel: SharedNotesViewModel = viewModel { container.createSharedNotesViewModel() }
                    SharedNotesScreen(
                        viewModel = viewModel,
                        onOpenNote = { navController.navigate(SharedNoteRoute(it)) },
                        onSignIn = { navController.navigate(SignInRoute) },
                    )
                }
                composable<SharedNoteRoute> { entry ->
                    val route = entry.toRoute<SharedNoteRoute>()
                    val viewModel: SharedNoteViewModel = viewModel { container.createSharedNoteViewModel(route.remoteId) }
                    SharedNoteScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                        onOpenLocal = { localId ->
                            navController.navigate(NoteEditorRoute(localId)) {
                                popUpTo<SharedNoteRoute> { inclusive = true }
                            }
                        },
                    )
                }
                composable<PublicNoteRoute> { entry ->
                    val route = entry.toRoute<PublicNoteRoute>()
                    val viewModel: PublicNoteViewModel = viewModel { container.createPublicNoteViewModel(route.token) }
                    PublicNoteScreen(
                        viewModel = viewModel,
                        onBack = {
                            if (!navController.popBackStack()) navController.navigate(NotesRoute)
                        },
                        onRedirect = { redirect ->
                            val target: Any = when (redirect) {
                                is PublicNoteRedirect.Local -> NoteEditorRoute(redirect.localId)
                                is PublicNoteRedirect.Shared -> SharedNoteRoute(redirect.remoteId)
                            }
                            navController.navigate(target) {
                                popUpTo<PublicNoteRoute> { inclusive = true }
                            }
                        },
                    )
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

/** Navegacion entre pestañas: una sola copia de cada una, con su estado guardado. */
private fun NavHostController.navigateToTab(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
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
        onSubmit = viewModel::submit,
        onForgotPassword = viewModel::forgotPassword,
        onContinueWithoutAccount = onContinueWithoutAccount,
        onConfirmPasswordChange = viewModel::onConfirmPasswordChange,
        onToggleMode = viewModel::toggleMode,
    )
}
