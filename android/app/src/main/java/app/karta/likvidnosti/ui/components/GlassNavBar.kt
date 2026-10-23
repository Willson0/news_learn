package app.karta.likvidnosti.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.karta.likvidnosti.data.Session
import app.karta.likvidnosti.ui.icons.AppIcon
import app.karta.likvidnosti.ui.icons.AppIcons
import app.karta.likvidnosti.ui.icons.IconDef
import app.karta.likvidnosti.ui.nav.Routes
import app.karta.likvidnosti.ui.theme.T
import app.karta.likvidnosti.ui.theme.designBottom

/** Корневые вкладки (iOS `Tab`). */
enum class AppTab(val route: String) {
    Home(Routes.HOME),
    Analytics(Routes.ANALYTICS),
    Community(Routes.COMMUNITY),
    Profile(Routes.PROFILE),
    ;

    companion object {
        fun of(route: String?): AppTab? = entries.firstOrNull { it.route == route }
    }
}

/**
 * Последняя вкладка, чей навбар был на экране: линза навбара новой вкладки
 * при появлении стартует с неё и переезжает на текущую (iOS router.previousTab).
 */
internal object TabMemory {
    var last: AppTab? = null
}

/** Круглая кнопка 64 справа от навбара (админский «+», чат на «Профиле»). */
class NavBarExtra(val icon: IconDef, val iconSize: Dp = 30.dp, val onClick: () -> Unit)

private class NavItem(val tab: AppTab, val icon: IconDef, val x: Dp)

private val AllItems = listOf(
    NavItem(AppTab.Home, AppIcons.navHome, 12.dp),
    NavItem(AppTab.Analytics, AppIcons.navAnalytics, 67.dp),
    NavItem(AppTab.Community, AppIcons.navCommunity, 122.dp),
    NavItem(AppTab.Profile, AppIcons.navProfile, 178.dp),
)

/** Без подписки (Figma 474:11853): навбар 120 — «Сообщество» и «Профиль». */
private val RestrictedItems = listOf(
    NavItem(AppTab.Community, AppIcons.navCommunity, 12.dp),
    NavItem(AppTab.Profile, AppIcons.navProfile, 68.dp),
)

private val LensSpring = spring<Dp>(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow)

/**
 * Нижний навбар из макета (iOS GlassNavBar): стеклянная капсула 230×64,
 * четыре пункта 40×40 (x = 12/67/122/178), активный — стеклянная линза с
 * заливкой «active», неактивные иконки — «белый неактив 2». Справа может стоять
 * круглая стеклянная кнопка 64 ([extra]); группа центрируется, низ — 45 от
 * края экрана, под навбаром затемнение 79. Без подписки — 120, два пункта.
 *
 * Навбар рисуется внутри корневого экрана вкладки (см. [TabScaffold]), поэтому
 * при переходах уезжает и возвращается вместе с экраном.
 */
@Composable
fun GlassNavBar(
    current: AppTab,
    onSelect: (AppTab) -> Unit,
    modifier: Modifier = Modifier,
    onReselect: () -> Unit = {},
    extra: NavBarExtra? = null,
) {
    val restricted = Session.restricted
    val items = if (restricted) RestrictedItems else AllItems
    val barWidth = if (restricted) 120.dp else 230.dp
    val haptics = LocalHapticFeedback.current

    val startX = remember(restricted) {
        val from = TabMemory.last ?: current
        (items.firstOrNull { it.tab == from } ?: items.firstOrNull { it.tab == current })?.x ?: 12.dp
    }
    val lensX = remember(restricted) { Animatable(startX, Dp.VectorConverter) }
    val targetX = items.firstOrNull { it.tab == current }?.x
    LaunchedEffect(targetX) {
        TabMemory.last = current
        if (targetX != null) lensX.animateTo(targetX, LensSpring)
    }

    val bottom = designBottom(T.NavBarBottom)
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
        // «затемнениние» — растушёвка снизу под нижним меню.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(79.dp - 7.dp + (bottom - T.NavBarBottom))
                .background(T.BottomFade),
        )
        Row(
            modifier = Modifier.padding(bottom = bottom),
            horizontalArrangement = Arrangement.spacedBy(13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(width = barWidth, height = 64.dp)
                    .liquidGlass(CapsuleShape, Fills.NavBar),
            ) {
                if (targetX != null) {
                    Box(
                        modifier = Modifier
                            .offset(x = lensX.value, y = 12.dp)
                            .size(40.dp)
                            .liquidGlass(CircleShape, Fills.Active),
                    )
                }
                items.forEach { item ->
                    Box(
                        modifier = Modifier
                            .offset(x = item.x, y = 12.dp)
                            .size(40.dp)
                            .pressable(pressAlpha = 1f) {
                                if (item.tab == current) {
                                    onReselect()
                                } else {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    TabMemory.last = current
                                    onSelect(item.tab)
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        // Иконка чата растворяется между активным и неактивным цветом (прототип: 0.8 с).
                        val tint by animateColorAsState(
                            targetValue = if (item.tab == current) T.Text else T.TextMuted,
                            animationSpec = tween(if (item.tab == AppTab.Community) 800 else 0, easing = EaseInOutBack),
                            label = "navIconTint",
                        )
                        AppIcon(item.icon, size = 24.dp, tint = tint)
                    }
                }
            }
            if (extra != null && !restricted) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .liquidGlass(CircleShape, GlassFill(T.SurfaceMuted))
                        .pressable {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            extra.onClick()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    AppIcon(extra.icon, size = extra.iconSize)
                }
            }
        }
    }
}

/**
 * Корневой экран вкладки (iOS `withGlassNavBar`): фон макета, контент и навбар
 * у нижнего края поверх него; навбар (и [overlay] — листы, попапы) получают
 * размытую подложку контента.
 */
@Composable
fun TabScaffold(
    navBar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    overlay: @Composable BoxScope.() -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    BackdropBox(
        modifier = modifier.fillMaxSize(),
        contentModifier = Modifier.screenBackground(),
        content = content,
        overlay = {
            Box(modifier = Modifier.align(Alignment.BottomCenter)) { navBar() }
            overlay()
        },
    )
}
