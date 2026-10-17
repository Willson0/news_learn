import SwiftUI
import PhotosUI
import UniformTypeIdentifiers

struct ChatView: View {
    @EnvironmentObject var router: Router
    @EnvironmentObject var session: Session
    let chatId: String

    @StateObject private var audio = ChatAudio()

    @State private var draft = ""
    @State private var messages: [ChatMessage] = []
    @State private var serverChat: ChatItemDto? = nil
    @State private var participants: [ParticipantDto] = []
    @State private var pinned: MessageQuoteDto? = nil
    @State private var replyTo: ChatMessage? = nil
    @State private var menuFor: Int? = nil
    @State private var selectMode = false
    @State private var selected: Set<Int> = []
    @State private var attachOpen = false
    @State private var photoItem: PhotosPickerItem? = nil
    @State private var fileImporterOpen = false
    @State private var photoViewerURL: URL? = nil
    @State private var loaded = false

    private let reactions = ["👍", "❤️", "🔥", "😂", "😮", "😢", "🙏", "👏"]

    private var isDirect: Bool {
        chatId == "admin" || chatId.hasPrefix("dm-") || serverChat?.type == "direct"
    }

    private var chatTitle: (title: String, subtitle: String) {
        if let c = serverChat { return (c.title, c.subtitle ?? "") }
        return ("Чат", "")
    }

    private var mentionQuery: String? {
        guard let r = draft.range(of: #"@\S*$"#, options: .regularExpression) else { return nil }
        return String(draft[r]).dropFirst().lowercased()
    }

    private var mentionMatches: [ParticipantDto] {
        guard let q = mentionQuery else { return [] }
        return participants.filter { p in
            q.isEmpty
                || (p.username?.lowercased().contains(q) ?? false)
                || (p.name?.lowercased().contains(q) ?? false)
        }
    }

    private var showSend: Bool { !draft.trimmed.isEmpty }

    var body: some View {
        VStack(spacing: 0) {
            header
            if let pinned = pinned, !selectMode { pinnedBar(pinned) }

            ScrollView(showsIndicators: false) {
                LazyVStack(spacing: 10) {
                    if !loaded {
                        ForEach(0..<7, id: \.self) { i in
                            MessageSkeleton(mine: i % 3 == 0, wide: i % 2 == 0)
                        }
                    } else {
                        HStack { Spacer()
                            Text("Сегодня").font(.system(size: Theme.fontSm)).foregroundStyle(Theme.textSecondary)
                                .padding(.horizontal, 14).padding(.vertical, 4)
                                .liquidGlass()
                            Spacer() }
                            .padding(.vertical, 8)
                        ForEach(messages) { m in messageRow(m) }
                    }
                }
                .padding(.horizontal, Theme.screenX).padding(.bottom, 16)
                .animation(.spring(response: 0.35, dampingFraction: 0.82), value: messages.count)
            }

            composer
        }
        .screenBackground {
            ZStack { Color(hex: "#1a1b1d")
                RadialGradient(colors: [Color(hex: "#463746").opacity(0.4), .clear], center: .top, startRadius: 0, endRadius: 300) }
        }
        .overlay { if photoViewerURL != nil { photoViewerOverlay } }
        .overlay { if menuFor != nil { contextMenuOverlay.transition(.opacity) } }
        .fileImporter(isPresented: $fileImporterOpen, allowedContentTypes: [.item], allowsMultipleSelection: false) { result in
            handleFileImport(result)
        }
        .onChange(of: photoItem) { _ in loadPickedPhoto() }
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
                            if !chatTitle.subtitle.isEmpty {
                                Text(chatTitle.subtitle).font(.system(size: Theme.fontSm)).foregroundStyle(Theme.textSecondary)
                            }
                        }
                        .foregroundStyle(Theme.text)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8).padding(.horizontal, 16)
                        .liquidGlass()
                    }.buttonStyle(.plain)
                    Button { router.push(.chatInfo(id: chatId)) } label: {
                        AvatarCircle(url: fileURL(serverChat?.avatarUrl), size: 48)
                    }.buttonStyle(.plain)
                }
            }
        }
        .padding(.horizontal, Theme.screenX).padding(.top, 12).padding(.bottom, 12)
    }

    private func pinnedBar(_ p: MessageQuoteDto) -> some View {
        HStack(spacing: 10) {
            VStack(alignment: .leading, spacing: 0) {
                Text(p.author ?? "Закреплённое сообщение").font(.system(size: Theme.fontSm, weight: .bold)).foregroundStyle(Color(hex: "#9ed06b"))
                Text(p.text ?? "").font(.system(size: Theme.fontBase)).foregroundStyle(Theme.textSecondary).lineLimit(1)
            }
            Spacer()
            Button { unpin(p.id) } label: { Icon(AppIcons.close, size: 20, color: Theme.textSecondary) }.buttonStyle(.plain)
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
                Button { openUser(m) } label: {
                    AvatarCircle(url: fileURL(m.avatarUrl), size: 32)
                }.buttonStyle(.plain)
            }
            if m.mine { Spacer(minLength: 0) }
            MessageBubble(message: m,
                          isPlaying: audio.playingId == m.id,
                          voiceProgress: audio.playingId == m.id ? audio.progress : 0,
                          onPlayVoice: { audio.togglePlay(id: m.id, url: m.voice?.url) },
                          onOpenFile: { openURL(m.file?.url) })
                .scaleEffect(menuFor == m.id ? 1.04 : 1)
                .animation(.spring(response: 0.3, dampingFraction: 0.7), value: menuFor)
                .onTapGesture { onTap(m) }
                .onLongPressGesture(minimumDuration: 0.3) { onLongPress(m) }
            if !m.mine { Spacer(minLength: 0) }
        }
        .frame(maxWidth: .infinity, alignment: m.mine ? .trailing : .leading)
        .transition(.asymmetric(
            insertion: .move(edge: .bottom).combined(with: .opacity).combined(with: .scale(scale: 0.92, anchor: m.mine ? .bottomTrailing : .bottomLeading)),
            removal: .opacity))
    }

    /// Тап: в режиме выбора — отметить; фото — открыть просмотр. Меню — только по долгому нажатию.
    private func onTap(_ m: ChatMessage) {
        if selectMode { toggleSelect(m.id) }
        else if let url = m.image?.url { photoViewerURL = url }
    }

    /// Долгое нажатие — контекстное меню сообщения с подъёмом и вибрацией (как в TG/iMessage).
    private func onLongPress(_ m: ChatMessage) {
        if selectMode { toggleSelect(m.id); return }
        Haptics.medium()
        withAnimation(.spring(response: 0.34, dampingFraction: 0.78)) { menuFor = m.id }
    }

    private func openUser(_ m: ChatMessage) {
        guard let uid = m.userId else { return }
        router.push(.chatUser(id: chatId, uid: String(uid)))
    }

    // MARK: - Composer

    @ViewBuilder private var composer: some View {
        if selectMode {
            HStack {
                roundButton(AppIcons.trash, danger: true) { deleteSelected() }
                Spacer()
                Text("\(selected.count)").foregroundStyle(Theme.textSecondary)
            }
            .padding(.horizontal, Theme.screenX).padding(.top, 10).padding(.bottom, 14)
        } else if audio.recording {
            HStack(spacing: 10) {
                roundButton(AppIcons.trash, danger: true) { audio.cancelRecording() }
                HStack {
                    Circle().fill(Color(hex: "#e23b3b")).frame(width: 9, height: 9)
                    Text(recordClock).font(.system(size: Theme.fontBase)).foregroundStyle(Theme.text)
                    Spacer()
                    Text("Идёт запись…").font(.system(size: Theme.fontBase)).foregroundStyle(Theme.textSecondary)
                }
                .padding(.horizontal, 16).frame(height: 48)
                .liquidGlass()
                Button { stopAndSendVoice() } label: {
                    Image(systemName: "arrow.up").font(.system(size: 18, weight: .semibold)).foregroundStyle(.white)
                        .frame(width: 48, height: 48).background(Theme.greenSend).clipShape(Circle())
                }.buttonStyle(.plain)
            }
            .padding(.horizontal, Theme.screenX).padding(.top, 10).padding(.bottom, 14)
        } else {
            VStack(spacing: 0) {
                if attachOpen { attachMenu }
                if !mentionMatches.isEmpty { mentionsMenu }
                if let reply = replyTo { replyStrip(reply) }
                HStack(spacing: 10) {
                    Button { withAnimation { attachOpen.toggle() } } label: {
                        Icon(AppIcons.plus, size: 22, color: Theme.text).rotationEffect(.degrees(attachOpen ? 45 : 0))
                            .frame(width: 48, height: 48).liquidGlass()
                    }.buttonStyle(.plain)
                    HStack {
                        TextField("", text: $draft, prompt: Text("Напишите что-нибудь").foregroundColor(Theme.textMuted))
                            .foregroundStyle(Theme.text).tint(Theme.green)
                            .onSubmit { send() }
                    }
                    .padding(.horizontal, 16).frame(height: 48)
                    .liquidGlass()
                    if showSend {
                        Button { send() } label: {
                            Image(systemName: "arrow.right").font(.system(size: 18, weight: .semibold)).foregroundStyle(.white)
                                .frame(width: 48, height: 48).background(Theme.greenSend).clipShape(Circle())
                        }.buttonStyle(.plain)
                    } else {
                        Button { startVoice() } label: {
                            Icon(AppIcons.mic, size: 22, color: Theme.text)
                                .frame(width: 48, height: 48).liquidGlass()
                        }.buttonStyle(.plain)
                    }
                }
            }
            .padding(.horizontal, Theme.screenX).padding(.top, 10).padding(.bottom, 14)
        }
    }

    private var attachMenu: some View {
        VStack(alignment: .leading, spacing: 0) {
            PhotosPicker(selection: $photoItem, matching: .images) {
                attachLabel("Изображение", system: "photo")
            }
            Button { attachOpen = false; fileImporterOpen = true } label: {
                attachLabel("Файл", system: "doc")
            }.buttonStyle(.plain)
        }
        .padding(6).background(Color(hex: "#2c2d30")).clipShape(RoundedRectangle(cornerRadius: 16))
        .frame(maxWidth: .infinity, alignment: .leading).padding(.bottom, 10)
    }

    private func attachLabel(_ title: String, system: String) -> some View {
        HStack(spacing: 10) {
            Image(systemName: system).frame(width: 22, height: 22)
            Text(title).font(.system(size: Theme.fontMd))
        }.foregroundStyle(Theme.text).padding(10)
    }

    private var mentionsMenu: some View {
        VStack(spacing: 0) {
            ForEach(mentionMatches) { u in
                Button { applyMention(u) } label: {
                    HStack(spacing: 10) {
                        AvatarCircle(url: fileURL(u.avatarUrl), size: 28)
                        Text(u.name ?? "").font(.system(size: Theme.fontBase, weight: .bold))
                        if let un = u.username { Text(un).font(.system(size: Theme.fontBase)).foregroundStyle(Theme.textSecondary) }
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
                Text(reply.text ?? replyPreview(reply)).font(.system(size: Theme.fontSm)).foregroundStyle(Theme.textSecondary).lineLimit(1)
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
                .frame(width: 52, height: 52).liquidGlass()
        }.buttonStyle(.plain)
    }

    // MARK: - Overlays

    private var photoViewerOverlay: some View {
        VStack(spacing: 0) {
            HStack(spacing: 10) {
                Button { photoViewerURL = nil } label: {
                    Icon(AppIcons.back, size: 22, color: Theme.text).frame(width: 42, height: 42).liquidGlass()
                }.buttonStyle(.plain)
                Spacer()
                Button { openURL(photoViewerURL) } label: {
                    Icon(AppIcons.downloadCloud, size: 22, color: Theme.text).frame(width: 42, height: 42).liquidGlass()
                }.buttonStyle(.plain)
            }
            .padding(.horizontal, Theme.screenX).padding(.top, 12).padding(.bottom, 12)
            Spacer()
            AsyncImage(url: photoViewerURL) { phase in
                switch phase {
                case .success(let img): img.resizable().aspectRatio(contentMode: .fit)
                case .failure: Image(systemName: "photo").font(.system(size: 60)).foregroundStyle(.white.opacity(0.3))
                default: ProgressView().tint(.white)
                }
            }
            .frame(maxWidth: .infinity)
            Spacer()
        }
        .background(Color.black.ignoresSafeArea())
    }

    private var contextMenuOverlay: some View {
        let m = messages.first { $0.id == menuFor }
        return ZStack {
            Color.black.opacity(0.6).ignoresSafeArea()
                .onTapGesture { withAnimation(.easeOut(duration: 0.2)) { menuFor = nil } }
            VStack(spacing: 12) {
                HStack {
                    ForEach(reactions, id: \.self) { r in
                        Button { react(menuFor, r) } label: { Text(r).font(.system(size: 22)) }.buttonStyle(.plain)
                        if r != reactions.last { Spacer() }
                    }
                }
                .padding(.horizontal, 14).padding(.vertical, 10).liquidGlass()
                if let m = m { HStack { MessageBubble(message: m); Spacer() } }
                VStack(spacing: 0) {
                    menuItem("Ответить", AppIcons.reply) { replyTo = m; menuFor = nil }
                    menuItem("Скопировать", AppIcons.copy) { copyText(m); menuFor = nil }
                    menuItem(m?.pinned == true ? "Открепить" : "Закрепить", AppIcons.pin) { if let id = menuFor { togglePin(id) } }
                    if let m = m, canDelete(m) {
                        menuItem("Удалить", AppIcons.trash, danger: true) { delete(m.id) }
                    }
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

    // MARK: - Select mode

    private func enterSelect() { selectMode = true; if let id = menuFor { selected = [id] }; menuFor = nil }
    private func exitSelect() { selectMode = false; selected = [] }
    private func toggleSelect(_ id: Int) { if selected.contains(id) { selected.remove(id) } else { selected.insert(id) } }

    private func canDelete(_ m: ChatMessage) -> Bool { m.mine || session.isAdmin }

    private func deleteSelected() {
        let ids = selected
        exitSelect()
        Task { @MainActor in
            for id in ids {
                if let m = messages.first(where: { $0.id == id }), canDelete(m) {
                    try? await Api.deleteMessage(chatId, id)
                    messages.removeAll { $0.id == id }
                    if pinned?.id == id { pinned = nil }
                }
            }
        }
    }

    // MARK: - Actions

    private var recordClock: String {
        String(format: "%d:%02d", audio.elapsed / 60, audio.elapsed % 60)
    }

    private func send() {
        let body = draft.trimmed
        guard !body.isEmpty else { return }
        draft = ""
        let reply = replyTo?.id
        replyTo = nil
        Task { @MainActor in
            if let dto = try? await Api.sendMessage(chatId, body: body, replyTo: reply) {
                messages.append(ChatMessage(dto))
            }
        }
    }

    private func sendAttachment(data: Data, filename: String, mime: String, kind: String, duration: Int? = nil) {
        let reply = replyTo?.id
        replyTo = nil
        attachOpen = false
        Task { @MainActor in
            if let dto = try? await Api.sendMessage(chatId, replyTo: reply, attachment: (data, filename, mime), kind: kind, duration: duration) {
                messages.append(ChatMessage(dto))
            }
        }
    }

    private func startVoice() {
        audio.requestMicPermission { granted in
            guard granted else { return }
            Task { @MainActor in _ = audio.startRecording() }
        }
    }

    private func stopAndSendVoice() {
        if let result = audio.stopRecording() {
            sendAttachment(data: result.data, filename: "voice.m4a", mime: "audio/m4a", kind: "voice", duration: result.duration)
        }
    }

    private func react(_ id: Int?, _ emoji: String) {
        guard let id = id else { return }
        menuFor = nil
        Task { @MainActor in
            if let dto = try? await Api.reactMessage(chatId, id, emoji: emoji) { replaceMessage(dto) }
        }
    }

    private func togglePin(_ id: Int) {
        menuFor = nil
        Task { @MainActor in
            pinned = try? await Api.pinMessage(chatId, id)
            await reloadMessages()
        }
    }

    private func unpin(_ id: Int) {
        Task { @MainActor in
            pinned = try? await Api.pinMessage(chatId, id)
            await reloadMessages()
        }
    }

    private func delete(_ id: Int) {
        menuFor = nil
        Task { @MainActor in
            try? await Api.deleteMessage(chatId, id)
            messages.removeAll { $0.id == id }
            if pinned?.id == id { pinned = nil }
        }
    }

    private func copyText(_ m: ChatMessage?) {
        UIPasteboard.general.string = m?.text ?? ""
    }

    private func replaceMessage(_ dto: MessageDto) {
        if let idx = messages.firstIndex(where: { $0.id == dto.id }) { messages[idx] = ChatMessage(dto) }
    }

    private func applyMention(_ u: ParticipantDto) {
        let handle = u.username ?? ("@" + (u.name ?? ""))
        draft = draft.replacingOccurrences(of: #"@\S*$"#, with: handle + " ", options: .regularExpression)
    }

    private func replyPreview(_ m: ChatMessage) -> String {
        if m.image != nil { return "Фото" }
        if m.voice != nil { return "Голосовое сообщение" }
        if let f = m.file { return f.name }
        return "Сообщение"
    }

    private func openURL(_ url: URL?) {
        guard let url = url else { return }
        UIApplication.shared.open(url)
    }

    // MARK: - Pickers

    private func loadPickedPhoto() {
        guard let item = photoItem else { return }
        attachOpen = false
        Task { @MainActor in
            if let data = try? await item.loadTransferable(type: Data.self) {
                sendAttachment(data: data, filename: "photo.jpg", mime: "image/jpeg", kind: "image")
            }
            photoItem = nil
        }
    }

    private func handleFileImport(_ result: Result<[URL], Error>) {
        guard case .success(let urls) = result, let url = urls.first else { return }
        let accessed = url.startAccessingSecurityScopedResource()
        defer { if accessed { url.stopAccessingSecurityScopedResource() } }
        guard let data = try? Data(contentsOf: url) else { return }
        let mime = UTType(filenameExtension: url.pathExtension)?.preferredMIMEType ?? "application/octet-stream"
        sendAttachment(data: data, filename: url.lastPathComponent, mime: mime, kind: "file")
    }

    // MARK: - Loading

    private func load() async {
        await reloadMessages()
        loaded = true
        if let parts = try? await Api.chatParticipants(chatId) { participants = parts }
    }

    private func reloadMessages() async {
        do {
            let resp = try await Api.fetchMessages(chatId)
            serverChat = resp.chat
            pinned = resp.pinned
            messages = resp.data.map(ChatMessage.init)
        } catch {}
    }
}

private extension View {
    func pill() -> some View {
        self.font(.system(size: Theme.fontBase)).foregroundStyle(Theme.text)
            .padding(.horizontal, 18).padding(.vertical, 10)
            .liquidGlass()
    }
}
