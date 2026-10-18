import SwiftUI

struct AnalyticsView: View {
    @EnvironmentObject var router: Router
    @EnvironmentObject var appData: AppData
    @EnvironmentObject var session: Session
    @State private var query = ""
    @State private var reports: [ReportDto] = []
    @State private var statusFilter: [String]? = nil
    @State private var filtersOpen = false
    @State private var loaded = false

    private var visible: [ReportDto] {
        let q = query.trimmed.lowercased()
        guard !q.isEmpty else { return reports }
        return reports.filter { $0.title.lowercased().contains(q) || $0.description.lowercased().contains(q) }
    }

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: 20) {
                Text("Список материалов").font(Theme.heading(Theme.fontTitle)).foregroundStyle(Theme.text)

                HStack(spacing: 12) {
                    AppInput(text: $query, placeholder: "Нужный отчёт или материал")
                    Button { filtersOpen = true } label: {
                        Icon(AppIcons.filter, size: 20, color: Theme.text)
                            .frame(width: 48, height: 48)
                            .background(Theme.gradientAccentSoft)
                            .clipShape(RoundedRectangle(cornerRadius: Theme.radiusCard, style: .continuous))
                    }
                    .buttonStyle(.plain)
                }

                if !loaded {
                    VStack(spacing: 16) {
                        ForEach(0..<4, id: \.self) { _ in ReportCardSkeleton() }
                    }
                } else {
                    LazyVStack(spacing: 16) {
                        ForEach(visible) { report in
                            ReportCard(report: report) { router.push(.reportDetail(id: report.id)) }
                        }
                        if visible.isEmpty {
                            Text("Ничего не найдено").foregroundStyle(Theme.textSecondary)
                                .frame(maxWidth: .infinity).padding(.vertical, 40)
                        }
                    }
                }
            }
            .padding(.horizontal, Theme.screenX)
            .padding(.top, 60)
            .padding(.bottom, 24)
        }
        .screenBackground()
        .overlay(alignment: .bottomTrailing) {
            if session.isAdmin {
                Button {
                    Haptics.light()
                    router.push(.reportCreate)
                } label: {
                    Icon(AppIcons.plus, size: 30, color: Theme.text)
                        .frame(width: 62, height: 62)
                        .background(Theme.gradientAccent, in: Circle())
                        .shadow(color: .black.opacity(0.35), radius: 10, y: 6)
                }
                .buttonStyle(.plain)
                .padding(.trailing, Theme.screenX)
                .padding(.bottom, 16)
            }
        }
        .onAppear(perform: seed)
        .task(id: statusFilter ?? []) { await load() }
        .sheet(isPresented: $filtersOpen) {
            FiltersSheet { result in
                statusFilter = result.statuses.isEmpty ? nil : result.statuses
            }
            .presentationDetents([.large])
            .presentationDragIndicator(.hidden)
        }
    }

    /// Мгновенно показываем предзагруженные материалы (без фильтра), чтобы без скелетона.
    private func seed() {
        guard !loaded, statusFilter == nil, !appData.reports.isEmpty else { return }
        reports = appData.reports
        loaded = true
    }

    private func load() async {
        do { reports = try await Api.fetchReports(status: statusFilter) }
        catch { /* при сбое обновления оставляем предзагруженные данные */ }
        loaded = true
    }
}
