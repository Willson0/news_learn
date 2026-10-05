package app.karta.likvidnosti.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.karta.likvidnosti.data.Repository
import app.karta.likvidnosti.data.api.ReportDto
import app.karta.likvidnosti.ui.components.MaterialChips
import app.karta.likvidnosti.ui.components.NavBarInset
import app.karta.likvidnosti.ui.components.ReportCard
import app.karta.likvidnosti.ui.components.Screen
import app.karta.likvidnosti.ui.components.ScreenTitle
import app.karta.likvidnosti.ui.theme.T

@Composable
fun HomeScreen(onOpenReport: (Long) -> Unit) {
    var filter by remember { mutableStateOf("all") }
    var reports by remember { mutableStateOf<List<ReportDto>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(filter) {
        loaded = false
        reports = runCatching {
            Repository.reports(instrument = if (filter == "all") null else filter)
        }.getOrDefault(emptyList())
        loaded = true
    }

    Screen(bottomInset = NavBarInset, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Spacer(Modifier.size(36.dp))
        ScreenTitle("Лента материалов")
        MaterialChips(value = filter, onSelect = { filter = it }, modifier = Modifier.fillMaxWidth())
        androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            reports.forEach { report ->
                ReportCard(report = report, onOpen = { onOpenReport(report.id) })
            }
            if (loaded && reports.isEmpty()) {
                Text(
                    "По этому фильтру материалов пока нет",
                    color = T.TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                )
            }
        }
    }
}
