package app.karta.likvidnosti.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.karta.likvidnosti.ui.components.Screen
import app.karta.likvidnosti.ui.components.ScreenHeader
import app.karta.likvidnosti.ui.theme.T

private data class AgreementSection(val title: String, val body: String)

private val SECTIONS = listOf(
    AgreementSection(
        "1. Общие положения",
        "Настоящее Пользовательское соглашение (далее — «Соглашение») регулирует отношения между " +
            "администрацией приложения «Карта Ликвидности» (далее — «Сервис») и физическим лицом " +
            "(далее — «Пользователь»), использующим Сервис. Используя Сервис, Пользователь подтверждает, " +
            "что ознакомился с условиями Соглашения и принимает их в полном объёме.",
    ),
    AgreementSection(
        "2. Предмет Соглашения",
        "Сервис предоставляет Пользователю доступ к аналитическим материалам, отчётам по финансовым " +
            "инструментам и возможностям сообщества. Материалы носят исключительно информационный характер " +
            "и не являются индивидуальной инвестиционной рекомендацией.",
    ),
    AgreementSection(
        "3. Учётная запись",
        "Для доступа к части функций требуется регистрация. Пользователь обязуется предоставлять достоверные " +
            "данные и несёт ответственность за сохранность своих учётных данных и за все действия, совершённые " +
            "под его учётной записью.",
    ),
    AgreementSection(
        "4. Подписка и оплата",
        "Отдельные возможности Сервиса предоставляются по платной подписке. Условия, стоимость и срок действия " +
            "подписки отображаются в приложении до оформления. Оплата означает согласие с выбранным тарифом.",
    ),
    AgreementSection(
        "5. Права и обязанности Пользователя",
        "Пользователь обязуется не нарушать работу Сервиса, не распространять материалы третьим лицам без " +
            "разрешения, соблюдать нормы общения в сообществе и действующее законодательство. Запрещено " +
            "размещать противоправный, оскорбительный или вводящий в заблуждение контент.",
    ),
    AgreementSection(
        "6. Ограничение ответственности",
        "Решения, принятые Пользователем на основе материалов Сервиса, принимаются им самостоятельно и на его " +
            "риск. Сервис не несёт ответственности за возможные убытки, возникшие в результате использования " +
            "представленной информации.",
    ),
    AgreementSection(
        "7. Персональные данные",
        "Сервис обрабатывает персональные данные Пользователя в объёме, необходимом для работы приложения, " +
            "и обеспечивает их защиту в соответствии с применимым законодательством. Используя Сервис, " +
            "Пользователь даёт согласие на такую обработку.",
    ),
    AgreementSection(
        "8. Изменение условий",
        "Администрация вправе изменять условия Соглашения. Актуальная редакция всегда доступна в приложении. " +
            "Продолжение использования Сервиса после внесения изменений означает согласие с новой редакцией.",
    ),
)

@Composable
fun UserAgreementScreen(onBack: () -> Unit) {
    Screen(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenHeader("Пользовательское соглашение", onBack = onBack)
        Text(
            "Редакция от 18.10.2026",
            color = T.TextMuted,
            fontSize = T.FsSm,
        )
        SECTIONS.forEach { section ->
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(section.title, color = T.Text, fontSize = T.FsMd, fontWeight = FontWeight.Bold)
                Text(section.body, color = T.TextSecondary, fontSize = T.FsBase, lineHeight = 20.sp)
            }
        }
    }
}
