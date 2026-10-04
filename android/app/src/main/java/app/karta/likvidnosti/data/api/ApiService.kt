package app.karta.likvidnosti.data.api

import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
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
        @Query("status") status: String? = null,
        @Query("search") search: String? = null,
    ): DataWrapper<List<ReportDto>>

    @GET("reports/{id}")
    suspend fun report(@Path("id") id: Long): DataWrapper<ReportDto>

    // --- Профиль ---
    @GET("profile")
    suspend fun profile(): ProfileResponse

    @PUT("profile/instruments")
    suspend fun syncInstruments(@Body body: InstrumentsBody): InstrumentsResponse

    @PUT("profile/notifications")
    suspend fun updateNotifications(@Body body: NotificationsBody): StatusResponse

    // --- Подписка ---
    @GET("subscription")
    suspend fun subscription(): DataWrapper<SubscriptionDto>

    @PUT("subscription")
    suspend fun updateSubscription(@Body body: SubscriptionBody): StatusResponse

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

    // --- Админка ---
    @GET("admin/admins")
    suspend fun admins(): DataWrapper<List<AdminDto>>

    @POST("admin/admins")
    suspend fun addAdmin(@Body body: AddAdminBody): DataWrapper<AdminDto>

    @DELETE("admin/admins/{id}")
    suspend fun removeAdmin(@Path("id") telegramId: String): StatusResponse

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
}
