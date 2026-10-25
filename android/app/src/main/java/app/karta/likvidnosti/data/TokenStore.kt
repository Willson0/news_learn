package app.karta.likvidnosti.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Хранилище токена Sanctum (аналог localStorage['kl_token'] в вебе).
 * Токен шифруется ключом AES-GCM из Android Keystore: ключ не покидает устройство,
 * поэтому файл настроек без него бесполезен (в том числе в резервной копии).
 */
object TokenStore {
    private const val PREFS = "karta_prefs"
    private const val KEY_TOKEN = "kl_token"
    private const val KEY_TOKEN_ENC = "kl_token_enc"
    private const val KEY_ALIAS = "karta_token_key"

    @Volatile
    private var cached: String? = null
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
        val prefs = prefs()
        cached = prefs.getString(KEY_TOKEN_ENC, null)?.let { decrypt(it) }
        // Прежние версии хранили токен открытым текстом: шифруем и стираем.
        val plain = prefs.getString(KEY_TOKEN, null)
        if (plain != null) {
            if (cached == null) cached = plain
            prefs.edit().remove(KEY_TOKEN).apply()
            cached?.let { save(it) }
        }
    }

    private fun prefs() = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var token: String?
        get() = cached
        set(value) {
            cached = value?.takeIf { it.isNotEmpty() }
            val v = cached
            if (v == null) prefs().edit().remove(KEY_TOKEN_ENC).remove(KEY_TOKEN).apply() else save(v)
        }

    val isAuthenticated: Boolean
        get() = !token.isNullOrEmpty()

    private fun save(value: String) {
        val enc = encrypt(value)
        prefs().edit().apply {
            // Если Keystore недоступен (редкие прошивки), токен живёт только в памяти до перезапуска.
            if (enc != null) putString(KEY_TOKEN_ENC, enc) else remove(KEY_TOKEN_ENC)
            remove(KEY_TOKEN)
        }.apply()
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    private fun encrypt(value: String): String? = runCatching {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val data = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        Base64.encodeToString(data, Base64.NO_WRAP)
    }.getOrNull()

    private fun decrypt(stored: String): String? = runCatching {
        val data = Base64.decode(stored, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data, 0, 12))
        String(cipher.doFinal(data, 12, data.size - 12), Charsets.UTF_8)
    }.getOrNull()
}
