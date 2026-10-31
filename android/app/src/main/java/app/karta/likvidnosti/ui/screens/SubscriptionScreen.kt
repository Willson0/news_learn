package app.karta.likvidnosti.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.karta.likvidnosti.BillingReturn
import app.karta.likvidnosti.data.Session
import app.karta.likvidnosti.data.AppData
import app.karta.likvidnosti.data.Repository
import app.karta.likvidnosti.data.api.BillingCardDto
import app.karta.likvidnosti.ui.components.AppButton
import app.karta.likvidnosti.ui.components.AppToast
import app.karta.likvidnosti.ui.components.AppToggle
import app.karta.likvidnosti.ui.components.ButtonVariant
import app.karta.likvidnosti.ui.components.pressable
import app.karta.likvidnosti.ui.components.SkeletonBox
import app.karta.likvidnosti.ui.components.HairlineDivider
import app.karta.likvidnosti.ui.components.InnerScreen
import app.karta.likvidnosti.ui.components.PurplePillButton
import app.karta.likvidnosti.ui.icons.AppIcon
import app.karta.likvidnosti.ui.icons.AppIcons
import app.karta.likvidnosti.ui.theme.AppFont
import app.karta.likvidnosti.ui.theme.T
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import app.karta.likvidnosti.data.api.onFailureNotCancelled
import app.karta.likvidnosti.ui.components.SlowNetworkHint

/**
 * «Управление подпиской» (iOS SubscriptionView, Figma 337:26959, привязка
 * 337:26981): заголовок на y=160; блок «Подписка / Тарифный план» (Bold 16,
 * статус SemiBold 16 зелёный или красный, «до …» 14 белый 65 %, линия 10 %);
 * через 12 — блок счёта 64 (белый круг 32 + «СБП 1488» 16, пурпурная пилюля
 * «Отвязать»; без счёта — круг с зелёным «+», «Новый счет», «Привязать»);
 * блок «Автопродление» со свитчем и датой следующего списания. После возврата
 * из Т-Кассы — «Проверяем платёж…», затем «Подписка активна до …» и «К материалам».
 */
/** Что с платежом после возврата из Т-Кассы. */
private sealed interface PaymentCheck {
    data object Checking : PaymentCheck
    data class Active(val until: String) : PaymentCheck
    data class Pending(val text: String) : PaymentCheck
    data class Failed(val text: String) : PaymentCheck
}

@Composable
fun SubscriptionScreen(onBack: () -> Unit, onToMaterials: () -> Unit = {}) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // Экран сразу открывается в состоянии пользователя (из кэша AppData); пока
    // данных нет совсем — плашки-скелетоны, а не «Неактивна» и выключенный свитч,
    // которые потом анимированно переключились бы.
    val cached = AppData.subscription ?: AppData.profile?.subscription
    var known by remember { mutableStateOf(cached != null) }
    var active by remember { mutableStateOf(cached?.active ?: false) }
    var until by remember { mutableStateOf(cached?.until ?: "") }
    var price by remember { mutableStateOf(cached?.price ?: "") }
    var autoPay by remember { mutableStateOf(cached?.autoPay ?: false) }
    var card by remember { mutableStateOf(AppData.card) }
    var paymentCheck by remember { mutableStateOf<PaymentCheck?>(null) }
    // Запрос оплаты ушёл: повторные нажатия «Привязать» ничего не делают (одна оплата).
    var cardBusy by remember { mutableStateOf(false) }
    // Статус не удалось получить, а сохранённого нет: честно «Нет связи», а не «Неактивна».
    var statusUnknown by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // Платёж ушёл, но итог неизвестен (сеть оборвалась): 15 минут «Привязать» не начинает
    // новую оплату, а проверяет эту.
    val prefs = remember { context.getSharedPreferences("kl_billing", android.content.Context.MODE_PRIVATE) }
    fun paymentUnsettled(): Boolean {
        val at = prefs.getLong("payment_check_at", 0L)
        return at > 0 && System.currentTimeMillis() - at < 15 * 60_000
    }
    fun markUnsettled(on: Boolean) {
        prefs.edit().apply { if (on) putLong("payment_check_at", System.currentTimeMillis()) else remove("payment_check_at") }.apply()
    }

    fun applySubscription(it: app.karta.likvidnosti.data.api.SubscriptionDto) {
        active = it.active
        until = it.until ?: ""
        price = it.price ?: ""
        autoPay = it.autoPay
        known = true
        statusUnknown = false
        AppData.subscription = it
        Session.setSubscription(it)
        if (it.active) markUnsettled(false)
    }

    suspend fun refreshCard() {
        runCatching { Repository.billingCard() }
            .onSuccess { card = it; AppData.card = it }
            // Не удалось узнать — показываем «Привязать карту», а не вечный скелетон.
            .onFailure { if (card == null) card = BillingCardDto() }
    }

    suspend fun loadStatus() {
        runCatching { Repository.subscription() }
            .onSuccess { sub -> if (sub != null) applySubscription(sub) else known = true }
            .onFailureNotCancelled { if (!known) statusUnknown = true }
    }

    LaunchedEffect(Unit) {
        loadStatus()
        refreshCard()
    }

    // Карта и статус могли измениться в браузере: перечитываем при возврате в приложение.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && paymentCheck == null) {
                scope.launch {
                    refreshCard()
                    runCatching { Repository.subscription() }.getOrNull()?.let { applySubscription(it) }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    /**
     * После оплаты списание приходит асинхронно (уведомлением банка): «Проверяем платёж…»
     * и опрос раз в 2,5 с до 30 с. Успех — «Подписка активна до …», иначе — понятный текст.
     */
    suspend fun checkPayment() {
        val wasActive = active
        paymentCheck = PaymentCheck.Checking
        var bound = false
        var reached = false
        val deadline = System.currentTimeMillis() + 30_000
        while (System.currentTimeMillis() < deadline) {
            // Запрос карты сам дожидается результата привязки и списывает подписку.
            refreshCard()
            bound = card?.bound == true
            val result = runCatching { Repository.subscription() }
            if (result.isSuccess) reached = true
            val sub = result.getOrNull()
            if (sub != null) applySubscription(sub)
            if (sub?.active == true && (!wasActive || bound)) {
                markUnsettled(false)
                paymentCheck = if (!wasActive) PaymentCheck.Active(sub.until ?: "") else null
                return
            }
            delay(2_500)
        }
        markUnsettled(true)
        paymentCheck = PaymentCheck.Pending(
            if (!reached) {
                "Нет связи с сервером, поэтому итог оплаты пока неизвестен. Повторно платить не нужно — проверьте ещё раз, когда появится интернет."
            } else if (bound) {
                "Карта привязана. Подписка включится, как только пройдёт списание или администратор назначит тариф — мы пришлём уведомление."
            } else {
                "Платёж ещё обрабатывается. Мы пришлём уведомление, как только он пройдёт."
            },
        )
    }

    fun startBind() {
        if (cardBusy) return
        // Итог прошлой оплаты ещё неизвестен — не предлагаем платить второй раз.
        if (paymentUnsettled()) {
            scope.launch { checkPayment() }
            return
        }
        cardBusy = true
        scope.launch {
            try {
                runCatching { Repository.bindCard() }
                    .onSuccess { url ->
                        runCatching {
                            CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url))
                        }.onFailure { AppToast.show("Не удалось открыть страницу оплаты") }
                    }
                    // Ошибка — тостом поверх: блоки ниже не сдвигаются.
                    .onFailureNotCancelled { AppToast.show(it.message ?: "Не удалось привязать карту") }
            } finally {
                cardBusy = false
            }
        }
    }

    // Возврат из Т-Кассы: karta://billing?status=…
    LaunchedEffect(Unit) {
        BillingReturn.results.collect { uri ->
            if (uri.getQueryParameter("status") == "error") {
                paymentCheck = PaymentCheck.Failed("Платёж не прошёл: его отменили или банк отклонил карту. Деньги не списаны.")
                refreshCard()
            } else {
                checkPayment()
            }
        }
    }

    val cardModifier = Modifier.fillMaxWidth().clip(T.ShapeCard).background(T.Surface)
    val cardLabel = card?.takeIf { it.bound }?.let { "Карта: ${it.brand ?: "Карта"} •••• ${it.last4 ?: ""}" }

    Box(Modifier.fillMaxSize()) {
    InnerScreen(onBack = onBack) {
        Text("Управление подпиской", style = AppFont.heading(T.FsTitle))

        Column(
            modifier = Modifier.padding(top = 24.dp).then(cardModifier).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Text("Подписка", style = AppFont.sans(T.FsMd, FontWeight.Bold))
                Spacer(Modifier.weight(1f).width(8.dp))
                if (!known && statusUnknown) {
                    Text(
                        "Нет связи · Повторить",
                        style = AppFont.sans(T.FsBase, FontWeight.SemiBold, T.TextSecondary),
                        modifier = Modifier.heightIn(min = 22.dp).pressable { statusUnknown = false; scope.launch { loadStatus() } },
                    )
                } else if (!known) {
                    SkeletonBox(Modifier.width(90.dp).height(20.dp))
                } else Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                if (!known) {
                    SkeletonBox(Modifier.width(110.dp).height(16.dp))
                } else {
                    Text(price.ifEmpty { "—" }, style = AppFont.sans(T.FsBase, color = T.TextSecondary))
                }
            }
        }

        Column(modifier = Modifier.padding(top = 12.dp).then(cardModifier).padding(horizontal = 16.dp)) {
            if (card == null) {
                // Карта ещё не известна: скелетон вместо «Привязать карту», которое сменилось бы картой.
                Row(
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SkeletonBox(Modifier.size(32.dp), shape = CircleShape)
                    SkeletonBox(Modifier.weight(1f).height(18.dp))
                    SkeletonBox(Modifier.width(96.dp).height(36.dp), shape = CircleShape)
                }
            } else Row(
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
                // Пилюля 32 в области нажатия 48 (визуально без изменений).
                Box(Modifier.heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
                    if (cardBusy) {
                        // Запрос идёт: индикатор на месте пилюли того же размера, повторный тап не шлёт второй запрос.
                        Box(Modifier.size(96.dp, 36.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = T.Text, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                        }
                    } else PurplePillButton(if (cardLabel == null) "Привязать" else "Отвязать") {
                        if (cardLabel == null) {
                            startBind()
                        } else {
                            cardBusy = true
                            scope.launch {
                                try {
                                    runCatching { Repository.unbindCard() }
                                        .onFailureNotCancelled { AppToast.show(it.message ?: "Не удалось отвязать карту") }
                                    refreshCard()
                                } finally {
                                    cardBusy = false
                                }
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.padding(top = 12.dp).then(cardModifier).height(64.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Автопродление", style = AppFont.sans(T.FsMd))
                // Когда и сколько спишется — видно до того, как это случится.
                if (known && autoPay && active && until.isNotEmpty()) {
                    Text(
                        "Следующее списание $until" + (if (price.isNotEmpty()) ", $price" else ""),
                        style = AppFont.sans(T.FsSm, color = T.TextSecondary),
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            if (!known) {
                SkeletonBox(Modifier.size(50.dp, 30.dp), shape = CircleShape)
            } else {
                AppToggle(checked = autoPay, onCheckedChange = { on ->
                    autoPay = on
                    scope.launch {
                        runCatching { Repository.updateSubscription(autoPay = on) }
                            .onSuccess { runCatching { Repository.subscription() }.getOrNull()?.let { AppData.subscription = it } }
                            .onFailure {
                                autoPay = !on
                                AppToast.show("Не удалось сохранить, попробуйте ещё раз")
                            }
                    }
                })
            }
        }
    }

    paymentCheck?.let { check ->
        PaymentCheckView(
            check = check,
            onToMaterials = { paymentCheck = null; onToMaterials() },
            onRetry = { paymentCheck = null; markUnsettled(false); startBind() },
            onRecheck = { scope.launch { checkPayment() } },
            onClose = { paymentCheck = null },
        )
    }
    SlowNetworkHint(active = !known && !statusUnknown)
    }
}

/** Полноэкранное состояние оплаты: проверка, успех, «ещё обрабатывается» или ошибка. */
@Composable
private fun PaymentCheckView(
    check: PaymentCheck,
    onToMaterials: () -> Unit,
    onRetry: () -> Unit,
    onRecheck: () -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(enabled = check !is PaymentCheck.Checking) { onClose() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(T.Background)
            .pressable(pressAlpha = 1f) {}
            .padding(horizontal = T.ScreenX),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (check) {
                PaymentCheck.Checking -> {
                    CircularProgressIndicator(color = T.Green, strokeWidth = 3.dp, modifier = Modifier.size(40.dp))
                    Text("Проверяем платёж…", style = AppFont.heading(T.FsTitle), textAlign = TextAlign.Center)
                    Text(
                        "Банк подтверждает оплату, обычно это несколько секунд",
                        style = AppFont.sans(T.FsBase, color = T.TextSecondary),
                        textAlign = TextAlign.Center,
                    )
                }
                is PaymentCheck.Active -> {
                    Text(
                        if (check.until.isNotEmpty()) "Подписка активна до ${check.until}" else "Подписка активна",
                        style = AppFont.heading(T.FsTitle),
                        textAlign = TextAlign.Center,
                    )
                    Text("Все материалы уже открыты", style = AppFont.sans(T.FsBase, color = T.TextSecondary), textAlign = TextAlign.Center)
                    AppButton("К материалам", modifier = Modifier.padding(top = 8.dp)) { onToMaterials() }
                }
                is PaymentCheck.Pending -> {
                    Text("Платёж обрабатывается", style = AppFont.heading(T.FsTitle), textAlign = TextAlign.Center)
                    Text(check.text, style = AppFont.sans(T.FsBase, color = T.TextSecondary), textAlign = TextAlign.Center)
                    AppButton("Понятно", modifier = Modifier.padding(top = 8.dp)) { onClose() }
                    AppButton("Проверить снова", variant = ButtonVariant.Secondary) { onRecheck() }
                }
                is PaymentCheck.Failed -> {
                    Text("Оплата не прошла", style = AppFont.heading(T.FsTitle), textAlign = TextAlign.Center)
                    Text(check.text, style = AppFont.sans(T.FsBase, color = T.TextSecondary), textAlign = TextAlign.Center)
                    AppButton("Попробовать снова", modifier = Modifier.padding(top = 8.dp)) { onRetry() }
                    AppButton("Закрыть", variant = ButtonVariant.Secondary) { onClose() }
                }
            }
        }
    }
}
