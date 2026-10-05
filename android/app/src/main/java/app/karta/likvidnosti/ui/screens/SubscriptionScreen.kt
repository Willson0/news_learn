package app.karta.likvidnosti.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.karta.likvidnosti.data.Repository
import app.karta.likvidnosti.ui.components.AppToggle
import app.karta.likvidnosti.ui.components.Screen
import app.karta.likvidnosti.ui.components.ScreenHeader
import app.karta.likvidnosti.ui.icons.AppIcon
import app.karta.likvidnosti.ui.icons.AppIcons
import app.karta.likvidnosti.ui.theme.T
import kotlinx.coroutines.launch

@Composable
fun SubscriptionScreen(onBack: () -> Unit) {
    var active by remember { mutableStateOf(false) }
    var until by remember { mutableStateOf("") }
    var plan by remember { mutableStateOf("") }
    var account by remember { mutableStateOf<String?>(null) }
    var autoPay by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        runCatching { Repository.subscription() }.getOrNull()?.let {
            active = it.active; until = it.until ?: ""; plan = it.plan ?: ""
            autoPay = it.autoPay; account = it.paymentMethod
        }
    }

    Screen(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ScreenHeader("Управление подпиской", onBack = onBack)

        Column(modifier = Modifier.fillMaxWidth().clip(T.ShapeCard).background(T.Surface).padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Подписка", color = T.Text, fontSize = T.FsMd, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (active) "Активна" else "Неактивна", color = if (active) T.Green else T.Error, fontSize = T.FsBase, fontWeight = FontWeight.SemiBold)
                    if (active && until.isNotEmpty()) Text("до $until", color = T.TextSecondary, fontSize = T.FsBase)
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x14FFFFFF)).padding(vertical = 10.dp))
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Тарифный план", color = T.Text, fontSize = T.FsMd, fontWeight = FontWeight.SemiBold)
                Text(plan, color = T.TextSecondary, fontSize = T.FsBase)
            }
        }

        // Способ оплаты
        Row(
            modifier = Modifier.fillMaxWidth().clip(T.ShapeCard).background(T.Surface).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                if (account == null) AppIcon(AppIcons.Plus, size = 20.dp, tint = T.Bg)
            }
            Text(account ?: "Новый счет", color = T.Text, fontSize = T.FsMd, modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .clip(T.ShapePill)
                    .background(T.GradientPurpleSolid)
                    .clickable {
                        account = if (account == null) "СБП 1488" else null
                        scope.launch { runCatching { Repository.updateSubscription(paymentMethod = account) } }
                    }
                    .padding(horizontal = 18.dp, vertical = 9.dp),
            ) {
                Text(if (account != null) "Отвязать" else "Привязать", color = T.Text, fontSize = T.FsBase)
            }
        }

        // Авто-платёж
        Row(
            modifier = Modifier.fillMaxWidth().clip(T.ShapeCard).background(T.Surface).padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Авто-платеж", color = T.Text, fontSize = T.FsMd, fontWeight = FontWeight.SemiBold)
            AppToggle(checked = autoPay, onCheckedChange = {
                autoPay = it
                scope.launch { runCatching { Repository.updateSubscription(autoPay = it) } }
            })
        }
    }
}
