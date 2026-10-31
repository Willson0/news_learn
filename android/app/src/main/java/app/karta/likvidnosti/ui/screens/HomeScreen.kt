package app.karta.likvidnosti.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import app.karta.likvidnosti.data.AppData
import app.karta.likvidnosti.data.Repository
import app.karta.likvidnosti.data.api.ReportDto
import app.karta.likvidnosti.ui.components.EmptyState
import app.karta.likvidnosti.ui.components.LoadErrorState
import app.karta.likvidnosti.ui.components.MaterialChips
import app.karta.likvidnosti.ui.components.StaleDataBanner
import app.karta.likvidnosti.ui.components.ReportCard
import app.karta.likvidnosti.ui.components.ReportListSkeleton
import app.karta.likvidnosti.ui.components.ScreenTitle
import app.karta.likvidnosti.ui.components.TabScaffold
import app.karta.likvidnosti.ui.components.PullToRefreshBox
import app.karta.likvidnosti.ui.theme.T
import app.karta.likvidnosti.ui.theme.designTop
import app.karta.likvidnosti.ui.theme.navBarClearance
import app.karta.likvidnosti.data.api.onFailureNotCancelled
import app.karta.likvidnosti.ui.components.SlowNetworkHint

/**
 * «Лента материалов» (iOS HomeView): заголовок Kreadon 24 на y = 94, чипы
 * материалов, карточки отчётов через 12; навбар поверх.
 */
@Composable
fun HomeScreen(onOpenReport: (Long) -> Unit, navBar: @Composable () -> Unit) {
    var filter by rememberSaveable { mutableStateOf("all") }
    // Для фильтра «все» берём предзагруженные данные — экран открывается без прогрузки.
    var reports by remember { mutableStateOf(AppData.reports ?: emptyList<ReportDto>()) }
    var loaded by remember { mutableStateOf(AppData.reports != null) }
    // Данные на экране не с сервера только что: сбой загрузки (с кэшем или без).
    var failed by remember { mutableStateOf(false) }
    var hasData by remember { mutableStateOf(AppData.reports != null) }
    var savedAt by remember { mutableStateOf(AppData.reportsAt) }
    var refreshing by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    suspend fun fetch() {
        runCatching { Repository.reports(instrument = if (filter == "all") null else filter) }
            .onSuccess {
                reports = it
                failed = false
                hasData = true
                savedAt = System.currentTimeMillis()
                if (filter == "all") {
                    AppData.reports = it
                    AppData.reportsAt = savedAt
                }
            }
            .onFailureNotCancelled { failed = true }
    }

    LaunchedEffect(filter) {
        if (filter == "all" && AppData.reports != null) {
            reports = AppData.reports!!
            savedAt = AppData.reportsAt
            hasData = true
            loaded = true
        } else if (filter != "all") {
            // Прежний список виден, пока грузится новый, но при сбое он к фильтру не относится.
            hasData = false
        }
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

    TabScaffold(navBar = navBar, overlay = { SlowNetworkHint(active = !loaded, bottom = navBarClearance()) }) {
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
                ScreenTitle("Лента материалов")
                MaterialChips(value = filter, onSelect = { filter = it })
                if (!loaded) {
                    ReportListSkeleton(cards = 4)
                } else if (failed && !hasData) {
                    LoadErrorState(onRetry = { retry() })
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (failed) StaleDataBanner(savedAt, onRetry = { retry() })
                        reports.forEach { report ->
                            ReportCard(report = report, onOpen = { onOpenReport(report.id) })
                        }
                        if (reports.isEmpty()) {
                            EmptyState(
                                if (filter == "all") "Материалов пока нет" else "По этому фильтру материалов пока нет",
                                actionLabel = if (filter == "all") null else "Показать все",
                                onAction = { filter = "all" },
                            )
                        }
                    }
                }
            }
        }
    }
}
