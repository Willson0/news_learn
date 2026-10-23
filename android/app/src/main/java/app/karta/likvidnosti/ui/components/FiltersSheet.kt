@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package app.karta.likvidnosti.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.karta.likvidnosti.ui.icons.AppIcon
import app.karta.likvidnosti.ui.icons.AppIcons
import app.karta.likvidnosti.ui.theme.AppFont
import app.karta.likvidnosti.ui.theme.T
import app.karta.likvidnosti.ui.theme.designTop
import app.karta.likvidnosti.ui.theme.hex
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Результат фильтров (iOS FiltersResult) + выбранный диапазон дат.
 * Пустой список / `null` означает «по этому фильтру ничего не фильтруем».
 */
data class FiltersResult(
    val instruments: List<String> = emptyList(),
    val statuses: List<String> = emptyList(),
    val dateMode: String? = null,
    val dateStart: LocalDate? = null,
    val dateEnd: LocalDate? = null,
)

private data class Toggleable(val key: String, val label: String, val on: Boolean)

private val INSTRUMENT_OPTIONS = listOf(
    "gold" to "Золото",
    "silver" to "Серебро",
    "platinum" to "Платина",
    "wti" to "Нефть WTI",
    "brent" to "Нефть Brent",
    "eurusd" to "EUR/USD",
    "gas" to "Нат. газ",
    "btc" to "Биткоин",
)

private val STATUS_OPTIONS = listOf(
    "hot" to "С пылу с жару",
    "actual" to "Актуальное",
    "inactual" to "Не актуально",
    "archive" to "Архивное",
)

private val SHORT_MONTHS = listOf("ЯНВ", "ФЕВ", "МАР", "АПР", "МАЙ", "ИЮН", "ИЮЛ", "АВГ", "СЕН", "ОКТ", "НОЯ", "ДЕК")

private fun shortDate(d: LocalDate?): String? = d?.let { "${it.dayOfMonth} ${SHORT_MONTHS[it.monthValue - 1]}" }

/**
 * Лист фильтров «Аналитика фильтры» (iOS FiltersSheet): плашка «фон плашки»,
 * радиус 24 сверху, полоска-хват 32 на 11; «Искать» справа сверху; заголовки
 * Kreadon 24; блоки «Фон плашки 2» с чипами; календарь — стеклянный поп-ап
 * над блоками. Тянется вниз, чтобы закрыть.
 */
@Composable
fun FiltersSheet(
    initial: FiltersResult,
    onApply: (FiltersResult) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Стартовое состояние — уже применённые фильтры (при первом открытии все пустые).
    val instruments = remember {
        mutableStateListOf<Toggleable>().apply {
            addAll(INSTRUMENT_OPTIONS.map { (key, label) -> Toggleable(key, label, key in initial.instruments) })
        }
    }
    val statuses = remember {
        mutableStateListOf<Toggleable>().apply {
            addAll(STATUS_OPTIONS.map { (key, label) -> Toggleable(key, label, key in initial.statuses) })
        }
    }
    var dateMode by remember { mutableStateOf(initial.dateMode) }
    var dateStart by remember { mutableStateOf(initial.dateStart) }
    var dateEnd by remember { mutableStateOf(initial.dateEnd) }
    var calendarOpen by remember { mutableStateOf(false) }

    val rangeLabel = shortDate(dateStart)?.let { s ->
        shortDate(dateEnd)?.let { e -> "С $s - $e" } ?: "С $s"
    } ?: ""

    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val dragY = remember { Animatable(0f) }

    fun apply() {
        onApply(
            FiltersResult(
                instruments = instruments.filter { it.on }.map { it.key },
                statuses = statuses.filter { it.on }.map { it.key },
                dateMode = dateMode,
                dateStart = dateStart,
                dateEnd = dateEnd,
            ),
        )
        onClose()
    }

    val sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    CompositionLocalProvider(LocalBackdrop provides null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .offset { IntOffset(0, max(dragY.value, 0f).roundToInt()) }
                .background(T.Surface, sheetShape)
                .pressable(pressAlpha = 1f) {}
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { delta ->
                        scope.launch { dragY.snapTo(max(0f, dragY.value + delta)) }
                    },
                    onDragStopped = { velocity ->
                        val closeBy = with(density) { 120.dp.toPx() }
                        if (dragY.value > closeBy || velocity > 1800f) {
                            onClose()
                        } else {
                            dragY.animateTo(0f, spring(dampingRatio = 0.85f))
                        }
                    },
                ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = T.ScreenX)
                    .padding(bottom = 60.dp),
            ) {
                Text("Инструменты", style = AppFont.heading(T.FsTitle), modifier = Modifier.padding(top = 35.dp))
                ChipCard(Modifier.padding(top = 24.dp)) {
                    instruments.forEachIndexed { i, item ->
                        InstrumentChip(item.label, item.on, onToggle = { instruments[i] = item.copy(on = !item.on) })
                    }
                }

                Text("По статусу", style = AppFont.heading(T.FsTitle), modifier = Modifier.padding(top = 40.dp))
                ChipCard(Modifier.padding(top = 24.dp)) {
                    statuses.forEachIndexed { i, item ->
                        InstrumentChip(
                            item.label, item.on,
                            onToggle = { statuses[i] = item.copy(on = !item.on) },
                            hPadding = 24.dp,
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("По дате", style = AppFont.heading(T.FsTitle))
                    Spacer(Modifier.weight(1f))
                    AnimatedVisibility(visible = rangeLabel.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                        // Выбранный диапазон (Figma 474:15508): крестик в области 38×32,
                        // пилюля 32 со стеклом «active», 14 Regular.
                        Row(
                            modifier = Modifier.padding(end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 38.dp, height = 32.dp)
                                    .pressable(pressAlpha = 1f) { dateStart = null; dateEnd = null },
                                contentAlignment = Alignment.Center,
                            ) { AppIcon(AppIcons.close, size = 24.dp) }
                            Box(
                                modifier = Modifier
                                    .height(32.dp)
                                    .liquidGlass(CapsuleShape, Fills.Active)
                                    .padding(horizontal = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) { Text(rangeLabel, style = AppFont.sans(T.FsBase)) }
                        }
                    }
                    AppIcon(
                        AppIcons.calendarNumber,
                        size = 32.dp,
                        modifier = Modifier.pressable(pressAlpha = 1f) { calendarOpen = !calendarOpen },
                    )
                }

                SegmentedControl(
                    value = dateMode.orEmpty(),
                    options = listOf("week" to "Неделя", "month" to "Месяц", "year" to "Год"),
                    onSelect = { dateMode = it },
                    modifier = Modifier.padding(top = 24.dp),
                )
            }

            AnimatedVisibility(
                visible = calendarOpen,
                enter = scaleIn(initialScale = 0.92f, transformOrigin = TransformOrigin(1f, 1f)) + fadeIn(),
                exit = scaleOut(targetScale = 0.92f, transformOrigin = TransformOrigin(1f, 1f)) + fadeOut(),
                modifier = Modifier.padding(horizontal = T.ScreenX).padding(top = 87.dp),
            ) {
                AppCalendar(start = dateStart, end = dateEnd, onChange = { s, e -> dateStart = s; dateEnd = e })
            }

            PillActionButton(
                "Искать",
                modifier = Modifier.align(Alignment.TopEnd).padding(end = T.ScreenX, top = 12.dp),
                onClick = { apply() },
            )

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 11.dp)
                    .size(width = 32.dp, height = 1.5.dp)
                    .background(Color.White, CapsuleShape),
            )
        }
    }
}

@Composable
private fun ChipCard(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.FlowRowScope.() -> Unit) {
    FlowLayout(
        modifier = modifier
            .fillMaxWidth()
            .background(T.Surface2Plate, T.ShapeCard)
            .padding(12.dp),
        spacing = 4.dp,
        lineSpacing = 4.dp,
        content = content,
    )
}

/**
 * Затемнение rgba(7,7,7,0.71) с размытием 6.45 и лист фильтров от y = 172
 * (iOS FiltersOverlay). Кладётся поверх экрана; размытие берётся из
 * [LocalBackdrop] (см. BackdropBox).
 */
@Composable
fun FiltersOverlay(
    open: Boolean,
    applied: FiltersResult,
    onClose: () -> Unit,
    onApply: (FiltersResult) -> Unit,
) {
    BackHandler(enabled = open) { onClose() }
    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = open, enter = fadeIn(), exit = fadeOut()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .backdropBlur(6.45.dp)
                    .background(hex("#070707").copy(alpha = 0.71f))
                    .pressable(pressAlpha = 1f) { onClose() },
            )
        }
        AnimatedVisibility(
            visible = open,
            enter = slideInVertically(spring(dampingRatio = 0.9f)) { it },
            exit = slideOutVertically(spring(dampingRatio = 0.9f)) { it },
            modifier = Modifier.padding(top = designTop(172.dp)),
        ) {
            FiltersSheet(initial = applied, onApply = onApply, onClose = onClose)
        }
    }
}
