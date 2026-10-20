package app.karta.likvidnosti.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.karta.likvidnosti.data.AppData
import app.karta.likvidnosti.data.Session
import app.karta.likvidnosti.data.TokenStore
import app.karta.likvidnosti.ui.components.AppTab
import app.karta.likvidnosti.ui.components.GlassNavBar
import app.karta.likvidnosti.ui.components.NavBarExtra
import app.karta.likvidnosti.ui.icons.AppIcons
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
import app.karta.likvidnosti.ui.screens.RegisterProfileScreen
import app.karta.likvidnosti.ui.screens.ReportDetailScreen
import app.karta.likvidnosti.ui.screens.ReportFormScreen
import app.karta.likvidnosti.ui.screens.SubscriptionScreen
import app.karta.likvidnosti.ui.screens.UserAgreementScreen
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
            if (authed) scope.launch { AppData.preload() }
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
    val exitToLogin = {
        Session.clear()
        nav.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
    }
    val actions = remember(nav) { NavActions(nav) }
    val start = if (authed) Routes.HOME else Routes.LOGIN

    val backStack by nav.currentBackStackEntryAsState()
    val currentBase = Routes.base(backStack?.destination?.route)
    val isAdmin = Session.isAdmin
    val restricted = Session.restricted

    // Без подписки главная и аналитика недоступны — открываем «Сообщество»
    // (iOS MainFlow.enforceRestriction). После оплаты возвращаем на главную.
    var wasRestricted by remember { mutableStateOf(restricted) }
    LaunchedEffect(restricted, currentBase) {
        if (restricted && (currentBase == Routes.HOME || currentBase == Routes.ANALYTICS)) {
            nav.navigate(Routes.COMMUNITY) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        } else if (wasRestricted && !restricted && currentBase in Routes.ROOT) {
            nav.navigate(Routes.HOME) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
        wasRestricted = restricted
    }

    fun selectTab(tab: AppTab) {
        when (tab) {
            AppTab.Home -> actions.toHome()
            AppTab.Analytics -> actions.toAnalytics()
            AppTab.Community -> actions.toCommunity()
            AppTab.Profile -> actions.toProfile()
        }
    }

    /** Навбар корневой вкладки; справа — «+» у админа на «Аналитике», чат на «Профиле». */
    val navBar: @Composable (AppTab) -> Unit = { tab ->
        val extra = when (tab) {
            AppTab.Analytics -> if (isAdmin) NavBarExtra(AppIcons.plusBig) { actions.toReportCreate() } else null
            AppTab.Profile -> NavBarExtra(AppIcons.chat) {
                // Админ ведёт личные чаты во вкладке «Сообщество», пользователь — свой чат с админом.
                if (Session.isAdmin) actions.toCommunity() else actions.toChat("admin")
            }
            else -> null
        }
        GlassNavBar(current = tab, onSelect = { selectTab(it) }, extra = extra)
    }

    /** Вход в приложение после авторизации (iOS Router.enterApp): стек авторизации сбрасывается. */
    fun enterApp() {
        scope.launch {
            runCatching { Session.load(force = true) }
            scope.launch { AppData.preload() }
            nav.navigate(Routes.HOME) { popUpTo(0) { inclusive = true } }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(T.Bg)) {
        NavHost(
            navController = nav,
            startDestination = start,
            // Вкладки переключаются без анимации, внутренние экраны въезжают справа (как push в iOS).
            enterTransition = { if (isTabSwitch()) EnterTransition.None else slideInHorizontally(tween(320)) { it } },
            exitTransition = { if (isTabSwitch()) ExitTransition.None else slideOutHorizontally(tween(320)) { -it / 4 } },
            popEnterTransition = { if (isTabSwitch()) EnterTransition.None else slideInHorizontally(tween(320)) { -it / 4 } },
            popExitTransition = { if (isTabSwitch()) ExitTransition.None else slideOutHorizontally(tween(320)) { it } },
        ) {
            // --- Auth ---
            composable(Routes.LOGIN) {
                LoginScreen(
                    onAuthed = { enterApp() },
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
                    // Шаг 2 регистрации: имя, тег и фото (iOS AuthRoute.registerProfile).
                    onConfirmed = { nav.navigate(Routes.REGISTER_PROFILE) { popUpTo(Routes.LOGIN) { inclusive = true } } },
                    onBack = { actions.back() },
                )
            }
            composable(Routes.REGISTER_PROFILE) {
                RegisterProfileScreen(onDone = { enterApp() })
            }
            composable(Routes.PASSWORD_RECOVERY) {
                PasswordRecoveryScreen(onBack = { actions.back() }, onDone = { enterApp() })
            }

            // --- Tabs ---
            composable(Routes.HOME) {
                HomeScreen(onOpenReport = { actions.toReportDetail(it) }, navBar = { navBar(AppTab.Home) })
            }
            composable(Routes.ANALYTICS) {
                AnalyticsScreen(onOpenReport = { actions.toReportDetail(it) }, navBar = { navBar(AppTab.Analytics) })
            }
            composable(Routes.COMMUNITY) {
                CommunityScreen(onOpenChat = { actions.toChat(it) }, navBar = { navBar(AppTab.Community) })
            }
            composable(Routes.PROFILE) {
                ProfileScreen(
                    onSettings = { actions.toProfileEdit() },
                    onSubscription = { actions.toSubscription() },
                    onNotifications = { actions.toNotifications() },
                    onAccountData = { actions.toAccountData() },
                    onAdmins = { actions.toAdmins() },
                    onAgreement = { actions.toUserAgreement() },
                    onLogout = { exitToLogin() },
                    onOpenReport = { actions.toReportDetail(it) },
                    navBar = { navBar(AppTab.Profile) },
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
                    onDiscuss = { slug -> if (slug != null) actions.toChat(slug) else actions.toCommunity() },
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
                ChatInfoScreen(
                    slug = slug,
                    onBack = { actions.back() },
                    onEdit = { actions.toChatEdit(slug) },
                    onReport = { actions.toReportDetail(it) },
                    onUser = { actions.toChatUser(it) },
                    onLeave = { actions.toCommunity() },
                )
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
            ) { entry ->
                val userId = entry.arguments?.getLong("userId") ?: 0L
                UserProfileScreen(userId = userId, onBack = { actions.back() })
            }

            // --- Profile section ---
            composable(Routes.PROFILE_EDIT) { ProfileEditScreen(onBack = { actions.back() }, onDone = { actions.back() }) }
            composable(Routes.NOTIFICATIONS) { NotificationsScreen(onBack = { actions.back() }) }
            composable(Routes.ACCOUNT_DATA) {
                AccountDataScreen(
                    onBack = { actions.back() },
                    onChange = { actions.toAccountChange(it) },
                    onLogout = { exitToLogin() },
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
            composable(Routes.USER_AGREEMENT) { UserAgreementScreen(onBack = { actions.back() }) }
        }
    }
}

/** Переход между корневыми вкладками (без анимации). */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch(): Boolean =
    Routes.base(initialState.destination.route) in Routes.ROOT && Routes.base(targetState.destination.route) in Routes.ROOT

/** Гард админских маршрутов: не-админа возвращаем на главную. */
@Composable
private fun AdminGuard(isAdmin: Boolean, actions: NavActions, content: @Composable () -> Unit) {
    if (isAdmin) {
        content()
    } else {
        LaunchedEffect(Unit) { actions.toHome() }
    }
}
