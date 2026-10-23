package app.karta.likvidnosti.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
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
import app.karta.likvidnosti.ui.components.AuthFieldError
import app.karta.likvidnosti.ui.components.AuthScaffold
import app.karta.likvidnosti.ui.components.ButtonVariant
import kotlinx.coroutines.launch

private data class ChangeField(val key: String, val placeholder: String, val secure: Boolean, val keyboard: KeyboardType)

/**
 * «Смена пароля / номера / почты» (iOS AccountChangeView, Figma 363:9417,
 * 363:9499, 363:9459): заголовок Kreadon 24 по центру на y=278, поля 48 через
 * 12 с отступом 24, внизу кнопка (низ — 45). Номер телефона — с маской
 * «+7 (999) 999-99-99».
 */
@Composable
fun AccountChangeScreen(
    field: String,
    onBack: () -> Unit,
    onSuccess: (String) -> Unit,
) {
    val title: String
    val button: String
    val fields: List<ChangeField>
    when (field) {
        "phone" -> {
            title = "Смена номера телефона"
            button = "Сменить номер"
            fields = listOf(
                ChangeField("old", "Прежний номер телефона", false, KeyboardType.Phone),
                ChangeField("new", "Новый номер телефона", false, KeyboardType.Phone),
            )
        }
        "password" -> {
            title = "Смена пароля"
            button = "Сменить пароль"
            fields = listOf(
                ChangeField("new", "Новый пароль", true, KeyboardType.Password),
                ChangeField("confirm", "Подтвердите пароль", true, KeyboardType.Password),
            )
        }
        else -> {
            title = "Смена почты"
            button = "Сменить почту"
            fields = listOf(
                ChangeField("old", "Прежняя почта", false, KeyboardType.Email),
                ChangeField("new", "Новая почта", false, KeyboardType.Email),
            )
        }
    }

    var values by remember { mutableStateOf(mapOf<String, String>()) }
    var error by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun value(key: String) = values[key] ?: ""

    fun submit() {
        error = null
        if (field == "password") {
            if (value("new").length < 8) {
                error = "Пароль должен содержать от 8 символов"
                return
            }
            if (value("new") != value("confirm")) {
                error = "Пароль не совпадает"
                return
            }
        }
        if (submitting) return
        submitting = true
        scope.launch {
            try {
                when (field) {
                    "email" -> Repository.changeEmail(value("new").trim())
                    "phone" -> Repository.changePhone(value("new").trim())
                    else -> Repository.changePassword(value("new"), value("confirm"), values["old"])
                }
                onSuccess(field)
            } catch (ex: ApiException) {
                error = ex.message
            } catch (ex: Exception) {
                error = "Не удалось сохранить изменения"
            } finally {
                submitting = false
            }
        }
    }

    AuthScaffold(
        title = title,
        onBack = onBack,
        contentTop = 278.dp,
        buttons = {
            AppButton(button, variant = ButtonVariant.Accent, loading = submitting) { submit() }
        },
    ) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            fields.forEach { f ->
                AppInput(
                    value(f.key),
                    { v ->
                        values = values + (f.key to if (field == "phone") PhoneMask.format(v) else v)
                        error = null
                    },
                    placeholder = f.placeholder,
                    isPassword = f.secure,
                    error = error != null,
                    keyboardType = f.keyboard,
                )
            }
            error?.let { AuthFieldError(it) }
        }
    }
}
