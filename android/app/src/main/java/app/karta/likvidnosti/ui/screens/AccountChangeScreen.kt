package app.karta.likvidnosti.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import app.karta.likvidnosti.data.Repository
import app.karta.likvidnosti.data.api.ApiException
import app.karta.likvidnosti.ui.components.AppButton
import app.karta.likvidnosti.ui.components.AppInput
import app.karta.likvidnosti.ui.components.ButtonVariant
import app.karta.likvidnosti.ui.components.FieldError
import app.karta.likvidnosti.ui.components.Screen
import app.karta.likvidnosti.ui.components.ScreenHeader
import app.karta.likvidnosti.ui.theme.T
import kotlinx.coroutines.launch

@Composable
fun AccountChangeScreen(
    field: String,
    onBack: () -> Unit,
    onSuccess: (String) -> Unit,
) {
    val title = when (field) {
        "phone" -> "Смена номера телефона"
        "password" -> "Смена пароля"
        else -> "Смена почты"
    }
    val button = when (field) {
        "phone" -> "Сменить номер"
        "password" -> "Сменить пароль"
        else -> "Сменить почту"
    }
    var oldValue by remember { mutableStateOf("") }
    var newValue by remember { mutableStateOf("") }
    var confirmValue by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun submit() {
        error = null
        scope.launch {
            try {
                when (field) {
                    "email" -> Repository.changeEmail(newValue.trim())
                    "phone" -> Repository.changePhone(newValue.trim())
                    else -> Repository.changePassword(newValue, confirmValue, oldValue)
                }
                onSuccess(field)
            } catch (ex: ApiException) {
                error = ex.message
            } catch (ex: Exception) {
                error = "Не удалось сохранить изменения"
            }
        }
    }

    Screen(scroll = false) {
        ScreenHeader("", onBack = onBack)
        Spacer(Modifier.size(40.dp))
        Text(title, color = T.Text, fontSize = T.FsTitle, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.size(16.dp))
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (field) {
                "password" -> {
                    AppInput(newValue, { newValue = it }, placeholder = "Новый пароль", isPassword = true)
                    AppInput(confirmValue, { confirmValue = it }, placeholder = "Подтвердите пароль", isPassword = true)
                }
                "phone" -> {
                    AppInput(oldValue, { oldValue = it }, placeholder = "Прежний номер телефона", keyboardType = KeyboardType.Phone)
                    AppInput(newValue, { newValue = it }, placeholder = "Новый номер телефона", keyboardType = KeyboardType.Phone)
                }
                else -> {
                    AppInput(oldValue, { oldValue = it }, placeholder = "Прежняя почта", keyboardType = KeyboardType.Email)
                    AppInput(newValue, { newValue = it }, placeholder = "Новая почта", keyboardType = KeyboardType.Email)
                }
            }
            FieldError(error)
        }
        Spacer(Modifier.weight(1f))
        AppButton(button, modifier = Modifier.fillMaxWidth().imePadding(), variant = ButtonVariant.Accent) { submit() }
    }
}
