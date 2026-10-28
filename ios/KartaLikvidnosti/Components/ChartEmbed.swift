import SwiftUI
import WebKit

/// Что показывать в блоке «График» отчёта по значению поля «График» из админки.
enum ChartSource: Equatable {
    /// Страница графика TradingView (виджет по тикеру) или обычная ссылка — грузится как есть.
    case url(URL, original: URL)
    /// Вставленный код виджета без тикера — показывается как html.
    case html(String)

    /// Ссылка, которую можно открыть в браузере, если график не загрузился.
    var externalURL: URL? {
        if case .url(_, let original) = self { return original }
        return nil
    }

    /// Ссылка TradingView с тикером (`/symbols/OANDA-XAUUSD/`, `/chart/?symbol=OANDA:XAUUSD`)
    /// и код виджета («Get a widget») с `"symbol": "…"` превращаются в страницу виджета
    /// графика: в блоке только график, без сайта вокруг. Страница грузится напрямую,
    /// без скрипта-загрузчика tv.js, поэтому не зависит от его подгрузки.
    static func parse(_ raw: String?) -> ChartSource? {
        let value = (raw ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        guard !value.isEmpty else { return nil }
        if value.contains("<") {
            if let symbol = symbol(inWidgetCode: value), let url = widgetURL(symbol: symbol) {
                return .url(url, original: url)
            }
            return .html(page(body: value))
        }
        var text = value
        if !text.lowercased().hasPrefix("http") { text = "https://" + text }
        guard let url = URL(string: text), let host = url.host?.lowercased() else { return nil }
        if host.hasSuffix("tradingview.com"), let symbol = symbol(in: url), let widget = widgetURL(symbol: symbol) {
            return .url(widget, original: url)
        }
        return .url(url, original: url)
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

    /// `"symbol": "OANDA:XAUUSD"` из кода виджета.
    private static func symbol(inWidgetCode code: String) -> String? {
        guard let regex = try? NSRegularExpression(pattern: #"["']?symbol["']?\s*:\s*["']([^"']+)["']"#) else { return nil }
        let ns = code as NSString
        guard let m = regex.firstMatch(in: code, range: NSRange(location: 0, length: ns.length)), m.numberOfRanges > 1 else { return nil }
        let s = ns.substring(with: m.range(at: 1)).trimmingCharacters(in: .whitespaces)
        return s.isEmpty ? nil : s
    }

    /// Страница виджета «Advanced chart» TradingView: тёмная тема, русский, без боковой панели.
    private static func widgetURL(symbol: String) -> URL? {
        let safe = symbol.filter { $0.isLetter || $0.isNumber || ":._!-".contains($0) }
        guard !safe.isEmpty else { return nil }
        var c = URLComponents(string: "https://s.tradingview.com/widgetembed/")
        c?.queryItems = [
            URLQueryItem(name: "symbol", value: safe),
            URLQueryItem(name: "interval", value: "D"),
            URLQueryItem(name: "hidesidetoolbar", value: "1"),
            URLQueryItem(name: "symboledit", value: "0"),
            URLQueryItem(name: "saveimage", value: "0"),
            URLQueryItem(name: "toolbarbg", value: "25262A"),
            URLQueryItem(name: "theme", value: "dark"),
            URLQueryItem(name: "style", value: "1"),
            URLQueryItem(name: "timezone", value: "Europe/Moscow"),
            URLQueryItem(name: "locale", value: "ru"),
            URLQueryItem(name: "hideideas", value: "1"),
        ]
        return c?.url
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
/// Пока страница грузится — мигающий блок; не загрузилась — сообщение и «Открыть в TradingView».
struct ChartEmbedView: View {
    let source: ChartSource
    @Environment(\.openURL) private var openURL
    @State private var state: ChartWebView.LoadState = .loading

    var body: some View {
        ZStack {
            ChartWebView(source: source, state: $state)
                .opacity(state == .loaded ? 1 : 0)
            switch state {
            case .loading:
                LoadingBlock()
            case .failed:
                VStack(spacing: 12) {
                    Text("Не удалось загрузить график")
                        .font(Theme.sans(Theme.fontBase))
                        .foregroundStyle(Theme.textSecondary)
                    if let url = source.externalURL {
                        Button { openURL(url) } label: {
                            Text("Открыть в TradingView")
                                .font(Theme.sans(Theme.fontBase, .semibold))
                                .foregroundStyle(Theme.text)
                                .padding(.horizontal, 16)
                                .frame(height: 40)
                                .liquidGlass(Capsule())
                        }
                        .buttonStyle(PressableStyle())
                    }
                }
            case .loaded:
                EmptyView()
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Theme.surface)
        .clipShape(RoundedRectangle(cornerRadius: Theme.radiusCard, style: .continuous))
        .animation(.easeOut(duration: 0.25), value: state)
    }
}

struct ChartWebView: UIViewRepresentable {
    enum LoadState: Equatable { case loading, loaded, failed }

    let source: ChartSource
    @Binding var state: LoadState

    func makeCoordinator() -> Coordinator { Coordinator(state: $state) }

    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        config.allowsInlineMediaPlayback = true
        config.websiteDataStore = .default()
        let web = WKWebView(frame: CGRect(x: 0, y: 0, width: 360, height: 600), configuration: config)
        web.isOpaque = false
        web.backgroundColor = .clear
        web.scrollView.backgroundColor = .clear
        web.scrollView.contentInsetAdjustmentBehavior = .never
        web.scrollView.isScrollEnabled = false
        web.navigationDelegate = context.coordinator
        DispatchQueue.main.async { context.coordinator.load(source, in: web) }
        return web
    }

    func updateUIView(_ web: WKWebView, context: Context) {
        context.coordinator.state = $state
        if context.coordinator.loaded != nil, context.coordinator.loaded != source {
            context.coordinator.load(source, in: web)
        }
    }

    final class Coordinator: NSObject, WKNavigationDelegate {
        var state: Binding<LoadState>
        var loaded: ChartSource?
        private var attempt = 0

        init(state: Binding<LoadState>) { self.state = state }

        func load(_ source: ChartSource, in web: WKWebView) {
            loaded = source
            attempt += 1
            let current = attempt
            state.wrappedValue = .loading
            switch source {
            case .html(let html): web.loadHTMLString(html, baseURL: URL(string: "https://www.tradingview.com"))
            case .url(let url, _): web.load(URLRequest(url: url, cachePolicy: .returnCacheDataElseLoad, timeoutInterval: 20))
            }
            // Страница так и не ответила — не мигаем бесконечно.
            DispatchQueue.main.asyncAfter(deadline: .now() + 20) { [weak self] in
                guard let self, self.attempt == current, self.state.wrappedValue == .loading else { return }
                self.state.wrappedValue = .failed
            }
        }

        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            let current = attempt
            // Виджету нужно мгновение, чтобы нарисовать график после загрузки страницы.
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) { [weak self] in
                guard let self, self.attempt == current, self.state.wrappedValue == .loading else { return }
                self.state.wrappedValue = .loaded
            }
        }

        func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
            fail(error)
        }

        func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
            fail(error)
        }

        private func fail(_ error: Error) {
            // Отменённая загрузка (переход внутри виджета) — не ошибка.
            if (error as NSError).code == NSURLErrorCancelled { return }
            state.wrappedValue = .failed
        }
    }
}
