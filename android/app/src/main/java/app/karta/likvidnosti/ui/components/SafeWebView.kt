package app.karta.likvidnosti.ui.components

import android.os.Build
import android.util.Log
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * WebViewClient, который не роняет приложение, если процесс отрисовки WebView упал
 * или система убила его из-за нехватки памяти (тяжёлый HTML-отчёт, графики).
 *
 * По умолчанию onRenderProcessGone возвращает false — и Android завершает всё приложение.
 * Здесь WebView отцепляется и уничтожается, а экран показывает ошибку с «Повторить».
 */
open class SafeWebViewClient(private val onRendererGone: () -> Unit) : WebViewClient() {
    override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
        val crashed = Build.VERSION.SDK_INT >= 26 && detail?.didCrash() == true
        Log.w("SafeWebView", "Процесс WebView завершён (${if (crashed) "сбой" else "нехватка памяти"})")
        view?.let { web ->
            (web.parent as? android.view.ViewGroup)?.removeView(web)
            runCatching { web.destroy() }
        }
        onRendererGone()
        return true
    }
}

/** Освобождает WebView, когда он уходит с экрана (иначе процесс отрисовки и память держатся). */
fun WebView.release() {
    runCatching {
        stopLoading()
        webViewClient = WebViewClient()
        (parent as? android.view.ViewGroup)?.removeView(this)
        destroy()
    }
}
