package app.karta.likvidnosti.data.api

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.karta.likvidnosti.BuildConfig
import app.karta.likvidnosti.data.OfflineCache
import app.karta.likvidnosti.data.TokenStore
import kotlinx.coroutines.CancellationException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import okhttp3.ResponseBody.Companion.toResponseBody
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit

/**
 * Ошибка API: несёт http-статус и поля валидации (errors) от Laravel —
 * аналог ApiError из frontend/src/api/client.js.
 */
class ApiException(
    override val message: String,
    val status: Int,
    val errors: Map<String, List<String>> = emptyMap(),
    /** Что случилось — для понятного текста и экрана ошибки. */
    val kind: Kind = Kind.Other,
) : Exception(message) {
    enum class Kind { Offline, Timeout, Server, Unauthorized, Other }

    /** Сетевой сбой (нет интернета или таймаут), а не ответ сервера. */
    val isNetwork: Boolean get() = kind == Kind.Offline || kind == Kind.Timeout

    companion object {
        const val OFFLINE = "Нет подключения к интернету"
        const val TIMEOUT = "Сервер долго не отвечает. Попробуйте ещё раз"
        const val SERVER = "Сервер временно недоступен. Попробуйте позже"
        const val EXPIRED = "Сессия истекла. Войдите снова"
        const val TOO_MANY = "Слишком много попыток. Подождите минуту и попробуйте снова"
    }

    /** Первое сообщение для указанного поля, если есть. */
    fun fieldError(vararg fields: String): String? {
        for (f in fields) errors[f]?.firstOrNull()?.let { return it }
        return null
    }

    /** Первое сообщение любой ошибки валидации. */
    fun firstError(): String? = errors.values.firstOrNull()?.firstOrNull() ?: message
}

/**
 * Последний сетевой сбой (экран ошибки показывает по нему свой текст) и событие
 * «сессия истекла» — AppRoot уводит на вход с пояснением.
 */
object NetStatus {
    var lastFailure by mutableStateOf<ApiException.Kind?>(null)
        internal set

    /** Растёт при каждом 401 на запрос с токеном. */
    var sessionExpired by mutableStateOf(0)
        internal set
}

object Network {
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
    }

    private val authInterceptor = Interceptor { chain ->
        val builder = chain.request().newBuilder()
            .header("Accept", "application/json")
        val token = TokenStore.token
        token?.let { builder.header("Authorization", "Bearer $it") }
        val request = builder.build()
        // Загрузка файлов идёт дольше обычного запроса.
        val isUpload = request.body?.contentType()?.type == "multipart"
        val c = if (isUpload) chain.withReadTimeout(120, TimeUnit.SECONDS).withWriteTimeout(120, TimeUnit.SECONDS) else chain
        val response = c.proceed(request)
        if (response.code == 401 && token != null) {
            // Токен больше не действует: выходим на экран входа с пояснением.
            TokenStore.token = null
            OfflineCache.clear()
            NetStatus.sessionExpired += 1
            return@Interceptor response.newBuilder().header(EXPIRED_HEADER, "1").build()
        }
        response
    }

    /**
     * Ответы основных GET-запросов (лента, чаты, профиль, подписка, пользователь)
     * сохраняются на диск: при следующем запуске, в том числе без сети, экраны сразу их показывают.
     */
    private val cacheInterceptor = Interceptor { chain ->
        val request = chain.request()
        val response = chain.proceed(request)
        val path = OfflineCache.pathFor(request.url, BuildConfig.API_BASE_URL)
        if (request.method == "GET" && response.isSuccessful && path != null) {
            val body = response.body
            if (body != null) {
                val bytes = body.bytes()
                OfflineCache.save(path, bytes)
                return@Interceptor response.newBuilder().body(bytes.toResponseBody(body.contentType())).build()
            }
        }
        response
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            // Без ответа дольше 20 с — ошибка «Сервер долго не отвечает» с «Повторить», а не вечный скелетон.
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(authInterceptor)
            .addInterceptor(cacheInterceptor)
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
                }
            }
            .build()
    }

    val api: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ApiService::class.java)
    }
}

/** Пометка ответа 401 на запрос с токеном (сессия истекла). */
private const val EXPIRED_HEADER = "X-Karta-Session-Expired"

/** Разбирает тело ошибки Laravel ({message, errors}) в ApiException. */
private fun parseError(e: HttpException): ApiException {
    val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
    // Сбой сервера и лимит запросов — свой человеческий текст, без кодов и английских фраз.
    if (e.code() >= 500) return ApiException(ApiException.SERVER, e.code(), kind = ApiException.Kind.Server)
    if (e.code() == 429) return ApiException(ApiException.TOO_MANY, e.code())
    if (e.code() == 401 && e.response()?.headers()?.get(EXPIRED_HEADER) != null) {
        return ApiException(ApiException.EXPIRED, 401, kind = ApiException.Kind.Unauthorized)
    }
    var message = "Ошибка запроса (${e.code()})"
    var errors: Map<String, List<String>> = emptyMap()
    if (!body.isNullOrBlank()) {
        runCatching {
            val obj = Network.json.parseToJsonElement(body)
            val map = obj as? kotlinx.serialization.json.JsonObject
            (map?.get("message") as? kotlinx.serialization.json.JsonPrimitive)?.let {
                if (it.isString) message = it.content
            }
            val errs = map?.get("errors") as? kotlinx.serialization.json.JsonObject
            if (errs != null) {
                errors = errs.mapValues { (_, v) ->
                    (v as? kotlinx.serialization.json.JsonArray)?.mapNotNull { el ->
                        (el as? kotlinx.serialization.json.JsonPrimitive)?.content
                    } ?: emptyList()
                }
            }
        }
    }
    return ApiException(message, e.code(), errors)
}

/**
 * Оборачивает вызов API: HttpException → ApiException, сетевые сбои → статус 0.
 */
suspend fun <T> apiCall(block: suspend () -> T): T {
    try {
        val result = block()
        NetStatus.lastFailure = null
        return result
    } catch (e: CancellationException) {
        // Ушли с экрана — запрос отменён. Это не ошибка: пробрасываем дальше, экран её не покажет.
        throw e
    } catch (e: HttpException) {
        val err = parseError(e)
        NetStatus.lastFailure = if (err.kind == ApiException.Kind.Server) err.kind else null
        throw err
    } catch (e: ApiException) {
        throw e
    } catch (e: Exception) {
        val err = networkError(e)
        NetStatus.lastFailure = err.kind
        throw err
    }
}

/** Сетевой сбой → понятная ошибка: таймаут отдельно от «нет интернета». */
private fun networkError(e: Exception): ApiException = when (e) {
    is SocketTimeoutException -> ApiException(ApiException.TIMEOUT, 0, kind = ApiException.Kind.Timeout)
    is InterruptedIOException -> ApiException(ApiException.TIMEOUT, 0, kind = ApiException.Kind.Timeout)
    is UnknownHostException, is ConnectException, is NoRouteToHostException, is SocketException ->
        ApiException(ApiException.OFFLINE, 0, kind = ApiException.Kind.Offline)
    else -> ApiException("Нет связи с сервером", 0, kind = ApiException.Kind.Offline)
}

/**
 * Как `onFailure`, но отмену запроса (ушли с экрана, сменили фильтр) пробрасывает
 * дальше: она не превращается в «нет соединения» на экране.
 */
inline fun <T> Result<T>.onFailureNotCancelled(action: (Throwable) -> Unit): Result<T> = onFailure {
    if (it is CancellationException) throw it
    action(it)
}
