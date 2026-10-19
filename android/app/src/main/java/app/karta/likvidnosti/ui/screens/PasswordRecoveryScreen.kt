package app.karta.likvidnosti.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val RESEND_SECONDS = 5 * 60

/**
 * «Восстановление пароля» (iOS PasswordRecoveryView, Figma 349:10487 … 349:10715):
 * почта и новый пароль; «Далее» отправляет письмо с кодом и запускает таймер
 * повторной отправки 5:00, после чего кнопка становится «Сменить пароль».
 * Бэкенд подтверждает смену кодом из письма, поэтому после отправки появляется
 * поле кода. Успех — «Вы успешно сменили пароль», затем вход в приложение.
 */
@Composable
fun PasswordRecoveryScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var errors by remember { mutableStateOf(mapOf<String, String>()) }
    var codeSent by remember { mutableStateOf(false) }
    var secondsLeft by remember { mutableStateOf(0) }
    var submitting by remember { mutableStateOf(false) }
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

    val canResend = secondsLeft <= 0
    val resendLabel = when {
        !canResend -> resendCountdown(secondsLeft)
        codeSent -> "Отправить код повторно"
        else -> "Отправить код"
    }

    fun resend() {
        if (!canResend) return
        if (!AUTH_EMAIL_RE.matches(email.trim())) {
            errors = mapOf("email" to "Введите email, чтобы отправить код")
            return
        }
        scope.launch {
            try {
                Repository.requestRecovery(email.trim())
                codeSent = true
                secondsLeft = RESEND_SECONDS
            } catch (ex: ApiException) {
                errors = mapOf("email" to ex.message)
            } catch (ex: Exception) {
                errors = mapOf("email" to "Не удалось отправить код")
            }
        }
    }

    /** Первый шаг: проверяем поля и отправляем письмо с кодом. */
    fun next() {
        val e = mutableMapOf<String, String>()
        if (!AUTH_EMAIL_RE.matches(email.trim())) e["email"] = "Несуществующий email"
        if (newPassword.length < 8) e["newPassword"] = "Пароль должен содержать от 8 символов"
        errors = e
        if (e.isEmpty()) resend()
    }

    fun submit() {
        val e = mutableMapOf<String, String>()
        if (!AUTH_EMAIL_RE.matches(email.trim())) e["email"] = "Несуществующий email"
        if (code.length < 6) e["code"] = "Введите код из 6 цифр из письма"
        if (newPassword.length < 8) e["newPassword"] = "Пароль должен содержать от 8 символов"
        errors = e
        if (e.isNotEmpty() || submitting) return
        submitting = true
        scope.launch {
            try {
                Repository.resetPassword(email.trim(), newPassword, code)
                done = true
            } catch (ex: ApiException) {
                val mapped = ex.errors.entries.associate { (k, v) ->
                    (if (k == "password") "newPassword" else k) to (v.firstOrNull() ?: ex.message)
                }
                errors = mapped.ifEmpty { mapOf("email" to ex.message) }
            } catch (ex: Exception) {
                errors = mapOf("email" to "Не удалось сменить пароль")
            } finally {
                submitting = false
            }
        }
    }

    AuthScaffold(
        title = "Восстановление пароля",
        subtitle = "Временный пароль придет на указанную почту, позднее пароль можно сменить в профиле",
        onBack = onBack,
        buttons = {
            AppButton(resendLabel, variant = ButtonVariant.Secondary, enabled = canResend) { resend() }
            AppButton(
                if (codeSent) "Сменить пароль" else "Далее",
                variant = ButtonVariant.Accent,
                enabled = !submitting,
            ) { if (codeSent) submit() else next() }
        },
    ) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AuthField(errors["email"]) {
                AppInput(
                    email,
                    { email = it },
                    placeholder = "Email",
                    error = errors.containsKey("email"),
                    keyboardType = KeyboardType.Email,
                )
            }
            AuthField(errors["newPassword"]) {
                AppInput(
                    newPassword,
                    { newPassword = it },
                    placeholder = "Новый пароль",
                    isPassword = true,
                    error = errors.containsKey("newPassword"),
                )
            }
            AnimatedVisibility(
                visible = codeSent,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                AuthField(errors["code"]) {
                    AppInput(
                        code,
                        { v ->
                            code = v.filter { it.isDigit() }.take(6)
                            if (errors.containsKey("code")) errors = errors - "code"
                        },
                        placeholder = "Код из письма",
                        error = errors.containsKey("code"),
                        keyboardType = KeyboardType.NumberPassword,
                    )
                }
            }
        }
    }
}
