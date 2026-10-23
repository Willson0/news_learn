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
class BillingCardDto(
    val bound: Boolean = false,
    val brand: String? = null,
    val last4: String? = null,
)

@Serializable
class BillingBindDto(@SerialName("confirmation_url") val confirmationUrl: String = "")

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
    /** Личный чат и чат отчёта нельзя покинуть вручную. */
    val permanent: Boolean = false,
    @SerialName("can_manage") val canManage: Boolean? = null,
)

@Serializable
class MessagesResponse(
    val chat: ChatItemDto? = null,
    val pinned: MessageQuoteDto? = null,
    val data: List<MessageDto> = emptyList(),
)

@Serializable
class MessageDto(
    val id: Long = 0,
    @SerialName("user_id") val userId: Long? = null,
    val mine: Boolean = false,
    val author: String? = null,
    val role: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val text: String? = null,
    val time: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val reply: MessageQuoteDto? = null,
    val image: MsgImageDto? = null,
    /** Все фото сообщения-альбома (image — первое, для совместимости). */
    val images: List<MsgImageDto> = emptyList(),
    val file: MsgFileDto? = null,
    val voice: MsgVoiceDto? = null,
    val reactions: List<ReactionDto> = emptyList(),
    val pinned: Boolean = false,
    val read: Boolean = false,
)

@Serializable
class MessageQuoteDto(
    val id: Long = 0,
    val author: String? = null,
    val text: String? = null,
)

@Serializable
class MsgImageDto(val url: String = "", val width: Int? = null, val height: Int? = null)

@Serializable
class MsgFileDto(
    val url: String = "",
    val name: String? = null,
    val ext: String? = null,
    val size: String? = null,
)

@Serializable
class MsgVoiceDto(val url: String = "", val duration: String? = null)

@Serializable
class ReactionDto(val emoji: String = "", val count: Int = 0, val mine: Boolean = false)

@Serializable
class PinResponse(val pinned: MessageQuoteDto? = null)

@Serializable
class ParticipantDto(
    val id: Long = 0,
    val name: String = "",
    val username: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val role: String? = null,
    val seen: String? = null,
)

/** Универсальный элемент вкладок инфо-экрана (медиа/голос/файлы/ссылки). */
@Serializable
class ChatAttachmentDto(
    val id: Long = 0,
    val url: String = "",
    val name: String? = null,
    val ext: String? = null,
    val size: String? = null,
    val duration: Int? = null,
    val author: String? = null,
    val date: String? = null,
    val meta: String? = null,
    // только для ссылок
    val title: String? = null,
    val text: String? = null,
    val month: String? = null,
)

@Serializable
class UserProfileDto(
    val id: Long = 0,
    val name: String? = null,
    val username: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("registered_at") val registeredAt: String? = null,
    @SerialName("registered_days") val registeredDays: Int? = null,
    val seen: String? = null,
    @SerialName("is_admin") val isAdmin: Boolean = false,
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
class ResetBody(val email: String, val password: String, val code: String)

@Serializable
class MessageBody(val body: String, @SerialName("reply_to") val replyTo: Long? = null)

@Serializable
class ReactBody(val emoji: String)

@Serializable
class ProfileBody(val name: String? = null, val username: String? = null)

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
class AddAdminBody(val tag: String, val name: String? = null)

@Serializable
class DeviceBody(val token: String, val platform: String = "android")

/** Документ из админки: открывается по ссылке внутри приложения. */
@Serializable
class DocumentDto(val title: String, val url: String)
