package app.karta.likvidnosti.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.time.LocalDate
import app.karta.likvidnosti.data.AppData
import app.karta.likvidnosti.data.Repository
import app.karta.likvidnosti.data.api.ReportDto
import app.karta.likvidnosti.ui.components.AppInput
import app.karta.likvidnosti.ui.components.CapsuleShape
import app.karta.likvidnosti.ui.components.EmptyState
import app.karta.likvidnosti.ui.components.LoadErrorState
import app.karta.likvidnosti.ui.components.StaleDataBanner
import app.karta.likvidnosti.ui.components.activeChips
import app.karta.likvidnosti.ui.components.activeCount
import app.karta.likvidnosti.ui.components.hasRange
import app.karta.likvidnosti.ui.components.normalized
import app.karta.likvidnosti.ui.components.period
import app.karta.likvidnosti.ui.components.without
import app.karta.likvidnosti.ui.components.Fills
import app.karta.likvidnosti.ui.components.FiltersResult
import app.karta.likvidnosti.ui.components.FiltersOverlay
import app.karta.likvidnosti.ui.components.ReportCard
import app.karta.likvidnosti.ui.components.ReportListSkeleton
import app.karta.likvidnosti.ui.components.ScreenTitle
import app.karta.likvidnosti.ui.components.TabScaffold
import app.karta.likvidnosti.ui.components.liquidGlass
import app.karta.likvidnosti.ui.components.pressable
import app.karta.likvidnosti.ui.components.PullToRefreshBox
import app.karta.likvidnosti.ui.icons.AppIcon
import app.karta.likvidnosti.ui.icons.AppIcons
import app.karta.likvidnosti.ui.theme.AppFont
import app.karta.likvidnosti.ui.theme.T
import app.karta.likvidnosti.ui.theme.designTop
import app.karta.likvidnosti.ui.theme.navBarClearance

/**
 * «Список материалов» (iOS AnalyticsView): заголовок на y = 94, поиск-стекло и
 * круглая кнопка фильтров 48 («active»), карточки отчётов; лист фильтров
 * поверх с затемнением. У админа справа от навбара — «+» (создать отчёт).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnalyticsScreen(onOpenReport: (Long) -> Unit, navBar: @Composable () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var filtersOpen by remember { mutableStateOf(false) }
    var statusFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var instrumentFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var periodFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var rangeFrom by rememberSaveable { mutableStateOf<String?>(null) }
    var rangeTo by rememberSaveable { mutableStateOf<String?>(null) }
    var reports by remember { mutableStateOf(AppData.reports ?: emptyList<ReportDto>()) }
    var loaded by remember { mutableStateOf(AppData.reports != null) }
    var failed by remember { mutableStateOf(false) }
    // Список на экране соответствует текущим фильтрам (а не взят из кэша ленты при сбое).
    var hasData by remember { mutableStateOf(AppData.reports != null) }
    var savedAt by remember { mutableStateOf(AppData.reportsAt) }
    var refreshing by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    // Применённые фильтры: лист получает их при каждом открытии, поэтому не сбрасывается.
    val applied = FiltersResult(
        instruments = instrumentFilter?.split(",").orEmpty(),
        statuses = statusFilter?.split(",").orEmpty(),
        dateMode = periodFilter,
        dateStart = rangeFrom?.let { LocalDate.parse(it) },
        dateEnd = rangeTo?.let { LocalDate.parse(it) },
    ).normalized()
    val filterCount = applied.activeCount

    fun applyFilters(result: FiltersResult) {
        val r = result.normalized()
        statusFilter = r.statuses.joinToString(",").ifEmpty { null }
        instrumentFilter = r.instruments.joinToString(",").ifEmpty { null }
        if (r.hasRange) {
            rangeFrom = r.dateStart.toString()
            rangeTo = r.dateEnd.toString()
            periodFilter = null
        } else {
            rangeFrom = null
            rangeTo = null
            periodFilter = r.period
        }
    }

    fun matchesQuery(list: List<ReportDto>): List<ReportDto> {
        val q = query.trim().lowercase()
        return if (q.isEmpty()) list
        else list.filter { it.title.lowercase().contains(q) || it.description.lowercase().contains(q) }
    }

    suspend fun load(f: FiltersResult): List<ReportDto> = Repository.reports(
        instruments = f.instruments.joinToString(",").ifEmpty { null },
        period = f.period,
        from = f.dateStart?.takeIf { f.hasRange }?.toString(),
        to = f.dateEnd?.takeIf { f.hasRange }?.toString(),
        status = f.statuses.joinToString(",").ifEmpty { null },
    )

    suspend fun fetch() {
        runCatching { load(applied) }
            .onSuccess {
                reports = it
                failed = false
                hasData = true
                savedAt = System.currentTimeMillis()
            }
            .onFailure { failed = true }
    }

    LaunchedEffect(statusFilter, instrumentFilter, periodFilter, rangeFrom, rangeTo) {
        // Кэш ленты подходит только к списку без фильтров.
        if (filterCount > 0) hasData = false
        fetch()
        loaded = true
    }

    fun retry() {
        scope.launch {
            loaded = hasData
            fetch()
            loaded = true
        }
    }

    val visible = remember(query, reports) { matchesQuery(reports) }

    TabScaffold(
        navBar = navBar,
        overlay = {
            FiltersOverlay(
                open = filtersOpen,
                applied = applied,
                onClose = { filtersOpen = false },
                onApply = { applyFilters(it) },
                countFor = { f -> runCatching { matchesQuery(load(f)).size }.getOrNull() },
            )
        },
    ) {
        PullToRefreshBox(
            isRefreshing = refreshing,
            scrollState = scrollState,
            onRefresh = {
                scope.launch {
                    refreshing = true
                    try { fetch() } finally { refreshing = false }
                }
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = T.ScreenX)
                    .padding(top = designTop(T.TitleTop), bottom = navBarClearance()),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                ScreenTitle("Список материалов")

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppInput(
                        query, { query = it },
                        placeholder = "Нужный отчет или материал",
                        modifier = Modifier.weight(1f),
                    )
                    Box {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .liquidGlass(CircleShape, Fills.Active)
                                .pressable { filtersOpen = true }
                                .semantics(mergeDescendants = true) {
                                    role = Role.Button
                                    contentDescription = if (filterCount > 0) "Фильтры, включено $filterCount" else "Фильтры"
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            AppIcon(AppIcons.filter, size = 16.dp)
                        }
                        // Бейдж с числом включённых фильтров.
                        if (filterCount > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 4.dp, y = (-4).dp)
                                    .size(20.dp)
                                    .background(T.Green, CircleShape)
                                    .clearAndSetSemantics {},
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(filterCount.toString(), style = AppFont.sans(12.sp, FontWeight.SemiBold))
                            }
                        }
                    }
                }

                // Включённые фильтры чипами: крестик снимает один, «Сбросить» — все.
                val chips = applied.activeChips()
                if (chips.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        chips.forEach { (id, label) ->
                            Row(
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .pressable(pressAlpha = 0.7f) { applyFilters(applied.without(id)) }
                                    .semantics(mergeDescendants = true) {
                                        role = Role.Button
                                        contentDescription = "Убрать фильтр $label"
                                    },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    modifier = Modifier
                                        .height(32.dp)
                                        .liquidGlass(CapsuleShape, Fills.Active)
                                        .padding(start = 12.dp, end = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(label, style = AppFont.sans(T.FsBase), maxLines = 1)
                                    AppIcon(AppIcons.close, size = 16.dp)
                                }
                            }
                        }
                        Box(
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .pressable(pressAlpha = 0.6f) { applyFilters(FiltersResult()) }
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text("Сбросить", style = AppFont.sans(T.FsBase, color = T.TextSecondary)) }
                    }
                }

                if (!loaded) {
                    ReportListSkeleton(cards = 4)
                } else if (failed && !hasData) {
                    LoadErrorState(onRetry = { retry() })
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (failed) StaleDataBanner(savedAt, onRetry = { retry() })
                        visible.forEach { report ->
                            ReportCard(report = report, onOpen = { onOpenReport(report.id) })
                        }
                        if (visible.isEmpty()) {
                            val narrowed = filterCount > 0 || query.isNotBlank()
                            EmptyState(
                                if (narrowed) "Ничего не найдено" else "Материалов пока нет",
                                actionLabel = if (narrowed) "Сбросить фильтры" else null,
                                onAction = { query = ""; applyFilters(FiltersResult()) },
                            )
                        }
                    }
                }
            }
        }
    }
}
