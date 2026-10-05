import SwiftUI
import WebKit

/// Обёртка над WKWebView для html-отчётов (аналог <iframe>).
struct WebView: UIViewRepresentable {
    let url: URL

    func makeUIView(context: Context) -> WKWebView {
        let web = WKWebView()
        web.isOpaque = false
        web.backgroundColor = .white
        web.scrollView.backgroundColor = .white
        return web
    }

    func updateUIView(_ web: WKWebView, context: Context) {
        if web.url != url {
            web.load(URLRequest(url: url))
        }
    }
}
