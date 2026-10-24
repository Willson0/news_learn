package app.karta.likvidnosti.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import app.karta.likvidnosti.data.Repository
import app.karta.likvidnosti.ui.components.figmaFade
import app.karta.likvidnosti.ui.components.AppToggle
import app.karta.likvidnosti.ui.components.FlowLayout
import app.karta.likvidnosti.ui.components.InnerScreen
import app.karta.likvidnosti.ui.components.InstrumentChip
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
    var all by remember { mutableStateOf(true) }
    var byInstrument by remember { mutableStateOf(false) }
    val instruments = remember { mutableStateListOf<NotifInstrument>() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        runCatching { Repository.profile() }.getOrNull()?.let { p ->
            all = p.notifications?.all ?: true
            byInstrument = p.notifications?.byInstrument ?: false
            instruments.clear()
            instruments.addAll(p.instruments.map { NotifInstrument(it.key, it.label, it.on) })
        }
    }

    fun saveInstruments() {
        scope.launch { runCatching { Repository.syncInstruments(instruments.filter { it.on }.map { it.key }) } }
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
            SwitchRow("Все уведомления", all) {
                all = it
                scope.launch { runCatching { Repository.updateNotifications(all = it) } }
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
                    scope.launch { runCatching { Repository.updateNotifications(byInstrument = it) } }
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
