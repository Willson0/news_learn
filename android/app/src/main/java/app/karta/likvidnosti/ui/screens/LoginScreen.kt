package app.karta.likvidnosti.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.karta.likvidnosti.data.Repository
import app.karta.likvidnosti.data.api.ApiException
import app.karta.likvidnosti.ui.components.AppButton
import app.karta.likvidnosti.ui.components.AppInput
import app.karta.likvidnosti.ui.components.AuthField
import app.karta.likvidnosti.ui.components.AuthFieldError
import app.karta.likvidnosti.ui.components.ButtonVariant
import app.karta.likvidnosti.ui.components.pressable
import app.karta.likvidnosti.ui.components.screenBackground
import app.karta.likvidnosti.ui.theme.AppFont
import app.karta.likvidnosti.ui.theme.T
import app.karta.likvidnosti.ui.theme.designBottom
import app.karta.likvidnosti.ui.theme.designTop
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import app.karta.likvidnosti.YandexSignIn
import app.karta.likvidnosti.data.Session
import app.karta.likvidnosti.data.TokenStore

internal val AUTH_EMAIL_RE = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
private val PHONE_RE = Regex("^\\+?[0-9\\s\\-()]{7,}$")

/** Маска российского номера из макета: «+7 (999) 999-99-99» (iOS PhoneMask). */
object PhoneMask {
    fun digits(s: String): String {
        var d = s.filter { it.isDigit() }
        if (d.startsWith("8")) d = "7" + d.drop(1)
        if (d.isNotEmpty() && !d.startsWith("7")) d = "7$d"
        return d.take(11)
    }

    fun format(s: String): String {
        val d = digits(s)
        if (d.isEmpty()) return ""
        val out = StringBuilder("+7")
        d.drop(1).forEachIndexed { i, ch ->
            when (i) {
                0 -> out.append(" (").append(ch)
                3 -> out.append(") ").append(ch)
                6, 8 -> out.append('-').append(ch)
                else -> out.append(ch)
            }
        }
        return out.toString()
    }
}

private enum class AuthMode { Login, Register }

/**
 * «Вход/регистрация» (iOS LoginView, Figma 337:26653, регистрация 337:26667,
 * ошибки 349:9124/9982/9056…): «16+» Kreadon 24 белый 40 % справа на y=71,
 * круг-логотип 100 (#25262A) на y=94, заголовок Kreadon 24 через 40 —
 * активная часть белая, вторая белый 40 %; поля 48 через 12 (отступ 24);
 * ошибки 12 красным 70 % в 4 под полем; внизу «Войти с Yandex» и
 * «Войти»/«Далее» (низ — 45).
 */
@Composable
fun LoginScreen(
    onAuthed: () -> Unit,
    onRecovery: () -> Unit,
    onNeedConfirm: (String) -> Unit,
) {
    var mode by remember { mutableStateOf(AuthMode.Login) }
    var identifier by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordConfirm by remember { mutableStateOf("") }
    var errors by remember { mutableStateOf(mapOf<String, String>()) }
    var submitting by remember { mutableStateOf(false) }
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        YandexSignIn.callbacks.collect { uri ->
            val token = uri.getQueryParameter("token")
            if (!token.isNullOrEmpty()) {
                TokenStore.token = token
                Session.load(force = true)
                if (Session.user != null) onAuthed()
                else { TokenStore.token = null; errors = mapOf("identifier" to "Не удалось загрузить профиль") }
            } else {
                errors = mapOf("identifier" to (uri.getQueryParameter("error") ?: "Не удалось войти через Яндекс"))
            }
        }
    }
    val scope = rememberCoroutineScope()

    fun switchMode(m: AuthMode) {
        if (mode == m) return
        mode = m
        errors = emptyMap()
    }

    fun validateLogin(): Boolean {
        val e = mutableMapOf<String, String>()
        val id = identifier.trim()
        if (id.isEmpty()) e["identifier"] = "Введите email или номер телефона"
        else if (!AUTH_EMAIL_RE.matches(id) && !PHONE_RE.matches(id)) e["identifier"] = "Неверный email или номер телефона"
        if (password.isEmpty()) e["password"] = "Введите пароль"
        errors = e
        return e.isEmpty()
    }

    fun validateRegister(): Boolean {
        val e = mutableMapOf<String, String>()
        if (!AUTH_EMAIL_RE.matches(email.trim())) e["email"] = "Несуществующий email"
        if (PhoneMask.digits(phone).length != 11) e["phone"] = "Неверный номер телефона"
        if (password.length < 8) e["password"] = "Пароль должен содержать от 8 символов"
        if (passwordConfirm != password) e["passwordConfirm"] = "Пароль не совпадает"
        errors = e
        return e.isEmpty()
    }

    fun submit() {
        val ok = if (mode == AuthMode.Login) validateLogin() else validateRegister()
        if (!ok || submitting) return
        submitting = true
        scope.launch {
            try {
                if (mode == AuthMode.Login) {
                    Repository.login(identifier.trim(), password)
                    onAuthed()
                } else {
                    val em = email.trim()
                    Repository.register(em, phone.trim(), password)
                    onNeedConfirm(em)
                }
            } catch (ex: ApiException) {
                val mapped = ex.errors.mapValues { it.value.firstOrNull() ?: "" }.filterValues { it.isNotEmpty() }
                errors = mapped.ifEmpty { mapOf("identifier" to ex.message, "email" to ex.message) }
            } catch (ex: Exception) {
                errors = mapOf("identifier" to "Не удалось выполнить запрос")
            } finally {
                submitting = false
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().screenBackground()) {
        Column(modifier = Modifier.fillMaxSize().imePadding()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = T.ScreenX)
                    .padding(top = designTop(94.dp), bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(100.dp).clip(CircleShape).background(T.Surface))

                Row(modifier = Modifier.padding(top = 40.dp)) {
                    val login = mode == AuthMode.Login
                    Text(
                        if (login) "Вход / " else "Вход ",
                        style = AppFont.heading(T.FsTitle, if (login) T.Text else T.TextMuted),
                        modifier = Modifier.pressable(pressAlpha = 1f) { switchMode(AuthMode.Login) },
                    )
                    Text(
                        if (!login) "/ Регистрация" else "Регистрация",
                        style = AppFont.heading(T.FsTitle, if (!login) T.Text else T.TextMuted),
                        modifier = Modifier.pressable(pressAlpha = 1f) { switchMode(AuthMode.Register) },
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (mode == AuthMode.Login) {
                        AuthField(errors["identifier"]) {
                            AppInput(
                                identifier,
                                { identifier = it },
                                placeholder = "Email или номер телефона",
                                error = errors.containsKey("identifier"),
                                keyboardType = KeyboardType.Email,
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            AppInput(
                                password,
                                { password = it },
                                placeholder = "Пароль",
                                isPassword = true,
                                error = errors.containsKey("password"),
                            )
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                                Box(modifier = Modifier.weight(1f)) {
                                    errors["password"]?.let { AuthFieldError(it) }
                                }
                                Text(
                                    "Забыли пароль?",
                                    style = AppFont.sans(T.FsSm, color = Color.White.copy(alpha = 0.6f)),
                                    modifier = Modifier
                                        .padding(top = 8.dp)
                                        .pressable { onRecovery() },
                                )
                            }
                        }
                    } else {
                        AuthField(errors["email"]) {
                            AppInput(
                                email,
                                { email = it },
                                placeholder = "Email",
                                error = errors.containsKey("email"),
                                keyboardType = KeyboardType.Email,
                            )
                        }
                        AuthField(errors["phone"]) {
                            AppInput(
                                phone,
                                { phone = PhoneMask.format(it) },
                                placeholder = "Номер телефона",
                                error = errors.containsKey("phone"),
                                keyboardType = KeyboardType.Phone,
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            AppInput(
                                password,
                                { password = it },
                                placeholder = "Пароль",
                                isPassword = true,
                                error = errors.containsKey("password"),
                            )
                            Text(
                                errors["password"] ?: "Пароль должен содержать от 8 символов",
                                style = AppFont.sans(
                                    T.FsSm,
                                    color = if (errors.containsKey("password")) T.Error.copy(alpha = 0.7f) else T.Text,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        AuthField(errors["passwordConfirm"]) {
                            AppInput(
                                passwordConfirm,
                                { passwordConfirm = it },
                                placeholder = "Подтвердите пароль",
                                isPassword = true,
                                error = errors.containsKey("passwordConfirm"),
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = T.ScreenX)
                    .padding(top = 12.dp, bottom = designBottom(T.NavBarBottom)),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AppButton("Войти с Yandex", variant = ButtonVariant.Secondary) {
                    YandexSignIn.start(context)
                }
                AppButton(
                    if (mode == AuthMode.Login) "Войти" else "Далее",
                    variant = ButtonVariant.Accent,
                    enabled = !submitting,
                ) { submit() }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(top = designTop(71.dp)),
        ) {
            Spacer(Modifier.weight(1f))
            Text("16+", style = AppFont.heading(T.FsTitle, T.TextMuted))
        }
    }
}
