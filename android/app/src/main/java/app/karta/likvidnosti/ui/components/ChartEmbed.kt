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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.karta.likvidnosti.ui.theme.AppFont
import kotlinx.coroutines.delay
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
    /** Вставленный код виджета без тикера — показывается как html. */
    data class Html(val html: String) : ChartSource()

    /** Страница графика TradingView (виджет по тикеру) или обычная ссылка; [original] — для браузера. */
    data class Url(val url: String, val original: String = url) : ChartSource()

    companion object {
        /**
         * Ссылка TradingView с тикером (`/symbols/OANDA-XAUUSD/`, `/chart/?symbol=OANDA:XAUUSD`)
         * и код виджета («Get a widget») с `"symbol": "…"` превращаются в страницу виджета
         * графика: в блоке только график, без сайта вокруг. Страница грузится напрямую,
         * без скрипта-загрузчика tv.js.
         */
        fun parse(raw: String?): ChartSource? {
            val value = raw?.trim().orEmpty()
            if (value.isEmpty()) return null
            if (value.contains("<")) {
                symbolInWidgetCode(value)?.let { widgetUrl(it) }?.let { return Url(it) }
                return Html(page(value))
            }
            // Просто тикер: «OANDA:XAUUSD», «OANDA-XAUUSD» или «XAUUSD».
            if (BareSymbol.matches(value) && (!value.contains('.') || value.contains(':'))) {
                val symbol = if (value.contains(':')) value else value.replace('-', ':')
                widgetUrl(symbol.uppercase())?.let { return Url(it) }
            }
            val text = if (value.lowercase().startsWith("http")) value else "https://$value"
            val uri = runCatching { Uri.parse(text) }.getOrNull() ?: return null
            val host = uri.host?.lowercase() ?: return null
            if (host.endsWith("tradingview.com")) {
                symbol(uri)?.let { widgetUrl(it) }?.let { return Url(it, text) }
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

        private val BareSymbol = Regex("""^[A-Za-z0-9_.!]+([:\-][A-Za-z0-9_.!]+)?$""")

        private val WidgetSymbol = Regex("""["']?symbol["']?\s*:\s*["']([^"']+)["']""")

        /** `"symbol": "OANDA:XAUUSD"` из кода виджета. */
        private fun symbolInWidgetCode(code: String): String? =
            WidgetSymbol.find(code)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }

        /** Страница виджета «Advanced chart» TradingView: тёмная тема, русский, без боковой панели. */
        private fun widgetUrl(symbol: String): String? {
            val safe = symbol.filter { it.isLetterOrDigit() || it in ":._!-" }
            if (safe.isEmpty()) return null
            return Uri.parse("https://s.tradingview.com/widgetembed/").buildUpon()
                .appendQueryParameter("symbol", safe)
                .appendQueryParameter("interval", "D")
                // Без панелей управления TradingView: только сам график.
                .appendQueryParameter("hidesidetoolbar", "1")
                .appendQueryParameter("hidetoptoolbar", "1")
                .appendQueryParameter("hidelegend", "1")
                .appendQueryParameter("withdateranges", "0")
                .appendQueryParameter("enable_publishing", "0")
                .appendQueryParameter("symboledit", "0")
                .appendQueryParameter("saveimage", "0")
                .appendQueryParameter("toolbarbg", "25262A")
                .appendQueryParameter("theme", "dark")
                .appendQueryParameter("style", "1")
                .appendQueryParameter("timezone", "Europe/Moscow")
                .appendQueryParameter("locale", "ru")
                .appendQueryParameter("hideideas", "1")
                .build()
                .toString()
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
    // 0 — грузится, 1 — показан, 2 — не загрузился.
    var state by remember(source) { mutableStateOf(0) }
    val shown by animateFloatAsState(if (state == 1) 1f else 0f, tween(250), label = "chart")
    val uriHandler = LocalUriHandler.current
    LaunchedEffect(source) {
        // Страница так и не ответила — не мигаем бесконечно. TradingView из России может
        // отвечать 15–30 с; если страница догрузится позже, график всё равно покажется.
        delay(45_000)
        if (state == 0) state = 2
    }
    Box(modifier = modifier.clip(T.ShapeCard).background(T.Surface), contentAlignment = Alignment.Center) {
        ChartWebView(
            source,
            Modifier.fillMaxSize().alpha(shown),
            onLoaded = { state = 1 },
            onFailed = { if (state != 1) state = 2 },
        )
        when (state) {
            0 -> LoadingBlock(Modifier.fillMaxSize())
            2 -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Не удалось загрузить график", style = AppFont.sans(T.FsBase, color = T.TextSecondary))
                (source as? ChartSource.Url)?.let { src ->
                    Box(
                        modifier = Modifier
                            .height(40.dp)
                            .liquidGlass(CapsuleShape)
                            .pressable { runCatching { uriHandler.openUri(src.original) } }
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("Открыть в TradingView", style = AppFont.sans(T.FsBase, FontWeight.SemiBold)) }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
private fun ChartWebView(source: ChartSource, modifier: Modifier, onLoaded: () -> Unit, onFailed: () -> Unit) {
    val onLoadedState = remember { mutableStateOf(onLoaded) }
    onLoadedState.value = onLoaded
    val onFailedState = remember { mutableStateOf(onFailed) }
    onFailedState.value = onFailed
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                // Жесты внутри графика (сдвиг, масштаб) не прокручивают экран отчёта.
                setOnTouchListener { v, event ->
                    if (event.action == MotionEvent.ACTION_DOWN) v.parent?.requestDisallowInterceptTouchEvent(true)
                    false
                }
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        // Виджету нужно мгновение, чтобы нарисовать график после загрузки скрипта.
                        Handler(Looper.getMainLooper()).postDelayed({ onLoadedState.value() }, 600)
                    }

                    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                        if (request?.isForMainFrame == true) onFailedState.value()
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
