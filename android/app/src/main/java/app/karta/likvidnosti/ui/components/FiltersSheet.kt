package app.karta.likvidnosti.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.karta.likvidnosti.ui.icons.AppIcon
import app.karta.likvidnosti.ui.icons.AppIcons
import app.karta.likvidnosti.ui.theme.T
import java.time.LocalDate

data class FilterResult(
    val instruments: List<String>,
    val statuses: List<String>,
    val dateMode: String,
)

private data class Toggleable(val key: String, val label: String, var on: Boolean)

/** Нижний лист фильтров (аналог FiltersSheet.vue). Размещается поверх экрана. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FiltersSheet(
    open: Boolean,
    onClose: () -> Unit,
    onApply: (FilterResult) -> Unit,
) {
    if (!open) return

    val instruments = remember {
        mutableStateListOf(
            Toggleable("gold", "Золото", true),
            Toggleable("silver", "Серебро", false),
            Toggleable("platinum", "Платина", true),
            Toggleable("wti", "Нефть WTI", false),
            Toggleable("brent", "Нефть Brent", true),
            Toggleable("eurusd", "EUR/USD", false),
            Toggleable("gas", "Нат. газ", false),
            Toggleable("btc", "Биткоин", false),
        )
    }
    val statuses = remember {
        mutableStateListOf(
            Toggleable("hot", "С пылу с жару", true),
            Toggleable("actual", "Актуальное", true),
            Toggleable("inactual", "Не актуально", true),
            Toggleable("archive", "Архивное", false),
        )
    }
    var dateMode by remember { mutableStateOf("week") }
    var selectedDate by remember { mutableStateOf<LocalDate?>(LocalDate.of(2026, 3, 17)) }
    var calendarOpen by remember { mutableStateOf(true) }

    fun apply() {
        onApply(
            FilterResult(
                instruments = instruments.filter { it.on }.map { it.key },
                statuses = statuses.filter { it.on }.map { it.key },
                dateMode = dateMode,
            ),
        )
        onClose()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x80000000))
            .clickable(onClick = onClose),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(Color(0xFF1C1D1F))
                .clickable(enabled = false) {}
                .padding(horizontal = T.ScreenX, vertical = 10.dp),
        ) {
            Box(
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(T.ShapePill)
                    .background(Color(0x4DFFFFFF))
                    .align(Alignment.CenterHorizontally),
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ScreenTitle("Инструменты")
                Box(
                    modifier = Modifier
                        .clip(T.ShapePill)
                        .background(T.GradientAccent)
                        .clickable { apply() }
                        .padding(horizontal = 22.dp, vertical = 10.dp),
                ) {
                    Text("Искать", color = T.Text, fontSize = T.FsBase)
                }
            }

            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                FilterCard {
                    instruments.forEachIndexed { i, item ->
                        InstrumentChip(label = item.label, checked = item.on, onToggle = {
                            instruments[i] = item.copy(on = !item.on)
                        })
                    }
                }

                ScreenTitle("По статусу")
                FilterCard {
                    statuses.forEachIndexed { i, item ->
                        InstrumentChip(label = item.label, checked = item.on, onToggle = {
                            statuses[i] = item.copy(on = !item.on)
                        })
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ScreenTitle("По дате")
                    Box(
                        modifier = Modifier.size(32.dp).clickable { calendarOpen = !calendarOpen },
                        contentAlignment = Alignment.Center,
                    ) {
                        AppIcon(AppIcons.Calendar, size = 24.dp)
                    }
                }

                if (calendarOpen) {
                    AppCalendar(selected = selectedDate, onSelect = { selectedDate = it })
                }

                SegmentedControl(
                    value = dateMode,
                    options = listOf("week" to "Неделя", "month" to "Месяц", "year" to "Год"),
                    onSelect = { dateMode = it },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterCard(content: @Composable androidx.compose.foundation.layout.FlowRowScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(T.ShapeCard)
            .background(T.Surface)
            .padding(16.dp),
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}
