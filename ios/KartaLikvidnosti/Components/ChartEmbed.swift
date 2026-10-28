import SwiftUI
import WebKit

/// Что показывать в блоке «График» отчёта по значению поля «График» из админки.
enum ChartSource: Equatable {
    /// Готовая html-страница: виджет TradingView по тикеру или вставленный код виджета.
    case html(String)
    /// Обычная ссылка (например, сохранённый график TradingView) — открывается внутри блока.
    case url(URL)

    /// Ссылка TradingView с тикером (`/symbols/OANDA-XAUUSD/`, `?symbol=OANDA:XAUUSD`)
    /// превращается в виджет графика: в блоке остаётся только график, без сайта вокруг.
    /// Код виджета («Get a widget») вставляется как есть.
    static func parse(_ raw: String?) -> ChartSource? {
        let value = (raw ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        guard !value.isEmpty else { return nil }
        if value.contains("<") {
            return .html(page(body: value))
        }
        var text = value
        if !text.lowercased().hasPrefix("http") { text = "https://" + text }
        guard let url = URL(string: text), let host = url.host?.lowercased() else { return nil }
        if host.hasSuffix("tradingview.com"), let symbol = symbol(in: url) {
            return .html(widgetPage(symbol: symbol))
        }
        return .url(url)
    }

    private static func symbol(in url: URL) -> String? {
        let items = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems ?? []
        if let s = items.first(where: { $0.name.lowercased() == "symbol" })?.value, !s.isEmpty {
            return s
        }
        let parts = url.pathComponents.filter { $0 != "/" }
        if let i = parts.firstIndex(where: { $0.lowercased() == "symbols" }), i + 1 < parts.count {
            let slug = parts[i + 1]
            // OANDA-XAUUSD → OANDA:XAUUSD (биржа и тикер через двоеточие).
            if let dash = slug.firstIndex(of: "-") {
                return slug.replacingCharacters(in: dash...dash, with: ":")
            }
            return slug
        }
        return nil
    }

    private static func widgetPage(symbol: String) -> String {
        let safe = symbol.filter { $0.isLetter || $0.isNumber || ":._!-".contains($0) }
        let body = """
        <div class="tradingview-widget-container" style="height:100%;width:100%">
          <div id="tv_chart" style="height:100%;width:100%"></div>
        </div>
        <script src="https://s3.tradingview.com/tv.js"></script>
        <script>
          new TradingView.widget({
            autosize: true, symbol: "\(safe)", interval: "D", timezone: "Europe/Moscow",
            theme: "dark", style: "1", locale: "ru", backgroundColor: "#25262A",
            gridColor: "rgba(255,255,255,0.06)", hide_side_toolbar: true,
            allow_symbol_change: false, save_image: false, container_id: "tv_chart"
          });
        </script>
        """
        return page(body: body)
    }

    private static func page(body: String) -> String {
        """
        <!doctype html><html><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1">
        <style>html,body{margin:0;padding:0;height:100%;background:#25262A;overflow:hidden}
        .tradingview-widget-container,iframe{height:100%!important;width:100%!important}</style>
        </head><body>\(body)</body></html>
        """
    }
}

/// Блок «График» под кнопками отчёта (Figma «Аналитика отчет график внутри»):
/// плашка #25262A со скруглением 24, внутри — график из ссылки админа.
/// Пока страница грузится — мигающий блок.
struct ChartEmbedView: View {
    let source: ChartSource
    @State private var loading = true

    var body: some View {
        ZStack {
            ChartWebView(source: source, loading: $loading)
                .opacity(loading ? 0 : 1)
            if loading { LoadingBlock() }
        }
        .background(Theme.surface)
        .clipShape(RoundedRectangle(cornerRadius: Theme.radiusCard, style: .continuous))
        .animation(.easeOut(duration: 0.25), value: loading)
    }
}

private struct ChartWebView: UIViewRepresentable {
    let source: ChartSource
    @Binding var loading: Bool

    func makeCoordinator() -> Coordinator { Coordinator(loading: $loading) }

    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        config.allowsInlineMediaPlayback = true
        let web = WKWebView(frame: .zero, configuration: config)
        web.isOpaque = false
        web.backgroundColor = .clear
        web.scrollView.backgroundColor = .clear
        web.scrollView.contentInsetAdjustmentBehavior = .never
        web.navigationDelegate = context.coordinator
        context.coordinator.load(source, in: web)
        return web
    }

    func updateUIView(_ web: WKWebView, context: Context) {
        context.coordinator.loading = $loading
        if context.coordinator.loaded != source { context.coordinator.load(source, in: web) }
    }

    final class Coordinator: NSObject, WKNavigationDelegate {
        var loading: Binding<Bool>
        var loaded: ChartSource?

        init(loading: Binding<Bool>) { self.loading = loading }

        func load(_ source: ChartSource, in web: WKWebView) {
            loaded = source
            loading.wrappedValue = true
            switch source {
            case .html(let html): web.loadHTMLString(html, baseURL: URL(string: "https://www.tradingview.com"))
            case .url(let url): web.load(URLRequest(url: url))
            }
        }

        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            // Виджету нужно мгновение, чтобы нарисовать график после загрузки скрипта.
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) { self.loading.wrappedValue = false }
        }

        func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
            loading.wrappedValue = false
        }

        func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
            loading.wrappedValue = false
        }
    }
}
