package app.karta.likvidnosti.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
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
import app.karta.likvidnosti.ui.components.AuthScaffold
import app.karta.likvidnosti.ui.components.ButtonVariant
import app.karta.likvidnosti.ui.components.pressable
import app.karta.likvidnosti.ui.theme.AppFont
import app.karta.likvidnosti.ui.theme.T
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
    var resending by remember { mutableStateOf(false) }
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
        // Один запрос — одно письмо: пока код отправляется, повторные нажатия ничего не делают.
        if (resendIn > 0 || submitting || resending) return
        resending = true
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
            } finally {
                resending = false
            }
        }
    }

    AuthScaffold(
        title = "Подтверждение почты",
        // Адрес целиком: его только что ввели, и опечатку должно быть видно.
        subtitle = "Мы отправили код подтверждения на $email. Введите его ниже",
        onBack = onBack,
        buttons = {
            AppButton(
                if (resendIn > 0) resendCountdown(resendIn) else "Отправить код повторно",
                variant = ButtonVariant.Secondary,
                enabled = resendIn <= 0,
                loading = resending,
            ) { resend() }
            AppButton("Далее", variant = ButtonVariant.Accent, loading = submitting) { confirm() }
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
        Box(
            modifier = Modifier.heightIn(min = 48.dp).pressable(pressAlpha = 0.6f) { onBack() },
            contentAlignment = Alignment.CenterStart,
        ) {
            Text("Изменить почту", style = AppFont.sans(T.FsBase, FontWeight.SemiBold, color = T.Green))
        }
    }
}
