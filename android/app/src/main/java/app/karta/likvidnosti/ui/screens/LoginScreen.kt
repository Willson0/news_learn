package app.karta.likvidnosti.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.karta.likvidnosti.data.Repository
import app.karta.likvidnosti.data.api.ApiException
import app.karta.likvidnosti.ui.components.AppButton
import app.karta.likvidnosti.ui.components.AppInput
import app.karta.likvidnosti.ui.components.ButtonVariant
import app.karta.likvidnosti.ui.components.FieldError
import app.karta.likvidnosti.ui.theme.T
import kotlinx.coroutines.launch

private val EMAIL_RE = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
private val PHONE_RE = Regex("^\\+?[0-9\\s\\-()]{7,}$")

@Composable
fun LoginScreen(
    onAuthed: () -> Unit,
    onRecovery: () -> Unit,
    onNeedConfirm: (String) -> Unit,
) {
    var mode by remember { mutableStateOf("login") }
    var identifier by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordConfirm by remember { mutableStateOf("") }
    var errors by remember { mutableStateOf(mapOf<String, String>()) }
    var submitting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun setMode(m: String) {
        if (mode == m) return
        mode = m
        errors = emptyMap()
    }

    fun validate(): Boolean {
        val e = mutableMapOf<String, String>()
        if (mode == "login") {
            val id = identifier.trim()
            if (id.isEmpty()) e["identifier"] = "Введите email или номер телефона"
            else if (!EMAIL_RE.matches(id) && !PHONE_RE.matches(id)) e["identifier"] = "Неверный формат email или телефона"
            if (password.isEmpty()) e["password"] = "Введите пароль"
        } else {
            if (!EMAIL_RE.matches(email.trim())) e["email"] = "Неверный формат email"
            if (!PHONE_RE.matches(phone.trim())) e["phone"] = "Неверный формат телефона"
            if (password.length < 8) e["password"] = "Пароль должен содержать от 8 символов"
            if (passwordConfirm != password) e["passwordConfirm"] = "Пароль не совпадает"
        }
        errors = e
        return e.isEmpty()
    }

    fun submit() {
        if (!validate()) return
        submitting = true
        scope.launch {
            try {
                if (mode == "login") {
                    Repository.login(identifier.trim(), password)
                    onAuthed()
                } else {
                    val em = email.trim()
                    Repository.register(em, phone.trim(), password)
                    onNeedConfirm(em)
                }
            } catch (ex: ApiException) {
                val mapped = ex.errors.mapValues { it.value.firstOrNull() ?: "" }
                errors = if (mapped.isNotEmpty()) mapped
                else mapOf("identifier" to ex.message, "email" to ex.message)
            } catch (ex: Exception) {
                errors = mapOf("identifier" to "Не удалось выполнить запрос")
            } finally {
                submitting = false
            }
        }
    }

    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Color(0xFF1A1B1D), Color(0xFF1E1F21), Color(0xFF161719))),
            ),
    ) {
        // Декоративное свечение сверху
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .size(width = 1.dp, height = 220.dp)
                .background(
                    Brush.radialGradient(
                        0f to Color(0x59782850),
                        1f to Color(0x00000000),
                        center = androidx.compose.ui.geometry.Offset(900f, 60f),
                        radius = 700f,
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(start = T.ScreenX, end = T.ScreenX, top = top + 24.dp, bottom = bottom + 24.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text("16+", color = T.TextMuted, fontSize = T.FsTitle, fontWeight = FontWeight.Bold)
            }

            Box(
                modifier = Modifier
                    .padding(top = 24.dp)
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(Color(0x0AFFFFFF))
                    .border(1.dp, Color(0x0FFFFFFF), CircleShape)
                    .align(Alignment.CenterHorizontally),
            )

            Row(
                modifier = Modifier
                    .padding(top = 28.dp, bottom = 24.dp)
                    .align(Alignment.CenterHorizontally),
            ) {
                ToggleWord("Вход", mode == "login") { setMode("login") }
                Text(" / ", color = T.TextMuted, fontSize = T.FsTitle, fontWeight = FontWeight.Bold)
                ToggleWord("Регистрация", mode == "register") { setMode("register") }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (mode == "login") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        AppInput(identifier, { identifier = it }, placeholder = "Email или номер телефона", error = errors.containsKey("identifier"))
                        FieldError(errors["identifier"])
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        AppInput(password, { password = it }, placeholder = "Пароль", isPassword = true, error = errors.containsKey("password"))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            FieldError(errors["password"], modifier = Modifier.weight(1f, fill = false))
                            Spacer(Modifier.weight(1f))
                            Text("Забыли пароль?", color = T.TextSecondary, fontSize = T.FsSm, modifier = Modifier.clickable { onRecovery() })
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        AppInput(email, { email = it }, placeholder = "Email", error = errors.containsKey("email"))
                        FieldError(errors["email"])
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        AppInput(phone, { phone = it }, placeholder = "Номер телефона", error = errors.containsKey("phone"))
                        FieldError(errors["phone"])
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        AppInput(password, { password = it }, placeholder = "Пароль", isPassword = true, error = errors.containsKey("password"))
                        Text(
                            "Пароль должен содержать от 8 символов",
                            color = if (errors.containsKey("password")) T.Error else T.TextSecondary,
                            fontSize = T.FsSm,
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        AppInput(passwordConfirm, { passwordConfirm = it }, placeholder = "Подтвердите пароль", isPassword = true, error = errors.containsKey("passwordConfirm"))
                        FieldError(errors["passwordConfirm"])
                    }
                }
            }

            Spacer(Modifier.size(32.dp))

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AppButton("Войти с Yandex", variant = ButtonVariant.Secondary) {}
                AppButton(
                    if (mode == "login") "Войти" else "Далее",
                    variant = ButtonVariant.Accent,
                    enabled = !submitting,
                ) { submit() }
            }
        }
    }
}

@Composable
private fun ToggleWord(text: String, active: Boolean, onClick: () -> Unit) {
    Text(
        text,
        color = if (active) T.Text else T.TextMuted,
        fontSize = T.FsTitle,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.clickable(onClick = onClick),
    )
}
