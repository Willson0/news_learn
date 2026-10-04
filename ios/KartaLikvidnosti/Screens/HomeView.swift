import SwiftUI

struct HomeView: View {
    @EnvironmentObject var router: Router
    @State private var filter = "all"
    @State private var reports: [ReportDto] = []

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: 20) {
                Text("Лента материалов").font(Theme.heading(Theme.fontTitle)).foregroundStyle(Theme.text)
                MaterialChips(selected: $filter)
                LazyVStack(spacing: 16) {
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
            .padding(.horizontal, Theme.screenX)
            .padding(.top, 60)
            .padding(.bottom, 120)
        }
        .screenBackground(Theme.bg)
        .task(id: filter) { await load() }
    }

    private func load() async {
        do { reports = try await Api.fetchReports(instrument: filter) }
        catch { reports = [] }
    }
}
