package app.karta.likvidnosti.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.karta.likvidnosti.data.Session
import app.karta.likvidnosti.data.TokenStore
import app.karta.likvidnosti.ui.components.BottomNav
import app.karta.likvidnosti.ui.nav.NavActions
import app.karta.likvidnosti.ui.nav.Routes
import app.karta.likvidnosti.ui.screens.AccountChangeScreen
import app.karta.likvidnosti.ui.screens.AccountDataScreen
import app.karta.likvidnosti.ui.screens.AccountSuccessScreen
import app.karta.likvidnosti.ui.screens.AdminsScreen
import app.karta.likvidnosti.ui.screens.AnalyticsScreen
import app.karta.likvidnosti.ui.screens.ChatEditScreen
import app.karta.likvidnosti.ui.screens.ChatInfoScreen
import app.karta.likvidnosti.ui.screens.ChatScreen
import app.karta.likvidnosti.ui.screens.CommunityScreen
import app.karta.likvidnosti.ui.screens.HomeScreen
import app.karta.likvidnosti.ui.screens.LoginScreen
import app.karta.likvidnosti.ui.screens.NotificationsScreen
import app.karta.likvidnosti.ui.screens.PasswordRecoveryScreen
import app.karta.likvidnosti.ui.screens.ProfileEditScreen
import app.karta.likvidnosti.ui.screens.ProfileScreen
import app.karta.likvidnosti.ui.screens.RegisterConfirmScreen
import app.karta.likvidnosti.ui.screens.ReportDetailScreen
import app.karta.likvidnosti.ui.screens.ReportFormScreen
import app.karta.likvidnosti.ui.screens.SubscriptionScreen
import app.karta.likvidnosti.ui.screens.UserProfileScreen
import app.karta.likvidnosti.ui.theme.T
import kotlinx.coroutines.launch

@Composable
fun AppRoot() {
    var ready by remember { mutableStateOf(false) }
    var authed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (TokenStore.isAuthenticated) {
            runCatching { Session.load() }
            authed = Session.user != null
        }
        ready = true
    }

    if (!ready) {
        Box(modifier = Modifier.fillMaxSize().background(T.Bg), contentAlignment = Alignment.Center) {
            Text("…", color = T.TextMuted)
        }
        return
    }

    val nav = rememberNavController()
    val actions = remember(nav) { NavActions(nav) }
    val start = if (authed) Routes.HOME else Routes.LOGIN

    val backStack by nav.currentBackStackEntryAsState()
    val currentBase = Routes.base(backStack?.destination?.route)
    val isAdmin = Session.isAdmin
    val showNav = currentBase in Routes.WITH_NAV

    Box(modifier = Modifier.fillMaxSize().background(T.Bg)) {
        NavHost(navController = nav, startDestination = start) {
            // --- Auth ---
            composable(Routes.LOGIN) {
                LoginScreen(
                    onAuthed = {
                        scope.launch {
                            runCatching { Session.load(force = true) }
                            nav.navigate(Routes.HOME) { popUpTo(Routes.LOGIN) { inclusive = true } }
                        }
                    },
                    onRecovery = { actions.toPasswordRecovery() },
                    onNeedConfirm = { email -> actions.toRegisterConfirm(email) },
                )
            }
            composable(
                Routes.REGISTER_CONFIRM,
                arguments = listOf(navArgument("email") { type = NavType.StringType }),
            ) { entry ->
                val email = entry.arguments?.getString("email").orEmpty()
                RegisterConfirmScreen(
                    email = email,
                    onConfirmed = { nav.navigate(Routes.HOME) { popUpTo(Routes.LOGIN) { inclusive = true } } },
                    onBack = { actions.back() },
                )
            }
            composable(Routes.PASSWORD_RECOVERY) {
                PasswordRecoveryScreen(onDone = { actions.back() })
            }

            // --- Tabs ---
            composable(Routes.HOME) { HomeScreen(onOpenReport = { actions.toReportDetail(it) }) }
            composable(Routes.ANALYTICS) { AnalyticsScreen(onOpenReport = { actions.toReportDetail(it) }) }
            composable(Routes.COMMUNITY) { CommunityScreen(onOpenChat = { actions.toChat(it) }) }
            composable(Routes.PROFILE) {
                ProfileScreen(
                    onSettings = { actions.toProfileEdit() },
                    onSubscription = { actions.toSubscription() },
                    onNotifications = { actions.toNotifications() },
                    onAccountData = { actions.toAccountData() },
                    onAdmins = { actions.toAdmins() },
                    onOpenReport = { actions.toReportDetail(it) },
                )
            }

            // --- Reports ---
            composable(
                Routes.REPORT_DETAIL,
                arguments = listOf(navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null }),
            ) { entry ->
                val id = entry.arguments?.getString("id")?.toLongOrNull()
                ReportDetailScreen(
                    id = id,
                    onBack = { actions.back() },
                    onEdit = { actions.toReportEdit(it) },
                    onDiscuss = { slug -> if (slug != null) actions.toChat(slug) },
                )
            }
            composable(Routes.REPORT_CREATE) {
                AdminGuard(isAdmin, actions) {
                    ReportFormScreen(
                        reportId = null,
                        onBack = { actions.back() },
                        onSaved = { id -> nav.navigate("report-detail?id=$id") { popUpTo(Routes.REPORT_CREATE) { inclusive = true } } },
                        onDeleted = { actions.toAnalytics() },
                    )
                }
            }
            composable(
                Routes.REPORT_EDIT,
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { entry ->
                val id = entry.arguments?.getLong("id") ?: 0L
                AdminGuard(isAdmin, actions) {
                    ReportFormScreen(
                        reportId = id,
                        onBack = { actions.back() },
                        onSaved = { savedId -> nav.navigate("report-detail?id=$savedId") { popUpTo(Routes.REPORT_EDIT) { inclusive = true } } },
                        onDeleted = { nav.navigate(Routes.ANALYTICS) { popUpTo(Routes.ANALYTICS) { inclusive = true } } },
                    )
                }
            }

            // --- Community ---
            composable(
                Routes.CHAT,
                arguments = listOf(navArgument("slug") { type = NavType.StringType }),
            ) { entry ->
                val slug = entry.arguments?.getString("slug").orEmpty()
                ChatScreen(
                    slug = slug,
                    onBack = { actions.back() },
                    onInfo = { actions.toChatInfo(slug) },
                    onUser = { actions.toChatUser(it) },
                )
            }
            composable(
                Routes.CHAT_INFO,
                arguments = listOf(navArgument("slug") { type = NavType.StringType }),
            ) { entry ->
                val slug = entry.arguments?.getString("slug").orEmpty()
                ChatInfoScreen(slug = slug, onBack = { actions.back() }, onEdit = { actions.toChatEdit(slug) })
            }
            composable(
                Routes.CHAT_EDIT,
                arguments = listOf(navArgument("slug") { type = NavType.StringType }),
            ) { entry ->
                val slug = entry.arguments?.getString("slug").orEmpty()
                AdminGuard(isAdmin, actions) {
                    ChatEditScreen(slug = slug, onBack = { actions.back() })
                }
            }
            composable(
                Routes.CHAT_USER,
                arguments = listOf(navArgument("userId") { type = NavType.LongType }),
            ) {
                UserProfileScreen(onBack = { actions.back() })
            }

            // --- Profile section ---
            composable(Routes.PROFILE_EDIT) { ProfileEditScreen(onDone = { actions.back() }) }
            composable(Routes.NOTIFICATIONS) { NotificationsScreen(onBack = { actions.back() }) }
            composable(Routes.ACCOUNT_DATA) {
                AccountDataScreen(
                    onBack = { actions.back() },
                    onChange = { actions.toAccountChange(it) },
                    onLogout = {
                        Session.clear()
                        nav.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
                    },
                )
            }
            composable(
                Routes.ACCOUNT_CHANGE,
                arguments = listOf(navArgument("field") { type = NavType.StringType }),
            ) { entry ->
                val field = entry.arguments?.getString("field").orEmpty()
                AccountChangeScreen(field = field, onBack = { actions.back() }, onSuccess = { actions.toAccountSuccess(it) })
            }
            composable(
                Routes.ACCOUNT_SUCCESS,
                arguments = listOf(navArgument("field") { type = NavType.StringType }),
            ) { entry ->
                val field = entry.arguments?.getString("field").orEmpty()
                AccountSuccessScreen(field = field, onBack = { actions.back() })
            }
            composable(Routes.ADMINS) {
                AdminGuard(isAdmin, actions) {
                    AdminsScreen(
                        onBack = { actions.back() },
                        onLeftAdmin = { nav.navigate(Routes.PROFILE) { popUpTo(Routes.PROFILE) { inclusive = true } } },
                    )
                }
            }
            composable(Routes.SUBSCRIPTION) { SubscriptionScreen(onBack = { actions.back() }) }
        }

        // Плавающая нижняя навигация поверх корневых вкладок.
        AnimatedVisibility(
            visible = showNav,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            BottomNav(
                current = currentBase,
                onTab = { route ->
                    when (route) {
                        Routes.HOME -> actions.toHome()
                        Routes.ANALYTICS -> actions.toAnalytics()
                        Routes.COMMUNITY -> actions.toCommunity()
                        Routes.PROFILE -> actions.toProfile()
                    }
                },
                showCreate = isAdmin && currentBase == Routes.ANALYTICS,
                onCreate = { actions.toReportCreate() },
                modifier = Modifier.padding(bottom = bottomInset + 16.dp),
            )
        }
    }
}

/** Гард админских маршрутов: не-админа возвращаем на главную. */
@Composable
private fun AdminGuard(isAdmin: Boolean, actions: NavActions, content: @Composable () -> Unit) {
    if (isAdmin) {
        content()
    } else {
        LaunchedEffect(Unit) { actions.toHome() }
    }
}
