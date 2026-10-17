package app.karta.likvidnosti.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.karta.likvidnosti.data.Repository
import app.karta.likvidnosti.data.api.ApiException
import app.karta.likvidnosti.ui.components.AppButton
import app.karta.likvidnosti.ui.components.AppInput
import app.karta.likvidnosti.ui.components.ButtonVariant
import app.karta.likvidnosti.ui.components.FieldError
import app.karta.likvidnosti.ui.components.ScreenTitle
import app.karta.likvidnosti.ui.components.authBackground
import app.karta.likvidnosti.ui.components.insetBottom
import app.karta.likvidnosti.ui.components.insetTop
import app.karta.likvidnosti.ui.theme.T
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val EMAIL_RE = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
private const val RESEND_SECONDS = 5 * 60

@Composable
fun PasswordRecoveryScreen(
    onDone: () -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var errors by remember { mutableStateOf(mapOf<String, String>()) }
    var codeSent by remember { mutableStateOf(false) }
    var secondsLeft by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(secondsLeft) {
        if (secondsLeft > 0) { delay(1000); secondsLeft -= 1 }
    }

    val canResend = secondsLeft <= 0
    val resendLabel = if (canResend) {
        if (codeSent) "Отправить код повторно" else "Отправить код"
    } else {
        val m = secondsLeft / 60
        val s = (secondsLeft % 60).toString().padStart(2, '0')
        "Повторное сообщение через $m:$s"
    }

    fun resend() {
        if (!canResend) return
        if (!EMAIL_RE.matches(email.trim())) {
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

    fun submit() {
        val e = mutableMapOf<String, String>()
        if (!EMAIL_RE.matches(email.trim())) e["email"] = "Неверный формат email"
        if (code.length < 6) e["code"] = "Введите код из 6 цифр из письма"
        if (newPassword.length < 8) e["newPassword"] = "Пароль должен содержать от 8 символов"
        errors = e
        if (e.isNotEmpty()) return
        scope.launch {
            try {
                Repository.resetPassword(email.trim(), newPassword, code)
                onDone()
            } catch (ex: ApiException) {
                val mapped = ex.errors.entries.firstOrNull()?.let { (k, v) ->
                    val key = if (k == "password") "newPassword" else k
                    key to (v.firstOrNull() ?: ex.message)
                }
                errors = if (mapped != null) mapOf(mapped.first to mapped.second) else mapOf("email" to ex.message)
            } catch (ex: Exception) {
                errors = mapOf("email" to "Не удалось сменить пароль")
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .authBackground()
            .imePadding()
            .padding(start = T.ScreenX, end = T.ScreenX, top = insetTop() + 24.dp, bottom = insetBottom() + 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.size(76.dp))
        ScreenTitle("Восстановление пароля", modifier = Modifier.padding(bottom = 12.dp))
        Text(
            "Отправим код на указанную почту. Введите его вместе с новым паролем — позднее пароль можно сменить в профиле.",
            color = T.TextSecondary, fontSize = T.FsSm, textAlign = TextAlign.Center, lineHeight = 17.sp,
            modifier = Modifier.padding(bottom = 28.dp),
        )

        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AppInput(email, { email = it }, placeholder = "Email", keyboardType = KeyboardType.Email, error = errors.containsKey("email"))
                FieldError(errors["email"])
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AppInput(
                    code,
                    { code = it.filter(Char::isDigit).take(6) },
                    placeholder = "Код из письма",
                    keyboardType = KeyboardType.Number,
                    error = errors.containsKey("code"),
                )
                FieldError(errors["code"])
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AppInput(newPassword, { newPassword = it }, placeholder = "Новый пароль", isPassword = true, error = errors.containsKey("newPassword"))
                FieldError(errors["newPassword"])
            }
        }

        Spacer(Modifier.weight(1f))
        Column(modifier = Modifier.fillMaxWidth().padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AppButton(resendLabel, variant = ButtonVariant.Secondary, enabled = canResend) { resend() }
            AppButton("Далее", variant = ButtonVariant.Accent) { submit() }
        }
    }
}
