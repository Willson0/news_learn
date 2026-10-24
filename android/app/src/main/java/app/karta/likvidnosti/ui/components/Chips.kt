package app.karta.likvidnosti.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.karta.likvidnosti.ui.icons.AppIcon
import app.karta.likvidnosti.ui.icons.AppIcons
import app.karta.likvidnosti.ui.icons.IconDef
import app.karta.likvidnosti.ui.theme.AppFont
import app.karta.likvidnosti.ui.theme.T
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class MaterialItem(val key: String, val label: String, val icon: IconDef?)

val MATERIALS = listOf(
    MaterialItem("all", "Все", null),
    MaterialItem("gold", "Золото", AppIcons.matGold),
    MaterialItem("silver", "Серебро", AppIcons.matSilver),
    MaterialItem("platinum", "Платина", AppIcons.matPlat),
    MaterialItem("wti", "Нефть WTI", AppIcons.matOil),
    MaterialItem("brent", "Нефть Brent", AppIcons.matOil),
    MaterialItem("usd", "USD", AppIcons.matUsd),
    MaterialItem("eur", "EUR", AppIcons.matEur),
    MaterialItem("gas", "Натуральный газ", AppIcons.matGas),
    MaterialItem("btc", "Биткоин", AppIcons.matBtc),
)

/** Прототип: «Слайдер» на главной, CHANGE_TO SMART_ANIMATE 800 мс, ease-in-out-back. */
private val LensSpringF = figmaTween<Float>()

/**
 * Горизонтальная лента чипов материалов (iOS MaterialChips): стеклянная
 * плашка 56, пункты 48 (мин. ширина 48, отступы 12); выбранный подсвечен
 * пурпурной линзой, которая «переезжает» между пунктами с пружиной, лента
 * докручивает выбранный пункт к центру.
 */
@Composable
fun MaterialChips(
    value: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val bounds = remember { mutableStateMapOf<String, Pair<Float, Float>>() }
    var viewport by remember { mutableIntStateOf(0) }
    val target = bounds[value]
    val lensX = remember { Animatable(0f) }
    val lensW = remember { Animatable(0f) }
    var lensShown by remember { mutableStateOf(false) }

    // Первое появление — без анимации, дальше линза переезжает с пружиной.
    LaunchedEffect(target) {
        val t = target ?: return@LaunchedEffect
        if (!lensShown) {
            lensX.snapTo(t.first)
            lensW.snapTo(t.second)
            lensShown = true
        } else {
            launch { lensX.animateTo(t.first, LensSpringF) }
            lensW.animateTo(t.second, LensSpringF)
        }
    }

    LaunchedEffect(value, target != null) {
        val t = bounds[value] ?: return@LaunchedEffect
        val to = (t.first + t.second / 2 - viewport / 2f).roundToInt().coerceIn(0, scroll.maxValue)
        scroll.animateScrollTo(to)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .liquidGlass(T.ShapeCard)
            .clip(T.ShapeCard)
            .onSizeChanged { viewport = it.width },
    ) {
        Box(modifier = Modifier.horizontalScroll(scroll).padding(4.dp)) {
            if (lensShown) {
                SelectionLens(
                    Modifier
                        .offset { IntOffset(lensX.value.roundToInt(), 0) }
                        .width(with(density) { lensW.value.toDp() })
                        .height(48.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                MATERIALS.forEach { item ->
                    Row(
                        modifier = Modifier
                            .height(48.dp)
                            .widthIn(min = 48.dp)
                            .onPlaced { c ->
                                val b = c.positionInParent().x to c.size.width.toFloat()
                                if (bounds[item.key] != b) bounds[item.key] = b
                            }
                            .pressable(pressAlpha = 1f) { if (value != item.key) onSelect(item.key) }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                    ) {
                        Text(item.label, style = AppFont.sans(T.FsBase), maxLines = 1)
                        if (item.icon != null) AppIcon(item.icon, size = 16.dp)
                    }
                }
            }
        }
    }
}
