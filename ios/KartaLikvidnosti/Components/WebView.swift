import SwiftUI
import WebKit

/// Обёртка над WKWebView для html-отчётов (аналог <iframe>).
struct WebView: UIViewRepresentable {
    let url: URL

    func makeUIView(context: Context) -> WKWebView {
        let web = WKWebView()
        web.isOpaque = false
        web.backgroundColor = .clear
        web.scrollView.backgroundColor = .clear
        // Не добавлять отступы под статус-бар и home indicator: страница сама уходит под них.
        web.scrollView.contentInsetAdjustmentBehavior = .never
        return web
    }

    func updateUIView(_ web: WKWebView, context: Context) {
        if web.url != url {
            web.load(URLRequest(url: url))
        }
    }
}
