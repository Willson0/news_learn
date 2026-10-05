package app.karta.likvidnosti.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.karta.likvidnosti.ui.theme.T
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private data class Candle(val open: Float, val close: Float, val high: Float, val low: Float, val up: Boolean)

private fun generateCandles(count: Int, start: Float): List<Candle> {
    val list = ArrayList<Candle>(count)
    var price = start
    var seed = 42L
    fun rnd(): Float {
        seed = (seed * 9301 + 49297) % 233280
        return seed / 233280f
    }
    repeat(count) {
        val open = price
        val drift = (rnd() - 0.42f) * 70000f
        val close = max(3_900_000f, open + drift)
        val high = max(open, close) + rnd() * 40000f
        val low = min(open, close) - rnd() * 40000f
        list.add(Candle(open, close, high, low, close >= open))
        price = close
    }
    return list
}

/** Демо-график свечей (аналог CandlestickChart.vue). */
@Composable
fun CandlestickChart(modifier: Modifier = Modifier) {
    val candles = remember { generateCandles(30, 3_980_000f) }
    val minV = 3_900_000f
    val maxV = 4_750_000f
    val step = 50_000f
    val current = 4_450_000f

    Column(
        modifier = modifier
            .clip(T.ShapeCard)
            .background(T.Surface)
            .padding(16.dp),
    ) {
        Text("XAUUSD", color = T.Text, fontSize = T.FsMd, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFD9D9DE)))
            Text("Золото / Доллар США", color = T.TextSecondary, fontSize = T.FsBase)
        }
        Row(
            modifier = Modifier.padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("4 419,315", color = T.Text, fontSize = T.FsBase, fontWeight = FontWeight.SemiBold)
            Text("-54,830 (-1,20%)", color = Color(0xFFFF5A5A), fontSize = T.FsBase)
        }
        Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(T.SurfaceMuted)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text("Объём - Тики ▾", color = T.TextSecondary, fontSize = T.FsSm)
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .aspectRatio(340f / 360f),
        ) {
            drawChart(candles, minV, maxV, step, current)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Диапазон дат ▾", color = T.Text, fontSize = T.FsBase)
            Text("20:48:42 UTC+3", color = T.TextSecondary, fontSize = T.FsBase)
        }
    }
}

private fun DrawScope.drawChart(
    candles: List<Candle>,
    minV: Float,
    maxV: Float,
    step: Float,
    current: Float,
) {
    val padRightFrac = 58f / 340f
    val padBottomFrac = 24f / 360f
    val plotW = size.width * (1f - padRightFrac)
    val plotH = size.height * (1f - padBottomFrac)
    val padRight = size.width * padRightFrac

    fun y(value: Float): Float {
        val t = (value - minV) / (maxV - minV)
        return plotH - t * plotH
    }

    // Сетка
    var v = minV
    while (v <= maxV) {
        val gy = y(v)
        drawLine(Color(0x0FFFFFFF), Offset(0f, gy), Offset(plotW, gy), 1f)
        drawContext.canvas.nativeCanvas.apply {
            drawText(
                "%,d".format(v.toLong()).replace(",", " "),
                plotW + 6f,
                gy + 3f,
                android.graphics.Paint().apply {
                    color = android.graphics.Color.argb(128, 255, 255, 255)
                    textSize = 8.dp.toPx() * 0.8f
                    isAntiAlias = true
                },
            )
        }
        v += step
    }

    // Свечи
    val n = candles.size
    val slot = plotW / n
    val bw = slot * 0.55f
    candles.forEachIndexed { i, c ->
        val cx = slot * i + slot / 2
        val col = if (c.up) Color(0xFFE9E9EE) else Color(0xFF8A8A92)
        drawLine(col, Offset(cx, y(c.high)), Offset(cx, y(c.low)), 1f)
        val bodyY = y(max(c.open, c.close))
        val bodyH = max(1f, abs(y(c.open) - y(c.close)))
        drawRoundRect(
            color = col,
            topLeft = Offset(cx - bw / 2, bodyY),
            size = Size(bw, bodyH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(1f, 1f),
        )
    }

    // Текущая цена
    val cy = y(current)
    drawLine(
        Color(0x59FFFFFF), Offset(0f, cy), Offset(plotW, cy), 1f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 3f)),
    )
    drawRoundRect(
        color = Color(0xFF3A3B40),
        topLeft = Offset(plotW, cy - 9f),
        size = Size(padRight, 18f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f),
    )
    drawContext.canvas.nativeCanvas.apply {
        drawText(
            "%,d".format(current.toLong()).replace(",", " "),
            plotW + 5f,
            cy + 3f,
            android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = 8.dp.toPx() * 0.8f
                isAntiAlias = true
            },
        )
    }

    // Месяцы
    val months = listOf("Июль" to 0.1f, "Авг" to 0.45f, "Сен" to 0.78f)
    months.forEach { (label, fx) ->
        drawContext.canvas.nativeCanvas.apply {
            drawText(
                label, plotW * fx, size.height - 6f,
                android.graphics.Paint().apply {
                    color = android.graphics.Color.argb(153, 255, 255, 255)
                    textSize = 9.dp.toPx() * 0.8f
                    isAntiAlias = true
                },
            )
        }
    }
}
