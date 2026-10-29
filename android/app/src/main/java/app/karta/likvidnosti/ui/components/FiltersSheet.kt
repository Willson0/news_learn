@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package app.karta.likvidnosti.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import app.karta.likvidnosti.ui.icons.AppIcon
import app.karta.likvidnosti.ui.icons.AppIcons
import app.karta.likvidnosti.ui.theme.AppFont
import app.karta.likvidnosti.ui.theme.T
import app.karta.likvidnosti.ui.theme.designBottom
import app.karta.likvidnosti.ui.theme.designTop
import app.karta.likvidnosti.ui.theme.hex
import kotlinx.coroutines.delay
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

private val PERIOD_OPTIONS = listOf("week" to "Неделя", "month" to "Месяц", "year" to "Год")

/** Режим «Свои даты»: период задан диапазоном в календаре. */
const val CUSTOM_DATES = "custom"

val FiltersResult.hasRange: Boolean get() = dateStart != null && dateEnd != null

/** Один выбор даты: период или диапазон. Незаконченный диапазон не фильтрует. */
fun FiltersResult.normalized(): FiltersResult = if (hasRange) {
    copy(dateMode = CUSTOM_DATES)
} else {
    copy(dateMode = dateMode.takeIf { m -> PERIOD_OPTIONS.any { it.first == m } }, dateStart = null, dateEnd = null)
}

/** Период для API (неделя / месяц / год) — только если не выбран диапазон. */
val FiltersResult.period: String? get() = normalized().dateMode.takeIf { it != CUSTOM_DATES }

/** Число включённых фильтров — бейдж на кнопке фильтров. */
val FiltersResult.activeCount: Int
    get() = normalized().let { it.instruments.size + it.statuses.size + (if (it.dateMode != null) 1 else 0) }

/** Снимаемые чипы над списком: id → подпись («Золото», «Месяц», «С 12 ОКТ - 20 ОКТ»). */
fun FiltersResult.activeChips(): List<Pair<String, String>> {
    val n = normalized()
    val list = mutableListOf<Pair<String, String>>()
    n.instruments.forEach { k -> list.add("i:$k" to (INSTRUMENT_OPTIONS.firstOrNull { it.first == k }?.second ?: k)) }
    n.statuses.forEach { k -> list.add("s:$k" to (STATUS_OPTIONS.firstOrNull { it.first == k }?.second ?: k)) }
    when (n.dateMode) {
        null -> Unit
        CUSTOM_DATES -> list.add("d" to "С ${shortDate(n.dateStart)} - ${shortDate(n.dateEnd)}")
        else -> list.add("d" to (PERIOD_OPTIONS.firstOrNull { it.first == n.dateMode }?.second ?: n.dateMode))
    }
    return list
}

/** Фильтры без одного чипа (крестик на чипе над списком). */
fun FiltersResult.without(chipId: String): FiltersResult = when {
    chipId.startsWith("i:") -> copy(instruments = instruments - chipId.removePrefix("i:"))
    chipId.startsWith("s:") -> copy(statuses = statuses - chipId.removePrefix("s:"))
    else -> copy(dateMode = null, dateStart = null, dateEnd = null)
}.normalized()

/** «материал / материала / материалов». */
fun materialsCount(n: Int): String {
    val mod100 = n % 100
    val mod10 = n % 10
    val word = when {
        mod100 in 11..14 -> "материалов"
        mod10 == 1 -> "материал"
        mod10 in 2..4 -> "материала"
        else -> "материалов"
    }
    return "$n $word"
}

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
 * радиус 24 сверху, полоска-хват 32 на 11; заголовки Kreadon 24; блоки «Фон
 * плашки 2» с чипами; календарь — стеклянный поп-ап над блоками. Внизу
 * закреплены «Показать N материалов» и «Сбросить». Тянется вниз, чтобы закрыть
 * (выбор при этом применяется, а не теряется).
 */
@Composable
fun FiltersSheet(
    initial: FiltersResult,
    onApply: (FiltersResult) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    /** Сколько материалов найдётся с такими фильтрами (null — неизвестно). */
    countFor: (suspend (FiltersResult) -> Int?)? = null,
    /** Текущий выбор в листе — чтобы применить его при закрытии свайпом. */
    onDraftChange: (FiltersResult) -> Unit = {},
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
    var dateMode by remember { mutableStateOf(initial.normalized().dateMode) }
    var dateStart by remember { mutableStateOf(initial.dateStart) }
    var dateEnd by remember { mutableStateOf(initial.dateEnd) }
    var calendarOpen by remember { mutableStateOf(false) }

    val current = FiltersResult(
        instruments = instruments.filter { it.on }.map { it.key },
        statuses = statuses.filter { it.on }.map { it.key },
        dateMode = dateMode,
        dateStart = dateStart,
        dateEnd = dateEnd,
    ).normalized()
    LaunchedEffect(current) { onDraftChange(current) }

    // «Показать N материалов»: число с сервера с небольшой задержкой после выбора.
    var count by remember { mutableStateOf<Int?>(null) }
    var counting by remember { mutableStateOf(false) }
    LaunchedEffect(current) {
        val counter = countFor ?: return@LaunchedEffect
        counting = true
        delay(300)
        count = counter(current)
        counting = false
    }

    fun reset() {
        for (i in instruments.indices) instruments[i] = instruments[i].copy(on = false)
        for (i in statuses.indices) statuses[i] = statuses[i].copy(on = false)
        dateMode = null; dateStart = null; dateEnd = null
        calendarOpen = false
    }

    val rangeLabel = shortDate(dateStart)?.let { s ->
        shortDate(dateEnd)?.let { e -> "С $s - $e" } ?: "С $s"
    } ?: ""

    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    // Лист закрывается свайпом вниз за любое место, в том числе за список,
    // когда он прокручен к самому верху (как системные листы).
    var dragY by remember { mutableFloatStateOf(0f) }
    fun settle(velocity: Float) {
        val closeBy = with(density) { 120.dp.toPx() }
        if ((dragY > closeBy && velocity >= 0f) || velocity > 1800f) {
            onClose()
        } else {
            scope.launch { animate(dragY, 0f, animationSpec = spring(dampingRatio = 0.85f)) { v, _ -> dragY = v } }
        }
    }
    val sheetScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Лист опущен: движение вверх сначала поднимает его, потом листает список.
                if (source != NestedScrollSource.UserInput || available.y >= 0f || dragY <= 0f) return Offset.Zero
                val used = max(available.y, -dragY)
                dragY += used
                return Offset(0f, used)
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                // Список упёрся в верх — остаток протяжки вниз тянет лист.
                if (source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
                dragY += available.y
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (dragY <= 0f) return Velocity.Zero
                settle(available.y)
                return available
            }
        }
    }

    fun apply() = onApply(current)

    val sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    CompositionLocalProvider(LocalBackdrop provides null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .offset { IntOffset(0, max(dragY, 0f).roundToInt()) }
                .background(T.Surface, sheetShape)
                .pressable(pressAlpha = 1f) {}
                .nestedScroll(sheetScroll)
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { delta -> dragY = max(0f, dragY + delta) },
                    onDragStopped = { velocity -> settle(velocity) },
                ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = T.ScreenX)
                    .padding(bottom = designBottom(24.dp) + 140.dp),
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
                    AnimatedVisibility(visible = dateMode == CUSTOM_DATES && rangeLabel.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                        // Выбранный диапазон (Figma 474:15508): крестик (область нажатия 48),
                        // пилюля 32 со стеклом «active», 14 Regular; нажатие на пилюлю — календарь.
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(0.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .pressable(pressAlpha = 1f) { dateStart = null; dateEnd = null; dateMode = null; calendarOpen = false }
                                    .semantics(mergeDescendants = true) { contentDescription = "Убрать диапазон дат" },
                                contentAlignment = Alignment.Center,
                            ) { AppIcon(AppIcons.close, size = 24.dp) }
                            Box(
                                modifier = Modifier
                                    .height(32.dp)
                                    .liquidGlass(CapsuleShape, Fills.Active)
                                    .pressable(pressAlpha = 1f) { calendarOpen = !calendarOpen }
                                    .padding(horizontal = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) { Text(rangeLabel, style = AppFont.sans(T.FsBase)) }
                        }
                    }
                }

                // Период и диапазон — один выбор: «Свои даты» открывает календарь.
                SegmentedControl(
                    value = dateMode.orEmpty(),
                    options = PERIOD_OPTIONS + (CUSTOM_DATES to "Свои даты"),
                    onSelect = { v ->
                        dateMode = v
                        if (v == CUSTOM_DATES) {
                            calendarOpen = true
                        } else {
                            dateStart = null; dateEnd = null
                            calendarOpen = false
                        }
                    },
                    modifier = Modifier.padding(top = 24.dp),
                )
            }

            AnimatedVisibility(
                visible = calendarOpen,
                enter = scaleIn(initialScale = 0.92f, transformOrigin = TransformOrigin(1f, 1f)) + fadeIn(),
                exit = scaleOut(targetScale = 0.92f, transformOrigin = TransformOrigin(1f, 1f)) + fadeOut(),
                modifier = Modifier.padding(horizontal = T.ScreenX).padding(top = 87.dp),
            ) {
                AppCalendar(start = dateStart, end = dateEnd, onChange = { s, e ->
                    dateStart = s; dateEnd = e; dateMode = CUSTOM_DATES
                    if (s != null && e != null) calendarOpen = false
                })
            }

            // Закреплённые внизу «Показать N материалов» и «Сбросить» — в зоне большого пальца.
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(T.Surface)
                    .padding(horizontal = T.ScreenX)
                    .padding(top = 12.dp, bottom = designBottom(24.dp)),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val n = count
                AppButton(
                    text = when {
                        countFor == null || n == null -> "Показать материалы"
                        n == 0 -> "Ничего не найдено"
                        else -> "Показать ${materialsCount(n)}"
                    },
                    enabled = countFor == null || counting || n == null || n > 0,
                    onClick = { apply() },
                )
                Box(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .height(48.dp)
                        .pressable(enabled = current.activeCount > 0, pressAlpha = 0.6f) { reset() }
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Сбросить",
                        style = AppFont.sans(T.FsBase, color = if (current.activeCount > 0) Color.White else T.TextSecondary),
                    )
                }
            }

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
    countFor: (suspend (FiltersResult) -> Int?)? = null,
) {
    // Выбор в листе не теряется: закрытие свайпом, «назад» или тапом по фону его применяет.
    var draft by remember(open) { mutableStateOf(applied.normalized()) }
    fun dismiss() {
        if (draft != applied.normalized()) onApply(draft)
        onClose()
    }
    BackHandler(enabled = open) { dismiss() }
    Box(modifier = Modifier.fillMaxSize()) {
        // Прототип: «Аналитика» → Chips → оверлей «Frame 287»: MOVE_IN снизу, пружина SLOW.
        AnimatedVisibility(visible = open, enter = fadeIn(figmaSlow()), exit = fadeOut(figmaSlow())) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .backdropBlur(6.45.dp)
                    .background(hex("#070707").copy(alpha = 0.71f))
                    .pressable(pressAlpha = 1f) { dismiss() },
            )
        }
        AnimatedVisibility(
            visible = open,
            enter = slideInVertically(figmaSlow()) { it },
            exit = slideOutVertically(figmaSlow()) { it },
            modifier = Modifier.padding(top = designTop(172.dp)),
        ) {
            FiltersSheet(
                initial = applied,
                onApply = { onApply(it); onClose() },
                onClose = { dismiss() },
                countFor = countFor,
                onDraftChange = { draft = it },
            )
        }
    }
}
