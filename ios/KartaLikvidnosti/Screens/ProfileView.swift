import SwiftUI

/// «Профиль» (Figma 484:21226): шапка 309 — фото пользователя под размытием 50
/// и чёрным 50 %, аватар 148 и @тег; стеклянная «настройки» справа. Ниже —
/// блок подписки, отслеживаемые инструменты, настройки и история с
/// сортировкой. В навбаре справа — круглая кнопка чата с админом.
struct ProfileView: View {
    @EnvironmentObject var router: Router
    @EnvironmentObject var session: Session
    @EnvironmentObject var appData: AppData

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
    /// «Сегодня» / «Вчера» и подписи в блоке подписки — белый 60 %.
    private let caption = Color.white.opacity(0.6)

    private var avatarInitial: String {
        let t = tag.replacingOccurrences(of: "@", with: "")
        return t.isEmpty ? "" : String(t.prefix(1)).uppercased()
    }

    private var displayTag: String {
        guard !tag.isEmpty else { return "" }
        return tag.hasPrefix("@") ? tag : "@" + tag
    }

    var body: some View {
        ScrollViewReader { proxy in
        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: 0) {
                header
                if session.restricted {
                    conditionsBlock
                } else {
                fullContent
                }
            }
            .padding(.bottom, Theme.navBarClearance)
        }
        #if DEBUG
        .onAppear {
            // -kl_scroll instruments|history — для скриншотов длинного экрана.
            if let id = UserDefaults.standard.string(forKey: "kl_scroll") {
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) { proxy.scrollTo(id, anchor: .top) }
            }
        }
        #endif
        }
        .ignoresSafeArea(.container, edges: .top)
        .screenBackground()
        .withGlassNavBar(extra: .init(icon: AppIcons.chat) { openAdminChat() })
        .onAppear(perform: seed)
        .task { await load() }
    }

    // MARK: Без подписки

    /// «Доступный экран профиля» (Figma 474:11853, 484:13055): «Условия»
    /// Kreadon 24 через 40 от шапки, пояснение 14 pt, блок подписки с кнопкой
    /// «Привязать счет и оплатить»; без тарифа — «Не определен» и подпись
    /// 14 pt белый 65 % под блоком.
    private var conditionsBlock: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("Условия").font(Theme.heading(Theme.fontTitle)).foregroundStyle(Theme.text)
            Text("Чтобы разблокировать доступ в приложение нужно оплатить ежемесячную подписку")
                .font(Theme.sans(Theme.fontBase)).foregroundStyle(Theme.text)
                .frame(maxWidth: 325, alignment: .leading)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.top, 12)
            VStack(spacing: 24) {
                VStack(spacing: 12) {
                    HStack {
                        Text("Подписка").font(Theme.sans(Theme.fontMd, .bold)).foregroundStyle(Theme.text)
                        Spacer(minLength: 8)
                        Text("Неактивна").font(Theme.sans(Theme.fontMd, .semibold)).foregroundStyle(Theme.error)
                    }
                    Rectangle().fill(Color.white.opacity(0.1)).frame(height: 1)
                    HStack {
                        Text("Тарифный план").font(Theme.sans(Theme.fontMd, .bold)).foregroundStyle(Theme.text)
                        Spacer(minLength: 8)
                        Text(subPlan.isEmpty ? "Не определен" : subPlan).font(Theme.sans(Theme.fontBase)).foregroundStyle(Theme.textSecondary)
                    }
                }
                .padding(.horizontal, 16)
                AppButton(title: "Привязать счет и оплатить", variant: .accent) { router.push(.subscription) }
            }
            .padding(.top, 16)
            .background(Theme.surface, in: RoundedRectangle(cornerRadius: Theme.radiusCard, style: .continuous))
            .padding(.top, 24)
            if subPlan.isEmpty {
                Text("После определение тарифа Вам придет уведомление и стоимость")
                    .font(Theme.sans(Theme.fontBase)).foregroundStyle(Theme.textSecondary)
                    .frame(maxWidth: 300, alignment: .leading)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.top, 12)
            }
        }
        .padding(.horizontal, Theme.screenX)
        .padding(.top, 40)
    }

    // MARK: Полный профиль

    @ViewBuilder private var fullContent: some View {
                subscriptionBlock
                    .padding(.horizontal, Theme.screenX)
                    .padding(.top, 24)

                Text("Отслеживаемые инструменты")
                    .font(Theme.heading(Theme.fontTitle))
                    .foregroundStyle(Theme.text)
                    .frame(maxWidth: 196, alignment: .leading)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.horizontal, Theme.screenX)
                    .padding(.top, 40)
                instrumentsBlock
                    .padding(.horizontal, Theme.screenX)
                    .padding(.top, 24)
                    .id("instruments")

                Text("Настройки")
                    .font(Theme.heading(Theme.fontTitle))
                    .foregroundStyle(Theme.text)
                    .padding(.horizontal, Theme.screenX)
                    .padding(.top, 40)
                settingsBlock
                    .padding(.horizontal, Theme.screenX)
                    .padding(.top, 24)

                historyHeader
                    .padding(.horizontal, Theme.screenX)
                    .padding(.top, 40)
                    .id("history")
                    .zIndex(1)
                historyList
                    .padding(.horizontal, Theme.screenX)
                    .padding(.top, 14)
    }

    // MARK: Шапка

    private var header: some View {
        ZStack(alignment: .top) {
            ZStack {
                Color(hex: "#3b5839")
                if let avatar { RemoteImage(url: avatar) }
            }
            .frame(height: 309)
            .frame(maxWidth: .infinity)
            .blur(radius: 50, opaque: true)
            .overlay(Color.black.opacity(0.5))
            .clipShape(RoundedRectangle(cornerRadius: Theme.radiusCard, style: .continuous))

            VStack(spacing: 0) {
                AvatarCircle(url: avatar, size: 148, initial: avatarInitial)
                    .padding(.top, 78)
                if session.restricted {
                    Text(session.user?.name ?? "")
                        .font(Theme.heading(20)).foregroundStyle(Theme.text).lineLimit(1)
                        .padding(.top, 12)
                    Text(displayTag)
                        .font(Theme.sans(Theme.fontBase)).foregroundStyle(Theme.text).lineLimit(1)
                        .padding(.top, 4)
                } else {
                    Text(displayTag)
                        .font(Theme.sans(Theme.fontBase))
                        .foregroundStyle(Theme.text)
                        .lineLimit(1)
                        .padding(.top, 40)
                }
            }
            .frame(maxWidth: .infinity)

            HStack {
                Spacer()
                GlassCircleButton(icon: AppIcons.settings, iconSize: 26) { router.push(.profileEdit) }
            }
            .padding(.horizontal, Theme.screenX)
            .padding(.top, 78)
        }
        .frame(height: 309)
    }

    // MARK: Подписка

    private var subscriptionBlock: some View {
        VStack(spacing: 24) {
            VStack(spacing: 12) {
                HStack(alignment: .center) {
                    Text("Подписка").font(Theme.sans(Theme.fontMd, .bold)).foregroundStyle(Theme.text)
                    Spacer(minLength: 8)
                    VStack(alignment: .trailing, spacing: 4) {
                        Text(subActive ? "Активна" : "Неактивна")
                            .font(Theme.sans(Theme.fontMd, .semibold))
                            .foregroundStyle(subActive ? Theme.green : caption)
                        if subActive, !subUntil.isEmpty {
                            Text("до \(subUntil)").font(Theme.sans(Theme.fontBase)).foregroundStyle(caption)
                        }
                    }
                }
                .frame(minHeight: 43)
                Rectangle().fill(Color.white.opacity(0.1)).frame(height: 1)
                HStack {
                    Text("Тарифный план").font(Theme.sans(Theme.fontMd, .bold)).foregroundStyle(Theme.text)
                    Spacer(minLength: 8)
                    Text(subPlan.isEmpty ? "—" : subPlan).font(Theme.sans(Theme.fontBase)).foregroundStyle(caption)
                }
                .frame(height: 20)
            }
            .padding(.horizontal, 16)

            AppButton(title: "Управление подпиской", variant: .accent) { router.push(.subscription) }
        }
        .padding(.top, 16)
        .background(Theme.surface, in: RoundedRectangle(cornerRadius: Theme.radiusCard, style: .continuous))
    }

    // MARK: Инструменты

    private var instrumentsBlock: some View {
        FlowLayout(spacing: 4, lineSpacing: 4) {
            ForEach(instruments.indices, id: \.self) { i in
                InstrumentChip(label: instruments[i].label, on: Binding(
                    get: { instruments[i].on },
                    set: { instruments[i].on = $0; saveInstruments() }))
            }
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.surface, in: RoundedRectangle(cornerRadius: Theme.radiusCard, style: .continuous))
    }

    // MARK: Настройки

    private var settingsBlock: some View {
        VStack(spacing: 0) {
            if session.isAdmin { settingRow("Администраторы") { router.push(.admins) } }
            settingRow("Push-уведомления") { router.push(.notifications) }
            settingRow("Данные аккаунта") { router.push(.accountData) }
            settingRow("Пользовательское соглашение") { router.push(.userAgreement) }
        }
        .padding(.vertical, 12)
        .padding(.horizontal, 16)
        .background(Theme.surface, in: RoundedRectangle(cornerRadius: Theme.radiusCard, style: .continuous))
    }

    private func settingRow(_ label: String, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 10) {
                Text(label).font(Theme.sans(Theme.fontBase)).foregroundStyle(Theme.text)
                Spacer(minLength: 0)
                Icon(AppIcons.back, size: 16, color: Theme.text)
                    .scaleEffect(x: -1, y: 1)
                    .frame(width: 16, height: 16)
            }
            .frame(height: 43)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    // MARK: История

    private var historyHeader: some View {
        HStack(alignment: .center) {
            Text("История").font(Theme.heading(Theme.fontTitle)).foregroundStyle(Theme.text)
            Spacer()
            Button {
                Haptics.light()
                withAnimation(.spring(response: 0.32, dampingFraction: 0.85)) { sortOpen.toggle() }
            } label: {
                Icon(AppIcons.filter, size: 16, color: Theme.text)
                    .frame(width: 48, height: 48)
                    .contentShape(Circle())
                    .liquidGlass(Circle(), fill: ActiveFill(), interactive: true)
            }
            .buttonStyle(PressableStyle())
        }
        .frame(height: 48)
        .overlay(alignment: .topTrailing) {
            if sortOpen {
                sortPopup
                    .offset(y: 56)
                    .transition(.scale(scale: 0.9, anchor: .topTrailing).combined(with: .opacity))
            }
        }
    }

    /// Попап сортировки (Figma 495:21374): 203 pt, стекло «чёрный 2», радиус 24.
    private var sortPopup: some View {
        VStack(alignment: .leading, spacing: 12) {
            ForEach(sortOptions, id: \.0) { opt in
                Button {
                    Haptics.light()
                    withAnimation(.spring(response: 0.32, dampingFraction: 0.85)) {
                        sort = opt.0
                        sortOpen = false
                    }
                } label: {
                    HStack(spacing: 8) {
                        Text(opt.1).font(Theme.sans(Theme.fontBase)).foregroundStyle(Theme.text)
                        Spacer(minLength: 0)
                        if sort == opt.0 {
                            Icon(AppIcons.check, size: 20, color: Theme.text)
                        }
                    }
                    .frame(height: 19)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.vertical, 16)
        .padding(.horizontal, 12)
        .frame(width: 203)
        .liquidGlass(RoundedRectangle(cornerRadius: Theme.radiusCard, style: .continuous), fill: Theme.black2)
    }

    private var historyList: some View {
        VStack(alignment: .leading, spacing: 24) {
            ForEach(historyGroups, id: \.title) { group in
                VStack(alignment: .leading, spacing: 12) {
                    if let title = group.title.nilIfEmpty {
                        Text(title).font(Theme.sans(Theme.fontBase)).foregroundStyle(caption)
                    }
                    ForEach(group.items) { report in
                        ReportCard(report: report) { router.push(.reportDetail(id: report.id)) }
                    }
                }
            }
        }
    }

    private struct HistoryGroup { let title: String; let items: [ReportDto] }

    /// По дате — группы «Сегодня» / «Вчера» / дата; по названию — одним списком.
    private var historyGroups: [HistoryGroup] {
        if sort == "name" {
            return [HistoryGroup(title: "", items: history.sorted {
                $0.title.localizedCaseInsensitiveCompare($1.title) == .orderedAscending
            })]
        }
        let dated = history.map { ($0, Self.parseDate($0.date)) }
        let sorted = dated.sorted { a, b in
            let da = a.1 ?? .distantPast, db = b.1 ?? .distantPast
            return sort == "old" ? da < db : da > db
        }
        var groups: [HistoryGroup] = []
        for (report, date) in sorted {
            let title = Self.dayTitle(date, fallback: report.date)
            if let last = groups.last, last.title == title {
                groups[groups.count - 1] = HistoryGroup(title: title, items: last.items + [report])
            } else {
                groups.append(HistoryGroup(title: title, items: [report]))
            }
        }
        return groups
    }

    private static func parseDate(_ s: String?) -> Date? {
        guard let s, !s.isEmpty else { return nil }
        let f = DateFormatter()
        f.locale = Locale(identifier: "ru_RU")
        for fmt in ["dd.MM.yyyy", "dd.MM.yy", "yyyy-MM-dd"] {
            f.dateFormat = fmt
            if let d = f.date(from: String(s.prefix(fmt.count))) { return d }
        }
        return nil
    }

    private static func dayTitle(_ date: Date?, fallback: String?) -> String {
        guard let date else { return fallback ?? "" }
        let cal = Calendar.current
        if cal.isDateInToday(date) { return "Сегодня" }
        if cal.isDateInYesterday(date) { return "Вчера" }
        let f = DateFormatter()
        f.dateFormat = "dd.MM.yyyy"
        return f.string(from: date)
    }

    // MARK: Данные

    private func openAdminChat() {
        // Админ ведёт личные чаты во вкладке «Сообщество», пользователь — свой
        // чат с админом (slug «admin» на бэкенде).
        if session.isAdmin { router.switchTab(.community) }
        else { router.push(.chat(id: "admin")) }
    }

    /// Мгновенно наполняем экран предзагруженными данными, чтобы при открытии не было пустого кадра.
    private func seed() {
        tag = session.user?.username ?? tag
        avatar = fileURL(session.user?.avatarUrl)
        if let p = appData.profile { apply(p) }
        if history.isEmpty, !appData.reports.isEmpty { history = appData.reports }
        #if DEBUG
        if UserDefaults.standard.bool(forKey: "kl_sort") { sortOpen = true }
        #endif
    }

    private func apply(_ p: ProfileResponse) {
        if let u = p.user { tag = u.username ?? tag; if let a = u.avatarUrl { avatar = fileURL(a) } }
        if let s = p.subscription { subActive = s.active; subUntil = s.until ?? ""; subPlan = s.plan ?? "" }
        instruments = p.instruments
    }

    private func load() async {
        tag = session.user?.username ?? tag
        avatar = fileURL(session.user?.avatarUrl)
        do { apply(try await Api.fetchProfile()) } catch {}
        do { history = try await Api.fetchReports() } catch { /* оставляем предзагруженную историю */ }
    }

    private func saveInstruments() {
        let keys = instruments.filter { $0.on }.map { $0.key }
        Task { try? await Api.syncInstruments(keys) }
    }
}

private extension String {
    var nilIfEmpty: String? { isEmpty ? nil : self }
}
