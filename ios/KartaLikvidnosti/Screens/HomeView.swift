import SwiftUI

struct HomeView: View {
    @EnvironmentObject var router: Router
    @EnvironmentObject var appData: AppData
    @State private var filter = "all"
    @State private var reports: [ReportDto] = []
    @State private var loaded = false

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: 20) {
                Text("Лента материалов").font(Theme.heading(Theme.fontTitle)).foregroundStyle(Theme.text)
                MaterialChips(selected: $filter)
                LazyVStack(spacing: 16) {
                    if !loaded {
                        ForEach(0..<4, id: \.self) { _ in ReportCardSkeleton() }
                    } else {
                        ForEach(reports) { report in
                            ReportCard(report: report) { router.push(.reportDetail(id: report.id)) }
                        }
                        if reports.isEmpty {
                            Text("По этому фильтру материалов пока нет")
                                .foregroundStyle(Theme.textSecondary)
                                .frame(maxWidth: .infinity).padding(.vertical, 40)
                        }
                    }
                }
            }
            .padding(.horizontal, Theme.screenX)
            .padding(.top, 60)
            .padding(.bottom, 120)
        }
        .screenBackground(Theme.bg)
        .onAppear(perform: seed)
        .task(id: filter) { await load() }
    }

    /// Мгновенно показываем предзагруженную ленту (фильтр «все»), чтобы без скелетона.
    private func seed() {
        guard !loaded, filter == "all", !appData.reports.isEmpty else { return }
        reports = appData.reports
        loaded = true
    }

    private func load() async {
        do { reports = try await Api.fetchReports(instrument: filter) }
        catch { /* при сбое обновления оставляем предзагруженные данные */ }
        loaded = true
    }
}
