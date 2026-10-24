package app.karta.likvidnosti.ui.screens

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.karta.likvidnosti.BillingReturn
import app.karta.likvidnosti.data.Repository
import app.karta.likvidnosti.data.api.BillingCardDto
import app.karta.likvidnosti.ui.components.AppToggle
import app.karta.likvidnosti.ui.components.HairlineDivider
import app.karta.likvidnosti.ui.components.InnerScreen
import app.karta.likvidnosti.ui.components.PurplePillButton
import app.karta.likvidnosti.ui.icons.AppIcon
import app.karta.likvidnosti.ui.icons.AppIcons
import app.karta.likvidnosti.ui.theme.AppFont
import app.karta.likvidnosti.ui.theme.T
import kotlinx.coroutines.launch

/**
 * «Управление подпиской» (iOS SubscriptionView, Figma 337:26959, привязка
 * 337:26981): заголовок на y=160; блок «Подписка / Тарифный план» (Bold 16,
 * статус SemiBold 16 зелёный или красный, «до …» 14 белый 65 %, линия 10 %);
 * через 12 — блок счёта 64 (белый круг 32 + «СБП 1488» 16, пурпурная пилюля
 * «Отвязать»; без счёта — круг с зелёным «+», «Новый счет», «Привязать»);
 * блок «Авто-платеж» со свитчем.
 */
@Composable
fun SubscriptionScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var active by remember { mutableStateOf(false) }
    var until by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var autoPay by remember { mutableStateOf(false) }
    var card by remember { mutableStateOf<BillingCardDto?>(null) }
    var cardError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun refreshCard() {
        runCatching { Repository.billingCard() }.getOrNull()?.let { card = it }
    }

    LaunchedEffect(Unit) {
        runCatching { Repository.subscription() }.getOrNull()?.let {
            active = it.active
            until = it.until ?: ""
            price = it.price ?: ""
            autoPay = it.autoPay
            Session.setSubscription(it.active)
        }
        refreshCard()
    }

    // Карта могла быть привязана в браузере: перечитываем при возврате в приложение.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) scope.launch { refreshCard() }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Возврат из YooKassa: karta://billing?status=…
    LaunchedEffect(Unit) {
        BillingReturn.results.collect { uri ->
            cardError = if (uri.getQueryParameter("status") == "error") "Не удалось привязать карту" else null
            refreshCard()
        }
    }

    val cardModifier = Modifier.fillMaxWidth().clip(T.ShapeCard).background(T.Surface)
    val cardLabel = card?.takeIf { it.bound }?.let { "Карта: ${it.brand ?: "Карта"} •••• ${it.last4 ?: ""}" }

    InnerScreen(onBack = onBack) {
        Text("Управление подпиской", style = AppFont.heading(T.FsTitle))

        Column(
            modifier = Modifier.padding(top = 24.dp).then(cardModifier).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Text("Подписка", style = AppFont.sans(T.FsMd, FontWeight.Bold))
                Spacer(Modifier.weight(1f).width(8.dp))
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        if (active) "Активна" else "Неактивна",
                        style = AppFont.sans(T.FsMd, FontWeight.SemiBold, if (active) T.Green else T.Error),
                    )
                    if (active && until.isNotEmpty()) {
                        Text("до $until", style = AppFont.sans(T.FsBase, color = T.TextSecondary))
                    }
                }
            }
            HairlineDivider()
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Тарифный план", style = AppFont.sans(T.FsMd, FontWeight.Bold))
                Spacer(Modifier.weight(1f).width(8.dp))
                Text(price.ifEmpty { "—" }, style = AppFont.sans(T.FsBase, color = T.TextSecondary))
            }
        }

        Column(modifier = Modifier.padding(top = 12.dp).then(cardModifier).padding(horizontal = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().height(64.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier.size(32.dp).clip(CircleShape).background(Color.White),
                    contentAlignment = Alignment.Center,
                ) {
                    if (cardLabel == null) AppIcon(AppIcons.plus, size = 20.dp, tint = T.Green)
                }
                Text(cardLabel ?: "Привязать карту", style = AppFont.sans(T.FsMd), modifier = Modifier.weight(1f))
                PurplePillButton(if (cardLabel == null) "Привязать" else "Отвязать") {
                    cardError = null
                    scope.launch {
                        if (cardLabel == null) {
                            runCatching { Repository.bindCard() }
                                .onSuccess { url ->
                                    runCatching {
                                        CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url))
                                    }.onFailure { cardError = "Не удалось открыть страницу YooKassa" }
                                }
                                .onFailure { cardError = it.message ?: "Не удалось привязать карту" }
                        } else {
                            runCatching { Repository.unbindCard() }
                                .onFailure { cardError = it.message ?: "Не удалось отвязать карту" }
                            refreshCard()
                        }
                    }
                }
            }
            cardError?.let {
                Text(
                    it,
                    style = AppFont.sans(T.FsBase, color = T.Error),
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
        }

        Row(
            modifier = Modifier.padding(top = 12.dp).then(cardModifier).height(64.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Авто-платеж", style = AppFont.sans(T.FsMd), modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            AppToggle(checked = autoPay, onCheckedChange = {
                autoPay = it
                scope.launch { runCatching { Repository.updateSubscription(autoPay = it) } }
            })
        }
    }
}
