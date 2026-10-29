package app.karta.likvidnosti.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.karta.likvidnosti.data.Repository
import app.karta.likvidnosti.data.api.ApiException
import app.karta.likvidnosti.ui.components.AppButton
import app.karta.likvidnosti.ui.components.AppInput
import app.karta.likvidnosti.ui.components.AuthField
import app.karta.likvidnosti.ui.components.AuthMessageView
import app.karta.likvidnosti.ui.components.AuthScaffold
import app.karta.likvidnosti.ui.components.ButtonVariant
import app.karta.likvidnosti.ui.components.pressable
import app.karta.likvidnosti.ui.theme.AppFont
import app.karta.likvidnosti.ui.theme.T
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val RESEND_SECONDS = 5 * 60

private enum class RecoveryStep { Email, Code, Password }

/**
 * «Восстановление пароля» (iOS PasswordRecoveryView, Figma 349:10487 … 349:10715)
 * в три шага: почта и «Получить код» → код из письма (после шестой цифры
 * проверяется сам, под полем — таймер повторной отправки) → новый пароль.
 * Успех — «Вы успешно сменили пароль», затем вход в приложение.
 */
@Composable
fun PasswordRecoveryScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    var step by remember { mutableStateOf(RecoveryStep.Email) }
    var email by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var errors by remember { mutableStateOf(mapOf<String, String>()) }
    var secondsLeft by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(secondsLeft) {
        if (secondsLeft > 0) {
            delay(1000)
            secondsLeft -= 1
        }
    }

    if (done) {
        LaunchedEffect(Unit) {
            delay(1500)
            onDone()
        }
        AuthMessageView("Вы успешно сменили пароль", showBack = true, onBack = onDone)
        return
    }

    fun back() {
        errors = emptyMap()
        when (step) {
            RecoveryStep.Email -> onBack()
            RecoveryStep.Code -> step = RecoveryStep.Email
            RecoveryStep.Password -> step = RecoveryStep.Code
        }
    }
    BackHandler(enabled = step != RecoveryStep.Email) { back() }

    /** Шаг 1 и «Отправить код повторно»: письмо с кодом и таймер 5:00. */
    fun sendCode() {
        if (!AUTH_EMAIL_RE.matches(email.trim())) {
            errors = mapOf("email" to "Проверьте адрес: например, name@mail.ru")
            return
        }
        if (busy || secondsLeft > 0) return
        busy = true
        scope.launch {
            try {
                Repository.requestRecovery(email.trim())
                code = ""
                errors = emptyMap()
                secondsLeft = RESEND_SECONDS
                step = RecoveryStep.Code
            } catch (ex: ApiException) {
                errors = mapOf("email" to ex.message)
            } catch (ex: Exception) {
                errors = mapOf("email" to "Не удалось отправить код")
            } finally {
                busy = false
            }
        }
    }

    /** Шаг 2: код проверяется на сервере, не тратится — дальше новый пароль. */
    fun checkCode() {
        if (code.length < 6) {
            errors = mapOf("code" to "Введите код из 6 цифр из письма")
            return
        }
        if (busy) return
        busy = true
        scope.launch {
            try {
                Repository.checkRecoveryCode(email.trim(), code)
                errors = emptyMap()
                step = RecoveryStep.Password
            } catch (ex: ApiException) {
                errors = mapOf("code" to (ex.errors["code"]?.firstOrNull() ?: ex.errors["email"]?.firstOrNull() ?: ex.message))
            } catch (ex: Exception) {
                errors = mapOf("code" to "Не удалось проверить код")
            } finally {
                busy = false
            }
        }
    }

    /** Шаг 3: новый пароль. */
    fun submit() {
        if (newPassword.length < 8) {
            errors = mapOf("newPassword" to "Пароль должен содержать от 8 символов")
            return
        }
        if (busy) return
        busy = true
        scope.launch {
            try {
                Repository.resetPassword(email.trim(), newPassword, code)
                done = true
            } catch (ex: ApiException) {
                val codeError = ex.errors["code"]?.firstOrNull()
                if (codeError != null) {
                    // Код успел истечь — возвращаемся к нему.
                    step = RecoveryStep.Code
                    errors = mapOf("code" to codeError)
                } else {
                    errors = mapOf("newPassword" to (ex.errors["password"]?.firstOrNull() ?: ex.message))
                }
            } catch (ex: Exception) {
                errors = mapOf("newPassword" to "Не удалось сменить пароль")
            } finally {
                busy = false
            }
        }
    }

    AuthScaffold(
        title = "Восстановление пароля",
        subtitle = when (step) {
            RecoveryStep.Email -> "Отправим код на почту, указанную при регистрации"
            RecoveryStep.Code -> "Мы отправили код на ${email.trim()}. Введите его ниже"
            RecoveryStep.Password -> "Придумайте новый пароль: не короче 8 символов"
        },
        onBack = { back() },
        buttons = {
            when (step) {
                RecoveryStep.Email -> AppButton("Получить код", variant = ButtonVariant.Accent, loading = busy) { sendCode() }
                RecoveryStep.Code -> AppButton("Далее", variant = ButtonVariant.Accent, loading = busy) { checkCode() }
                RecoveryStep.Password -> AppButton("Сменить пароль", variant = ButtonVariant.Accent, loading = busy) { submit() }
            }
        },
    ) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (step) {
                RecoveryStep.Email -> AuthField(errors["email"]) {
                    AppInput(
                        email,
                        { email = it; if (errors.isNotEmpty()) errors = emptyMap() },
                        placeholder = "Email",
                        error = errors.containsKey("email"),
                        keyboardType = KeyboardType.Email,
                    )
                }
                RecoveryStep.Code -> {
                    AuthField(errors["code"]) {
                        AppInput(
                            code,
                            { v ->
                                val digits = v.filter { it.isDigit() }.take(6)
                                val completed = digits.length == 6 && code.length < 6
                                code = digits
                                if (errors.isNotEmpty()) errors = emptyMap()
                                // Шестая цифра — проверяем сразу, без кнопки.
                                if (completed) checkCode()
                            },
                            placeholder = "Код из письма",
                            error = errors.containsKey("code"),
                            keyboardType = KeyboardType.NumberPassword,
                        )
                    }
                    // Таймер повторной отправки — текстом под полем; по истечении — ссылка.
                    Box(
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .pressable(enabled = secondsLeft <= 0 && !busy, pressAlpha = 0.6f) { sendCode() },
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (secondsLeft > 0) {
                            Text(resendCountdown(secondsLeft), style = AppFont.sans(T.FsBase, color = T.TextSecondary))
                        } else {
                            Text("Отправить код повторно", style = AppFont.sans(T.FsBase, FontWeight.SemiBold, color = T.Green))
                        }
                    }
                }
                RecoveryStep.Password -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AuthField(errors["newPassword"]) {
                        AppInput(
                            newPassword,
                            { newPassword = it; if (errors.isNotEmpty()) errors = emptyMap() },
                            placeholder = "Новый пароль",
                            isPassword = true,
                            error = errors.containsKey("newPassword"),
                        )
                    }
                    if (!errors.containsKey("newPassword")) {
                        PasswordHint(newPassword, Modifier.padding(start = 4.dp))
                    }
                }
            }
        }
    }
}
