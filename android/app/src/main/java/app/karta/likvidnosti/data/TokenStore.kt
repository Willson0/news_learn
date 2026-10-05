package app.karta.likvidnosti.data

import android.content.Context

/**
 * Хранилище токена Sanctum (аналог localStorage['kl_token'] в вебе).
 */
object TokenStore {
    private const val PREFS = "karta_prefs"
    private const val KEY_TOKEN = "kl_token"

    @Volatile
    private var cached: String? = null
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
        cached = prefs().getString(KEY_TOKEN, null)
    }

    private fun prefs() = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var token: String?
        get() = cached
        set(value) {
            cached = value
            prefs().edit().apply {
                if (value.isNullOrEmpty()) remove(KEY_TOKEN) else putString(KEY_TOKEN, value)
            }.apply()
        }

    val isAuthenticated: Boolean
        get() = !token.isNullOrEmpty()
}
