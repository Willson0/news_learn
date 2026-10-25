package app.karta.likvidnosti.data.api

import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {

    // --- Авторизация ---
    @POST("auth/login")
    suspend fun login(@Body body: LoginBody): TokenResponse

    @POST("auth/register")
    suspend fun register(@Body body: RegisterBody): StatusResponse

    @POST("auth/register/confirm")
    suspend fun confirmRegistration(@Body body: ConfirmBody): TokenResponse

    @POST("auth/register/resend")
    suspend fun resendRegistration(@Body body: EmailBody): StatusResponse

    @POST("auth/recovery")
    suspend fun requestRecovery(@Body body: EmailBody): StatusResponse

    @POST("auth/recovery/reset")
    suspend fun resetPassword(@Body body: ResetBody): TokenResponse

    @POST("auth/yandex/exchange")
    suspend fun yandexExchange(@Body body: YandexExchangeBody): TokenResponse

    @POST("auth/logout")
    suspend fun logout(): StatusResponse

    @GET("user")
    suspend fun me(): MeResponse

    // --- Справочники / отчёты ---
    @GET("instruments")
    suspend fun instruments(): DataWrapper<List<InstrumentDto>>

    @GET("reports")
    suspend fun reports(
        @Query("instrument") instrument: String? = null,
        @Query("instruments") instruments: String? = null,
        @Query("period") period: String? = null,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
        @Query("status") status: String? = null,
        @Query("search") search: String? = null,
    ): DataWrapper<List<ReportDto>>

    @GET("reports/{id}")
    suspend fun report(@Path("id") id: Long): DataWrapper<ReportDto>

    // --- Профиль ---
    @GET("profile")
    suspend fun profile(): ProfileResponse

    @PUT("profile")
    suspend fun updateProfile(@Body body: ProfileBody): StatusResponse

    @Multipart
    @POST("profile/avatar")
    suspend fun uploadAvatar(@Part parts: List<MultipartBody.Part>): DataWrapper<UserDto>

    @DELETE("profile/avatar")
    suspend fun removeAvatar(): StatusResponse

    @PUT("profile/instruments")
    suspend fun syncInstruments(@Body body: InstrumentsBody): InstrumentsResponse

    @PUT("profile/notifications")
    suspend fun updateNotifications(@Body body: NotificationsBody): StatusResponse

    // --- Подписка ---
    @GET("subscription")
    suspend fun subscription(): DataWrapper<SubscriptionDto>

    @POST("chats/{slug}/leave")
    suspend fun leaveChat(@Path("slug") slug: String): StatusResponse

    @PUT("subscription")
    suspend fun updateSubscription(@Body body: SubscriptionBody): StatusResponse

    /** Привязанная карта Т-Кассы (bound = false, если карты нет). */
    @GET("billing/card")
    suspend fun billingCard(): BillingCardDto

    /** Ссылка на страницу Т-Кассы, по которой открывается привязка карты. */
    @POST("billing/card/bind")
    suspend fun bindCard(): BillingBindDto

    @DELETE("billing/card")
    suspend fun unbindCard()

    // --- Аккаунт ---
    @PUT("account/email")
    suspend fun changeEmail(@Body body: ChangeEmailBody): StatusResponse

    @PUT("account/phone")
    suspend fun changePhone(@Body body: ChangePhoneBody): StatusResponse

    @PUT("account/password")
    suspend fun changePassword(@Body body: ChangePasswordBody): StatusResponse

    // --- Сообщество ---
    @GET("chats")
    suspend fun chats(): DataWrapper<List<ChatSectionDto>>

    @GET("chats/{slug}/messages")
    suspend fun messages(@Path("slug") slug: String): MessagesResponse

    @POST("chats/{slug}/messages")
    suspend fun sendMessage(@Path("slug") slug: String, @Body body: MessageBody): DataWrapper<MessageDto>

    @Multipart
    @POST("chats/{slug}/messages")
    suspend fun sendMessageMultipart(@Path("slug") slug: String, @Part parts: List<MultipartBody.Part>): DataWrapper<MessageDto>

    @POST("chats/{slug}/messages/{id}/react")
    suspend fun reactMessage(@Path("slug") slug: String, @Path("id") id: Long, @Body body: ReactBody): DataWrapper<MessageDto>

    @POST("chats/{slug}/messages/{id}/pin")
    suspend fun pinMessage(@Path("slug") slug: String, @Path("id") id: Long): PinResponse

    @DELETE("chats/{slug}/messages/{id}")
    suspend fun deleteMessage(@Path("slug") slug: String, @Path("id") id: Long): StatusResponse

    @GET("chats/{slug}/participants")
    suspend fun participants(@Path("slug") slug: String): DataWrapper<List<ParticipantDto>>

    @GET("chats/{slug}/attachments/{kind}")
    suspend fun chatAttachments(@Path("slug") slug: String, @Path("kind") kind: String): DataWrapper<List<ChatAttachmentDto>>

    @GET("users/{id}")
    suspend fun userProfile(@Path("id") id: Long): DataWrapper<UserProfileDto>

    // --- Админка ---
    @GET("admin/admins")
    suspend fun admins(): DataWrapper<List<AdminDto>>

    @POST("admin/admins")
    suspend fun addAdmin(@Body body: AddAdminBody): DataWrapper<AdminDto>

    @DELETE("admin/admins/{id}")
    suspend fun removeAdmin(@Path("id") id: Long): StatusResponse

    @Multipart
    @POST("admin/reports")
    suspend fun createReport(@Part parts: List<MultipartBody.Part>): DataWrapper<ReportDto>

    @Multipart
    @POST("admin/reports/{id}")
    suspend fun updateReport(@Path("id") id: Long, @Part parts: List<MultipartBody.Part>): DataWrapper<ReportDto>

    @DELETE("admin/reports/{id}")
    suspend fun deleteReport(@Path("id") id: Long): StatusResponse

    @Multipart
    @POST("admin/chats/{slug}")
    suspend fun updateChat(@Path("slug") slug: String, @Part parts: List<MultipartBody.Part>): DataWrapper<ChatItemDto>

    // --- Push и документы ---
    @POST("devices")
    suspend fun registerDevice(@Body body: DeviceBody): StatusResponse

    @HTTP(method = "DELETE", path = "devices", hasBody = true)
    suspend fun unregisterDevice(@Body body: DeviceBody): StatusResponse

    @GET("documents")
    suspend fun documents(): DataWrapper<List<DocumentDto>>
}
