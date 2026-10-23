package app.karta.likvidnosti.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PullToRefreshBox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import app.karta.likvidnosti.data.AppData
import app.karta.likvidnosti.data.Repository
import app.karta.likvidnosti.data.api.ReportDto
import app.karta.likvidnosti.ui.components.MaterialChips
import app.karta.likvidnosti.ui.components.ReportCard
import app.karta.likvidnosti.ui.components.ReportListSkeleton
import app.karta.likvidnosti.ui.components.ScreenTitle
import app.karta.likvidnosti.ui.components.TabScaffold
import app.karta.likvidnosti.ui.theme.AppFont
import app.karta.likvidnosti.ui.theme.T
import app.karta.likvidnosti.ui.theme.designTop
import app.karta.likvidnosti.ui.theme.navBarClearance

/**
 * «Лента материалов» (iOS HomeView): заголовок Kreadon 24 на y = 94, чипы
 * материалов, карточки отчётов через 12; навбар поверх.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpenReport: (Long) -> Unit, navBar: @Composable () -> Unit) {
    var filter by rememberSaveable { mutableStateOf("all") }
    // Для фильтра «все» берём предзагруженные данные — экран открывается без прогрузки.
    var reports by remember { mutableStateOf(AppData.reports ?: emptyList<ReportDto>()) }
    var loaded by remember { mutableStateOf(AppData.reports != null) }
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun fetch() {
        runCatching { Repository.reports(instrument = if (filter == "all") null else filter) }
            .onSuccess {
                reports = it
                if (filter == "all") AppData.reports = it
            }
    }

    LaunchedEffect(filter) {
        if (filter == "all" && AppData.reports != null) {
            reports = AppData.reports!!
            loaded = true
        }
        fetch()
        // при сбое обновления оставляем предзагруженные данные
        loaded = true
    }

    TabScaffold(navBar = navBar) {
        PullToRefreshBox(
            isRefreshing = refreshing,
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
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = T.ScreenX)
                    .padding(top = designTop(T.TitleTop), bottom = navBarClearance()),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                ScreenTitle("Лента материалов")
                MaterialChips(value = filter, onSelect = { filter = it })
                if (!loaded) {
                    ReportListSkeleton(cards = 4)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        reports.forEach { report ->
                            ReportCard(report = report, onOpen = { onOpenReport(report.id) })
                        }
                        if (reports.isEmpty()) {
                            Text(
                                "По этому фильтру материалов пока нет",
                                style = AppFont.sans(T.FsLg, color = T.TextSecondary),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
