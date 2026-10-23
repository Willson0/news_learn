package app.karta.likvidnosti.ui.screens

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.karta.likvidnosti.data.Repository
import app.karta.likvidnosti.data.api.DocumentDto
import app.karta.likvidnosti.ui.components.InnerScreen
import app.karta.likvidnosti.ui.theme.AppFont
import app.karta.likvidnosti.ui.theme.T

private data class AgreementSection(val title: String, val body: String)

private val SECTIONS = listOf(
    AgreementSection(
        "1. Общие положения",
        "Настоящее Пользовательское соглашение регулирует отношения между администрацией приложения «Карта ликвидности» и пользователем. Используя приложение, вы подтверждаете согласие с условиями соглашения. Если вы не согласны с ними, прекратите использование приложения.",
    ),
    AgreementSection(
        "2. Предмет соглашения",
        "Приложение предоставляет доступ к аналитическим материалам, отчётам и сообществу по финансовым инструментам. Материалы носят информационный характер и не являются индивидуальной инвестиционной рекомендацией.",
    ),
    AgreementSection(
        "3. Учётная запись",
        "Для доступа к части функций требуется регистрация. Пользователь обязуется предоставлять достоверные данные и несёт ответственность за сохранность пароля и за все действия в своей учётной записи.",
    ),
    AgreementSection(
        "4. Подписка и оплата",
        "Отдельные возможности предоставляются по платной подписке. Условия, стоимость и порядок автоматического продления отображаются в приложении до оформления подписки.",
    ),
    AgreementSection(
        "5. Контент и поведение",
        "Пользователь обязуется не публиковать в чатах противоправный, оскорбительный или рекламный контент. Администрация вправе удалять такие сообщения и ограничивать доступ нарушителям.",
    ),
    AgreementSection(
        "6. Ограничение ответственности",
        "Приложение и его материалы предоставляются «как есть». Администрация не несёт ответственности за инвестиционные решения пользователя и возможные убытки, связанные с использованием информации из приложения.",
    ),
    AgreementSection(
        "7. Персональные данные",
        "Обработка персональных данных осуществляется в соответствии с применимым законодательством и политикой конфиденциальности. Данные используются для работы сервиса и не передаются третьим лицам без законных оснований.",
    ),
    AgreementSection(
        "8. Изменение условий",
        "Администрация вправе изменять условия соглашения. Актуальная версия всегда доступна в приложении. Продолжение использования после изменений означает согласие с новой редакцией.",
    ),
)

/**
 * Пользовательское соглашение. Документы берутся из админки (GET /documents) и открываются
 * ссылками в браузере внутри приложения. Если список пуст или не загрузился, показываем встроенный текст.
 */
@Composable
fun UserAgreementScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var documents by remember { mutableStateOf<List<DocumentDto>>(emptyList()) }

    LaunchedEffect(Unit) {
        documents = runCatching { Repository.documents() }.getOrDefault(emptyList())
    }

    InnerScreen(onBack = onBack, spacing = 18.dp) {
        Text("Пользовательское соглашение", style = AppFont.heading(T.FsTitle))
        if (documents.isNotEmpty()) {
            documents.forEach { doc ->
                Text(
                    doc.title,
                    style = AppFont.sans(T.FsMd, FontWeight.Medium, color = T.Text),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(doc.url)) }
                        .padding(vertical = 8.dp),
                )
            }
        } else {
            Text("Последнее обновление: 17.10.2026", style = AppFont.sans(T.FsSm, color = T.TextMuted))
            SECTIONS.forEach { section ->
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(section.title, style = AppFont.sans(T.FsMd, FontWeight.Bold))
                    Text(section.body, style = AppFont.sans(T.FsBase, color = T.TextSecondary).copy(lineHeight = 20.sp))
                }
            }
        }
    }
}
