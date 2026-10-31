package app.karta.likvidnosti.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.karta.likvidnosti.data.api.ApiException
import app.karta.likvidnosti.data.api.NetStatus
import app.karta.likvidnosti.ui.theme.AppFont
import app.karta.likvidnosti.ui.theme.T
import app.karta.likvidnosti.ui.theme.statusBarTop
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Три состояния списка: загрузка (скелетоны на экране), пусто и ошибка.
 * Ошибка не выдаёт себя за «материалов нет»: «Нет соединения» и «Повторить».
 */
@Composable
fun LoadErrorState(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    text: String? = null,
) {
    // Текст по причине сбоя: нет интернета, сервер не ответил вовремя или упал.
    val (reasonTitle, reasonText) = when (NetStatus.lastFailure) {
        ApiException.Kind.Timeout -> "Сервер долго не отвечает" to "Соединение слишком медленное. Попробуйте ещё раз"
        ApiException.Kind.Server -> "Сервер временно недоступен" to "Мы уже разбираемся. Попробуйте чуть позже"
        else -> "Нет соединения" to "Проверьте интернет и попробуйте ещё раз"
    }
    val title = title ?: reasonTitle
    val text = text ?: reasonText
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = AppFont.sans(T.FsLg, FontWeight.SemiBold), textAlign = TextAlign.Center)
        Text(text, style = AppFont.sans(T.FsBase, color = T.TextSecondary), textAlign = TextAlign.Center)
        AppButton("Повторить", modifier = Modifier.padding(top = 8.dp).width(200.dp)) { onRetry() }
    }
}

/** Пустой результат: пояснение и, если есть, действие («Сбросить фильтры»). */
@Composable
fun EmptyState(
    text: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text, style = AppFont.sans(T.FsLg, color = T.TextSecondary), textAlign = TextAlign.Center)
        if (actionLabel != null) {
            AppButton(actionLabel, variant = ButtonVariant.Secondary, modifier = Modifier.width(220.dp)) { onAction() }
        }
    }
}

/** Плашка над списком из кэша, когда обновить не удалось: «Показаны данные от 14:05 · Повторить». */
@Composable
fun StaleDataBanner(savedAt: Long?, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val time = savedAt?.let { SimpleDateFormat("HH:mm", Locale("ru")).format(Date(it)) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .liquidGlass(RoundedCornerShape(24.dp))
            .pressable { onRetry() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            if (time != null) "Нет соединения. Показаны данные от $time" else "Нет соединения. Показаны сохранённые данные",
            style = AppFont.sans(T.FsBase),
            modifier = Modifier.weight(1f),
        )
        Text("Повторить", style = AppFont.sans(T.FsBase, FontWeight.SemiBold, color = T.Green), modifier = Modifier.padding(start = 12.dp))
    }
}

/**
 * Общий тост приложения: «Не удалось сохранить, попробуйте ещё раз» и т. п.
 * Показывается из любого экрана через `AppToast.show(...)`; хост — в AppRoot.
 */
object AppToast {
    var message by mutableStateOf<String?>(null)
        private set
    private var serial = 0
    var shownSerial by mutableStateOf(0)
        private set

    /** Сколько тост на экране: 3,5 с — успеть прочитать. */
    const val DURATION_MS = 3500L

    /** Тот же текст ещё на экране — не мигает и не складывается стопкой, только продлевается. */
    fun show(text: String) {
        message = text
        serial += 1
        shownSerial = serial
    }

    fun hide() { message = null }
}

@Composable
fun BoxScope.AppToastHost() {
    val serial = AppToast.shownSerial
    LaunchedEffect(serial) {
        if (AppToast.message == null) return@LaunchedEffect
        delay(AppToast.DURATION_MS)
        if (AppToast.shownSerial == serial) AppToast.hide()
    }
    AnimatedVisibility(
        visible = AppToast.message != null,
        modifier = Modifier.align(Alignment.TopCenter).padding(top = statusBarTop() + 76.dp, start = T.ScreenX, end = T.ScreenX),
        enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
        exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
    ) {
        var last by remember { mutableStateOf("") }
        AppToast.message?.let { last = it }
        Box(
            modifier = Modifier
                .heightIn(min = 32.dp)
                .liquidGlass(RoundedCornerShape(24.dp))
                .padding(horizontal = 12.dp, vertical = 7.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            contentAlignment = Alignment.Center,
        ) { Text(last, style = AppFont.sans(T.FsBase), textAlign = TextAlign.Center) }
    }
}

/**
 * Подсказка «Соединение медленное…»: появляется, если загрузка идёт дольше 8 с.
 * Рисуется поверх (в BoxScope) и ничего не сдвигает; по таймауту запроса её
 * сменяет экран ошибки с «Повторить».
 */
@Composable
fun BoxScope.SlowNetworkHint(active: Boolean, bottom: androidx.compose.ui.unit.Dp = 24.dp) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(active) {
        shown = false
        if (active) {
            delay(8000)
            shown = true
        }
    }
    AnimatedVisibility(
        visible = shown && active,
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = bottom),
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 40.dp)
                .liquidGlass(RoundedCornerShape(24.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            androidx.compose.material3.CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                color = T.Text,
                strokeWidth = 2.dp,
            )
            Text("Соединение медленное…", style = AppFont.sans(T.FsBase))
        }
    }
}
