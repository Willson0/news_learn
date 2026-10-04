package app.karta.likvidnosti.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.karta.likvidnosti.data.Repository
import app.karta.likvidnosti.data.Session
import app.karta.likvidnosti.data.api.ReportDto
import app.karta.likvidnosti.data.fileUrl
import app.karta.likvidnosti.ui.components.ActionVariant
import app.karta.likvidnosti.ui.components.BadgeColor
import app.karta.likvidnosti.ui.components.CandlestickChart
import app.karta.likvidnosti.ui.components.ReportActionButton
import app.karta.likvidnosti.ui.components.insetBottom
import app.karta.likvidnosti.ui.components.insetTop
import app.karta.likvidnosti.ui.icons.AppIcon
import app.karta.likvidnosti.ui.icons.AppIcons
import app.karta.likvidnosti.ui.icons.AppImage
import app.karta.likvidnosti.ui.theme.T
import coil.compose.AsyncImage

private val DEMO_REPORT = ReportDto(
    title = "Нефть в 2026 году, как она?",
    date = "12.02.26",
    badge = "Актуальный",
    description = "Здесь мы расскажем о состоянии нефти на момент 2026 года и возможное ее будущее",
    material = "wti",
)

@Composable
fun ReportDetailScreen(
    id: Long?,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onDiscuss: (String?) -> Unit,
) {
    var report by remember { mutableStateOf(DEMO_REPORT) }
    var reportOpen by remember { mutableStateOf(false) }
    var graphOpen by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val isAdmin = Session.isAdmin

    LaunchedEffect(id) {
        if (id != null) {
            runCatching { Repository.report(id) }.getOrNull()?.let { report = it }
        }
    }

    val cover = fileUrl(report.coverUrl)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(T.Bg)
            .verticalScroll(rememberScrollState())
            .padding(bottom = insetBottom() + 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Hero
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 420.dp)
                .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFFC9C6D6), Color(0xFFA9ADBD), Color(0xFFCFC3CF),
                            Color(0xFFB7C2BF), Color(0xFFC7BCC9), Color(0xFFA7ADBA), Color(0xFFC4C7D2),
                        ),
                    ),
                ),
        ) {
            if (cover != null) {
                AsyncImage(
                    model = cover,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize().blur(24.dp),
                )
            }
            // Затемняющий градиент
            Box(
                modifier = Modifier.matchParentSize().background(
                    Brush.verticalGradient(
                        0f to Color(0x591A1B1D), 0.35f to Color(0x1A1A1B1D), 1f to Color(0xD91A1B1D),
                    ),
                ),
            )

            if (!report.badge.isNullOrEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = insetTop() + 28.dp)
                        .clip(T.ShapeCard)
                        .background(Color(0xB328282C))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(report.badge!!, color = T.Text, fontSize = T.FsSm)
                }
            }

            // Назад
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = T.ScreenX, top = insetTop() + 24.dp)
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color(0xB328282C))
                    .clickable { onBack() },
                contentAlignment = Alignment.Center,
            ) { AppIcon(AppIcons.Back, size = 22.dp, tint = Color.White) }

            if (isAdmin && id != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = T.ScreenX, top = insetTop() + 24.dp)
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(0xB328282C))
                        .clickable { onEdit(id) },
                    contentAlignment = Alignment.Center,
                ) { AppIcon(AppIcons.Gear, size = 24.dp, tint = Color.White) }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = T.ScreenX)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(report.title, color = T.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        if (!report.date.isNullOrEmpty()) {
                            Text(report.date!!, color = T.TextSecondary, fontSize = T.FsBase, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xB328282C)),
                        contentAlignment = Alignment.Center,
                    ) {
                        AppImage(AppIcons.material(report.material ?: "wti"), size = 18.dp)
                    }
                }
                Text(report.description, color = T.Text, fontSize = T.FsBase, lineHeight = 20.sp, modifier = Modifier.fillMaxWidth())
            }
        }

        // Actions
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = T.ScreenX),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ReportActionButton("Открыть отчёт", AppIcons.Doc, { reportOpen = !reportOpen }, ActionVariant.Accent, BadgeColor.Green)
            ReportActionButton("Обсудить", AppIcons.Chat, { onDiscuss(report.chat) }, ActionVariant.Purple, BadgeColor.Pink)
            ReportActionButton(
                if (graphOpen) "Закрыть график" else "Открыть график",
                AppIcons.ChartBadge,
                {
                    val url = report.chartUrl
                    if (!url.isNullOrEmpty()) runCatching { uriHandler.openUri(url) }
                    else graphOpen = !graphOpen
                },
                ActionVariant.Dark, BadgeColor.Green,
            )
        }

        if (reportOpen) {
            val body = report.body
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = T.ScreenX)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(T.ShapeCard)
                        .background(T.Surface)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        if (!body.isNullOrEmpty()) body
                        else "Нефть остаётся одним из ключевых индикаторов мировой экономики. " +
                            "В 2026 году на цену влияют баланс спроса и предложения, политика ОПЕК+ и курс доллара.",
                        color = T.Text, fontSize = T.FsBase, lineHeight = 21.sp,
                    )
                }
            }
        }

        if (graphOpen) {
            CandlestickChart(modifier = Modifier.fillMaxWidth().padding(horizontal = T.ScreenX))
        }
    }
}
