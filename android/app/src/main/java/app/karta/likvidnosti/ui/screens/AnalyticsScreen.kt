package app.karta.likvidnosti.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.karta.likvidnosti.data.AppData
import app.karta.likvidnosti.data.Repository
import app.karta.likvidnosti.data.api.ReportDto
import app.karta.likvidnosti.ui.components.AppInput
import app.karta.likvidnosti.ui.components.Fills
import app.karta.likvidnosti.ui.components.FiltersOverlay
import app.karta.likvidnosti.ui.components.ReportCard
import app.karta.likvidnosti.ui.components.ReportListSkeleton
import app.karta.likvidnosti.ui.components.ScreenTitle
import app.karta.likvidnosti.ui.components.TabScaffold
import app.karta.likvidnosti.ui.components.liquidGlass
import app.karta.likvidnosti.ui.components.pressable
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
@Composable
fun AnalyticsScreen(onOpenReport: (Long) -> Unit, navBar: @Composable () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var filtersOpen by remember { mutableStateOf(false) }
    var statusFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var reports by remember { mutableStateOf(AppData.reports ?: emptyList<ReportDto>()) }
    var loaded by remember { mutableStateOf(AppData.reports != null) }

    LaunchedEffect(statusFilter) {
        runCatching { Repository.reports(status = statusFilter) }.onSuccess { reports = it }
        // при сбое обновления оставляем предзагруженные данные
        loaded = true
    }

    val visible = remember(query, reports) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) reports
        else reports.filter { it.title.lowercase().contains(q) || it.description.lowercase().contains(q) }
    }

    TabScaffold(
        navBar = navBar,
        overlay = {
            FiltersOverlay(
                open = filtersOpen,
                onClose = { filtersOpen = false },
                onApply = { result -> statusFilter = result.statuses.joinToString(",").ifEmpty { null } },
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
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
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .liquidGlass(CircleShape, Fills.Active)
                        .pressable { filtersOpen = true },
                    contentAlignment = Alignment.Center,
                ) {
                    AppIcon(AppIcons.filter, size = 16.dp)
                }
            }

            if (!loaded) {
                ReportListSkeleton(cards = 4)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    visible.forEach { report ->
                        ReportCard(report = report, onOpen = { onOpenReport(report.id) })
                    }
                    if (visible.isEmpty()) {
                        Text(
                            "Ничего не найдено",
                            style = AppFont.sans(T.FsBase, color = T.TextSecondary),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                        )
                    }
                }
            }
        }
    }
}
