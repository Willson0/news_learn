import SwiftUI

struct ChatView: View {
    @EnvironmentObject var router: Router
    let chatId: String

    @State private var draft = ""
    @State private var serverMessages: [ChatMessage]? = nil
    @State private var serverChat: ChatItemDto? = nil
    @State private var replyTo: ChatMessage? = nil
    @State private var menuFor: Int? = nil
    @State private var selectMode = false
    @State private var selected: Set<Int> = []
    @State private var attachOpen = false
    @State private var recording = false
    @State private var pinned: (title: String, text: String)? = ("Закрепленное сообщение", "Здравствуте! Помогите разобраться...")
    @State private var photoViewer = false

    private let reactions = ["👍", "❤️", "🔥", "😂", "😮", "😢", "🙏", "👏"]
    private let mentionList = [("Владислав", "@Submetal"), ("Артем", "@Submetalllliiist"), ("Влад", "@Submetaliiist")]

    private var isDirect: Bool {
        chatId == "admin" || chatId.hasPrefix("dm-") || serverChat?.type == "direct"
    }

    private var chatTitle: (title: String, subtitle: String) {
        if let c = serverChat { return (c.title, c.subtitle ?? "") }
        return isDirect ? ("Игорь Гломозда", "Был в сети 1 час назад") : ("Нефть 12.12.27", "15 участников")
    }

    private var messages: [ChatMessage] {
        if let s = serverMessages { return s }
        return isDirect ? Self.directMessages : Self.groupMessages
    }

    private var showMentions: Bool { draft.contains("@") }
    private var showSend: Bool { !draft.trimmed.isEmpty || replyTo != nil }

    var body: some View {
        VStack(spacing: 0) {
            header
            if let pinned = pinned, !selectMode, !isDirect { pinnedBar(pinned) }

            ScrollView(showsIndicators: false) {
                LazyVStack(spacing: 10) {
                    HStack { Spacer()
                        Text("Сегодня").font(.system(size: Theme.fontSm)).foregroundStyle(Theme.textSecondary)
                            .padding(.horizontal, 14).padding(.vertical, 4)
                            .background(Theme.surfaceMuted).clipShape(Capsule())
                        Spacer() }
                        .padding(.vertical, 8)
                    ForEach(messages) { m in messageRow(m) }
                }
                .padding(.horizontal, Theme.screenX).padding(.bottom, 16)
            }

            composer
        }
        .screenBackground(
            ZStack { Color(hex: "#1a1b1d")
                RadialGradient(colors: [Color(hex: "#463746").opacity(0.4), .clear], center: .top, startRadius: 0, endRadius: 300) }
        )
        .overlay { if photoViewer { photoViewerOverlay } }
        .overlay { if menuFor != nil { contextMenuOverlay } }
        .task { await load() }
    }

    // MARK: - Header

    private var header: some View {
        Group {
            if selectMode {
                HStack {
                    Text("Выбрано \(selected.count)").pill()
                    Spacer()
                    Button { exitSelect() } label: { Text("Отмена").pill() }.buttonStyle(.plain)
                }
            } else {
                HStack(spacing: 10) {
                    Button { router.push(.chatInfo(id: chatId)) } label: {
                        VStack(spacing: 2) {
                            Text(chatTitle.title).font(.system(size: Theme.fontMd, weight: .bold))
                            Text(chatTitle.subtitle).font(.system(size: Theme.fontSm)).foregroundStyle(Theme.textSecondary)
                        }
                        .foregroundStyle(Theme.text)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8).padding(.horizontal, 16)
                        .background(Theme.surfaceMuted)
                        .overlay(Capsule().stroke(Color.white.opacity(0.08), lineWidth: 1))
                        .clipShape(Capsule())
                    }.buttonStyle(.plain)
                    Button { router.push(.chatInfo(id: chatId)) } label: {
                        Circle().fill(LinearGradient(colors: [Color(hex: "#cfc6d6"), Color(hex: "#a9adbd"), Color(hex: "#b7c2bf")],
                                                     startPoint: .topLeading, endPoint: .bottomTrailing))
                            .frame(width: 48, height: 48)
                    }.buttonStyle(.plain)
                }
            }
        }
        .padding(.horizontal, Theme.screenX).padding(.top, 12).padding(.bottom, 12)
    }

    private func pinnedBar(_ p: (title: String, text: String)) -> some View {
        HStack(spacing: 10) {
            VStack(alignment: .leading, spacing: 0) {
                Text(p.title).font(.system(size: Theme.fontSm, weight: .bold)).foregroundStyle(Color(hex: "#9ed06b"))
                Text(p.text).font(.system(size: Theme.fontBase)).foregroundStyle(Theme.textSecondary).lineLimit(1)
            }
            Spacer()
            Button { pinned = nil } label: { Icon(AppIcons.close, size: 20, color: Theme.textSecondary) }.buttonStyle(.plain)
        }
        .padding(.vertical, 10).padding(.horizontal, 14)
        .background(Theme.surface)
        .overlay(Rectangle().fill(Color(hex: "#7bc043")).frame(width: 3), alignment: .leading)
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .padding(.horizontal, Theme.screenX)
    }

    // MARK: - Message row

    private func messageRow(_ m: ChatMessage) -> some View {
        HStack(alignment: .bottom, spacing: 8) {
            if selectMode {
                Circle()
                    .fill(selected.contains(m.id) ? Theme.green : Color.clear)
                    .overlay(Circle().stroke(selected.contains(m.id) ? Theme.green : Color.white.opacity(0.4), lineWidth: 2))
                    .frame(width: 24, height: 24)
                    .overlay { if selected.contains(m.id) { Image(systemName: "checkmark").font(.system(size: 10, weight: .bold)).foregroundStyle(.white) } }
            }
            if !m.mine && !selectMode && !isDirect {
                Button { router.push(.chatUser(id: chatId, uid: "artem")) } label: {
                    Circle().fill(Color(hex: "#8a8a8a")).frame(width: 32, height: 32)
                }.buttonStyle(.plain)
            }
            if m.mine { Spacer(minLength: 0) }
            MessageBubble(message: m)
                .onTapGesture { onBubble(m) }
            if !m.mine { Spacer(minLength: 0) }
        }
        .frame(maxWidth: .infinity, alignment: m.mine ? .trailing : .leading)
    }

    private func onBubble(_ m: ChatMessage) {
        if selectMode { toggleSelect(m.id) }
        else if m.image { photoViewer = true }
        else { menuFor = m.id }
    }

    // MARK: - Composer

    @ViewBuilder private var composer: some View {
        if selectMode {
            HStack {
                roundButton(AppIcons.trash, danger: true) {}
                Spacer()
                roundButton(AppIcons.forward) {}
            }
            .padding(.horizontal, Theme.screenX).padding(.top, 10).padding(.bottom, 14)
        } else if recording {
            HStack(spacing: 10) {
                roundButton(AppIcons.trash, danger: true) { recording = false }
                HStack {
                    Circle().fill(Color(hex: "#e23b3b")).frame(width: 9, height: 9)
                    Text("00:03").font(.system(size: Theme.fontBase)).foregroundStyle(Theme.text)
                    Spacer()
                    Text("Отмена").font(.system(size: Theme.fontBase)).foregroundStyle(Theme.textSecondary)
                }
                .padding(.horizontal, 16).frame(height: 48)
                .background(Theme.surfaceMuted).overlay(Capsule().stroke(Color.white.opacity(0.08), lineWidth: 1)).clipShape(Capsule())
                Button { recording = false } label: {
                    Icon(AppIcons.plus, size: 22, color: .white).rotationEffect(.degrees(45))
                        .frame(width: 48, height: 48).background(Theme.greenSend).clipShape(Circle())
                }.buttonStyle(.plain)
            }
            .padding(.horizontal, Theme.screenX).padding(.top, 10).padding(.bottom, 14)
        } else {
            VStack(spacing: 0) {
                if attachOpen { attachMenu }
                if showMentions { mentionsMenu }
                if let reply = replyTo { replyStrip(reply) }
                HStack(spacing: 10) {
                    Button { attachOpen.toggle() } label: {
                        Icon(AppIcons.plus, size: 22, color: Theme.text).rotationEffect(.degrees(attachOpen ? 45 : 0))
                            .frame(width: 48, height: 48).background(Theme.surfaceMuted)
                            .overlay(Circle().stroke(Color.white.opacity(0.08), lineWidth: 1)).clipShape(Circle())
                    }.buttonStyle(.plain)
                    HStack {
                        TextField("", text: $draft, prompt: Text("Напишите что-нибудь").foregroundColor(Theme.textMuted))
                            .foregroundStyle(Theme.text).tint(Theme.green)
                            .onSubmit { send() }
                    }
                    .padding(.horizontal, 16).frame(height: 48)
                    .background(Theme.surfaceMuted).overlay(Capsule().stroke(Color.white.opacity(0.08), lineWidth: 1)).clipShape(Capsule())
                    if showSend {
                        Button { send() } label: {
                            Image(systemName: "arrow.right").font(.system(size: 18, weight: .semibold)).foregroundStyle(.white)
                                .frame(width: 48, height: 48).background(Theme.greenSend).clipShape(Circle())
                        }.buttonStyle(.plain)
                    } else {
                        Button { recording = true } label: {
                            Icon(AppIcons.mic, size: 22, color: Theme.text)
                                .frame(width: 48, height: 48).background(Theme.surfaceMuted)
                                .overlay(Circle().stroke(Color.white.opacity(0.08), lineWidth: 1)).clipShape(Circle())
                        }.buttonStyle(.plain)
                    }
                }
            }
            .padding(.horizontal, Theme.screenX).padding(.top, 10).padding(.bottom, 14)
        }
    }

    private var attachMenu: some View {
        VStack(alignment: .leading, spacing: 0) {
            attachItem("Изображение", system: "photo") { attachOpen = false }
            attachItem("Файл", system: "doc") { attachOpen = false }
        }
        .padding(6).background(Color(hex: "#2c2d30")).clipShape(RoundedRectangle(cornerRadius: 16))
        .frame(maxWidth: .infinity, alignment: .leading).padding(.bottom, 10)
    }

    private func attachItem(_ title: String, system: String, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 10) {
                Image(systemName: system).frame(width: 22, height: 22)
                Text(title).font(.system(size: Theme.fontMd))
            }.foregroundStyle(Theme.text).padding(10)
        }.buttonStyle(.plain)
    }

    private var mentionsMenu: some View {
        VStack(spacing: 0) {
            ForEach(mentionList, id: \.1) { u in
                Button { draft = draft.replacingOccurrences(of: #"@\S*$"#, with: u.1 + " ", options: .regularExpression) } label: {
                    HStack(spacing: 10) {
                        Circle().fill(Color(hex: "#8a8a8a")).frame(width: 28, height: 28)
                        Text(u.0).font(.system(size: Theme.fontBase, weight: .bold))
                        Text(u.1).font(.system(size: Theme.fontBase)).foregroundStyle(Theme.textSecondary)
                        Spacer()
                    }.foregroundStyle(Theme.text).padding(.horizontal, 14).padding(.vertical, 10)
                }.buttonStyle(.plain)
            }
        }
        .background(Color(hex: "#2c2d30")).clipShape(RoundedRectangle(cornerRadius: 16)).padding(.bottom, 10)
    }

    private func replyStrip(_ reply: ChatMessage) -> some View {
        HStack(spacing: 10) {
            Icon(AppIcons.reply, size: 18, color: Theme.textSecondary)
            VStack(alignment: .leading) {
                Text(reply.author ?? "Вы").font(.system(size: Theme.fontSm, weight: .bold))
                Text(reply.text ?? "Сообщение").font(.system(size: Theme.fontSm)).foregroundStyle(Theme.textSecondary).lineLimit(1)
            }
            Spacer()
            Button { replyTo = nil } label: { Icon(AppIcons.close, size: 18, color: Theme.textSecondary) }.buttonStyle(.plain)
        }
        .padding(.horizontal, 12).padding(.vertical, 8)
        .background(Theme.surfaceMuted).clipShape(RoundedRectangle(cornerRadius: 14)).padding(.bottom, 8)
    }

    private func roundButton(_ icon: IconDef, danger: Bool = false, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Icon(icon, size: 22, color: danger ? Theme.error : Theme.text)
                .frame(width: 52, height: 52).background(Theme.surfaceMuted)
                .overlay(Circle().stroke(Color.white.opacity(0.08), lineWidth: 1)).clipShape(Circle())
        }.buttonStyle(.plain)
    }

    // MARK: - Overlays

    private var photoViewerOverlay: some View {
        VStack(spacing: 0) {
            HStack(spacing: 10) {
                Button { photoViewer = false } label: {
                    Icon(AppIcons.back, size: 22, color: Theme.text).frame(width: 42, height: 42).background(Theme.surfaceMuted).clipShape(Circle())
                }.buttonStyle(.plain)
                VStack { Text("Вы").font(.system(size: Theme.fontMd, weight: .bold)); Text("Сегодня в 1:42").font(.system(size: Theme.fontSm)).foregroundStyle(Theme.textSecondary) }
                    .frame(maxWidth: .infinity)
                Image(systemName: "ellipsis").foregroundStyle(Theme.text).frame(width: 42, height: 42).background(Theme.surfaceMuted).clipShape(Circle())
            }
            .padding(.horizontal, Theme.screenX).padding(.top, 12).padding(.bottom, 12)
            Color(hex: "#d4d4d4").frame(maxWidth: .infinity, maxHeight: .infinity)
            HStack { roundButton(AppIcons.forward) {}; Spacer(); roundButton(AppIcons.trash, danger: true) {} }
                .padding(.horizontal, Theme.screenX).padding(.vertical, 14)
        }
        .background(Color.black.ignoresSafeArea())
    }

    private var contextMenuOverlay: some View {
        let m = messages.first { $0.id == menuFor }
        return ZStack {
            Color.black.opacity(0.6).ignoresSafeArea().onTapGesture { menuFor = nil }
            VStack(spacing: 12) {
                HStack {
                    ForEach(reactions, id: \.self) { r in Text(r).font(.system(size: 22)); if r != reactions.last { Spacer() } }
                }
                .padding(.horizontal, 14).padding(.vertical, 10).background(Color(hex: "#2c2d30")).clipShape(Capsule())
                if let m = m { HStack { MessageBubble(message: m); Spacer() } }
                VStack(spacing: 0) {
                    menuItem("Ответить", AppIcons.reply) { replyTo = m; menuFor = nil }
                    menuItem("Скопировать", AppIcons.copy) { menuFor = nil }
                    menuItem("Закрепить", AppIcons.pin) { menuFor = nil }
                    menuItem("Копировать ссылку", AppIcons.link) { menuFor = nil }
                    menuItem("Пожаловаться", AppIcons.report) { menuFor = nil }
                    menuItem("Удалить", AppIcons.trash, danger: true) { menuFor = nil }
                    Rectangle().fill(Color.white.opacity(0.1)).frame(height: 1).padding(.horizontal, 8).padding(.vertical, 4)
                    menuItem("Выбрать", AppIcons.checkSquare) { enterSelect() }
                }
                .padding(6).background(Color(hex: "#2c2d30")).clipShape(RoundedRectangle(cornerRadius: 18))
            }
            .frame(maxWidth: 340).padding(.horizontal, Theme.screenX)
        }
    }

    private func menuItem(_ title: String, _ icon: IconDef, danger: Bool = false, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Icon(icon, size: 20, color: danger ? Theme.error : Theme.text)
                Text(title).font(.system(size: Theme.fontMd)).foregroundStyle(danger ? Theme.error : Theme.text)
                Spacer()
            }.padding(12)
        }.buttonStyle(.plain)
    }

    // MARK: - Actions

    private func enterSelect() { selectMode = true; if let id = menuFor { selected = [id] }; menuFor = nil }
    private func exitSelect() { selectMode = false; selected = [] }
    private func toggleSelect(_ id: Int) { if selected.contains(id) { selected.remove(id) } else { selected.insert(id) } }

    private func send() {
        guard showSend else { return }
        let body = draft.trimmed
        draft = ""; replyTo = nil
        guard !body.isEmpty else { return }
        if serverMessages != nil {
            Task {
                if let message = try? await Api.sendMessage(chatId, body: body) {
                    serverMessages?.append(ChatMessage(message))
                }
            }
        }
    }

    private func load() async {
        do {
            let resp = try await Api.fetchMessages(chatId)
            serverChat = resp.chat
            serverMessages = resp.data.map(ChatMessage.init)
        } catch {
            serverMessages = nil; serverChat = nil
        }
    }

    // MARK: - Demo data

    static let groupMessages: [ChatMessage] = [
        ChatMessage(id: 1, author: "Артем", role: "Участник", text: "Здравствуте! Помогите разобраться с графиком", time: "20:41"),
        ChatMessage(id: 2, mine: true, text: "А то никак понять не могу", time: "20:42", read: true),
        ChatMessage(id: 3, author: "Владимир", role: "Участник",
                    text: "Ага, сам не понял, но видно, что очень хорошо сделан отчет и много чего было все равно было объяснено русским языком",
                    time: "20:42", reply: .init(author: "Алексей", text: "Я сам немного не оче..."),
                    reactions: [.init(emoji: "👍", count: 2)]),
        ChatMessage(id: 4, mine: true, time: "20:42", read: true, voice: .init(duration: "10:42")),
        ChatMessage(id: 5, author: "Артем", role: "Участник", time: "20:42", voice: .init(duration: "00:42")),
        ChatMessage(id: 6, author: "Артем", role: "Участник", text: "Здравствуте! Помогите разобраться с графиком",
                    time: "20:42", file: .init(ext: "zip", name: "zip file for windows", size: "1.9 мб")),
        ChatMessage(id: 7, mine: true, time: "20:42", read: true, image: true),
        ChatMessage(id: 8, mine: true, text: "«Как долго вы делали этот отчет?»", time: "20:42", read: true, toAuthor: true),
    ]

    static let directMessages: [ChatMessage] = [
        ChatMessage(id: 1, text: "Здравствуйте! Чем могу помочь?", time: "16:30"),
        ChatMessage(id: 2, mine: true, text: "Здравствуйте! Не получается оплатить подписку", time: "16:31", read: true),
        ChatMessage(id: 3, text: "Подскажите, пожалуйста, какой способ оплаты выбираете?", time: "16:31"),
        ChatMessage(id: 4, mine: true, text: "СБП", time: "16:32", read: true),
        ChatMessage(id: 5, text: "Что интересует?", time: "16:32"),
    ]
}

private extension View {
    func pill() -> some View {
        self.font(.system(size: Theme.fontBase)).foregroundStyle(Theme.text)
            .padding(.horizontal, 18).padding(.vertical, 10)
            .background(Theme.surfaceMuted)
            .overlay(Capsule().stroke(Color.white.opacity(0.08), lineWidth: 1))
            .clipShape(Capsule())
    }
}
