package app.karta.likvidnosti.ui.components

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.karta.likvidnosti.ui.icons.AppIcon
import app.karta.likvidnosti.ui.icons.AppIcons
import app.karta.likvidnosti.ui.icons.IconDef
import app.karta.likvidnosti.ui.theme.AppFont
import app.karta.likvidnosti.ui.theme.T
import app.karta.likvidnosti.ui.theme.designTop
import app.karta.likvidnosti.ui.theme.hex
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

// Стеклянные элементы и заливки из UI-кита макета — порт iOS `Components/Glass.swift`.

/** Капсула (iOS `Capsule()`). */
val CapsuleShape = RoundedCornerShape(percent = 50)

/**
 * Линейный градиент в семантике CSS `linear-gradient(<angle>, …)`, как его
 * отдаёт Figma (iOS `CSSLinearGradient`): угол от «вверх» по часовой, линия
 * проходит через центр и имеет длину |w·sinθ| + |h·cosθ|. `squareAngle` —
 * угол на квадратном элементе; для w×h пересчитывается как atan(tan(a)·h/w).
 * Позиции стопов вне [0, 1] поддерживаются (линия удлиняется).
 */
@Immutable
class CssLinearGradient(
    private val squareAngle: Double,
    private val stops: List<Pair<Float, Color>>,
) : ShaderBrush() {
    override fun createShader(size: Size): Shader {
        val w = max(size.width, 1f).toDouble()
        val h = max(size.height, 1f).toDouble()
        val a = squareAngle * PI / 180
        val theta = atan2(sin(a) * h, cos(a) * w)
        val dx = sin(theta)
        val dy = -cos(theta)
        val len = abs(w * dx) + abs(h * dy)
        val sx = w / 2 - dx * len / 2
        val sy = h / 2 - dy * len / 2
        val ex = w / 2 + dx * len / 2
        val ey = h / 2 + dy * len / 2
        val first = stops.first().first
        val last = stops.last().first
        val span = if (last - first == 0f) 1f else last - first
        fun at(t: Float) = Offset((sx + (ex - sx) * t).toFloat(), (sy + (ey - sy) * t).toFloat())
        return LinearGradientShader(
            from = at(first),
            to = at(last),
            colors = stops.map { it.second },
            colorStops = stops.map { (it.first - first) / span },
            tileMode = TileMode.Clamp,
        )
    }

    override fun equals(other: Any?): Boolean =
        other is CssLinearGradient && other.squareAngle == squareAngle && other.stops == stops

    override fun hashCode(): Int = squareAngle.hashCode() * 31 + stops.hashCode()
}

/**
 * Заливка стеклянного элемента: один или несколько слоёв (снизу вверх).
 * Аналог `fill:` в iOS `liquidGlass(_:fill:)`.
 */
@Immutable
class GlassFill(val layers: List<Brush>) {
    constructor(color: Color) : this(listOf(SolidColor(color)))
    constructor(brush: Brush) : this(listOf(brush))
}

/** Готовые заливки макета (iOS ActiveFill / PurpleFill / PinkFill / Theme.*). */
object Fills {
    /** «Second color» 20 % — обычное стекло (Theme.glassFill). */
    val Glass = GlassFill(T.GlassFill)

    /**
     * «active» (iOS ActiveFill): два слоя по 20 % —
     * диагональ #FFFF00 (4 %) → #1BEB00 (70 %) и 90° #FFD900 → #1C4A00.
     */
    val Active = GlassFill(
        listOf(
            CssLinearGradient(
                38.6133,
                listOf(0.042691f to hex("#ffff00").copy(alpha = 0.2f), 0.70449f to hex("#1beb00").copy(alpha = 0.2f)),
            ),
            Brush.horizontalGradient(listOf(hex("#ffd900").copy(alpha = 0.2f), hex("#1c4a00").copy(alpha = 0.2f))),
        ),
    )

    /** «акцент 3» (iOS PurpleFill): #572546 (30 %) → #BD5197 (111 %), обе 60 %. */
    val Purple = GlassFill(
        CssLinearGradient(
            83.9115,
            listOf(0.30441f to hex("#572546").copy(alpha = 0.6f), 1.1103f to hex("#bd5197").copy(alpha = 0.6f)),
        ),
    )

    /** Розово-малиновая «Обсудить» (iOS PinkFill): #DA517D → #FF0052, 40 %. */
    val Pink = GlassFill(
        CssLinearGradient(
            269.0,
            listOf(0.012609f to hex("#da517d").copy(alpha = 0.4f), 0.96049f to hex("#ff0052").copy(alpha = 0.4f)),
        ),
    )

    /** Навбар: Theme.navbarBg + Theme.surfaceMuted. */
    val NavBar = GlassFill(listOf(SolidColor(T.NavbarBg), SolidColor(T.SurfaceMuted)))

    /** «чёрный 2» — попапы, календарь, диалог. */
    val Black2 = GlassFill(T.Black2)

    /** «red» — опасные кнопки. */
    val Danger = GlassFill(T.Danger)
}

// MARK: - Подложка для размытия (backdrop blur)

/**
 * Источник подложки для размытия под стеклом. Содержимое, обёрнутое в
 * [backdropSource], записывается в GraphicsLayer; стеклянные элементы,
 * лежащие поверх (и получившие его через [LocalBackdrop]), рисуют под собой
 * размытую копию — как BackdropBlur в iOS. Работает на Android 12+ (API 31);
 * ниже — только полупрозрачная заливка и кромка.
 */
class Backdrop internal constructor(internal val layer: GraphicsLayer) {
    internal var origin: Offset = Offset.Zero
    internal var recorded: Boolean = false
}

/** Подложка, доступная стеклу; null — размытия нет (элемент на непрозрачной плашке). */
val LocalBackdrop = staticCompositionLocalOf<Backdrop?> { null }

private val BlurSupported: Boolean get() = Build.VERSION.SDK_INT >= 31

@Composable
fun rememberBackdrop(): Backdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { Backdrop(layer) }
}

/** Записывает содержимое в подложку [backdrop] (и рисует его как обычно). */
fun Modifier.backdropSource(backdrop: Backdrop): Modifier = this
    .onGloballyPositioned { backdrop.origin = it.positionInRoot() }
    .drawWithContent {
        if (BlurSupported) {
            backdrop.layer.record { this@drawWithContent.drawContent() }
            backdrop.recorded = true
            drawLayer(backdrop.layer)
        } else {
            drawContent()
        }
    }

/**
 * Контейнер «контент + слой поверх»: [content] пишется в подложку, [overlay]
 * (навбар, верхняя панель, затемнение листа) получает её через [LocalBackdrop].
 */
@Composable
fun BackdropBox(
    modifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
    overlay: @Composable BoxScope.() -> Unit,
) {
    val backdrop = rememberBackdrop()
    Box(modifier = modifier) {
        Box(modifier = Modifier.matchParentSize().backdropSource(backdrop).then(contentModifier)) {
            CompositionLocalProvider(LocalBackdrop provides null) { content() }
        }
        CompositionLocalProvider(LocalBackdrop provides backdrop) { overlay() }
    }
}

/**
 * Размытая подложка под элементом (iOS `BackdropBlur(radius:)`), обрезанная
 * формой [shape]. Без [LocalBackdrop] или ниже API 31 ничего не рисует.
 */
fun Modifier.backdropBlur(radius: Dp, shape: Shape = RectangleShape): Modifier = composed {
    val backdrop = LocalBackdrop.current
    if (backdrop == null || !BlurSupported) return@composed this
    val blurLayer = rememberGraphicsLayer()
    val holder = remember { PositionHolder() }
    this
        .onGloballyPositioned { holder.pos = it.positionInRoot() }
        .drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            val clip = Path().apply { addOutline(outline) }
            val r = radius.toPx()
            onDrawWithContent {
                if (backdrop.recorded) {
                    val off = holder.pos - backdrop.origin
                    blurLayer.renderEffect = BlurEffect(r, r, TileMode.Clamp)
                    blurLayer.record {
                        translate(-off.x, -off.y) { drawLayer(backdrop.layer) }
                    }
                    clipPath(clip) { drawLayer(blurLayer) }
                }
                drawContent()
            }
        }
}

private class PositionHolder {
    var pos: Offset = Offset.Zero
}

// MARK: - Жидкое стекло

/**
 * «Жидкое стекло» макета (GLASS: blur 10, refraction 0.75, depth 9, свет −45°) —
 * порт iOS `liquidGlass(_:fill:)` (вариант ниже iOS 26): размытие подложки
 * (если доступна, см. [BackdropBox]), полупрозрачная заливка [fill], мягкое
 * внутреннее свечение кромки и тонкий блик-обводка — яркий сверху-слева и
 * снизу-справа.
 */
fun Modifier.liquidGlass(shape: Shape = CapsuleShape, fill: GlassFill = Fills.Glass): Modifier =
    this
        .backdropBlur(10.dp, shape)
        .drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            val path = Path().apply { addOutline(outline) }
            val rim = Brush.sweepGradient(
                0.0f to Color.White.copy(alpha = 0.30f),
                0.125f to Color.White.copy(alpha = 0.42f),
                0.375f to Color.White.copy(alpha = 0.06f),
                0.5f to Color.White.copy(alpha = 0.28f),
                0.625f to Color.White.copy(alpha = 0.55f),
                0.875f to Color.White.copy(alpha = 0.06f),
                1.0f to Color.White.copy(alpha = 0.30f),
            )
            val glowWide = Stroke(width = 6.dp.toPx())
            val glowNarrow = Stroke(width = 3.dp.toPx())
            val hairline = Stroke(width = 1.dp.toPx())
            onDrawWithContent {
                clipPath(path) {
                    fill.layers.forEach { drawRect(it) }
                }
                drawContent()
                clipPath(path) {
                    drawPath(path, Color.White.copy(alpha = 0.035f), style = glowWide)
                    drawPath(path, Color.White.copy(alpha = 0.035f), style = glowNarrow)
                }
                drawPath(path, rim, style = hairline)
            }
        }

/** Стекло с одноцветной заливкой. */
fun Modifier.liquidGlass(shape: Shape, fill: Color): Modifier = liquidGlass(shape, GlassFill(fill))

/** Плоская заливка без стекла (cardSurface / background(fill, in: shape)). */
fun Modifier.fillShape(fill: GlassFill, shape: Shape = RectangleShape): Modifier =
    this.drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val path = Path().apply { addOutline(outline) }
        onDrawBehind { clipPath(path) { fill.layers.forEach { drawRect(it) } } }
    }

// MARK: - Нажатие

/**
 * Клик без ripple с пресс-эффектом iOS `PressableStyle` (opacity 0.85 при
 * нажатии). [pressAlpha] = 1 — «plain» кнопка без эффекта.
 */
fun Modifier.pressable(
    enabled: Boolean = true,
    pressAlpha: Float = 0.85f,
    onClick: () -> Unit,
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    this
        .alpha(if (pressed && enabled) pressAlpha else 1f)
        .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
}

/** Подпись для TalkBack у кнопки без текста: «Назад», «Фильтры», «Настройки». */
fun Modifier.a11yButton(label: String): Modifier = semantics(mergeDescendants = true) {
    role = Role.Button
    contentDescription = label
}

// MARK: - Кнопки и плашки

/** Круглая стеклянная кнопка 42×42 (Button/back, Button/Settings, Button/add …). */
@Composable
fun GlassCircleButton(
    icon: IconDef,
    /** Подпись для TalkBack: «Назад», «Удалить», «Скачать». */
    contentDescription: String,
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp,
    size: Dp = 42.dp,
    iconColor: Color = T.Text,
    fill: GlassFill = Fills.Glass,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .size(size)
            .liquidGlass(CircleShape, fill)
            .pressable(onClick = onClick)
            .a11yButton(contentDescription),
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(icon, size = iconSize, tint = iconColor)
    }
}

/** Кнопка «назад» из макета: стеклянный круг 42 с шевроном 22. */
@Composable
fun GlassBackButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    GlassCircleButton(icon = AppIcons.back, contentDescription = "Назад", iconSize = 22.dp, modifier = modifier, onClick = onClick)
}

/** Стеклянная плашка-лейбл (Label/label): «Актуальный», «Сегодня», «Готово». */
@Composable
fun GlassLabel(text: String, modifier: Modifier = Modifier, height: Dp? = null) {
    Box(
        modifier = modifier
            .then(if (height != null) Modifier.height(height) else Modifier)
            .liquidGlass(RoundedCornerShape(24.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = AppFont.sans(T.FsSm, FontWeight.SemiBold))
    }
}

/**
 * Верхняя панель внутреннего экрана: «назад» слева на y = 78 макета,
 * опционально — элемент по центру и справа. Кладётся поверх экрана сверху.
 */
@Composable
fun GlassTopBar(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    center: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = designTop(T.TopBarY))
            .padding(horizontal = T.ScreenX)
            .height(42.dp),
    ) {
        if (center != null) {
            Box(modifier = Modifier.align(Alignment.Center)) { center() }
        }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            GlassBackButton(onClick = onBack)
            Spacer(Modifier.weight(1f))
            if (trailing != null) trailing()
        }
    }
}
