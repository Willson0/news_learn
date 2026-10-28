package app.karta.likvidnosti.ui.components

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.viewinterop.AndroidView
import app.karta.likvidnosti.ui.theme.T

/** Что показывать в блоке «График» отчёта по значению поля «График» из админки (iOS ChartSource). */
sealed class ChartSource {
    /** Готовая html-страница: виджет TradingView по тикеру или вставленный код виджета. */
    data class Html(val html: String) : ChartSource()

    /** Обычная ссылка (например, сохранённый график TradingView) — открывается внутри блока. */
    data class Url(val url: String) : ChartSource()

    companion object {
        /**
         * Ссылка TradingView с тикером (`/symbols/OANDA-XAUUSD/`, `?symbol=OANDA:XAUUSD`)
         * превращается в виджет графика: в блоке остаётся только график, без сайта вокруг.
         * Код виджета («Get a widget») вставляется как есть.
         */
        fun parse(raw: String?): ChartSource? {
            val value = raw?.trim().orEmpty()
            if (value.isEmpty()) return null
            if (value.contains("<")) return Html(page(value))
            val text = if (value.lowercase().startsWith("http")) value else "https://$value"
            val uri = runCatching { Uri.parse(text) }.getOrNull() ?: return null
            val host = uri.host?.lowercase() ?: return null
            if (host.endsWith("tradingview.com")) {
                symbol(uri)?.let { return Html(widgetPage(it)) }
            }
            return Url(text)
        }

        private fun symbol(uri: Uri): String? {
            val names = runCatching { uri.queryParameterNames }.getOrDefault(emptySet())
            names.firstOrNull { it.equals("symbol", ignoreCase = true) }
                ?.let { uri.getQueryParameter(it) }
                ?.takeIf { it.isNotEmpty() }
                ?.let { return it }
            val parts = uri.pathSegments
            val i = parts.indexOfFirst { it.equals("symbols", ignoreCase = true) }
            if (i >= 0 && i + 1 < parts.size) {
                // OANDA-XAUUSD → OANDA:XAUUSD (биржа и тикер через двоеточие).
                return parts[i + 1].replaceFirst('-', ':')
            }
            return null
        }

        private fun widgetPage(symbol: String): String {
            val safe = symbol.filter { it.isLetterOrDigit() || it in ":._!-" }
            val body = """
                <div class="tradingview-widget-container" style="height:100%;width:100%">
                  <div id="tv_chart" style="height:100%;width:100%"></div>
                </div>
                <script src="https://s3.tradingview.com/tv.js"></script>
                <script>
                  new TradingView.widget({
                    autosize: true, symbol: "$safe", interval: "D", timezone: "Europe/Moscow",
                    theme: "dark", style: "1", locale: "ru", backgroundColor: "#25262A",
                    gridColor: "rgba(255,255,255,0.06)", hide_side_toolbar: true,
                    allow_symbol_change: false, save_image: false, container_id: "tv_chart"
                  });
                </script>
            """.trimIndent()
            return page(body)
        }

        private fun page(body: String): String = """
            <!doctype html><html><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1">
            <style>html,body{margin:0;padding:0;height:100%;background:#25262A;overflow:hidden}
            .tradingview-widget-container,iframe{height:100%!important;width:100%!important}</style>
            </head><body>$body</body></html>
        """.trimIndent()
    }
}

/**
 * Блок «График» под кнопками отчёта (Figma «Аналитика отчет график внутри»):
 * плашка #25262A со скруглением 24, внутри — график из ссылки админа.
 * Пока страница грузится — мигающий блок.
 */
@Composable
fun ChartEmbedView(source: ChartSource, modifier: Modifier = Modifier) {
    var loading by remember(source) { mutableStateOf(true) }
    val shown by animateFloatAsState(if (loading) 0f else 1f, tween(250), label = "chart")
    Box(modifier = modifier.clip(T.ShapeCard).background(T.Surface)) {
        ChartWebView(source, Modifier.fillMaxSize().alpha(shown)) { loading = false }
        if (loading) LoadingBlock(Modifier.fillMaxSize())
    }
}

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
private fun ChartWebView(source: ChartSource, modifier: Modifier, onLoaded: () -> Unit) {
    val onLoadedState = remember { mutableStateOf(onLoaded) }
    onLoadedState.value = onLoaded
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                // Жесты внутри графика (сдвиг, масштаб) не прокручивают экран отчёта.
                setOnTouchListener { v, event ->
                    if (event.action == MotionEvent.ACTION_DOWN) v.parent?.requestDisallowInterceptTouchEvent(true)
                    false
                }
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        // Виджету нужно мгновение, чтобы нарисовать график после загрузки скрипта.
                        Handler(Looper.getMainLooper()).postDelayed({ onLoadedState.value() }, 400)
                    }

                    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                        if (request?.isForMainFrame == true) onLoadedState.value()
                    }
                }
                tag = source
                load(source)
            }
        },
        update = { web ->
            if (web.tag != source) {
                web.tag = source
                web.load(source)
            }
        },
    )
}

private fun WebView.load(source: ChartSource) {
    when (source) {
        is ChartSource.Html -> loadDataWithBaseURL("https://www.tradingview.com", source.html, "text/html", "utf-8", null)
        is ChartSource.Url -> loadUrl(source.url)
    }
}
