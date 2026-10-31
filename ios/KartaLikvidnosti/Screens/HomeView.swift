import SwiftUI

struct HomeView: View {
    @EnvironmentObject var router: Router
    @EnvironmentObject var appData: AppData
    @State private var filter = "all"
    @State private var reports: [ReportDto] = []
    @State private var loaded = false
    /// Обновить не удалось: без данных — экран ошибки, с кэшем — плашка над списком.
    @State private var failed = false
    /// Список на экране относится к текущему фильтру (а не остался от прошлого).
    @State private var hasData = false
    @State private var savedAt: Date? = nil

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: 24) {
                Text("Лента материалов").font(Theme.heading(Theme.fontTitle)).foregroundStyle(Theme.text)
                MaterialChips(selected: $filter)
                if !loaded {
                    VStack(spacing: 12) {
                        ForEach(0..<4, id: \.self) { _ in ReportCardSkeleton() }
                    }
                } else if failed && !hasData {
                    LoadErrorState { retry() }
                } else {
                    // Плашка вне LazyVStack: ленивый контейнер держит устаревшие условные элементы.
                    if failed { StaleDataBanner(savedAt: savedAt) { retry() } }
                    LazyVStack(spacing: 12) {
                        ForEach(reports) { report in
                            ReportCard(report: report) { router.push(.reportDetail(id: report.id)) }
                        }
                        if reports.isEmpty {
                            EmptyStateView(text: filter == "all" ? "Материалов пока нет" : "По этому фильтру материалов пока нет",
                                           actionTitle: filter == "all" ? nil : "Показать все") { filter = "all" }
                        }
                    }
                }
            }
            .padding(.horizontal, Theme.screenX)
            .padding(.top, Theme.titleTop)
            .padding(.bottom, Theme.navBarClearance)
            .pullToRefreshAnchor()
        }
        .pullToRefresh { await load() }
        .ignoresSafeArea(.container, edges: .top)
        .screenBackground()
        .slowNetworkHint(active: !loaded, bottom: Theme.navBarClearance)
        .withGlassNavBar()
        .onAppear(perform: seed)
        .task(id: filter) { await load() }
    }

    /// Мгновенно показываем предзагруженную ленту (фильтр «все»), чтобы без скелетона.
    private func seed() {
        guard !loaded, filter == "all", appData.reportsAt != nil else { return }
        reports = appData.reports
        savedAt = appData.reportsAt
        hasData = true
        loaded = true
    }

    private func retry() {
        Task {
            if !hasData { loaded = false }
            await load()
        }
    }

    private func load() async {
        if filter != "all" { hasData = false } else if appData.reportsAt != nil { hasData = true }
        do {
            let fresh = try await Api.fetchReports(instrument: filter)
            reports = fresh
            failed = false
            hasData = true
            savedAt = Date()
            if filter == "all" { appData.reports = fresh; appData.reportsAt = savedAt }
        } catch {
            // Ушли с экрана или сменили фильтр — это не ошибка.
            if error.isCancellation { return }
            failed = true
        }
        loaded = true
    }
}
