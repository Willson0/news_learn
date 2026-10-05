package app.karta.likvidnosti.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.karta.likvidnosti.data.Repository
import app.karta.likvidnosti.ui.components.AppToggle
import app.karta.likvidnosti.ui.components.InstrumentChip
import app.karta.likvidnosti.ui.components.Screen
import app.karta.likvidnosti.ui.components.ScreenHeader
import app.karta.likvidnosti.ui.components.ScreenTitle
import app.karta.likvidnosti.ui.theme.T
import kotlinx.coroutines.launch

private data class NotifInstrument(val key: String, val label: String, val on: Boolean)

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

    Screen(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenHeader("Уведомления", onBack = onBack)

        Column(
            modifier = Modifier.fillMaxWidth().clip(T.ShapeCard).background(T.Surface).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Все уведомления", color = T.Text, fontSize = T.FsMd)
                AppToggle(checked = all, onCheckedChange = {
                    all = it
                    scope.launch { runCatching { Repository.updateNotifications(all = it) } }
                })
            }
            if (!all) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Только по инструментам", color = T.Text, fontSize = T.FsMd)
                    AppToggle(checked = byInstrument, onCheckedChange = {
                        byInstrument = it
                        scope.launch { runCatching { Repository.updateNotifications(byInstrument = it) } }
                    })
                }
            }
        }

        ScreenTitle("Уведомления по инструментам")
        Text("Для этой функции выше включите уведомления по инструментам", color = T.TextMuted, fontSize = T.FsBase)

        Box(modifier = Modifier.fillMaxWidth().clip(T.ShapeCard).background(T.Surface).padding(16.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                instruments.forEachIndexed { i, item ->
                    InstrumentChip(label = item.label, checked = item.on, onToggle = {
                        instruments[i] = item.copy(on = !item.on)
                        saveInstruments()
                    })
                }
            }
        }
    }
}
