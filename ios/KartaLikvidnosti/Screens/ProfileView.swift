import SwiftUI

struct ProfileView: View {
    @EnvironmentObject var router: Router
    @EnvironmentObject var session: Session

    @State private var tag = ""
    @State private var avatar: URL? = nil
    @State private var subActive = false
    @State private var subUntil = ""
    @State private var subPlan = ""
    @State private var instruments: [InstrumentDto] = []
    @State private var history: [ReportDto] = []
    @State private var sortOpen = false
    @State private var sort = "new"

    private let sortOptions = [("new", "Сначала новые"), ("old", "Сначала Старые"), ("name", "По названию")]

    private var avatarInitial: String {
        let t = tag.replacingOccurrences(of: "@", with: "")
        return t.isEmpty ? "" : String(t.prefix(1)).uppercased()
    }

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: 20) {
                hero
                subscriptionCard
                Text("Отслеживаемые инструменты").font(Theme.heading(Theme.fontTitle)).foregroundStyle(Theme.text)
                FlowLayout(spacing: 8) {
                    ForEach(instruments.indices, id: \.self) { i in
                        InstrumentChip(label: instruments[i].label, on: Binding(
                            get: { instruments[i].on },
                            set: { instruments[i].on = $0; saveInstruments() }))
                    }
                }
                .padding(16).background(Theme.surface).clipShape(RoundedRectangle(cornerRadius: Theme.radiusCard, style: .continuous))

                Text("Настройки").font(Theme.heading(Theme.fontTitle)).foregroundStyle(Theme.text)
                settingsList

                historyHeader
                VStack(spacing: 16) {
                    ForEach(history) { report in
                        ReportCard(report: report) { router.push(.reportDetail(id: report.id)) }
                    }
                }
            }
            .padding(.bottom, 120)
        }
        .screenBackground(Theme.bg)
        .task { await load() }
    }

    private var hero: some View {
        VStack(spacing: 12) {
            AvatarCircle(url: avatar, size: 104, initial: avatarInitial)
                .overlay(Circle().stroke(Color.white.opacity(0.12), lineWidth: 3))
            Text(tag).font(.system(size: Theme.fontLg, weight: .bold)).foregroundStyle(Theme.text)
        }
        .frame(maxWidth: .infinity)
        .padding(.top, 56).padding(.bottom, 20).padding(.horizontal, Theme.screenX)
        .background(
            LinearGradient(colors: [Color(hex: "#3a3a32"), Color(hex: "#232420")], startPoint: .top, endPoint: .bottom)
                .overlay(RadialGradient(colors: [Color(hex: "#3c5028").opacity(0.55), .clear], center: .top, startRadius: 0, endRadius: 240))
        )
        .overlay(alignment: .topTrailing) {
            Button { router.push(.profileEdit) } label: {
                Icon(AppIcons.settings, size: 22, color: Theme.text).frame(width: 44, height: 44).background(Theme.surfaceMuted).clipShape(Circle())
            }.buttonStyle(.plain).padding(.trailing, Theme.screenX).padding(.top, 16)
        }
    }

    private var subscriptionCard: some View {
        VStack(spacing: 14) {
            HStack {
                Text("Подписка").font(.system(size: Theme.fontMd, weight: .semibold)).foregroundStyle(Theme.text)
                Spacer()
                HStack(spacing: 8) {
                    Text(subActive ? "Активна" : "Неактивна").foregroundStyle(Color(hex: "#7bc043")).font(.system(size: Theme.fontBase, weight: .semibold))
                    Text("до \(subUntil)").foregroundStyle(Theme.textSecondary).font(.system(size: Theme.fontBase))
                }
            }
            HStack {
                Text("Тарифный план").font(.system(size: Theme.fontMd, weight: .semibold)).foregroundStyle(Theme.text)
                Spacer()
                Text(subPlan).font(.system(size: Theme.fontBase)).foregroundStyle(Theme.textSecondary)
            }
            AppButton(title: "Управление подпиской", variant: .accent) { router.push(.subscription) }
        }
        .padding(16).background(Theme.surface).clipShape(RoundedRectangle(cornerRadius: Theme.radiusCard, style: .continuous))
        .padding(.horizontal, Theme.screenX)
    }

    private var settingsList: some View {
        VStack(spacing: 0) {
            if session.isAdmin { settingRow("Администраторы") { router.push(.admins) } }
            settingRow("Push-уведомления") { router.push(.notifications) }
            settingRow("Данные аккаунта") { router.push(.accountData) }
            settingRow("Пользовательское соглашение") {}
        }
        .padding(.horizontal, Theme.screenX)
    }

    private func settingRow(_ label: String, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack {
                Text(label).font(.system(size: Theme.fontMd)).foregroundStyle(Theme.text)
                Spacer()
                Icon(AppIcons.chevronRight, size: 18, color: Theme.textSecondary)
            }
            .padding(.vertical, 16).padding(.horizontal, 4)
            .overlay(Rectangle().fill(Color.white.opacity(0.08)).frame(height: 1), alignment: .bottom)
        }.buttonStyle(.plain)
    }

    private var historyHeader: some View {
        HStack {
            Text("История").font(Theme.heading(Theme.fontTitle)).foregroundStyle(Theme.text)
            Spacer()
            Menu {
                ForEach(sortOptions, id: \.0) { opt in
                    Button { sort = opt.0 } label: {
                        if sort == opt.0 { Label(opt.1, systemImage: "checkmark") } else { Text(opt.1) }
                    }
                }
            } label: {
                Icon(AppIcons.sort, size: 22, color: Theme.text).frame(width: 44, height: 44).background(Theme.gradientAccent).clipShape(Circle())
            }
        }
        .padding(.horizontal, Theme.screenX)
    }

    private func load() async {
        tag = session.user?.username ?? tag
        avatar = fileURL(session.user?.avatarUrl)
        do {
            let p = try await Api.fetchProfile()
            if let u = p.user { tag = u.username ?? tag; if let a = u.avatarUrl { avatar = fileURL(a) } }
            if let s = p.subscription { subActive = s.active; subUntil = s.until ?? ""; subPlan = s.plan ?? "" }
            instruments = p.instruments
        } catch {}
        do { history = try await Api.fetchReports() } catch { history = [] }
    }

    private func saveInstruments() {
        let keys = instruments.filter { $0.on }.map { $0.key }
        Task { try? await Api.syncInstruments(keys) }
    }
}
