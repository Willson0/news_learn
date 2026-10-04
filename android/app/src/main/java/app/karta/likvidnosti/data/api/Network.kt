package app.karta.likvidnosti.data.api

import app.karta.likvidnosti.BuildConfig
import app.karta.likvidnosti.data.TokenStore
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
) : Exception(message) {
    /** Первое сообщение для указанного поля, если есть. */
    fun fieldError(vararg fields: String): String? {
        for (f in fields) errors[f]?.firstOrNull()?.let { return it }
        return null
    }

    /** Первое сообщение любой ошибки валидации. */
    fun firstError(): String? = errors.values.firstOrNull()?.firstOrNull() ?: message
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
        TokenStore.token?.let { builder.header("Authorization", "Bearer $it") }
        val response = chain.proceed(builder.build())
        if (response.code == 401) {
            TokenStore.token = null
        }
        response
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
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

/** Разбирает тело ошибки Laravel ({message, errors}) в ApiException. */
private fun parseError(e: HttpException): ApiException {
    val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
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
        return block()
    } catch (e: HttpException) {
        throw parseError(e)
    } catch (e: ApiException) {
        throw e
    } catch (e: Exception) {
        throw ApiException("Нет связи с сервером", 0)
    }
}
