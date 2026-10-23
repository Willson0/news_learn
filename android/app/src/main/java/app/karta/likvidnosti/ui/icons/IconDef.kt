package app.karta.likvidnosti.ui.icons

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.karta.likvidnosti.ui.theme.T

/*
 * Иконки макета Figma — порт iOS `Icons/AppIcons.swift` + `Icons/FigmaIcons.swift`.
 * IconDef = «коробка» (viewBox) + части (SVG-путь / скруглённый прямоугольник /
 * окружность) с заливкой или обводкой. Цвет части null = currentColor (цвет,
 * переданный в AppIcon), иначе — фиксированный (иконки материалов, красная корзина).
 */

/** Геометрия одной части иконки (IconGeom). */
sealed class IconGeom {
    class SvgPath(val d: String) : IconGeom()
    class RRect(val x: Float, val y: Float, val w: Float, val h: Float, val r: Float) : IconGeom()
    class Circle(val cx: Float, val cy: Float, val r: Float) : IconGeom()
}

/** Стиль части: заливка (с even-odd) или обводка. */
@Immutable
data class IconStyle(
    val stroke: Boolean,
    val eoFill: Boolean = false,
    val width: Float = 1.6f,
    val cap: StrokeCap = StrokeCap.Round,
    val join: StrokeJoin = StrokeJoin.Round,
    val opacity: Float = 1f,
    val color: Color? = null,
)

/** Часть иконки (IconPart). */
@Immutable
class IconPart(val geom: IconGeom, val style: IconStyle, val dx: Float = 0f, val dy: Float = 0f) {
    /** Путь в координатах коробки; парсится один раз. */
    val path: Path by lazy {
        val p = when (geom) {
            is IconGeom.SvgPath -> PathParser().parsePathString(geom.d).toPath()
            is IconGeom.RRect -> Path().apply {
                addRoundRect(RoundRect(Rect(geom.x, geom.y, geom.x + geom.w, geom.y + geom.h), CornerRadius(geom.r, geom.r)))
            }
            is IconGeom.Circle -> Path().apply {
                addOval(Rect(geom.cx - geom.r, geom.cy - geom.r, geom.cx + geom.r, geom.cy + geom.r))
            }
        }
        p.fillType = if (style.eoFill) PathFillType.EvenOdd else PathFillType.NonZero
        p
    }

    fun moved(dx: Number, dy: Number) = IconPart(geom, style, this.dx + dx.toFloat(), this.dy + dy.toFloat())
}

/** Иконка: коробка + части (IconDef). */
@Immutable
class IconDef(val box: Size, val parts: List<IconPart>)

fun box(w: Number, h: Number) = Size(w.toFloat(), h.toFloat())

/** Заливка SVG-пути (fp в iOS). */
fun fp(d: String, opacity: Number = 1, eo: Boolean = false, color: Color? = null) =
    IconPart(IconGeom.SvgPath(d), IconStyle(stroke = false, eoFill = eo, opacity = opacity.toFloat(), color = color))

/** Обводка SVG-пути (sp в iOS). */
fun sp(
    d: String,
    w: Number = 1.6,
    cap: StrokeCap = StrokeCap.Round,
    join: StrokeJoin = StrokeJoin.Round,
    color: Color? = null,
) = IconPart(IconGeom.SvgPath(d), IconStyle(stroke = true, width = w.toFloat(), cap = cap, join = join, color = color))

internal fun sRRect(x: Number, y: Number, w: Number, h: Number, r: Number, sw: Number = 1.6) = IconPart(
    IconGeom.RRect(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), r.toFloat()),
    IconStyle(stroke = true, width = sw.toFloat()),
)

internal fun sCircle(cx: Number, cy: Number, r: Number, sw: Number = 1.6) = IconPart(
    IconGeom.Circle(cx.toFloat(), cy.toFloat(), r.toFloat()),
    IconStyle(stroke = true, width = sw.toFloat()),
)

/**
 * Иконка макета (iOS `Icon(def, size:, color:)`): коробка вписывается в квадрат
 * [size] по центру, части без своего цвета красятся в [tint].
 * [flipX] — зеркально по горизонтали (шеврон «вперёд» из «назад»).
 */
@Composable
fun AppIcon(
    icon: IconDef,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = T.Text,
    flipX: Boolean = false,
) {
    Canvas(modifier = modifier.size(size)) {
        val s = minOf(this.size.width / icon.box.width, this.size.height / icon.box.height)
        val ox = (this.size.width - icon.box.width * s) / 2
        val oy = (this.size.height - icon.box.height * s) / 2
        scale(if (flipX) -1f else 1f, 1f, pivot = Offset(this.size.width / 2, this.size.height / 2)) {
            translate(ox, oy) {
                scale(s, s, pivot = Offset.Zero) {
                    icon.parts.forEach { part ->
                        val c = part.style.color ?: tint
                        translate(part.dx, part.dy) {
                            if (part.style.stroke) {
                                drawPath(
                                    part.path,
                                    color = c,
                                    alpha = part.style.opacity,
                                    style = Stroke(width = part.style.width, cap = part.style.cap, join = part.style.join),
                                )
                            } else {
                                drawPath(part.path, color = c, alpha = part.style.opacity, style = Fill)
                            }
                        }
                    }
                }
            }
        }
    }
}
