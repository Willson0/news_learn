package app.karta.likvidnosti.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.karta.likvidnosti.data.Repository
import app.karta.likvidnosti.data.api.ReportDto
import app.karta.likvidnosti.ui.components.AppInput
import app.karta.likvidnosti.ui.components.FiltersSheet
import app.karta.likvidnosti.ui.components.NavBarInset
import app.karta.likvidnosti.ui.components.ReportCard
import app.karta.likvidnosti.ui.components.Screen
import app.karta.likvidnosti.ui.components.ScreenTitle
import app.karta.likvidnosti.ui.icons.AppIcon
import app.karta.likvidnosti.ui.icons.AppIcons
import app.karta.likvidnosti.ui.theme.T

@Composable
fun AnalyticsScreen(onOpenReport: (Long) -> Unit) {
    var query by remember { mutableStateOf("") }
    var filtersOpen by remember { mutableStateOf(false) }
    var statusFilter by remember { mutableStateOf<String?>(null) }
    var reports by remember { mutableStateOf<List<ReportDto>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(statusFilter) {
        loaded = false
        reports = runCatching { Repository.reports(status = statusFilter) }.getOrDefault(emptyList())
        loaded = true
    }

    val visible = remember(query, reports) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) reports
        else reports.filter { it.title.lowercase().contains(q) || it.description.lowercase().contains(q) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Screen(bottomInset = NavBarInset, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Spacer(Modifier.size(36.dp))
            ScreenTitle("Список материалов")

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppInput(query, { query = it }, placeholder = "Нужный отчёт или материал", modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(T.ShapeCard)
                        .background(T.GradientAccentSoft)
                        .clickable { filtersOpen = true },
                    contentAlignment = Alignment.Center,
                ) {
                    AppIcon(AppIcons.Filter, size = 20.dp)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                visible.forEach { report ->
                    ReportCard(report = report, onOpen = { onOpenReport(report.id) })
                }
                if (loaded && visible.isEmpty()) {
                    Text(
                        "Ничего не найдено",
                        color = T.TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                    )
                }
            }
        }

        FiltersSheet(
            open = filtersOpen,
            onClose = { filtersOpen = false },
            onApply = { result ->
                statusFilter = result.statuses.joinToString(",").ifEmpty { null }
            },
        )
    }
}
