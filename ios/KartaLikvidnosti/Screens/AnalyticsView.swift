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
    @State private var failed = false
    /// Список на экране соответствует текущим фильтрам (а не взят из кэша ленты при сбое).
    @State private var hasData = false
    @State private var savedAt: Date? = nil

    private var visible: [ReportDto] { matchesQuery(reports) }

    private func matchesQuery(_ list: [ReportDto]) -> [ReportDto] {
        let q = query.trimmed.lowercased()
        guard !q.isEmpty else { return list }
        return list.filter { $0.title.lowercased().contains(q) || $0.description.lowercased().contains(q) }
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
                            // Бейдж с числом включённых фильтров.
                            .overlay(alignment: .topTrailing) {
                                if filterCount > 0 {
                                    Text("\(filterCount)")
                                        .font(Theme.sans(12, .semibold))
                                        .foregroundStyle(Theme.text)
                                        .frame(width: 20, height: 20)
                                        .background(Theme.green, in: Circle())
                                        .offset(x: 4, y: -4)
                                }
                            }
                    }
                    .buttonStyle(PressableStyle())
                    .accessibilityLabel(filterCount > 0 ? "Фильтры, включено \(filterCount)" : "Фильтры")
                }

                // Включённые фильтры чипами: крестик снимает один, «Сбросить» — все.
                let chips = appliedFilters.activeChips
                if !chips.isEmpty {
                    FlowLayout(spacing: 8, lineSpacing: 8) {
                        ForEach(chips, id: \.id) { chip in
                            Button { apply(appliedFilters.without(chip.id)) } label: {
                                HStack(spacing: 4) {
                                    Text(chip.label).font(Theme.sans(Theme.fontBase)).lineLimit(1)
                                    Icon(AppIcons.close, size: 16, color: Theme.text)
                                }
                                .foregroundStyle(Theme.text)
                                .padding(.leading, 12).padding(.trailing, 8)
                                .frame(height: 32)
                                .liquidGlass(Capsule(), fill: ActiveFill())
                                .frame(minHeight: 44)
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(PressableStyle())
                            .accessibilityLabel("Убрать фильтр \(chip.label)")
                        }
                        Button { apply(FiltersResult()) } label: {
                            Text("Сбросить").font(Theme.sans(Theme.fontBase)).foregroundStyle(Theme.textSecondary)
                                .padding(.horizontal, 8)
                                .frame(minHeight: 44)
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(PressableStyle())
                    }
                }

                if !loaded {
                    VStack(spacing: 12) {
                        ForEach(0..<4, id: \.self) { _ in ReportCardSkeleton() }
                    }
                } else if failed && !hasData {
                    LoadErrorState { retry() }
                } else {
                    if failed { StaleDataBanner(savedAt: savedAt) { retry() } }
                    LazyVStack(spacing: 12) {
                        ForEach(visible) { report in
                            ReportCard(report: report) { router.push(.reportDetail(id: report.id)) }
                        }
                    }
                    if visible.isEmpty {
                        let narrowed = filterCount > 0 || !query.trimmed.isEmpty
                        EmptyStateView(text: narrowed ? "По выбранным фильтрам ничего не найдено" : "Материалов пока нет",
                                       actionTitle: narrowed ? "Сбросить фильтры" : nil) {
                            query = ""
                            apply(FiltersResult())
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
        .withGlassNavBar(extra: session.isAdmin
            ? .init(icon: AppIcons.plusBig, label: "Создать отчет") { router.push(.reportCreate) }
            : nil)
        .overlay {
            FiltersOverlay(isOpen: $filtersOpen, applied: appliedFilters,
                           onApply: { apply($0) })
        }
        .onAppear {
            seed()
            #if DEBUG
            if UserDefaults.standard.bool(forKey: "kl_filters") { filtersOpen = true }
            #endif
        }
        .task(id: filterKey) { await load() }
    }

    /// Фильтры, которые уже применены: лист фильтров открывается с ними.
    private var appliedFilters: FiltersResult {
        FiltersResult(
            instruments: instrumentFilter ?? [],
            statuses: statusFilter ?? [],
            dateMode: periodFilter,
            dateStart: rangeFilter?.start,
            dateEnd: rangeFilter?.end
        ).normalized
    }

    private var filterCount: Int { appliedFilters.activeCount }

    private func apply(_ result: FiltersResult) {
        let r = result.normalized
        statusFilter = r.statuses.isEmpty ? nil : r.statuses
        instrumentFilter = r.instruments.isEmpty ? nil : r.instruments
        if r.hasRange, let s = r.dateStart, let e = r.dateEnd {
            rangeFilter = (s, e)
            periodFilter = nil
        } else {
            rangeFilter = nil
            periodFilter = r.period
        }
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
        guard !loaded, !hasFilters, appData.reportsAt != nil else { return }
        reports = appData.reports
        savedAt = appData.reportsAt
        hasData = true
        loaded = true
    }

    private func fetch(_ f: FiltersResult) async throws -> [ReportDto] {
        try await Api.fetchReports(
            instruments: f.instruments.isEmpty ? nil : f.instruments,
            period: f.period,
            from: f.hasRange ? f.dateStart : nil,
            to: f.hasRange ? f.dateEnd : nil,
            status: f.statuses.isEmpty ? nil : f.statuses
        )
    }

    private func retry() {
        Task {
            if !hasData { loaded = false }
            await load()
        }
    }

    private func load() async {
        // Кэш ленты подходит только к списку без фильтров.
        if hasFilters { hasData = false }
        do {
            reports = try await fetch(appliedFilters)
            failed = false
            hasData = true
            savedAt = Date()
        } catch {
            if error.isCancellation { return }
            failed = true
        }
        loaded = true
    }
}
