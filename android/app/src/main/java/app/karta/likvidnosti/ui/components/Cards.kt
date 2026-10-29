package app.karta.likvidnosti.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.karta.likvidnosti.data.api.ReportDto
import app.karta.likvidnosti.data.fileUrl
import app.karta.likvidnosti.ui.icons.AppIcon
import app.karta.likvidnosti.ui.icons.AppIcons
import app.karta.likvidnosti.ui.theme.AppFont
import app.karta.likvidnosti.ui.theme.T

/**
 * Block/Карточка отчета (iOS ReportCard): плашка «фон плашки», обложка 162,
 * справа сверху — бейдж статуса; заголовок Min Sans Bold 16, дата 14
 * «белый неактив», описание Regular 14 (не больше трёх строк), иконка
 * материала 20. Нажимается вся карточка, справа внизу — шеврон.
 */
@Composable
fun ReportCard(
    report: ReportDto,
    onOpen: (ReportDto) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(T.ShapeCard)
            .background(T.Surface)
            .pressable(pressAlpha = 0.8f) { onOpen(report) }
            .semantics(mergeDescendants = true) {
                role = Role.Button
                onClick(label = "Открыть отчет") { onOpen(report); true }
            }
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(162.dp)) {
            ReportCover(
                fileUrl(report.coverUrl),
                Modifier.fillMaxWidth().height(162.dp).clip(T.ShapeCard),
            )
            val badge = report.badge
            if (!badge.isNullOrEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .clip(T.ShapeCard)
                        .background(T.Badge)
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Text(badge, style = AppFont.sans(T.FsSm, FontWeight.SemiBold))
                }
            }
        }

        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Row(modifier = Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            report.title,
                            style = AppFont.sans(T.FsMd, FontWeight.Bold),
                            modifier = Modifier.widthIn(max = 260.dp),
                        )
                        val date = report.date
                        if (!date.isNullOrEmpty()) {
                            Text(date, style = AppFont.sans(T.FsBase, color = T.TextSecondary))
                        }
                    }
                    if (report.description.isNotEmpty()) {
                        Text(
                            report.description,
                            style = AppFont.sans(T.FsBase),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.size(0.dp))
                Column(
                    modifier = Modifier.fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                        AppIcon(AppIcons.material(report.material), size = 17.5.dp)
                    }
                    AppIcon(AppIcons.chevronRight, size = 20.dp, tint = T.TextSecondary)
                }
            }
        }
    }
}
