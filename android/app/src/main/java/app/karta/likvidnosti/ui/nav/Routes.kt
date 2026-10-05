package app.karta.likvidnosti.ui.nav

import androidx.navigation.NavController
import androidx.navigation.NavHostController

/**
 * Маршруты приложения (аналог frontend/src/router/index.js).
 * Константы — шаблоны для NavHost; хелперы ниже строят конкретные адреса.
 */
object Routes {
    const val LOGIN = "login"
    const val REGISTER_CONFIRM = "register-confirm/{email}"
    const val PASSWORD_RECOVERY = "password-recovery"

    const val HOME = "home"
    const val ANALYTICS = "analytics"
    const val REPORT_CREATE = "report-create"
    const val REPORT_EDIT = "report-edit/{id}"
    const val REPORT_DETAIL = "report-detail?id={id}"

    const val COMMUNITY = "community"
    const val CHAT = "chat/{slug}"
    const val CHAT_INFO = "chat-info/{slug}"
    const val CHAT_EDIT = "chat-edit/{slug}"
    const val CHAT_USER = "chat-user/{userId}"

    const val PROFILE = "profile"
    const val PROFILE_EDIT = "profile-edit"
    const val NOTIFICATIONS = "notifications"
    const val ACCOUNT_DATA = "account-data"
    const val ACCOUNT_CHANGE = "account-change/{field}"
    const val ACCOUNT_SUCCESS = "account-success/{field}"
    const val ADMINS = "admins"
    const val SUBSCRIPTION = "subscription"

    /** Корневые вкладки — без кнопки «назад». */
    val ROOT = setOf(HOME, ANALYTICS, COMMUNITY, PROFILE)

    /** Вкладки с нижней навигацией. */
    val WITH_NAV = setOf(HOME, ANALYTICS, COMMUNITY, PROFILE)

    /** Публичные маршруты (без авторизации). */
    val PUBLIC = setOf(LOGIN, "register-confirm", PASSWORD_RECOVERY)

    /** Админские маршруты. */
    val ADMIN = setOf("report-create", "report-edit", "chat-edit", "admins")

    /** Базовое имя маршрута без аргументов. */
    fun base(route: String?): String = route?.substringBefore("/")?.substringBefore("?") ?: ""
}

/** Типизированные переходы, передаются в экраны. */
class NavActions(private val nav: NavHostController) {
    val controller: NavController get() = nav

    fun back() {
        if (!nav.popBackStack()) nav.navigate(Routes.HOME)
    }

    private fun tab(route: String) = nav.navigate(route) {
        popUpTo(nav.graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    fun toHome() = tab(Routes.HOME)
    fun toAnalytics() = tab(Routes.ANALYTICS)
    fun toCommunity() = tab(Routes.COMMUNITY)
    fun toProfile() = tab(Routes.PROFILE)

    fun toLogin() = nav.navigate(Routes.LOGIN) {
        popUpTo(0) { inclusive = true }
    }

    fun toRegisterConfirm(email: String) = nav.navigate("register-confirm/${enc(email)}")
    fun toPasswordRecovery() = nav.navigate(Routes.PASSWORD_RECOVERY)

    fun toReportCreate() = nav.navigate(Routes.REPORT_CREATE)
    fun toReportEdit(id: Long) = nav.navigate("report-edit/$id")
    fun toReportDetail(id: Long?) =
        nav.navigate(if (id != null) "report-detail?id=$id" else "report-detail")

    fun toChat(slug: String) = nav.navigate("chat/$slug")
    fun toChatInfo(slug: String) = nav.navigate("chat-info/$slug")
    fun toChatEdit(slug: String) = nav.navigate("chat-edit/$slug")
    fun toChatUser(userId: Long) = nav.navigate("chat-user/$userId")

    fun toProfileEdit() = nav.navigate(Routes.PROFILE_EDIT)
    fun toNotifications() = nav.navigate(Routes.NOTIFICATIONS)
    fun toAccountData() = nav.navigate(Routes.ACCOUNT_DATA)
    fun toAccountChange(field: String) = nav.navigate("account-change/$field")
    fun toAccountSuccess(field: String) = nav.navigate("account-success/$field") {
        popUpTo(Routes.ACCOUNT_DATA)
    }
    fun toAdmins() = nav.navigate(Routes.ADMINS)
    fun toSubscription() = nav.navigate(Routes.SUBSCRIPTION)

    private fun enc(s: String): String = java.net.URLEncoder.encode(s, "UTF-8")
}
