package app.karta.likvidnosti.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import app.karta.likvidnosti.data.Repository
import app.karta.likvidnosti.data.api.ApiException
import app.karta.likvidnosti.ui.components.AppButton
import app.karta.likvidnosti.ui.components.AppInput
import app.karta.likvidnosti.ui.components.AuthField
import app.karta.likvidnosti.ui.components.AuthScaffold
import app.karta.likvidnosti.ui.components.ButtonVariant
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** «Повторное сообщение через m:ss». */
internal fun resendCountdown(seconds: Int): String =
    "Повторное сообщение через ${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

/**
 * Подтверждение почты (iOS RegisterConfirmView): отдельного кадра в макете нет,
 * экран собран на каркасе «Восстановления пароля» — заголовок на y=234, поле
 * кода, внизу «Повторное сообщение через m:ss» и «Далее». После подтверждения —
 * шаг 2 регистрации (имя, тег, фото).
 */
@Composable
fun RegisterConfirmScreen(
    email: String,
    onConfirmed: () -> Unit,
    onBack: () -> Unit,
) {
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    var resendIn by remember { mutableStateOf(60) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (email.isEmpty()) onBack()
    }
    LaunchedEffect(resendIn) {
        if (resendIn > 0) {
            delay(1000)
            resendIn -= 1
        }
    }

    val masked = remember(email) {
        val parts = email.split("@", limit = 2)
        if (parts.size != 2) {
            email
        } else {
            val name = parts[0]
            val head = if (name.length <= 2) name else name.take(2) + "***"
            "$head@${parts[1]}"
        }
    }

    fun confirm() {
        if (code.length < 6) {
            error = "Введите код из 6 цифр"
            return
        }
        if (submitting) return
        submitting = true
        scope.launch {
            try {
                Repository.confirmRegistration(email, code)
                onConfirmed()
            } catch (ex: ApiException) {
                error = ex.firstError()
            } catch (ex: Exception) {
                error = "Не удалось подтвердить код"
            } finally {
                submitting = false
            }
        }
    }

    fun resend() {
        if (resendIn > 0 || submitting) return
        scope.launch {
            try {
                Repository.resendRegistration(email)
                code = ""
                error = null
                resendIn = 60
            } catch (ex: ApiException) {
                error = ex.firstError()
            } catch (ex: Exception) {
                error = "Не удалось отправить код"
            }
        }
    }

    AuthScaffold(
        title = "Подтверждение почты",
        subtitle = "Мы отправили код подтверждения на $masked. Введите его ниже",
        onBack = onBack,
        buttons = {
            AppButton(
                if (resendIn > 0) resendCountdown(resendIn) else "Отправить код повторно",
                variant = ButtonVariant.Secondary,
                enabled = resendIn <= 0,
            ) { resend() }
            AppButton("Далее", variant = ButtonVariant.Accent, enabled = !submitting) { confirm() }
        },
    ) {
        AuthField(error) {
            AppInput(
                code,
                { v ->
                    code = v.filter { it.isDigit() }.take(6)
                    error = null
                },
                placeholder = "Код из письма",
                error = error != null,
                keyboardType = KeyboardType.NumberPassword,
            )
        }
    }
}
