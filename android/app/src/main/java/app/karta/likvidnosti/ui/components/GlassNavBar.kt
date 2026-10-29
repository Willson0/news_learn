package app.karta.likvidnosti.ui.components

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
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
enum class AppTab(val route: String, val label: String) {
    Home(Routes.HOME, "Главная"),
    Analytics(Routes.ANALYTICS, "Аналитика"),
    Community(Routes.COMMUNITY, "Сообщество"),
    Profile(Routes.PROFILE, "Профиль"),
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

    /** Кнопка справа у каждой вкладки, какой она была на экране (null — кнопки нет). */
    val extras = mutableMapOf<AppTab, NavBarExtra?>()
}

/** Круглая кнопка 64 справа от навбара (админский «+», чат на «Профиле»). */
class NavBarExtra(val icon: IconDef, val label: String, val iconSize: Dp = 30.dp, val onClick: () -> Unit)

/** [restrictedX] — место без подписки (Figma 474:11853: навбар 120, «Сообщество» и «Профиль»), null — пункт скрыт. */
private class NavItem(val tab: AppTab, val icon: IconDef, val x: Dp, val restrictedX: Dp?)

private val AllItems = listOf(
    NavItem(AppTab.Home, AppIcons.navHome, 12.dp, null),
    NavItem(AppTab.Analytics, AppIcons.navAnalytics, 67.dp, null),
    NavItem(AppTab.Community, AppIcons.navCommunity, 122.dp, 12.dp),
    NavItem(AppTab.Profile, AppIcons.navProfile, 178.dp, 68.dp),
)

/** Половина ширины кнопки 64 и зазора 13: на столько капсула смещается из центра. */
private val ExtraShift = (64.dp + 13.dp) / 2

/** Центр кнопки справа от центра экрана, когда группа «капсула + кнопка» по центру. */
private val ExtraCenterX = (230.dp + 13.dp + 64.dp) / 2 - 32.dp

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
    // После оплаты навбар раскрывается на месте (SMART_ANIMATE 800 мс): капсула
    // 120 → 230, пункты разъезжаются, главная и аналитика проявляются.
    val barWidth by animateDpAsState(if (restricted) 120.dp else 230.dp, figmaTween(), label = "barWidth")
    val itemX = AllItems.associate { item ->
        val target = if (restricted) item.restrictedX ?: item.x else item.x
        item.tab to animateDpAsState(target, figmaTween(), label = "x").value
    }
    val itemAlpha = AllItems.associate { item ->
        val target = if (restricted && item.restrictedX == null) 0f else 1f
        item.tab to animateFloatAsState(target, figmaFade(), label = "alpha").value
    }
    val haptics = LocalHapticFeedback.current
    val shownExtra = if (restricted) null else extra

    // Переход между вкладками в прототипе — SMART_ANIMATE 800 мс, ease-in-out-back:
    // линза и подсветка иконки переезжают с прошлой вкладки, капсула сдвигается под
    // новую раскладку (на «Профиле» уезжает влево, освобождая место кнопке чата),
    // кнопка справа проявляется на месте, а кнопка прошлой вкладки растворяется.
    // Проигрывается только при входе на вкладку; при возврате с внутреннего экрана
    // (TabMemory.last == current) навбар сразу в итоговом состоянии.
    val from = remember { TabMemory.last ?: current }
    val entering = from != current
    val prevExtra = remember { if (entering) TabMemory.extras[from] else null }
    val progress = remember { Animatable(if (entering) 0f else 1f) }
    LaunchedEffect(Unit) {
        TabMemory.last = current
        if (entering) progress.animateTo(1f, tween(800, easing = EaseInOutBack))
    }
    SideEffect { TabMemory.extras[current] = shownExtra }

    val p = progress.value
    val shown = p.coerceIn(0f, 1f)
    val startShift = when {
        !entering -> 0.dp
        prevExtra == null && shownExtra != null -> ExtraShift
        prevExtra != null && shownExtra == null -> -ExtraShift
        else -> 0.dp
    }
    // После оплаты кнопка справа появляется так же, как при входе на вкладку:
    // капсула уезжает из центра, кнопка проявляется на месте.
    val unlockShift by animateDpAsState(if (restricted && extra != null) ExtraShift else 0.dp, figmaTween(), label = "unlockShift")
    val unlockAlpha by animateFloatAsState(if (restricted) 0f else 1f, figmaFade(), label = "unlockAlpha")
    val barShift = startShift * (1f - p) + if (shownExtra != null) unlockShift else 0.dp
    val targetX = itemX[current]
    val fromX = itemX[from] ?: targetX
    val lensX = if (targetX != null && fromX != null) fromX + (targetX - fromX) * p else null
    // Кнопка есть на обеих вкладках — растворяется только иконка внутри неё.
    val crossIcon = if (prevExtra != null && shownExtra != null) prevExtra else null

    val bottom = designBottom(T.NavBarBottom)
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
        // «затемнениние» — растушёвка снизу под нижним меню.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(79.dp - 7.dp + (bottom - T.NavBarBottom))
                .background(T.BottomFade),
        )
        Box(modifier = Modifier.padding(bottom = bottom), contentAlignment = Alignment.Center) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .offset(x = barShift)
                        .size(width = barWidth, height = 64.dp)
                        .liquidGlass(CapsuleShape, Fills.NavBar),
                ) {
                    if (lensX != null) {
                        Box(
                            modifier = Modifier
                                .offset(x = lensX, y = 12.dp)
                                .size(40.dp)
                                .liquidGlass(CircleShape, Fills.Active),
                        )
                    }
                    AllItems.forEach { item ->
                        val hidden = restricted && item.restrictedX == null
                        Box(
                            modifier = Modifier
                                .offset(x = itemX.getValue(item.tab), y = 12.dp)
                                .graphicsLayer { alpha = itemAlpha.getValue(item.tab) }
                                .size(40.dp)
                                .pressable(pressAlpha = 1f, enabled = !hidden) {
                                    if (item.tab == current) {
                                        onReselect()
                                    } else {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        TabMemory.last = current
                                        onSelect(item.tab)
                                    }
                                }
                                // TalkBack: «Главная, вкладка, выбрано».
                                .semantics(mergeDescendants = true) {
                                    if (hidden) invisibleToUser()
                                    contentDescription = item.tab.label
                                    role = Role.Tab
                                    selected = item.tab == current
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            // Подсветка иконки растворяется вместе с переездом линзы.
                            val tint = when (item.tab) {
                                current -> lerp(if (entering) T.IconMuted else T.Text, T.Text, shown)
                                from -> lerp(T.Text, T.IconMuted, shown)
                                else -> T.IconMuted
                            }
                            AppIcon(item.icon, size = 24.dp, tint = tint)
                        }
                    }
                }
                if (shownExtra != null) {
                    Box(
                        modifier = Modifier
                            .graphicsLayer { alpha = (if (prevExtra == null && entering) shown else 1f) * unlockAlpha }
                            .size(64.dp)
                            .liquidGlass(CircleShape, GlassFill(T.SurfaceMuted))
                            .pressable {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                shownExtra.onClick()
                            },
                            .semantics(mergeDescendants = true) {
                                role = Role.Button
                                contentDescription = shownExtra.label
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (crossIcon != null) {
                            Box(Modifier.graphicsLayer { alpha = 1f - shown }) {
                                AppIcon(crossIcon.icon, size = crossIcon.iconSize)
                            }
                            Box(Modifier.graphicsLayer { alpha = shown }) {
                                AppIcon(shownExtra.icon, size = shownExtra.iconSize)
                            }
                        } else {
                            AppIcon(shownExtra.icon, size = shownExtra.iconSize)
                        }
                    }
                }
            }
            // Кнопка прошлой вкладки растворяется там, где стояла (слой без пары на
            // новом кадре Figma SMART_ANIMATE исчезает на месте).
            if (shownExtra == null && prevExtra != null && shown < 1f) {
                Box(
                    modifier = Modifier
                        .offset(x = ExtraCenterX)
                        .graphicsLayer { alpha = 1f - shown }
                        .size(64.dp)
                        .liquidGlass(CircleShape, GlassFill(T.SurfaceMuted)),
                    contentAlignment = Alignment.Center,
                ) {
                    AppIcon(prevExtra.icon, size = prevExtra.iconSize)
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
