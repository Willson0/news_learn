import SwiftUI

struct CommunityView: View {
    @EnvironmentObject var router: Router
    @EnvironmentObject var session: Session

    @State private var query = ""
    @State private var tab = "all"
    @State private var sections: [ChatSectionDto] = CommunityView.demo

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

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: 18) {
                AppInput(text: $query, placeholder: placeholder)

                if session.isAdmin {
                    SegmentedControl(selection: $tab, options: [("all", "Все чаты"), ("direct", "Личные чаты")])
                        .padding(4)
                        .overlay(RoundedRectangle(cornerRadius: Theme.radiusPill).stroke(Color.white.opacity(0.14), lineWidth: 1))
                }

                ForEach(visibleSections) { section in
                    VStack(alignment: .leading, spacing: 8) {
                        Text(section.title).font(Theme.heading(Theme.fontTitle)).foregroundStyle(Theme.text)
                        VStack(spacing: 0) {
                            ForEach(section.items) { item in
                                chatRow(item)
                                if item.id != section.items.last?.id {
                                    Rectangle().fill(Color.white.opacity(0.06)).frame(height: 1)
                                }
                            }
                        }
                    }
                }

                if visibleSections.isEmpty {
                    Text(session.isAdmin && tab == "direct" ? "Личных сообщений пока нет" : "Ничего не найдено")
                        .foregroundStyle(Theme.textSecondary)
                        .frame(maxWidth: .infinity).padding(.vertical, 40)
                }
            }
            .padding(.horizontal, Theme.screenX)
            .padding(.top, 56)
            .padding(.bottom, 120)
        }
        .screenBackground(Theme.bg)
        .task { await load() }
    }

    private func chatRow(_ item: ChatItemDto) -> some View {
        Button { router.push(.chat(id: item.id)) } label: {
            HStack(spacing: 12) {
                AvatarCircle(url: fileURL(item.avatarUrl), size: 56)
                VStack(alignment: .leading, spacing: 2) {
                    HStack {
                        Text(item.title).font(.system(size: Theme.fontMd, weight: .bold)).foregroundStyle(Theme.text)
                        Spacer()
                        HStack(spacing: 4) {
                            if item.pinned { Icon(AppIcons.pin, size: 14, color: Theme.textSecondary) }
                            Text(item.time ?? "").font(.system(size: Theme.fontSm)).foregroundStyle(Theme.textSecondary)
                        }
                    }
                    if let sender = item.sender {
                        Text(sender).font(.system(size: Theme.fontBase)).foregroundStyle(Theme.text)
                    }
                    HStack {
                        Text(item.preview ?? "").font(.system(size: Theme.fontBase)).foregroundStyle(Theme.textSecondary).lineLimit(1)
                        Spacer()
                        if item.unread > 0 {
                            Text("\(item.unread)")
                                .font(.system(size: Theme.fontSm)).foregroundStyle(Theme.textSecondary)
                                .padding(.horizontal, 8).frame(minWidth: 34, minHeight: 20)
                                .overlay(Capsule().stroke(Color.white.opacity(0.2), lineWidth: 1))
                        }
                    }
                }
            }
            .padding(.vertical, 12)
        }
        .buttonStyle(.plain)
    }

    private func load() async {
        do {
            let s = try await Api.fetchChats()
            if !s.isEmpty { sections = s }
        } catch {}
    }
}
