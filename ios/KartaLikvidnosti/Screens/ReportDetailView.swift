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

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(spacing: 16) {
                hero
                VStack(spacing: 12) {
                    ReportActionButton(label: "Открыть отчёт", variant: .accent, badge: .green, icon: AppIcons.doc) {
                        reportOpen.toggle()
                    }
                    ReportActionButton(label: "Обсудить", variant: .purple, badge: .pink, icon: AppIcons.chat) {
                        discuss()
                    }
                    ReportActionButton(label: graphOpen ? "Закрыть график" : "Открыть график",
                                       variant: .dark, badge: .green, icon: AppIcons.chartBadge) {
                        toggleGraph()
                    }
                }
                .padding(.horizontal, Theme.screenX)

                if reportOpen {
                    reportBody.padding(.horizontal, Theme.screenX)
                }
                if graphOpen {
                    CandlestickChart().padding(.horizontal, Theme.screenX)
                }
            }
            .padding(.bottom, 32)
        }
        .screenBackground(Theme.bg)
        .task { await load() }
    }

    @ViewBuilder private var hero: some View {
        ZStack(alignment: .bottomLeading) {
            HoloFoil()
            if let cover = report.coverUrl {
                RemoteImage(url: cover).blur(radius: 24).clipped()
            }
            LinearGradient(colors: [Color(hex: "#1a1b1d").opacity(0.35), Color(hex: "#1a1b1d").opacity(0.1), Color(hex: "#1a1b1d").opacity(0.85)],
                           startPoint: .top, endPoint: .bottom)

            VStack(alignment: .leading, spacing: 12) {
                HStack(alignment: .top, spacing: 8) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(report.title).font(.system(size: 20, weight: .bold)).foregroundStyle(Theme.text)
                        if let date = report.date {
                            Text(date).font(.system(size: Theme.fontBase)).foregroundStyle(Theme.textSecondary)
                        }
                    }
                    Spacer(minLength: 8)
                    Icon(AppIcons.material(report.material), size: 18, color: .white)
                        .frame(width: 32, height: 32)
                        .background(Color(hex: "#28282c").opacity(0.7))
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                }
                Text(report.description).font(.system(size: Theme.fontBase)).foregroundStyle(Theme.text)
                    .lineSpacing(3).frame(maxWidth: 300, alignment: .leading)
            }
            .padding(.horizontal, Theme.screenX)
            .padding(.bottom, 24)

            if let badge = report.badge, !badge.isEmpty {
                Text(badge).font(.system(size: Theme.fontSm)).foregroundStyle(Theme.text)
                    .padding(.horizontal, 16).padding(.vertical, 8)
                    .background(Color(hex: "#28282c").opacity(0.7))
                    .clipShape(RoundedRectangle(cornerRadius: Theme.radiusCard))
                    .frame(maxWidth: .infinity, alignment: .center)
                    .padding(.top, 40)
                    .frame(maxHeight: .infinity, alignment: .top)
            }

            if session.isAdmin, reportId != nil {
                Button { router.push(.reportEdit(id: reportId!)) } label: {
                    Icon(AppIcons.gear, size: 24, color: .white)
                        .frame(width: 42, height: 42)
                        .background(Color(hex: "#28282c").opacity(0.7)).clipShape(Circle())
                }
                .buttonStyle(.plain)
                .padding(.trailing, Theme.screenX).padding(.top, 40)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topTrailing)
            }
        }
        .frame(minHeight: 420)
        .clipShape(UnevenRoundedRectangle(bottomLeadingRadius: Theme.radiusCard, bottomTrailingRadius: Theme.radiusCard, style: .continuous))
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
            .font(.system(size: Theme.fontBase)).foregroundStyle(Theme.text).lineSpacing(4)
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
