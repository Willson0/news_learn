package app.karta.likvidnosti.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.karta.likvidnosti.data.AppData
import app.karta.likvidnosti.data.Repository
import app.karta.likvidnosti.ui.components.figmaFade
import app.karta.likvidnosti.ui.components.AppToggle
import app.karta.likvidnosti.ui.components.FlowLayout
import app.karta.likvidnosti.ui.components.InnerScreen
import app.karta.likvidnosti.ui.components.InstrumentChip
import app.karta.likvidnosti.ui.components.SkeletonBox
import app.karta.likvidnosti.ui.theme.AppFont
import app.karta.likvidnosti.ui.theme.T
import kotlinx.coroutines.launch

private data class NotifInstrument(val key: String, val label: String, val on: Boolean)

/**
 * «Профиль push-уведомления» (iOS NotificationsView, Figma 337:26945,
 * 337:27005, 349:8338): заголовок Kreadon 24 на y=160, блок свитчей
 * (#25262A, радиус 24, отступы 16/12, строки 43, Min Sans 14), через 40 —
 * «Уведомления по инструментам», подпись 12 белый 65 %, блок чипов (отступ
 * 12, шаг 4). «Только по инструментам» показывается, пока не включены все
 * уведомления; без них блок чипов приглушён.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NotificationsScreen(onBack: () -> Unit) {
    // Сразу в состоянии пользователя (кэш AppData): свитчи не переключаются
    // анимацией из значений по умолчанию. Без кэша — скелетоны до ответа сервера.
    val cached = AppData.profile
    var known by remember { mutableStateOf(cached != null) }
    var all by remember { mutableStateOf(cached?.notifications?.all ?: true) }
    var byInstrument by remember { mutableStateOf(cached?.notifications?.byInstrument ?: false) }
    val instruments = remember {
        mutableStateListOf<NotifInstrument>().apply {
            cached?.instruments?.let { list -> addAll(list.map { NotifInstrument(it.key, it.label, it.on) }) }
        }
    }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        runCatching { Repository.profile() }.getOrNull()?.let { p ->
            AppData.profile = p
            all = p.notifications?.all ?: true
            byInstrument = p.notifications?.byInstrument ?: false
            val fresh = p.instruments.map { NotifInstrument(it.key, it.label, it.on) }
            if (fresh != instruments.toList()) {
                instruments.clear()
                instruments.addAll(fresh)
            }
        }
        known = true
    }

    fun saveInstruments() {
        scope.launch {
            runCatching { Repository.syncInstruments(instruments.filter { it.on }.map { it.key }) }
            AppData.refreshProfile()
        }
    }

    fun saveNotifications(all: Boolean? = null, byInstrument: Boolean? = null) {
        scope.launch {
            runCatching { Repository.updateNotifications(all = all, byInstrument = byInstrument) }
            AppData.refreshProfile()
        }
    }

    InnerScreen(onBack = onBack) {
        Text("Уведомления", style = AppFont.heading(T.FsTitle))

        Column(
            modifier = Modifier
                .padding(top = 24.dp)
                .fillMaxWidth()
                .clip(T.ShapeCard)
                .background(T.Surface)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            if (!known) {
                repeat(2) {
                    Row(Modifier.fillMaxWidth().height(43.dp), verticalAlignment = Alignment.CenterVertically) {
                        SkeletonBox(Modifier.width(160.dp).height(16.dp))
                        Spacer(Modifier.weight(1f))
                        SkeletonBox(Modifier.size(50.dp, 30.dp), shape = CircleShape)
                    }
                }
            } else {
                SwitchRow("Все уведомления", all) {
                    all = it
                    saveNotifications(all = it)
                }
                // Прототип: «Профиль push-уведомления» 484:21266 ↔ 484:21302 — строка схлопывается,
                // ниже всё подтягивается: SMART_ANIMATE 800 мс, ease-in-out-back.
                AnimatedVisibility(
                    visible = !all,
                    enter = fadeIn(figmaFade()) + expandVertically(figmaFade()),
                    exit = fadeOut(figmaFade()) + shrinkVertically(figmaFade()),
                ) {
                    SwitchRow("Только по инструментам", byInstrument) {
                        byInstrument = it
                        saveNotifications(byInstrument = it)
                    }
                }
            }
        }

        Text(
            "Уведомления\nпо инструментам",
            style = AppFont.heading(T.FsTitle),
            modifier = Modifier.padding(top = 40.dp),
        )
        Text(
            "Для этой функции выше включите\nуведомления по инструментам",
            style = AppFont.sans(T.FsSm, color = T.TextSecondary),
            modifier = Modifier.padding(top = 12.dp),
        )

        FlowLayout(
            modifier = Modifier
                .padding(top = 24.dp)
                .fillMaxWidth()
                .alpha(if (byInstrument || all) 1f else 0.5f)
                .clip(T.ShapeCard)
                .background(T.Surface)
                .padding(12.dp),
            spacing = 4.dp,
        ) {
            instruments.forEachIndexed { i, item ->
                InstrumentChip(label = item.label, checked = item.on, onToggle = {
                    instruments[i] = item.copy(on = !item.on)
                    saveInstruments()
                })
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().height(43.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = AppFont.sans(T.FsBase), modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        AppToggle(checked = checked, onCheckedChange = onChange)
    }
}
