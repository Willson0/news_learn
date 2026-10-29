import SwiftUI

struct ReportDetailView: View {
    @EnvironmentObject var router: Router
    @EnvironmentObject var session: Session
    @EnvironmentObject var appData: AppData
    let reportId: Int?

    @State private var report = ReportModel.placeholder
    /// Данные отчёта уже есть (из ленты или с сервера) — до этого вместо обложки и
    /// текста мигающие блоки, а не демо-заглушка, которая сменялась бы настоящим отчётом.
    @State private var loaded = false
    @State private var reportOpen = false
    @State private var graphOpen = false
    /// html-отчёт открывается на весь экран, как отдельная страница.
    @State private var htmlOpen = false

    struct ReportModel {
        var title: String
        var date: String?
        var badge: String?
        var description: String
        var body: String?
        var material: String?
        var chat: String?
        var chartUrl: String?
        var coverUrl: URL?
        var htmlUrl: URL?

        static let placeholder = ReportModel(
            title: "", date: nil, badge: nil, description: "",
            body: nil, material: nil, chat: nil, chartUrl: nil, coverUrl: nil, htmlUrl: nil)

        init(title: String, date: String?, badge: String?, description: String, body: String?,
             material: String?, chat: String?, chartUrl: String?, coverUrl: URL?, htmlUrl: URL?) {
            self.title = title; self.date = date; self.badge = badge; self.description = description
            self.body = body; self.material = material; self.chat = chat; self.chartUrl = chartUrl
            self.coverUrl = coverUrl; self.htmlUrl = htmlUrl
        }

        init(_ d: ReportDto) {
            self.init(title: d.title, date: d.date, badge: d.badge, description: d.description,
                      body: d.body, material: d.material, chat: d.chat, chartUrl: d.chartUrl,
                      coverUrl: fileURL(d.coverUrl), htmlUrl: fileURL(d.htmlUrl))
        }
    }

    private var chartSource: ChartSource? { ChartSource.parse(report.chartUrl) }

    // «Аналитика отчет»: размытая обложка (blur 25 + чёрный 50%) высотой 465
    // со скруглением снизу, сверху — стеклянные «назад» и лейбл статуса; кнопки
    // прижаты к низу. С открытым графиком обложка 302, кнопки идут сразу под
    // ней, ниже — блок графика.
    var body: some View {
        GeometryReader { geo in
            ScrollView(showsIndicators: false) {
                VStack(spacing: 0) {
                    hero
                    // Кнопки прижаты к низу, а при раскрытии идут сразу под обложкой. Отступ —
                    // явная высота, которая анимируется той же пружиной, что и обложка: все
                    // кнопки поднимаются одним блоком, без скачков.
                    Color.clear.frame(height: actionsGap(screenHeight: geo.size.height + geo.safeAreaInsets.top + geo.safeAreaInsets.bottom))
                    actions
                        .padding(.horizontal, Theme.screenX)
                        // Кнопки — одна геометрическая группа: при раскрытии отчёта или графика
                        // едут вверх целиком, а не каждая по своей траектории.
                        .modifier(GeometryGroupIfAvailable())
                    if reportOpen {
                        reportBody.padding(.horizontal, Theme.screenX).padding(.top, 24)
                    }
                    if graphOpen, let source = chartSource {
                        // Прототип: «Аналитика отчет график внутри» — блок графика во всю ширину.
                        ChartEmbedView(source: source)
                            .frame(height: 600)
                            .padding(.top, 24)
                            .transition(.opacity.combined(with: .move(edge: .bottom)))
                    }
                }
                .padding(.bottom, 45)
                .frame(minHeight: geo.size.height + geo.safeAreaInsets.top + geo.safeAreaInsets.bottom, alignment: .top)
            }
            .ignoresSafeArea(.container, edges: [.top, .bottom])
        }
        .background(Theme.background.ignoresSafeArea())
        .overlay(alignment: .top) {
            GlassTopBar {
                if let badge = report.badge, !badge.isEmpty {
                    GlassLabel(text: badge, height: 42)
                }
            } trailing: {
                if session.isAdmin, let id = reportId {
                    GlassCircleButton(icon: AppIcons.settings) { router.push(.reportEdit(id: id)) }
                }
            }
            .ignoresSafeArea(.container, edges: .top)
        }
        // Прототип: «Аналитика отчет» 484:13666 ↔ 484:13689 (Button chart) — обложка
        // сжимается, график раскрывается: SMART_ANIMATE, пружина SLOW (≈1.25 с).
        .animation(Motion.slow, value: graphOpen)
        .animation(Motion.slow, value: reportOpen)
        // Лист, а не полноэкранная обложка: закрывается свайпом вниз, как в системных приложениях.
        .sheet(isPresented: $htmlOpen) {
            if let url = report.htmlUrl {
                HtmlReportView(url: url)
                    .presentationDetents([.large])
                    .presentationDragIndicator(.visible)
            }
        }
        .onAppear { seedFromFeed() }
        .task { await load() }
    }

    private func actionsGap(screenHeight: CGFloat) -> CGFloat {
        if graphOpen || reportOpen { return 24 }
        let count: CGFloat = chartSource != nil ? 3 : 2
        let actionsHeight = 48 * count + 12 * (count - 1)
        return max(24, screenHeight - 465 - actionsHeight - 45)
    }

    private var actions: some View {
        VStack(spacing: 12) {
            ReportActionButton(label: "Открыть отчет", variant: .accent, icon: AppIcons.doc) {
                if report.htmlUrl != nil { htmlOpen = true } else { reportOpen.toggle() }
            }
            ReportActionButton(label: "Обсудить", variant: .pink, icon: AppIcons.chat) {
                discuss()
            }
            // Без ссылки на график кнопки нет (Figma «Аналитика отчет без графика»).
            if chartSource != nil {
                ReportActionButton(label: graphOpen ? "Закрыть график" : "Открыть график",
                                   variant: graphOpen ? .chartOn : .chartOff, icon: AppIcons.chartBadge) {
                    graphOpen.toggle()
                }
            }
        }
    }

    @ViewBuilder private var hero: some View {
        let shape = UnevenRoundedRectangle(bottomLeadingRadius: Theme.radiusCard, bottomTrailingRadius: Theme.radiusCard, style: .continuous)
        ZStack(alignment: .bottomLeading) {
            // Обложка лежит в overlay у фиксированного Color.clear: размер картинки
            // (например, вертикальной 1206×2622) не растягивает шапку и не выталкивает текст.
            Color.clear
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .overlay {
                    ZStack {
                        if !loaded {
                            LoadingBlock()
                        } else if let cover = report.coverUrl {
                            RemoteImage(url: cover) { ReportCoverPlaceholder() }
                        } else {
                            ReportCoverPlaceholder()
                        }
                    }
                }
                .clipped()
                .overlay { ZStack { BackdropBlur(radius: 25); Color.black.opacity(0.5) } }

            if !loaded {
                VStack(alignment: .leading, spacing: 12) {
                    SkeletonBlock(width: 220, height: 20)
                    SkeletonBlock(width: 80, height: 14)
                    SkeletonBlock(width: 300, height: 14)
                    SkeletonBlock(width: 240, height: 14)
                }
                .padding(.horizontal, Theme.screenX)
                .padding(.bottom, 16)
            } else {
            VStack(alignment: .leading, spacing: 12) {
                HStack(alignment: .top, spacing: 0) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(report.title).font(Theme.heading(20)).foregroundStyle(Theme.text)
                            .frame(maxWidth: 258, alignment: .leading)
                        if let date = report.date {
                            Text(date).font(Theme.sans(Theme.fontBase)).foregroundStyle(Theme.textSecondary)
                        }
                    }
                    Spacer(minLength: 8)
                    Icon(AppIcons.material(report.material), size: 20, color: .white)
                }
                Text(report.description).font(Theme.sans(Theme.fontBase, .light)).foregroundStyle(Theme.text)
                    .frame(maxWidth: 300, alignment: .leading)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(.horizontal, Theme.screenX)
            .padding(.bottom, 16)
            }
        }
        .frame(height: (graphOpen || reportOpen) ? 302 : 465)
        .clipShape(shape)
    }

    @ViewBuilder private var reportBody: some View {
        if report.htmlUrl == nil {
            VStack(alignment: .leading, spacing: 12) {
                if let body = report.body, !body.isEmpty {
                    Text(body)
                } else {
                    Text("Нефть остаётся одним из ключевых индикаторов мировой экономики. В 2026 году на цену влияют баланс спроса и предложения, политика ОПЕК+ и курс доллара.")
                    Text("В этом отчёте мы разбираем основные сценарии, факторы риска и возможные уровни цены на горизонте ближайших месяцев.")
                }
            }
            .font(Theme.sans(Theme.fontBase)).foregroundStyle(Theme.text).lineSpacing(4)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(16)
            .background(Theme.surface)
            .clipShape(RoundedRectangle(cornerRadius: Theme.radiusCard, style: .continuous))
        }
    }

    private func discuss() {
        if let chat = report.chat { router.push(.chat(id: chat)) }
        else { router.switchTab(.community) }
    }

    /// Отчёт из ленты (уже загруженной) показывается сразу: обложка берётся из кэша картинок.
    private func seedFromFeed() {
        guard !loaded, let id = reportId, let d = appData.reports.first(where: { $0.id == id }) else { return }
        report = ReportModel(d)
        loaded = true
    }

    private func load() async {
        #if DEBUG
        if reportId == nil, let chart = UserDefaults.standard.string(forKey: "kl_chart") {
            report = ReportModel(title: "Проверка графика", date: nil, badge: nil, description: "", body: nil,
                                 material: nil, chat: nil, chartUrl: chart, coverUrl: nil, htmlUrl: nil)
            loaded = true
            graphOpen = true
            return
        }
        #endif
        guard let id = reportId else { loaded = true; return }
        do {
            guard let d = try await Api.fetchReport(id: String(id)) else { loaded = true; return }
            report = ReportModel(d)
        } catch {}
        loaded = true
    }
}

/// Полноэкранная html-страница отчёта: WebView и кнопка закрытия.
struct HtmlReportView: View {
    let url: URL
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ZStack(alignment: .topTrailing) {
            Color.black.ignoresSafeArea()
            WebView(url: url).ignoresSafeArea()
            GlassCircleButton(icon: AppIcons.close, iconSize: 20) { dismiss() }
                .padding(16)
        }
    }
}

private struct GeometryGroupIfAvailable: ViewModifier {
    func body(content: Content) -> some View {
        if #available(iOS 17.0, *) { content.geometryGroup() } else { content }
    }
}
