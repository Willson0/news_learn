package app.karta.likvidnosti.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class DataWrapper<T>(val data: T? = null)

@Serializable
class StatusResponse(val status: String? = null, val message: String? = null)

@Serializable
class MeResponse(val user: UserDto? = null)

@Serializable
class TokenResponse(val token: String = "", val user: UserDto? = null)

@Serializable
class UserDto(
    val id: Long = 0,
    val name: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val username: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("telegram_id") val telegramId: String? = null,
    @SerialName("is_admin") val isAdmin: Boolean = false,
    @SerialName("is_root_admin") val isRootAdmin: Boolean = false,
)

@Serializable
class InstrumentDto(
    val key: String = "",
    val label: String = "",
    val category: String? = null,
    val on: Boolean = false,
)

@Serializable
class ReportDto(
    val id: Long = 0,
    val title: String = "",
    val description: String = "",
    val badge: String? = null,
    val status: String? = null,
    val date: String? = null,
    val material: String? = null,
    val instrument: String? = null,
    @SerialName("chart_url") val chartUrl: String? = null,
    @SerialName("cover_url") val coverUrl: String? = null,
    @SerialName("cover_name") val coverName: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    @SerialName("html_name") val htmlName: String? = null,
    val chat: String? = null,
    val body: String? = null,
)

@Serializable
class ProfileResponse(
    val user: UserDto? = null,
    val subscription: SubscriptionDto? = null,
    val notifications: NotificationsDto? = null,
    val instruments: List<InstrumentDto> = emptyList(),
)

@Serializable
class NotificationsDto(
    val all: Boolean = true,
    @SerialName("by_instrument") val byInstrument: Boolean = false,
)

@Serializable
class InstrumentsResponse(val instruments: List<InstrumentDto> = emptyList())

@Serializable
class SubscriptionDto(
    val active: Boolean = false,
    val plan: String? = null,
    val until: String? = null,
    @SerialName("auto_pay") val autoPay: Boolean = false,
    @SerialName("payment_method") val paymentMethod: String? = null,
)

@Serializable
class ChatSectionDto(
    val key: String = "",
    val title: String = "",
    val items: List<ChatItemDto> = emptyList(),
)

@Serializable
class ChatItemDto(
    val id: String = "",
    val type: String? = null,
    val title: String = "",
    val subtitle: String? = null,
    val pinned: Boolean = false,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val report: Long? = null,
    val sender: String? = null,
    val preview: String? = null,
    val time: String? = null,
    val unread: Int = 0,
)

@Serializable
class MessagesResponse(
    val chat: ChatItemDto? = null,
    val data: List<MessageDto> = emptyList(),
)

@Serializable
class MessageDto(
    val id: Long = 0,
    val mine: Boolean = false,
    val author: String? = null,
    val role: String? = null,
    val text: String? = null,
    val time: String? = null,
)

@Serializable
class AdminDto(
    @SerialName("telegram_id") val telegramId: String = "",
    val name: String? = null,
    val username: String? = null,
    val root: Boolean = false,
)

// --- Тела запросов ---
@Serializable
class LoginBody(val identifier: String, val password: String)

@Serializable
class RegisterBody(val email: String, val phone: String, val password: String, val name: String? = null)

@Serializable
class ConfirmBody(val email: String, val code: String)

@Serializable
class EmailBody(val email: String)

@Serializable
class ResetBody(val email: String, val password: String)

@Serializable
class MessageBody(val body: String)

@Serializable
class InstrumentsBody(val instruments: List<String>)

@Serializable
class NotificationsBody(val all: Boolean? = null, @SerialName("by_instrument") val byInstrument: Boolean? = null)

@Serializable
class SubscriptionBody(
    @SerialName("auto_pay") val autoPay: Boolean? = null,
    @SerialName("payment_method") val paymentMethod: String? = null,
)

@Serializable
class ChangeEmailBody(val email: String)

@Serializable
class ChangePhoneBody(val phone: String)

@Serializable
class ChangePasswordBody(
    val password: String,
    @SerialName("password_confirmation") val passwordConfirmation: String,
    @SerialName("current_password") val currentPassword: String? = null,
)

@Serializable
class AddAdminBody(@SerialName("telegram_id") val telegramId: String, val name: String? = null)
