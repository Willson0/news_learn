package app.karta.likvidnosti.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.karta.likvidnosti.data.api.ReportDto
import app.karta.likvidnosti.data.fileUrl
import app.karta.likvidnosti.ui.icons.AppIcon
import app.karta.likvidnosti.ui.icons.AppIcons
import app.karta.likvidnosti.ui.icons.AppImage
import app.karta.likvidnosti.ui.theme.T
import coil.compose.AsyncImage

@Composable
fun ReportCard(
    report: ReportDto,
    onOpen: (ReportDto) -> Unit,
    modifier: Modifier = Modifier,
    actionLabel: String = "Перейти в отчёт",
) {
    val cover = fileUrl(report.coverUrl)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(T.ShapeCard)
            .background(T.Surface),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Обложка
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(162.dp)
                .clip(T.ShapeCard)
                .then(if (cover == null) Modifier.background(T.Holo) else Modifier),
        ) {
            if (cover != null) {
                AsyncImage(
                    model = cover,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(162.dp),
                )
            }
            if (!report.badge.isNullOrEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .clip(T.ShapeCard)
                        .background(T.Badge)
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Text(report.badge!!, color = T.Text, fontSize = T.FsSm, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Column(
            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(report.title, color = T.Text, fontSize = T.FsMd, fontWeight = FontWeight.Bold)
                    if (!report.date.isNullOrEmpty()) {
                        Text(report.date!!, color = T.TextSecondary, fontSize = T.FsBase)
                    }
                }
                Spacer(Modifier.size(8.dp))
                AppImage(AppIcons.material(report.material), size = 20.dp)
            }
            if (report.description.isNotEmpty()) {
                Text(report.description, color = T.Text, fontSize = T.FsBase, lineHeight = 19.sp)
            }
            AppButton(text = actionLabel, variant = ButtonVariant.Soft) { onOpen(report) }
        }
    }
}

enum class ActionVariant { Accent, Purple, Dark }
enum class BadgeColor { Green, Pink }

@Composable
fun ReportActionButton(
    label: String,
    iconRes: Int,
    onClick: () -> Unit,
    variant: ActionVariant = ActionVariant.Accent,
    badge: BadgeColor = BadgeColor.Green,
    modifier: Modifier = Modifier,
) {
    val base = Modifier
        .fillMaxWidth()
        .height(56.dp)
        .clip(T.ShapePill)
    val styled = when (variant) {
        ActionVariant.Accent -> base.background(T.GradientAccent)
        ActionVariant.Purple -> base.background(T.GradientPurpleBtn)
        ActionVariant.Dark -> base.background(T.SurfaceMuted)
    }
    Row(
        modifier = modifier
            .then(styled)
            .clickable { onClick() }
            .padding(start = 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = T.Text, fontSize = T.FsMd, modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (badge == BadgeColor.Green) Color(0xFF4E9C17) else Color(0xFFD7609F)),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(iconRes, size = 22.dp, tint = Color.White)
        }
    }
}
