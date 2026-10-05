package app.karta.likvidnosti.data

import app.karta.likvidnosti.BuildConfig
import app.karta.likvidnosti.data.api.AddAdminBody
import app.karta.likvidnosti.data.api.AdminDto
import app.karta.likvidnosti.data.api.ChangeEmailBody
import app.karta.likvidnosti.data.api.ChangePasswordBody
import app.karta.likvidnosti.data.api.ChangePhoneBody
import app.karta.likvidnosti.data.api.ChatItemDto
import app.karta.likvidnosti.data.api.ChatSectionDto
import app.karta.likvidnosti.data.api.ConfirmBody
import app.karta.likvidnosti.data.api.EmailBody
import app.karta.likvidnosti.data.api.InstrumentDto
import app.karta.likvidnosti.data.api.InstrumentsBody
import app.karta.likvidnosti.data.api.LoginBody
import app.karta.likvidnosti.data.api.MessageBody
import app.karta.likvidnosti.data.api.MessagesResponse
import app.karta.likvidnosti.data.api.MessageDto
import app.karta.likvidnosti.data.api.Network
import app.karta.likvidnosti.data.api.NotificationsBody
import app.karta.likvidnosti.data.api.ProfileResponse
import app.karta.likvidnosti.data.api.RegisterBody
import app.karta.likvidnosti.data.api.ReportDto
import app.karta.likvidnosti.data.api.ResetBody
import app.karta.likvidnosti.data.api.SubscriptionBody
import app.karta.likvidnosti.data.api.SubscriptionDto
import app.karta.likvidnosti.data.api.TokenResponse
import app.karta.likvidnosti.data.api.UserDto
import app.karta.likvidnosti.data.api.apiCall
import okhttp3.MultipartBody

/** Полный URL файла, который бэкенд отдаёт относительной ссылкой. */
fun fileUrl(path: String?): String? {
    if (path.isNullOrEmpty()) return null
    if (path.startsWith("http://") || path.startsWith("https://")) return path
    val base = BuildConfig.API_BASE_URL.removeSuffix("/")
    return base + path
}

/**
 * Единый доступ к API (аналог frontend/src/api). Все методы suspend,
 * ошибки приходят как ApiException.
 */
object Repository {
    private val api get() = Network.api

    private fun applyToken(resp: TokenResponse): UserDto? {
        TokenStore.token = resp.token
        Session.setCurrent(resp.user)
        return resp.user
    }

    // --- Авторизация ---
    suspend fun login(identifier: String, password: String): UserDto? =
        apiCall { applyToken(api.login(LoginBody(identifier, password))) }

    suspend fun register(email: String, phone: String, password: String) =
        apiCall { api.register(RegisterBody(email, phone, password)) }

    suspend fun confirmRegistration(email: String, code: String): UserDto? =
        apiCall { applyToken(api.confirmRegistration(ConfirmBody(email, code))) }

    suspend fun resendRegistration(email: String) =
        apiCall { api.resendRegistration(EmailBody(email)) }

    suspend fun requestRecovery(email: String) =
        apiCall { api.requestRecovery(EmailBody(email)) }

    suspend fun resetPassword(email: String, password: String): UserDto? =
        apiCall { applyToken(api.resetPassword(ResetBody(email, password))) }

    suspend fun logout() {
        try {
            apiCall { api.logout() }
        } finally {
            TokenStore.token = null
            Session.clear()
        }
    }

    // --- Отчёты ---
    suspend fun reports(instrument: String? = null, status: String? = null, search: String? = null): List<ReportDto> =
        apiCall {
            val inst = if (instrument == "all") null else instrument
            api.reports(inst, status, search).data ?: emptyList()
        }

    suspend fun report(id: Long): ReportDto? = apiCall { api.report(id).data }

    suspend fun instruments(): List<InstrumentDto> = apiCall { api.instruments().data ?: emptyList() }

    // --- Профиль ---
    suspend fun profile(): ProfileResponse = apiCall { api.profile() }

    suspend fun syncInstruments(keys: List<String>): List<InstrumentDto> =
        apiCall { api.syncInstruments(InstrumentsBody(keys)).instruments }

    suspend fun updateNotifications(all: Boolean? = null, byInstrument: Boolean? = null) =
        apiCall { api.updateNotifications(NotificationsBody(all, byInstrument)) }

    // --- Подписка ---
    suspend fun subscription(): SubscriptionDto? = apiCall { api.subscription().data }

    suspend fun updateSubscription(autoPay: Boolean? = null, paymentMethod: String? = null) =
        apiCall { api.updateSubscription(SubscriptionBody(autoPay, paymentMethod)) }

    // --- Аккаунт ---
    suspend fun changeEmail(email: String) = apiCall { api.changeEmail(ChangeEmailBody(email)) }
    suspend fun changePhone(phone: String) = apiCall { api.changePhone(ChangePhoneBody(phone)) }
    suspend fun changePassword(password: String, confirm: String, current: String?) =
        apiCall { api.changePassword(ChangePasswordBody(password, confirm, current)) }

    // --- Сообщество ---
    suspend fun chats(): List<ChatSectionDto> = apiCall { api.chats().data ?: emptyList() }
    suspend fun messages(slug: String): MessagesResponse = apiCall { api.messages(slug) }
    suspend fun sendMessage(slug: String, body: String): MessageDto? =
        apiCall { api.sendMessage(slug, MessageBody(body)).data }

    // --- Админка ---
    suspend fun admins(): List<AdminDto> = apiCall { api.admins().data ?: emptyList() }
    suspend fun addAdmin(telegramId: String, name: String? = null): AdminDto? =
        apiCall { api.addAdmin(AddAdminBody(telegramId, name)).data }
    suspend fun removeAdmin(telegramId: String) = apiCall { api.removeAdmin(telegramId) }

    suspend fun createReport(parts: List<MultipartBody.Part>): ReportDto? =
        apiCall { api.createReport(parts).data }
    suspend fun updateReport(id: Long, parts: List<MultipartBody.Part>): ReportDto? =
        apiCall { api.updateReport(id, parts).data }
    suspend fun deleteReport(id: Long) = apiCall { api.deleteReport(id) }

    suspend fun updateChat(slug: String, parts: List<MultipartBody.Part>): ChatItemDto? =
        apiCall { api.updateChat(slug, parts).data }
}
