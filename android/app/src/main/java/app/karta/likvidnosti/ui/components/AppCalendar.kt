package app.karta.likvidnosti.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.karta.likvidnosti.ui.icons.AppIcon
import app.karta.likvidnosti.ui.icons.AppIcons
import app.karta.likvidnosti.ui.theme.AppFont
import app.karta.likvidnosti.ui.theme.T
import java.time.LocalDate

private val MONTHS = listOf(
    "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
    "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь",
)
private val WEEKDAYS = listOf("ПН", "ВТ", "СР", "ЧТ", "ПТ", "СБ", "ВС")

private data class DayCell(val day: Int, val date: LocalDate?)

/**
 * Логика диапазона (iOS AppCalendar.select): нет начала или уже выбраны обе
 * даты → начинаем заново; иначе второй тап задаёт конец (если не раньше
 * начала) либо переносит начало.
 */
fun calendarPick(start: LocalDate?, end: LocalDate?, date: LocalDate): Pair<LocalDate?, LocalDate?> = when {
    start == null || end != null -> date to null
    !date.isBefore(start) -> start to date
    else -> date to null
}

/**
 * Календарь выбора диапазона дат (iOS AppCalendar, Calendar из макета):
 * стеклянная плашка 361 («чёрный 2»), шапка с шевронами и месяцем (SemiBold 20),
 * линия 40 %, дни недели Medium 12 капсом, даты Medium 18 в ячейках 37×42;
 * начало диапазона — зелёная капсула, конец — белая с зелёной цифрой,
 * промежуток — зелёный 30 %, чужие дни — 40 %.
 */
@Composable
fun AppCalendar(
    start: LocalDate?,
    end: LocalDate?,
    onChange: (start: LocalDate?, end: LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var cursor by remember { mutableStateOf((start ?: LocalDate.now()).withDayOfMonth(1)) }

    val cells = remember(cursor) {
        val lead = cursor.dayOfWeek.value - 1 // понедельник = 0
        val daysInMonth = cursor.lengthOfMonth()
        val daysPrev = cursor.minusMonths(1).lengthOfMonth()
        buildList {
            for (i in 0 until lead) add(DayCell(daysPrev - lead + 1 + i, null))
            for (d in 1..daysInMonth) add(DayCell(d, cursor.withDayOfMonth(d)))
            var tail = 1
            while (size % 7 != 0) add(DayCell(tail++, null))
        }
    }

    Column(
        modifier = modifier
            .widthIn(max = 361.dp)
            .fillMaxWidth()
            .liquidGlass(T.ShapeCard, Fills.Black2)
            .padding(horizontal = 14.83.dp),
    ) {
        Row(
            modifier = Modifier
                .padding(top = 4.dp)
                .height(51.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Box(
                modifier = Modifier.size(44.dp).pressable(pressAlpha = 1f) { cursor = cursor.minusMonths(1) },
                contentAlignment = Alignment.Center,
            ) { AppIcon(AppIcons.monthPrev, size = 24.dp) }
            Text(
                MONTHS[cursor.monthValue - 1],
                style = AppFont.sans(20.sp, FontWeight.SemiBold),
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier.size(44.dp).pressable(pressAlpha = 1f) { cursor = cursor.plusMonths(1) },
                contentAlignment = Alignment.Center,
            ) { AppIcon(AppIcons.monthNext, size = 24.dp) }
        }

        HairlineDivider(modifier = Modifier.padding(top = 8.dp).padding(horizontal = 3.dp), alpha = 0.4f)

        Row(modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 16.dp).height(16.dp)) {
            WEEKDAYS.forEach {
                Text(
                    it,
                    style = AppFont.sans(T.FsSm, FontWeight.Medium).copy(letterSpacing = 0.36.sp),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Column(modifier = Modifier.padding(bottom = 4.dp)) {
            cells.chunked(7).forEach { row ->
                Row(modifier = Modifier.fillMaxWidth().height(50.dp)) {
                    row.forEach { cell ->
                        val date = cell.date
                        val isStart = date != null && date == start
                        val isEnd = date != null && date == end && end != start
                        val inRange = date != null && start != null && end != null && date.isAfter(start) && date.isBefore(end)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .pressable(enabled = date != null, pressAlpha = 1f) {
                                    if (date != null) {
                                        val (s, e) = calendarPick(start, end, date)
                                        onChange(s, e)
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            val capsule = Modifier.size(width = 37.dp, height = 42.dp)
                            // Подсветка плавно меняет прозрачность. AnimatedVisibility здесь не используем:
                            // внешний Row даёт RowScope-перегрузку, которая не подходит внутри Box.
                            val inRangeAlpha by animateFloatAsState(if (inRange) 1f else 0f, tween(100), label = "inRange")
                            val startAlpha by animateFloatAsState(if (isStart) 1f else 0f, tween(100), label = "start")
                            val endAlpha by animateFloatAsState(if (isEnd) 1f else 0f, tween(100), label = "end")
                            Box(capsule.alpha(inRangeAlpha).fillShape(GlassFill(T.Green.copy(alpha = 0.3f)), CapsuleShape))
                            Box(capsule.alpha(startAlpha).fillShape(GlassFill(T.Green), CapsuleShape))
                            Box(capsule.alpha(endAlpha).fillShape(GlassFill(Color.White), CapsuleShape))
                            Text(
                                cell.day.toString(),
                                style = AppFont.sans(18.sp, FontWeight.Medium, color = if (isEnd) T.Green else T.Text),
                                modifier = Modifier.alpha(if (date == null) 0.4f else 1f),
                            )
                        }
                    }
                }
            }
        }
    }
}
