import SwiftUI

struct ReportDetailView: View {
    @EnvironmentObject var router: Router
    @EnvironmentObject var session: Session
    let reportId: Int?

    @State private var report = ReportModel.placeholder
    @State private var reportOpen = false
    @State private var graphOpen = false

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
            title: "Нефть в 2026 году, как она?", date: "12.02.26", badge: "Актуальный",
            description: "Здесь мы расскажем о состоянии нефти на момент 2026 года и возможное ее будущее",
            body: nil, material: "wti", chat: nil, chartUrl: nil, coverUrl: nil, htmlUrl: nil)
    }

    // «Аналитика отчет»: размытая обложка (blur 25 + чёрный 50%) высотой 465
    // со скруглением снизу, сверху — стеклянные «назад» и лейбл статуса; кнопки
    // прижаты к низу. С открытым графиком обложка 302, кнопки идут сразу под
    // ней, ниже — блок графика.
    var body: some View {
        GeometryReader { geo in
            ScrollView(showsIndicators: false) {
                VStack(spacing: 0) {
                    hero
                    if !(graphOpen || reportOpen) {
                        Spacer(minLength: 24)
                    }
                    actions
                        .padding(.horizontal, Theme.screenX)
                        .padding(.top, (graphOpen || reportOpen) ? 24 : 0)
                    if reportOpen {
                        reportBody.padding(.horizontal, Theme.screenX).padding(.top, 24)
                    }
                    if graphOpen {
                        CandlestickChart().padding(.top, 24)
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
        .animation(.spring(response: 0.4, dampingFraction: 0.88), value: graphOpen)
        .animation(.spring(response: 0.4, dampingFraction: 0.88), value: reportOpen)
        .task { await load() }
    }

    private var actions: some View {
        VStack(spacing: 12) {
            ReportActionButton(label: "Открыть отчет", variant: .accent, icon: AppIcons.doc) {
                reportOpen.toggle()
            }
            ReportActionButton(label: "Обсудить", variant: .pink, icon: AppIcons.chat) {
                discuss()
            }
            ReportActionButton(label: graphOpen ? "Закрыть график" : "Открыть график",
                               variant: graphOpen ? .chartOn : .chartOff, icon: AppIcons.chartBadge) {
                toggleGraph()
            }
        }
    }

    @ViewBuilder private var hero: some View {
        let shape = UnevenRoundedRectangle(bottomLeadingRadius: Theme.radiusCard, bottomTrailingRadius: Theme.radiusCard, style: .continuous)
        ZStack(alignment: .bottomLeading) {
            Group {
                if let cover = report.coverUrl {
                    RemoteImage(url: cover)
                } else {
                    HoloFoil()
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .clipped()
            .overlay { ZStack { BackdropBlur(radius: 25); Color.black.opacity(0.5) } }

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
        .frame(height: (graphOpen || reportOpen) ? 302 : 465)
        .clipShape(shape)
    }

    @ViewBuilder private var reportBody: some View {
        if let html = report.htmlUrl {
            WebView(url: html).frame(minHeight: 500)
                .clipShape(RoundedRectangle(cornerRadius: Theme.radiusCard, style: .continuous))
        } else {
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

    private func toggleGraph() {
        if let chart = report.chartUrl, let url = URL(string: chart) {
            UIApplication.shared.open(url)
            return
        }
        graphOpen.toggle()
    }

    private func load() async {
        guard let id = reportId else { return }
        do {
            guard let d = try await Api.fetchReport(id: String(id)) else { return }
            report = ReportModel(
                title: d.title, date: d.date, badge: d.badge, description: d.description,
                body: d.body, material: d.material, chat: d.chat, chartUrl: d.chartUrl,
                coverUrl: fileURL(d.coverUrl), htmlUrl: fileURL(d.htmlUrl))
        } catch {}
    }
}
