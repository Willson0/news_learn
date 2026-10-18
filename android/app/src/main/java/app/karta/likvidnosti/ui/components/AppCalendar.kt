package app.karta.likvidnosti.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.karta.likvidnosti.ui.icons.AppIcon
import app.karta.likvidnosti.ui.icons.AppIcons
import app.karta.likvidnosti.ui.theme.T
import java.time.LocalDate

private val MONTHS = listOf(
    "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
    "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь",
)
private val WEEKDAYS = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

/**
 * Месячный календарь с выбором диапазона (аналог AppCalendar.vue).
 * [start]/[end] — начало и конец диапазона; если [end] == null, выбрана
 * только начальная дата. Нажатие на день сообщается через [onSelect] —
 * логику диапазона ведёт вызывающий экран.
 */
@Composable
fun AppCalendar(
    start: LocalDate?,
    end: LocalDate?,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = remember { LocalDate.now() }
    var cursor by remember { mutableStateOf((start ?: today).withDayOfMonth(1)) }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                modifier = Modifier.size(32.dp).clip(CircleShape)
                    .clickable { cursor = cursor.minusMonths(1) },
                contentAlignment = Alignment.Center,
            ) { AppIcon(AppIcons.Back, size = 18.dp) }
            Text(
                "${MONTHS[cursor.monthValue - 1]} ${cursor.year}",
                color = T.Text, fontSize = T.FsMd, fontWeight = FontWeight.Bold,
            )
            Box(
                modifier = Modifier.size(32.dp).clip(CircleShape)
                    .clickable { cursor = cursor.plusMonths(1) },
                contentAlignment = Alignment.Center,
            ) { AppIcon(AppIcons.Forward, size = 18.dp) }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            WEEKDAYS.forEach {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(it, color = T.TextMuted, fontSize = T.FsSm)
                }
            }
        }

        // ISO: понедельник = 1
        val firstDow = cursor.dayOfWeek.value // 1..7
        val leading = firstDow - 1
        val daysInMonth = cursor.lengthOfMonth()
        val cells = leading + daysInMonth
        val rows = (cells + 6) / 7

        for (r in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (c in 0 until 7) {
                    val index = r * 7 + c
                    val dayNum = index - leading + 1
                    Box(
                        modifier = Modifier.weight(1f).aspectRatio(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (dayNum in 1..daysInMonth) {
                            val date = cursor.withDayOfMonth(dayNum)
                            val isStart = date == start
                            val isEnd = date == end
                            val isEdge = isStart || isEnd
                            val inRange = start != null && end != null &&
                                date.isAfter(start) && date.isBefore(end)
                            val isToday = date == today
                            val cell = Modifier.size(36.dp).clip(CircleShape)
                            val styled = when {
                                isEdge -> cell.background(T.GradientAccent)
                                inRange -> cell.background(Color(0x332E7D32))
                                else -> cell
                            }
                            Box(
                                modifier = styled.clickable { onSelect(date) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    dayNum.toString(),
                                    color = if (isToday && !isEdge) T.Green else T.Text,
                                    fontSize = T.FsBase,
                                    fontWeight = if (isEdge || isToday) FontWeight.Bold else FontWeight.Normal,
                                    modifier = if (isEdge) Modifier else Modifier.alpha(0.9f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
