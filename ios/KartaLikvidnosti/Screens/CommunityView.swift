import SwiftUI

struct CommunityView: View {
    @EnvironmentObject var router: Router
    @EnvironmentObject var session: Session
    @EnvironmentObject var appData: AppData

    @State private var query = ""
    @State private var tab = "all"
    @State private var sections: [ChatSectionDto] = CommunityView.demo
    @State private var loaded = false
    /// Чаты без звука (локально, как «Без звука» в инфо чата) — для подписи в меню.
    @State private var muted: Set<String> = []
    @State private var leaveTarget: ChatItemDto? = nil

    static let demo: [ChatSectionDto] = [
        ChatSectionDto(key: "admin", title: "Чат с админом", items: [
            ChatItemDto(id: "admin", title: "Игорь Гломозда", sender: "Игор", preview: "Что интересует?", time: "16:32", unread: 1)
        ]),
        ChatSectionDto(key: "main", title: "Чаты", items: [
            ChatItemDto(id: "general", title: "Общий чат", sender: "Артем", preview: "Кто читал новый отчет по золоту?", time: "16:32", unread: 234)
        ]),
        ChatSectionDto(key: "reports", title: "Чаты по отчетам", items: [
            ChatItemDto(id: "gold", title: "Отчет по золоту 26.02.26", pinned: true, sender: "Вы", preview: "Я вот что не понял это хедж и к...", time: "16:32", unread: 12),
            ChatItemDto(id: "oil", title: "Нефть 12.12.27", sender: "Артем", preview: "Кто читал новый отчет по золоту?", time: "16:32", unread: 234),
            ChatItemDto(id: "plat1", title: "Отчет по платине 26.02.26", sender: "Артем", preview: "Кто читал новый отчет по золоту?", time: "16:32", unread: 234),
        ]),
    ]

    private var visibleSections: [ChatSectionDto] {
        var result = sections
        if session.restricted {
            // Без подписки (Figma 474:12121): только общий чат под заголовком «Чаты».
            return result.filter { $0.key == "main" }.map { ChatSectionDto(key: $0.key, title: "Чаты", items: $0.items) }
        }
        if session.isAdmin {
            result = result.filter { tab == "direct" ? $0.key == "direct" : $0.key != "direct" }
        }
        let q = query.trimmed.lowercased()
        guard !q.isEmpty else { return result }
        return result.compactMap { s in
            let items = s.items.filter { "\($0.title) \($0.preview ?? "")".lowercased().contains(q) }
            return items.isEmpty ? nil : ChatSectionDto(key: s.key, title: s.title, items: items)
        }
    }

    private var placeholder: String {
        session.isAdmin && tab == "direct" ? "Нужный чат" : "Нужный отчет или материал"
    }

    // «Сообщество» (Figma 337:27149, админ — 349:12176): поиск-стекло на y=78,
    // у админа под ним стеклянные сегменты «Все чаты / Личные чаты»; секции —
    // заголовок Kreadon 20, строки Block/Чат 65 pt с разделителями во всю
    // ширину экрана (белый 10 %).
    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: 0) {
                if !session.restricted {
                    AppInput(text: $query, placeholder: placeholder)
                        .padding(.horizontal, Theme.screenX)
                }

                if session.isAdmin {
                    SegmentedControl(selection: $tab, options: [("all", "Все чаты"), ("direct", "Личные чаты")], glass: true)
                        .padding(.horizontal, Theme.screenX)
                        .padding(.top, 12)
                }

                if !loaded {
                    VStack(spacing: 0) {
                        ForEach(0..<6, id: \.self) { _ in ChatRowSkeleton() }
                    }
                    .padding(.horizontal, Theme.screenX)
                    .padding(.top, 24)
                } else {
                    VStack(alignment: .leading, spacing: 40) {
                        ForEach(visibleSections) { section in
                            VStack(alignment: .leading, spacing: 24) {
                                Text(section.title)
                                    .font(Theme.heading(20))
                                    .foregroundStyle(Theme.text)
                                    .padding(.horizontal, Theme.screenX)
                                VStack(spacing: 0) {
                                    ForEach(Array(section.items.enumerated()), id: \.element.id) { idx, item in
                                        if idx > 0 {
                                            Rectangle().fill(Color.white.opacity(0.1)).frame(height: 1)
                                                .padding(.vertical, 12)
                                        }
                                        chatRow(item).padding(.horizontal, Theme.screenX)
                                    }
                                }
                            }
                        }
                    }
                    .padding(.top, session.restricted ? 0 : 24)

                    if visibleSections.isEmpty {
                        Text(session.isAdmin && tab == "direct" ? "Личных сообщений пока нет" : "Ничего не найдено")
                            .font(Theme.sans(Theme.fontBase))
                            .foregroundStyle(Theme.textSecondary)
                            .frame(maxWidth: .infinity).padding(.vertical, 40)
                    }
                }
            }
            .padding(.top, Theme.topBarY)
            .padding(.bottom, Theme.navBarClearance)
            .pullToRefreshAnchor()
        }
        .pullToRefresh { await load() }
        .ignoresSafeArea(.container, edges: .top)
        .screenBackground()
        .withGlassNavBar()
        .onAppear { seed(); refreshMuted() }
        .task { await load(); refreshMuted() }
        .alert(
            "Выйти из чата?",
            isPresented: Binding(get: { leaveTarget != nil }, set: { if !$0 { leaveTarget = nil } }),
            presenting: leaveTarget
        ) { item in
            Button("Выйти", role: .destructive) { leave(item) }
            Button("Отмена", role: .cancel) {}
        } message: { item in
            Text("Вы больше не будете видеть «\(item.title)» в списке чатов.")
        }
    }

    /// Мгновенно показываем предзагруженные чаты, чтобы без скелетона.
    private func seed() {
        guard !loaded, !appData.chats.isEmpty else { return }
        sections = appData.chats
        loaded = true
    }

    /// Block/Чат: аватар 64, отступ 12; название SemiBold 14, отправитель 14,
    /// превью 14 белый 60 %; справа время 14 (и скрепка 12 у закреплённого) и
    /// стеклянный счётчик непрочитанных (Regular 10).
    private func chatRow(_ item: ChatItemDto) -> some View {
        Button { router.push(.chat(id: item.id)) } label: {
            chatRowContent(item)
        }
        .buttonStyle(.plain)
        // Долгое нажатие — системное меню чата, как в Телеграме: чат поднимается
        // карточкой, под ним действия.
        .contextMenu {
            chatMenuItems(item)
        } preview: {
            chatRowContent(item)
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
                .frame(width: UIScreen.main.bounds.width - Theme.screenX * 2)
                .background(Theme.surface)
                .environment(\.colorScheme, .dark)
        }
    }

    @ViewBuilder private func chatMenuItems(_ item: ChatItemDto) -> some View {
        Button { router.push(.chat(id: item.id)) } label: {
            Label("Открыть", systemImage: "bubble.left.and.bubble.right")
        }
        if item.unread > 0 {
            Button { markRead(item) } label: {
                Label("Отметить прочитанным", systemImage: "checkmark.message")
            }
        }
        Button { toggleMute(item) } label: {
            if muted.contains(item.id) {
                Label("Включить звук", systemImage: "bell")
            } else {
                Label("Без звука", systemImage: "bell.slash")
            }
        }
        Button { router.push(.chatInfo(id: item.id)) } label: {
            Label("Информация о чате", systemImage: "info.circle")
        }
        if !item.permanent {
            Divider()
            Button(role: .destructive) { leaveTarget = item } label: {
                Label("Выйти из чата", systemImage: "rectangle.portrait.and.arrow.right")
            }
        }
    }

    private func chatRowContent(_ item: ChatItemDto) -> some View {
        let hasDetails = !(item.sender ?? "").isEmpty || !(item.preview ?? "").isEmpty
        return HStack(alignment: hasDetails ? .top : .center, spacing: 14) {
                HStack(spacing: 12) {
                    chatAvatar(item)
                    VStack(alignment: .leading, spacing: 4) {
                        Text(item.title).font(Theme.sans(Theme.fontBase, .semibold)).foregroundStyle(Theme.text)
                            .lineLimit(1)
                        if let sender = item.sender, !sender.isEmpty {
                            Text(sender).font(Theme.sans(Theme.fontBase)).foregroundStyle(Theme.text).lineLimit(1)
                        }
                        if let preview = item.preview, !preview.isEmpty {
                            Text(preview).font(Theme.sans(Theme.fontBase)).foregroundStyle(Color.white.opacity(0.6))
                                .lineLimit(1)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                VStack(alignment: .trailing, spacing: 0) {
                    HStack(spacing: 4) {
                        Text(item.time ?? "").font(Theme.sans(Theme.fontBase)).foregroundStyle(Color.white.opacity(0.6))
                        if item.pinned { Icon(AppIcons.pinSmall, size: 12, color: Theme.text) }
                    }
                    .frame(height: 19)
                    if item.unread > 0 {
                        Text("\(item.unread)")
                            .font(Theme.sans(10)).foregroundStyle(Color.white.opacity(0.6))
                            .padding(.horizontal, 8).padding(.vertical, 4)
                            .frame(minWidth: 28)
                            .liquidGlass(Capsule())
                            .padding(.top, 19)
                    }
                }
                .padding(.top, hasDetails ? 3 : 0)
            }
            .frame(minHeight: 65, alignment: .center)
            .contentShape(Rectangle())
    }

    @ViewBuilder private func chatAvatar(_ item: ChatItemDto) -> some View {
        ZStack {
            Circle().fill(Color(hex: "#797979"))
            if let url = fileURL(item.avatarUrl) {
                RemoteImage(url: url) {
                    Text(String(item.title.prefix(1)).uppercased())
                        .font(Theme.heading(24))
                        .foregroundStyle(Theme.text)
                }
                .clipShape(Circle())
            } else {
                Text(String(item.title.prefix(1)).uppercased())
                    .font(Theme.heading(24))
                    .foregroundStyle(Theme.text)
            }
        }
        .frame(width: 64, height: 64)
    }

    private func updateItem(_ id: String, _ change: (inout ChatItemDto) -> Void) {
        sections = sections.map { s in
            ChatSectionDto(key: s.key, title: s.title, items: s.items.map { var i = $0; if i.id == id { change(&i) }; return i })
        }
        appData.chats = sections
    }

    private func markRead(_ item: ChatItemDto) {
        updateItem(item.id) { $0.unread = 0 }
        Task { try? await Api.markChatRead(slug: item.id) }
    }

    private func toggleMute(_ item: ChatItemDto) {
        let key = "kl_muted_\(item.id)"
        let on = !UserDefaults.standard.bool(forKey: key)
        UserDefaults.standard.set(on, forKey: key)
        if on { muted.insert(item.id) } else { muted.remove(item.id) }
    }

    private func leave(_ item: ChatItemDto) {
        let before = sections
        sections = sections.compactMap { s in
            let items = s.items.filter { $0.id != item.id }
            return items.isEmpty ? nil : ChatSectionDto(key: s.key, title: s.title, items: items)
        }
        appData.chats = sections
        Task {
            do {
                try await Api.leaveChat(slug: item.id)
            } catch {
                // Не получилось — возвращаем чат в список.
                sections = before
                appData.chats = before
            }
        }
    }

    private func refreshMuted() {
        muted = Set(sections.flatMap(\.items).map(\.id).filter { UserDefaults.standard.bool(forKey: "kl_muted_\($0)") })
    }

    private func load() async {
        do {
            let s = try await Api.fetchChats()
            if !s.isEmpty { sections = s }
        } catch {}
        loaded = true
    }
}
