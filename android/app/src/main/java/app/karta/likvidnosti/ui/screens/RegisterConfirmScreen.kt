package app.karta.likvidnosti.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
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
        val parts = email.split("@")
        if (parts.size != 2) email
        else {
            val name = parts[0]
            val head = if (name.length <= 2) name else name.take(2) + "***"
            "$head@${parts[1]}"
        }
    }

    fun confirm() {
        if (code.length < 6) { error = "Введите код из 6 цифр"; return }
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
            runCatching { Repository.resendRegistration(email) }
            code = ""; error = null; resendIn = 60
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .authBackground()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(start = T.ScreenX, end = T.ScreenX, top = insetTop() + 40.dp, bottom = insetBottom() + 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 8.dp, bottom = 28.dp)
                .size(72.dp)
                .clip(CircleShape)
                .background(androidx.compose.ui.graphics.Color(0x0AFFFFFF))
                .border(1.dp, androidx.compose.ui.graphics.Color(0x0FFFFFFF), CircleShape),
        )

        ScreenTitle("Подтверждение почты", modifier = Modifier.padding(bottom = 12.dp))
        Text(
            "Мы отправили код подтверждения на $masked. Введите его ниже.",
            color = T.TextSecondary, fontSize = T.FsSm, textAlign = TextAlign.Center, lineHeight = 18.sp,
            modifier = Modifier.padding(bottom = 28.dp),
        )

        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AppInput(
                value = code,
                onValueChange = { v -> code = v.filter { it.isDigit() }.take(6); error = null },
                placeholder = "Код из письма",
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                error = error != null,
            )
            FieldError(error)
        }

        Text(
            if (resendIn > 0) "Отправить код повторно ($resendIn)" else "Отправить код повторно",
            color = T.TextSecondary, fontSize = T.FsSm,
            modifier = Modifier
                .padding(top = 16.dp)
                .align(Alignment.Start)
                .alpha(if (resendIn > 0) 0.5f else 1f)
                .clickable { resend() },
        )

        Spacer(Modifier.weight(1f))
        Spacer(Modifier.size(32.dp))
        AppButton("Подтвердить", modifier = Modifier.fillMaxWidth(), variant = ButtonVariant.Accent, enabled = !submitting) { confirm() }
    }
}
