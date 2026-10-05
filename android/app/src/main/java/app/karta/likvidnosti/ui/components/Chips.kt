package app.karta.likvidnosti.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.karta.likvidnosti.ui.icons.AppImage
import app.karta.likvidnosti.ui.icons.AppIcons
import app.karta.likvidnosti.ui.theme.T

data class MaterialItem(val key: String, val label: String, val hasIcon: Boolean)

val MATERIALS = listOf(
    MaterialItem("all", "Все", false),
    MaterialItem("gold", "Золото", true),
    MaterialItem("silver", "Серебро", true),
    MaterialItem("platinum", "Платина", true),
    MaterialItem("wti", "Нефть WTI", true),
    MaterialItem("brent", "Нефть Brent", true),
    MaterialItem("usd", "USD", true),
    MaterialItem("eur", "EUR", true),
    MaterialItem("gas", "Натуральный газ", true),
    MaterialItem("btc", "Биткоин", true),
)

@Composable
fun MaterialChips(
    value: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(56.dp)
            .clip(T.ShapeCard)
            .background(T.SurfaceMuted)
            .horizontalScroll(rememberScrollState())
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MATERIALS.forEach { item ->
            val active = value == item.key
            val base = Modifier
                .height(48.dp)
                .clip(T.ShapeChip)
            Row(
                modifier = (if (active) base.background(T.GradientAccentPurple) else base)
                    .clickable { onSelect(item.key) }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(item.label, color = T.Text, fontSize = T.FsBase)
                if (item.hasIcon) {
                    AppImage(AppIcons.material(item.key), size = 16.dp)
                }
            }
        }
    }
}
