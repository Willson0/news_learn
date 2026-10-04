package app.karta.likvidnosti.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.karta.likvidnosti.ui.icons.AppIcon
import app.karta.likvidnosti.ui.icons.AppIcons
import app.karta.likvidnosti.ui.theme.T

private data class Tab(val route: String, val icon: Int)

private val TABS = listOf(
    Tab("home", AppIcons.Home),
    Tab("analytics", AppIcons.Analytics),
    Tab("community", AppIcons.Community),
    Tab("profile", AppIcons.Profile),
)

@Composable
fun BottomNav(
    current: String,
    onTab: (String) -> Unit,
    showCreate: Boolean,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Row(
            modifier = Modifier
                .clip(T.ShapePill)
                .background(T.NavbarBg)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(15.dp),
        ) {
            TABS.forEach { tab ->
                val active = current == tab.route
                val base = Modifier.size(40.dp).clip(T.ShapePill)
                Box(
                    modifier = (if (active) base.background(T.GradientAccentSoft) else base)
                        .alpha(if (active) 1f else 0.6f)
                        .clickable { onTab(tab.route) },
                    contentAlignment = Alignment.Center,
                ) {
                    AppIcon(tab.icon, size = 24.dp)
                }
            }
        }
        if (showCreate) {
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .clip(CircleShape)
                    .background(T.GradientAccent)
                    .clickable { onCreate() },
                contentAlignment = Alignment.Center,
            ) {
                AppIcon(AppIcons.Plus, size = 30.dp)
            }
        }
    }
}
