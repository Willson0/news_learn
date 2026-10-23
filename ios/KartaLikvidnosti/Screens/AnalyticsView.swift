import SwiftUI

struct AnalyticsView: View {
    @EnvironmentObject var router: Router
    @EnvironmentObject var appData: AppData
    @EnvironmentObject var session: Session
    @State private var query = ""
    @State private var reports: [ReportDto] = []
    @State private var statusFilter: [String]? = nil
    @State private var instrumentFilter: [String]? = nil
    @State private var periodFilter: String? = nil
    @State private var rangeFilter: (start: String, end: String)? = nil
    @State private var filtersOpen = false
    @State private var loaded = false

    private var visible: [ReportDto] {
        let q = query.trimmed.lowercased()
        guard !q.isEmpty else { return reports }
        return reports.filter { $0.title.lowercased().contains(q) || $0.description.lowercased().contains(q) }
    }

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: 24) {
                Text("Список материалов").font(Theme.heading(Theme.fontTitle)).foregroundStyle(Theme.text)

                HStack(spacing: 12) {
                    AppInput(text: $query, placeholder: "Нужный отчет или материал")
                    Button {
                        Haptics.light()
                        filtersOpen = true
                    } label: {
                        Icon(AppIcons.filter, size: 16, color: Theme.text)
                            .frame(width: 48, height: 48)
                            .contentShape(Circle())
                            .liquidGlass(Circle(), fill: ActiveFill(), interactive: true)
                    }
                    .buttonStyle(PressableStyle())
                }

                if !loaded {
                    VStack(spacing: 12) {
                        ForEach(0..<4, id: \.self) { _ in ReportCardSkeleton() }
                    }
                } else {
                    LazyVStack(spacing: 12) {
                        ForEach(visible) { report in
                            ReportCard(report: report) { router.push(.reportDetail(id: report.id)) }
                        }
                        if visible.isEmpty {
                            Text("Ничего не найдено").font(Theme.sans(Theme.fontBase)).foregroundStyle(Theme.textSecondary)
                                .frame(maxWidth: .infinity).padding(.vertical, 40)
                        }
                    }
                }
            }
            .padding(.horizontal, Theme.screenX)
            .padding(.top, Theme.titleTop)
            .padding(.bottom, Theme.navBarClearance)
        }
        .ignoresSafeArea(.container, edges: .top)
        .screenBackground()
        .withGlassNavBar(extra: session.isAdmin
            ? .init(icon: AppIcons.plusBig) { router.push(.reportCreate) }
            : nil)
        .overlay {
            FiltersOverlay(isOpen: $filtersOpen) { result in
                statusFilter = result.statuses.isEmpty ? nil : result.statuses
                instrumentFilter = result.instruments.isEmpty ? nil : result.instruments
                if let s = result.dateStart, let e = result.dateEnd {
                    rangeFilter = (s, e)
                    periodFilter = nil
                } else {
                    rangeFilter = nil
                    periodFilter = result.dateMode
                }
            }
        }
        .onAppear {
            seed()
            #if DEBUG
            if UserDefaults.standard.bool(forKey: "kl_filters") { filtersOpen = true }
            #endif
        }
        .task(id: filterKey) { await load() }
    }

    /// Мгновенно показываем предзагруженные материалы (без фильтра), чтобы без скелетона.
    private var hasFilters: Bool {
        statusFilter != nil || instrumentFilter != nil || periodFilter != nil || rangeFilter != nil
    }

    /// Ключ фильтров: при изменении любого из них список перезагружается.
    private var filterKey: String {
        [
            (statusFilter ?? []).joined(separator: ","),
            (instrumentFilter ?? []).joined(separator: ","),
            periodFilter ?? "",
            rangeFilter.map { "\($0.start)|\($0.end)" } ?? "",
        ].joined(separator: ";")
    }

    private func seed() {
        guard !loaded, !hasFilters, !appData.reports.isEmpty else { return }
        reports = appData.reports
        loaded = true
    }

    private func load() async {
        do {
            reports = try await Api.fetchReports(
                instruments: instrumentFilter,
                period: rangeFilter == nil ? periodFilter : nil,
                from: rangeFilter?.start,
                to: rangeFilter?.end,
                status: statusFilter
            )
        }
        catch { /* при сбое обновления оставляем предзагруженные данные */ }
        loaded = true
    }
}
