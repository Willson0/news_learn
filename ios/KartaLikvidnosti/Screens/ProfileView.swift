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
    @State private var loggingOut = false
    @State private var avatar: URL? = nil
    @State private var subActive = false
    @State private var subUntil = ""
    @State private var subPrice = ""
    @State private var instruments: [InstrumentDto] = []
    @State private var history: [ReportDto] = []
    @State private var sortOpen = false
    @State private var sort = "new"
    /// Данные уже на экране: повторное появление (возврат с «Управления подпиской»)
    /// не откатывает их к закэшированным, а только тихо обновляет.
    @State private var seeded = false

    private let sortOptions = [("new", "Сначала новые"), ("old", "Сначала старые"), ("name", "По названию")]
    /// «Сегодня» / «Вчера» и подписи в блоке подписки — белый 60 %.
    private let caption = Color.white.opacity(0.6)

    private var avatarInitial: String {
        let t = tag.replacingOccurrences(of: "@", with: "")
        let source = t.isEmpty ? (session.user?.name ?? "") : t
        return source.isEmpty ? "?" : String(source.prefix(1)).uppercased()
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
                    // Без подписки доступа к настройкам аккаунта нет: выход оставляем здесь.
                    AppButton(title: "Выйти из аккаунта", variant: .danger, loading: loggingOut) { logoutRestricted() }
                        .padding(.horizontal, Theme.screenX)
                        .padding(.top, 24)
                } else {
                fullContent
                }
            }
            .padding(.bottom, Theme.navBarClearance)
            .pullToRefreshAnchor()
        }
        .pullToRefresh { await load() }
        .overlayPreferenceValue(SortButtonAnchor.self) { sortOverlay($0) }
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
        .withGlassNavBar(extra: .init(icon: AppIcons.chat, label: session.isAdmin ? "Личные чаты" : "Чат с администратором") { openAdminChat() })
        .onAppear(perform: seed)
        .task { await load() }
    }

    // MARK: Без подписки

    /// «Доступный экран профиля» (Figma 474:11853, 484:13055): «Условия»
    /// Kreadon 24 через 40 от шапки, пояснение 14 pt, блок подписки с кнопкой
    /// «Привязать счет и оплатить»; без тарифа — «Не определен» и подпись
    /// 14 pt белый 65 % под блоком.
    private func logoutRestricted() {
        loggingOut = true
        Task {
            await Api.logout()
            session.clear()
            router.logout()
        }
    }

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
                        Text(subPrice.isEmpty ? "Не определен" : subPrice).font(Theme.sans(Theme.fontBase)).foregroundStyle(Theme.textSecondary)
                    }
                }
                .padding(.horizontal, 16)
                AppButton(title: "Привязать счет и оплатить", variant: .accent) { router.push(.subscription) }
            }
            .padding(.top, 16)
            .background(Theme.surface, in: RoundedRectangle(cornerRadius: Theme.radiusCard, style: .continuous))
            .padding(.top, 24)
            if !subUntil.isEmpty {
                Text("Истекла \(subUntil). Чтобы продлить доступ, оплатите подписку.")
                    .font(Theme.sans(Theme.fontBase)).foregroundStyle(Theme.textSecondary)
                    .frame(maxWidth: 300, alignment: .leading)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.top, 12)
            }
            if subPrice.isEmpty {
                Text("Когда тариф будет назначен, мы пришлём уведомление со стоимостью")
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
            // Фото — только оверлей поверх фона заданного размера: в режиме .fill
            // широкая картинка шире экрана, и, будучи частью раскладки, она
            // раздвигала шапку — аватар и тег съезжали вправо.
            Color(hex: "#3b5839")
                .frame(maxWidth: .infinity)
                .frame(height: 309)
                .overlay { if let avatar { RemoteImage(url: avatar) } }
                .clipped()
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
                GlassCircleButton(icon: AppIcons.settings, label: "Настройки профиля", iconSize: 26) { router.push(.profileEdit) }
            }
            .padding(.horizontal, Theme.screenX)
            .padding(.top, 78)
        }
        .frame(maxWidth: .infinity)
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
                    Text(subPrice.isEmpty ? "—" : subPrice).font(Theme.sans(Theme.fontBase)).foregroundStyle(caption)
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
                    set: { v in instruments[i].on = v; saveInstruments(index: i, value: v) }))
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
                withAnimation(Motion.standard) { sortOpen.toggle() }
            } label: {
                Icon(AppIcons.filter, size: 16, color: Theme.text)
                    .frame(width: 48, height: 48)
                    .contentShape(Circle())
                    .liquidGlass(Circle(), fill: ActiveFill(), interactive: true)
            }
            .buttonStyle(PressableStyle())
            .accessibilityLabel("Сортировка истории")
            .accessibilityValue(sortOptions.first { $0.0 == sort }?.1 ?? "")
            .anchorPreference(key: SortButtonAnchor.self, value: .bounds) { $0 }
        }
        .frame(height: 48)
    }

    /// Попап сортировки поверх экрана: тап мимо него закрывает.
    @ViewBuilder private func sortOverlay(_ anchor: Anchor<CGRect>?) -> some View {
        if sortOpen, let anchor {
            GeometryReader { geo in
                let r = geo[anchor]
                ZStack(alignment: .topLeading) {
                    Color.clear
                        .contentShape(Rectangle())
                        .onTapGesture { withAnimation(Motion.standard) { sortOpen = false } }
                        .accessibilityHidden(true)
                    sortPopup
                        .offset(x: r.maxX - 203, y: r.maxY + 8)
                }
            }
            .transition(.opacity)
        }
    }

    /// Попап сортировки (Figma 495:21374): 203 pt, стекло «чёрный 2», радиус 24.
    private var sortPopup: some View {
        VStack(alignment: .leading, spacing: 0) {
            ForEach(sortOptions, id: \.0) { opt in
                Button {
                    Haptics.light()
                    withAnimation(Motion.standard) {
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
                    .frame(height: 43)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(sort == opt.0 ? .isSelected : [])
            }
        }
        .padding(.vertical, 4)
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
        guard !seeded else { return }
        seeded = true
        withoutAnimation {
            tag = session.user?.username ?? tag
            avatar = fileURL(session.user?.avatarUrl)
            if let p = appData.profile { apply(p) }
            if let s = appData.subscription { applySubscription(s) }
            if history.isEmpty, !appData.reports.isEmpty { history = appData.reports }
        }
        #if DEBUG
        if UserDefaults.standard.bool(forKey: "kl_sort") { sortOpen = true }
        #endif
    }

    private func apply(_ p: ProfileResponse) {
        if let u = p.user { tag = u.username ?? tag; if let a = u.avatarUrl { avatar = fileURL(a) } }
        if let s = p.subscription { applySubscription(s) }
        instruments = p.instruments
    }

    private func applySubscription(_ s: SubscriptionDto) {
        subActive = s.active; subUntil = s.until ?? ""; subPrice = s.price ?? ""
    }

    private func load() async {
        tag = session.user?.username ?? tag
        avatar = fileURL(session.user?.avatarUrl)
        do {
            let p = try await Api.fetchProfile()
            withoutAnimation { apply(p) }
            appData.profile = p
            if let s = p.subscription { appData.subscription = s; session.setSubscription(s) }
        } catch {}
        do { history = try await Api.fetchReports() } catch { /* оставляем предзагруженную историю */ }
    }

    /// При ошибке чип возвращается назад и всплывает тост.
    private func saveInstruments(index: Int, value: Bool) {
        let keys = instruments.filter { $0.on }.map { $0.key }
        Task { @MainActor in
            do { try await Api.syncInstruments(keys) }
            catch {
                if instruments.indices.contains(index) { withoutAnimation { instruments[index].on = !value } }
                AppToast.shared.show("Не удалось сохранить, попробуйте ещё раз")
            }
        }
    }
}

private struct SortButtonAnchor: PreferenceKey {
    static var defaultValue: Anchor<CGRect>? = nil
    static func reduce(value: inout Anchor<CGRect>?, nextValue: () -> Anchor<CGRect>?) { value = value ?? nextValue() }
}

private extension String {
    var nilIfEmpty: String? { isEmpty ? nil : self }
}
