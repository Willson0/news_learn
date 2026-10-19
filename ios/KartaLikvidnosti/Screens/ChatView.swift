import SwiftUI
import PhotosUI
import UniformTypeIdentifiers

/// «Чат» (Figma 337:27172 и состояния 337:27570 … 337:36316): фон с узором из
/// треугольников, сверху на y=78 — стеклянные «назад», пилюля с названием и
/// числом участников, аватар 42; закреп — стеклянная плашка на y=144; снизу на
/// 45 pt от края — «+», поле ввода и микрофон (при наборе — зелёная «отправить»
/// внутри поля). Запись голосового: удержание микрофона, влево — отмена, вверх —
/// замок; в замке — пауза и отправка. Долгое нажатие — реакции и меню
/// действий на размытом фоне; режим выбора — «Выбрано N / Отмена», удалить и
/// переслать.
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
    @State private var deleteMenu = false
    @State private var selectMode = false
    @State private var selected: Set<Int> = []
    @State private var attachOpen = false
    @State private var photoItem: PhotosPickerItem? = nil
    @State private var fileImporterOpen = false
    @State private var photoViewerURL: URL? = nil
    @State private var loaded = false
    @State private var keyboardShown = false
    @State private var toast: String? = nil
    @State private var shareText: String? = nil
    /// Сообщения, удалённые «у меня» (локально, по чату).
    @State private var hiddenIds: Set<Int> = []

    // Запись голосового.
    @State private var micPressed = false
    @State private var recordLocked = false
    @State private var recordDrag: CGSize = .zero

    @FocusState private var inputFocused: Bool

    private let reactions = ["👍", "❤️", "🔥", "😂", "😮", "😢", "🙏", "👏"]
    private let secondary = Color.white.opacity(0.6)

    private var isDirect: Bool {
        chatId == "admin" || chatId.hasPrefix("dm-") || serverChat?.type == "direct"
    }

    private var chatTitle: (title: String, subtitle: String) {
        if let c = serverChat { return (c.title, c.subtitle ?? "") }
        return ("Чат", "")
    }

    private var visibleMessages: [ChatMessage] { messages.filter { !hiddenIds.contains($0.id) } }

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

    /// Высота шапки (и закрепа), под которой начинаются сообщения.
    private var contentTop: CGFloat {
        Theme.topBarY + 42 + 24 + (pinned != nil && !selectMode ? 65 + 12 : 0)
    }

    var body: some View {
        ZStack(alignment: .top) {
            messagesScroll
            topBar
        }
        .safeAreaInset(edge: .bottom, spacing: 0) { composer }
        .background(ChatBackdrop())
        .ignoresSafeArea(.container, edges: [.top, .bottom])
        .toolbar(.hidden, for: .navigationBar)
        .overlay { if menuFor != nil { contextMenuOverlay.transition(.opacity) } }
        .overlay { if photoViewerURL != nil { photoViewerOverlay } }
        .overlay(alignment: .top) {
            if let toast {
                GlassLabel(text: toast, height: 32, font: Theme.sans(Theme.fontBase))
                    .padding(.top, 104)
                    .transition(.move(edge: .top).combined(with: .opacity))
            }
        }
        .animation(.spring(response: 0.3, dampingFraction: 0.85), value: menuFor)
        .animation(.spring(response: 0.3, dampingFraction: 0.85), value: deleteMenu)
        .animation(.spring(response: 0.32, dampingFraction: 0.85), value: selectMode)
        .animation(.spring(response: 0.32, dampingFraction: 0.85), value: audio.recording)
        .animation(.spring(response: 0.32, dampingFraction: 0.85), value: recordLocked)
        .animation(.spring(response: 0.32, dampingFraction: 0.85), value: audio.paused)
        .animation(.spring(response: 0.3, dampingFraction: 0.85), value: attachOpen)
        .animation(.easeOut(duration: 0.2), value: toast)
        .fileImporter(isPresented: $fileImporterOpen, allowedContentTypes: [.item], allowsMultipleSelection: false) { result in
            handleFileImport(result)
        }
        .sheet(item: Binding(get: { shareText.map { ShareItem(text: $0) } }, set: { shareText = $0?.text })) { item in
            ShareSheet(items: [item.text])
        }
        .onChange(of: photoItem) { _ in loadPickedPhoto() }
        .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardWillShowNotification)) { _ in
            withAnimation(.easeOut(duration: 0.25)) { keyboardShown = true }
        }
        .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardWillHideNotification)) { _ in
            withAnimation(.easeOut(duration: 0.25)) { keyboardShown = false }
        }
        .onAppear { hiddenIds = Self.loadHidden(chatId) }
        .task { await load() }
    }

    // MARK: - Шапка

    private var topBar: some View {
        VStack(spacing: 24) {
            if selectMode {
                HStack {
                    Text("Выбрано \(selected.count)")
                        .font(Theme.sans(Theme.fontBase, .semibold))
                        .glassPill()
                    Spacer()
                    Button { exitSelect() } label: {
                        Text("Отмена").font(Theme.sans(Theme.fontBase)).glassPill()
                    }
                    .buttonStyle(PressableStyle())
                }
            } else {
                HStack(spacing: 0) {
                    GlassBackButton()
                    Spacer(minLength: 12)
                    Button { router.push(.chatInfo(id: chatId)) } label: {
                        VStack(spacing: 0) {
                            Text(chatTitle.title)
                                .font(Theme.sans(Theme.fontBase, .semibold))
                                .foregroundStyle(Theme.text)
                                .lineLimit(1)
                            if !chatTitle.subtitle.isEmpty {
                                Text(chatTitle.subtitle)
                                    .font(Theme.sans(10))
                                    .foregroundStyle(secondary)
                                    .lineLimit(1)
                            }
                        }
                        .padding(.horizontal, 24)
                        .frame(height: 42)
                        .liquidGlass(RoundedRectangle(cornerRadius: 24, style: .continuous), interactive: true)
                    }
                    .buttonStyle(PressableStyle())
                    Spacer(minLength: 12)
                    Button { router.push(.chatInfo(id: chatId)) } label: {
                        chatAvatar
                    }
                    .buttonStyle(.plain)
                }
                if let pinned { pinnedBar(pinned).transition(.move(edge: .top).combined(with: .opacity)) }
            }
        }
        .padding(.horizontal, Theme.screenX)
        .padding(.top, Theme.topBarY)
    }

    @ViewBuilder private var chatAvatar: some View {
        Group {
            if let url = fileURL(serverChat?.avatarUrl) {
                RemoteImage(url: url)
            } else {
                ReportCoverPlaceholder()
            }
        }
        .frame(width: 42, height: 42)
        .clipShape(Circle())
    }

    /// Закреплённое сообщение: стекло, радиус 24, линия 1 pt слева, заголовок
    /// SemiBold 14 и текст 14; крестик 12 справа.
    private func pinnedBar(_ p: MessageQuoteDto) -> some View {
        HStack(spacing: 10) {
            HStack(spacing: 12) {
                Rectangle().fill(Color.white).frame(width: 1, height: 40)
                VStack(alignment: .leading, spacing: 4) {
                    Text("Закрепленное сообщение").font(Theme.sans(Theme.fontBase, .semibold))
                    Text(p.text ?? "").font(Theme.sans(Theme.fontBase)).lineLimit(1)
                }
                .foregroundStyle(Theme.text)
                Spacer(minLength: 0)
            }
            Button { unpin(p.id) } label: {
                Icon(AppIcons.close, size: 12, color: Theme.text)
                    .frame(width: 38, height: 36)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        }
        .padding(.vertical, 12)
        .padding(.leading, 16)
        .padding(.trailing, 12)
        .frame(height: 65)
        .liquidGlass(RoundedRectangle(cornerRadius: Theme.radiusCard, style: .continuous))
    }

    // MARK: - Сообщения

    private var messagesScroll: some View {
        GeometryReader { geo in
            ScrollViewReader { proxy in
                ScrollView(showsIndicators: false) {
                    VStack(spacing: 0) {
                        if !loaded {
                            VStack(spacing: 10) {
                                ForEach(0..<7, id: \.self) { i in
                                    MessageSkeleton(mine: i % 3 == 0, wide: i % 2 == 0)
                                }
                            }
                        } else {
                            if !visibleMessages.isEmpty {
                                Text("Сегодня")
                                    .font(Theme.sans(Theme.fontSm))
                                    .foregroundStyle(Theme.text)
                                    .padding(.horizontal, 12)
                                    .frame(height: 24)
                                    .liquidGlass(Capsule())
                                    .padding(.bottom, 12)
                            }
                            let list = visibleMessages
                            ForEach(Array(list.enumerated()), id: \.element.id) { idx, m in
                                messageRow(m, prev: idx > 0 ? list[idx - 1] : nil,
                                           next: idx + 1 < list.count ? list[idx + 1] : nil)
                                    .padding(.top, idx == 0 ? 0 : (sameAuthor(list[idx - 1], m) ? 4 : 12))
                                    .id(m.id)
                            }
                        }
                        Color.clear.frame(height: 12).id("bottom")
                    }
                    .padding(.horizontal, Theme.screenX)
                    .padding(.top, contentTop)
                    .frame(minHeight: geo.size.height, alignment: .bottom)
                    .animation(.spring(response: 0.35, dampingFraction: 0.82), value: visibleMessages.count)
                }
                .scrollDismissesKeyboard(.interactively)
                .onChange(of: visibleMessages.count) { _ in
                    withAnimation(.easeOut(duration: 0.25)) { proxy.scrollTo("bottom", anchor: .bottom) }
                }
                .onChange(of: loaded) { _ in
                    DispatchQueue.main.async { proxy.scrollTo("bottom", anchor: .bottom) }
                }
                .onChange(of: keyboardShown) { _ in
                    withAnimation(.easeOut(duration: 0.25)) { proxy.scrollTo("bottom", anchor: .bottom) }
                }
            }
        }
    }

    private func sameAuthor(_ a: ChatMessage, _ b: ChatMessage) -> Bool {
        if a.mine != b.mine { return false }
        if a.mine { return true }
        return a.userId != nil && a.userId == b.userId
    }

    private func messageRow(_ m: ChatMessage, prev: ChatMessage?, next: ChatMessage?) -> some View {
        let isTail = next.map { !sameAuthor(m, $0) } ?? true
        let isFirst = prev.map { !sameAuthor($0, m) } ?? true
        let showAvatarColumn = !m.mine && !isDirect
        return HStack(alignment: .bottom, spacing: 16) {
            if selectMode {
                Icon(selected.contains(m.id) ? AppIcons.radioOn : AppIcons.radioOff, size: 20, color: Theme.text)
                    .frame(maxHeight: .infinity, alignment: .center)
                    .transition(.move(edge: .leading).combined(with: .opacity))
            }
            HStack(alignment: .bottom, spacing: 4) {
                if m.mine { Spacer(minLength: 0) }
                if showAvatarColumn {
                    Group {
                        if isTail {
                            Button { openUser(m) } label: {
                                messageAvatar(m)
                            }
                            .buttonStyle(.plain)
                        } else {
                            Color.clear
                        }
                    }
                    .frame(width: 30, height: 30)
                }
                bubble(m, isTail: isTail, showHeader: isFirst && !isDirect)
                    .opacity(menuFor == m.id ? 0 : 1)
                    .onTapGesture { onTap(m) }
                    .onLongPressGesture(minimumDuration: 0.3) { onLongPress(m) }
                if !m.mine { Spacer(minLength: 0) }
            }
        }
        .contentShape(Rectangle())
        .onTapGesture { if selectMode { toggleSelect(m.id) } }
        .transition(.asymmetric(
            insertion: .move(edge: .bottom).combined(with: .opacity)
                .combined(with: .scale(scale: 0.92, anchor: m.mine ? .bottomTrailing : .bottomLeading)),
            removal: .opacity))
    }

    @ViewBuilder private func messageAvatar(_ m: ChatMessage) -> some View {
        ZStack {
            Circle().fill(Color(hex: "#d9d9d9"))
            if let url = fileURL(m.avatarUrl) {
                RemoteImage(url: url).clipShape(Circle())
            } else {
                Text(String((m.author ?? "?").prefix(1)).uppercased())
                    .font(Theme.heading(13))
                    .foregroundStyle(Theme.text)
            }
        }
        .frame(width: 30, height: 30)
    }

    private func bubble(_ m: ChatMessage, isTail: Bool, showHeader: Bool) -> some View {
        MessageBubble(message: m,
                      isTail: isTail,
                      showHeader: showHeader,
                      isPlaying: audio.playingId == m.id,
                      voiceProgress: audio.playingId == m.id ? audio.progress : 0,
                      onPlayVoice: { audio.togglePlay(id: m.id, url: m.voice?.url) },
                      onOpenFile: { openURL(m.file?.url) },
                      onOpenImage: { if !selectMode { photoViewerURL = m.image?.url } })
    }

    /// Тап: в режиме выбора — отметить. Меню — только по долгому нажатию.
    private func onTap(_ m: ChatMessage) {
        if selectMode { toggleSelect(m.id) }
    }

    /// Долгое нажатие — реакции и меню действий (как в макете «зажатие сообщения»).
    private func onLongPress(_ m: ChatMessage) {
        if selectMode { toggleSelect(m.id); return }
        inputFocused = false
        Haptics.medium()
        deleteMenu = false
        menuFor = m.id
    }

    private func openUser(_ m: ChatMessage) {
        guard !selectMode, let uid = m.userId else { return }
        router.push(.chatUser(id: chatId, uid: String(uid)))
    }

    // MARK: - Нижняя панель

    @ViewBuilder private var composer: some View {
        Group {
            if selectMode {
                HStack {
                    GlassCircleButton(icon: AppIcons.trashX, iconSize: 24, iconColor: Theme.error) { deleteSelected() }
                    Spacer()
                    GlassCircleButton(icon: AppIcons.forward, iconSize: 24) { forwardSelected() }
                }
            } else if audio.recording && audio.paused {
                pausedRecordingBar
            } else {
                // Поле ввода остаётся в иерархии во время записи: жест удержания
                // микрофона продолжается, пока поверх показана плашка записи.
                ZStack {
                    inputBar
                        .opacity(audio.recording ? 0 : 1)
                        .allowsHitTesting(!audio.recording || micPressed)
                    if audio.recording {
                        recordingBar
                            .transition(.opacity)
                    }
                }
            }
        }
        .padding(.horizontal, Theme.screenX)
        .padding(.top, 12)
        .padding(.bottom, keyboardShown ? 12 : 45)
    }

    private var inputBar: some View {
        HStack(alignment: .bottom, spacing: 12) {
            // Поворачивается только «+», стекло остаётся на месте: вращение
            // самого glassEffect на iOS 26 раздувает линзу на поле ввода.
            Button {
                Haptics.light()
                attachOpen.toggle()
            } label: {
                Icon(AppIcons.plus, size: 20, color: Theme.text)
                    .rotationEffect(.degrees(attachOpen ? 45 : 0))
                    .frame(width: 42, height: 42)
                    .contentShape(Circle())
                    .liquidGlass(Circle())
            }
            .buttonStyle(PressableStyle())
            .overlay(alignment: .bottomLeading) {
                if attachOpen {
                    attachMenu
                        .fixedSize()
                        .offset(y: -54)
                        .transition(.scale(scale: 0.9, anchor: .bottomLeading).combined(with: .opacity))
                }
            }
            .zIndex(1)

            VStack(alignment: .leading, spacing: 8) {
                if let reply = replyTo { composerQuote(reply) }
                HStack(spacing: 10) {
                    TextField("", text: $draft, prompt: Text("Напишите что-нибудь").foregroundColor(Color.white.opacity(0.4)), axis: .vertical)
                        .lineLimit(1...5)
                        .font(Theme.sans(Theme.fontBase))
                        .foregroundStyle(Theme.text)
                        .tint(Theme.green)
                        .focused($inputFocused)
                    if showSend {
                        Button { send() } label: {
                            Icon(AppIcons.sendTriangle, size: 20, color: .white)
                                .offset(x: 1)
                                .frame(width: 42, height: 42)
                                .background(Theme.green, in: Circle())
                        }
                        .buttonStyle(PressableStyle())
                        .transition(.scale.combined(with: .opacity))
                    }
                }
            }
            .padding(.leading, 16)
            .padding(.trailing, showSend ? 0 : 12)
            .padding(.top, replyTo != nil ? 12 : 0)
            .frame(minHeight: 42)
            .liquidGlass(RoundedRectangle(cornerRadius: Theme.radiusInput, style: .continuous))
            .overlay(alignment: .bottomLeading) {
                if !mentionMatches.isEmpty {
                    mentionsMenu.offset(y: -54).transition(.opacity)
                }
            }

            if !showSend {
                micButton.transition(.scale.combined(with: .opacity))
            }
        }
        .animation(.spring(response: 0.3, dampingFraction: 0.85), value: showSend)
    }

    /// Цитата ответа внутри поля ввода (Figma 337:30778).
    private func composerQuote(_ reply: ChatMessage) -> some View {
        HStack(spacing: 4) {
            Rectangle().fill(Color.white).frame(width: 1)
            VStack(alignment: .leading, spacing: 0) {
                Text(reply.author ?? session.user?.name ?? "Вы").font(Theme.sans(Theme.fontSm, .semibold)).lineLimit(1)
                Text(reply.text ?? replyPreview(reply)).font(Theme.sans(Theme.fontSm)).foregroundStyle(secondary).lineLimit(1)
            }
            .foregroundStyle(Theme.text)
            Spacer(minLength: 4)
            Button { replyTo = nil } label: {
                Icon(AppIcons.close, size: 12, color: Theme.text).frame(width: 28, height: 32)
            }
            .buttonStyle(.plain)
        }
        .frame(height: 32)
        .background(Color.white.opacity(0.2),
                    in: UnevenRoundedRectangle(bottomTrailingRadius: 8, topTrailingRadius: 8, style: .continuous))
        .padding(.trailing, 12)
    }

    /// Микрофон: удержание — запись; влево — отмена, вверх — замок.
    private var micButton: some View {
        Icon(AppIcons.mic, size: 24, color: Theme.text)
            .frame(width: 42, height: 42)
            .contentShape(Circle())
            .liquidGlass(Circle(), interactive: true)
            .gesture(recordGesture)
    }

    private var recordGesture: some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { v in
                if !micPressed {
                    micPressed = true
                    startVoice()
                }
                guard audio.recording, !recordLocked else { return }
                recordDrag = v.translation
                if v.translation.width < -110 {
                    Haptics.light()
                    cancelVoice()
                } else if v.translation.height < -90 {
                    Haptics.medium()
                    recordLocked = true
                    recordDrag = .zero
                }
            }
            .onEnded { _ in
                micPressed = false
                recordDrag = .zero
                if audio.recording && !recordLocked { stopAndSendVoice() }
            }
    }

    /// Идёт запись (Figma 337:32380 / замок 337:32783): стеклянная плашка с
    /// красной точкой и временем, большая зелёная кнопка 75 с ореолом 96.
    private var recordingBar: some View {
        HStack(spacing: 0) {
            HStack(spacing: 4) {
                Circle().fill(Theme.error).frame(width: 8, height: 8)
                    .padding(.trailing, 4)
                Text(recordClock).font(Theme.sans(Theme.fontBase)).foregroundStyle(Theme.text).monospacedDigit()
                Spacer(minLength: 12)
                if recordLocked {
                    Button { cancelVoice() } label: {
                        Text("Отмена").font(Theme.sans(Theme.fontBase)).foregroundStyle(Theme.text)
                    }
                    .buttonStyle(.plain)
                    Spacer(minLength: 0)
                } else {
                    HStack(spacing: 4) {
                        Icon(AppIcons.back, size: 14, color: Theme.text)
                        Text("Влево — отмена").font(Theme.sans(Theme.fontBase)).foregroundStyle(Theme.text)
                    }
                    .offset(x: min(0, recordDrag.width / 3))
                    .opacity(1 + Double(min(0, recordDrag.width)) / 160)
                    Spacer(minLength: 0)
                }
            }
            .padding(.leading, 16)
            .padding(.trailing, 60)
            .frame(height: 44)
            .liquidGlass(RoundedRectangle(cornerRadius: Theme.radiusInput, style: .continuous))
            .padding(.trailing, 37)
        }
        .overlay(alignment: .trailing) {
            bigRecordButton.offset(x: 0)
        }
        .overlay(alignment: .topTrailing) {
            Group {
                if recordLocked {
                    Button { audio.pauseRecording() } label: {
                        HStack(spacing: 4) {
                            Capsule().fill(Color.white).frame(width: 3, height: 13)
                            Capsule().fill(Color.white).frame(width: 3, height: 13)
                        }
                        .frame(width: 48, height: 42)
                        .liquidGlass(RoundedRectangle(cornerRadius: 24, style: .continuous))
                    }
                    .buttonStyle(PressableStyle())
                    .offset(x: -13, y: -90)
                } else {
                    VStack(spacing: 6) {
                        Icon(AppIcons.lockOpen, size: 20, color: Theme.text)
                        Icon(AppIcons.back, size: 16, color: Theme.text).rotationEffect(.degrees(90))
                    }
                    .padding(.vertical, 12)
                    .frame(width: 48)
                    .liquidGlass(Capsule())
                    .offset(x: -13, y: -118 + max(-30, min(0, recordDrag.height / 3)))
                }
            }
            .transition(.opacity)
        }
    }

    private var bigRecordButton: some View {
        ZStack {
            ActiveFill().clipShape(Circle()).opacity(0.55).frame(width: 96, height: 96)
            Button {
                if recordLocked { stopAndSendVoice() }
            } label: {
                Group {
                    if recordLocked {
                        Image(systemName: "arrow.up").font(.system(size: 26, weight: .light)).foregroundStyle(.white)
                    } else {
                        Icon(AppIcons.mic, size: 24, color: .white)
                    }
                }
                .frame(width: 75, height: 75)
                .background(Theme.green, in: Circle())
            }
            .buttonStyle(.plain)
        }
        .frame(width: 96, height: 96)
        .offset(x: 11, y: 0)
        .allowsHitTesting(recordLocked)
    }

    /// Запись на паузе (Figma 337:33184): корзина, волна с плашкой «▶ 1:30»,
    /// зелёная «отправить»; над ней — микрофон для продолжения записи.
    private var pausedRecordingBar: some View {
        HStack(spacing: 12) {
            GlassCircleButton(icon: AppIcons.trashX, iconSize: 24, iconColor: Theme.error) { cancelVoice() }
            ZStack {
                HStack(spacing: 1.5) {
                    ForEach(0..<64, id: \.self) { i in
                        Capsule().fill(Color.white.opacity(0.4))
                            .frame(width: 1.5, height: CGFloat(2 + Int((abs(sin(Double(i) * 1.9)) * 5).rounded())))
                    }
                }
                .frame(height: 7)
                .clipped()
                HStack(spacing: 4) {
                    Icon(AppIcons.play, size: 9, color: Color.white.opacity(0.4))
                    Text(recordClock).font(Theme.sans(10)).foregroundStyle(secondary)
                }
                .padding(.horizontal, 14)
                .frame(height: 17)
                .background(Color(hex: "#5c5c5c"), in: Capsule())
            }
            .padding(.leading, 16)
            .padding(.trailing, 54)
            .frame(maxWidth: .infinity)
            .frame(height: 42)
            .liquidGlass(RoundedRectangle(cornerRadius: Theme.radiusInput, style: .continuous))
            .overlay(alignment: .trailing) {
                Button { stopAndSendVoice() } label: {
                    Icon(AppIcons.sendTriangle, size: 20, color: .white)
                        .offset(x: 1)
                        .frame(width: 42, height: 42)
                        .background(Theme.green, in: Circle())
                }
                .buttonStyle(PressableStyle())
            }
        }
        .overlay(alignment: .topTrailing) {
            GlassCircleButton(icon: AppIcons.mic, iconSize: 24) { audio.resumeRecording() }
                .offset(y: -66)
        }
    }

    /// popup/отправка: стекло «чёрный 2», радиус 24, 193 pt.
    private var attachMenu: some View {
        VStack(alignment: .leading, spacing: 12) {
            PhotosPicker(selection: $photoItem, matching: .images) {
                menuLabel("Изображение", AppIcons.picture, iconSize: 24)
            }
            .buttonStyle(.plain)
            Button { attachOpen = false; fileImporterOpen = true } label: {
                menuLabel("Файл", AppIcons.doc, iconSize: 20)
            }
            .buttonStyle(.plain)
        }
        .padding(16)
        .frame(width: 193, alignment: .leading)
        .liquidGlass(RoundedRectangle(cornerRadius: Theme.radiusCard, style: .continuous), fill: Theme.black2)
    }

    private func menuLabel(_ title: String, _ icon: IconDef, iconSize: CGFloat = 24, color: Color = Theme.text) -> some View {
        HStack(spacing: 4) {
            Icon(icon, size: iconSize, color: color).frame(width: 24, height: 24)
            Text(title).font(Theme.sans(Theme.fontBase)).foregroundStyle(color)
            Spacer(minLength: 0)
        }
        .contentShape(Rectangle())
    }

    private var mentionsMenu: some View {
        VStack(alignment: .leading, spacing: 12) {
            ForEach(mentionMatches.prefix(5)) { u in
                Button { applyMention(u) } label: {
                    HStack(spacing: 8) {
                        AvatarCircle(url: fileURL(u.avatarUrl), size: 24, name: u.name ?? u.username)
                        Text(u.name ?? "").font(Theme.sans(Theme.fontBase, .semibold)).foregroundStyle(Theme.text)
                        if let un = u.username {
                            Text(un).font(Theme.sans(Theme.fontBase)).foregroundStyle(secondary)
                        }
                        Spacer(minLength: 0)
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
        }
        .padding(16)
        .frame(width: 280, alignment: .leading)
        .liquidGlass(RoundedRectangle(cornerRadius: Theme.radiusCard, style: .continuous), fill: Theme.black2)
    }

    // MARK: - Меню сообщения

    /// «Чат зажатие сообщения» (Figma 337:36256 / 337:36316): размытие 12.9 и
    /// затемнение #070707 70 %, сверху плашка реакций, затем сообщение и
    /// стеклянное меню 193 pt.
    private var contextMenuOverlay: some View {
        let m = messages.first { $0.id == menuFor }
        return ZStack {
            ZStack {
                BackdropBlur(radius: 12.9)
                Color(hex: "#070707").opacity(0.7)
            }
            .ignoresSafeArea()
            .onTapGesture { closeMenu() }

            if let m {
                VStack(alignment: m.mine ? .trailing : .leading, spacing: 4) {
                    HStack(spacing: 4) {
                        ForEach(reactions, id: \.self) { r in
                            Button { react(m.id, r) } label: {
                                Text(r).font(.system(size: 20)).frame(width: 24, height: 24)
                            }
                            .buttonStyle(PressableStyle())
                        }
                    }
                    .padding(.horizontal, 12)
                    .frame(height: 40)
                    .background(Theme.surface, in: Capsule())

                    MessageBubble(message: m, isTail: true, showHeader: !isDirect)
                        .allowsHitTesting(false)

                    Group {
                        if deleteMenu { deleteMenuPanel(m) } else { actionsMenuPanel(m) }
                    }
                    .transition(.scale(scale: 0.95, anchor: m.mine ? .topTrailing : .topLeading).combined(with: .opacity))
                }
                .frame(maxWidth: .infinity, alignment: m.mine ? .trailing : .leading)
                .padding(.leading, m.mine || isDirect ? Theme.screenX : Theme.screenX + 34)
                .padding(.trailing, Theme.screenX)
                .transition(.scale(scale: 0.96).combined(with: .opacity))
            }
        }
    }

    private func actionsMenuPanel(_ m: ChatMessage) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            VStack(alignment: .leading, spacing: 12) {
                menuButton("Ответить", AppIcons.reply) { replyTo = m; closeMenu(); inputFocused = true }
                menuButton("Скопировать", AppIcons.copy) { copyText(m); closeMenu(); showToast("Скопировано") }
                menuButton(m.pinned ? "Открепить" : "Закрепить", AppIcons.pin) { togglePin(m.id) }
                menuButton("Копировать ссылку", AppIcons.link) { copyLink(m); closeMenu(); showToast("Ссылка скопирована") }
                if !session.isAdmin && !m.mine {
                    menuButton("Пожаловаться", AppIcons.report) { complain(m) }
                }
                menuButton("Удалить", AppIcons.trashX, color: Theme.error) { deleteMenu = true }
            }
            Rectangle().fill(Color.white.opacity(0.1)).frame(height: 1)
            menuButton("Выбрать", AppIcons.selectMultiple) { enterSelect() }
        }
        .padding(16)
        .frame(width: 193, alignment: .leading)
        .liquidGlass(RoundedRectangle(cornerRadius: Theme.radiusCard, style: .continuous))
    }

    private func deleteMenuPanel(_ m: ChatMessage) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            if canDelete(m) {
                Button { delete(m.id) } label: {
                    Text("Удалить у всех").font(Theme.sans(Theme.fontBase)).foregroundStyle(Theme.error)
                        .frame(maxWidth: .infinity, alignment: .leading).contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                Rectangle().fill(Color.white.opacity(0.1)).frame(height: 1)
            }
            Button { hideLocally([m.id]); closeMenu() } label: {
                Text("Удалить у меня").font(Theme.sans(Theme.fontBase)).foregroundStyle(Theme.error)
                    .frame(maxWidth: .infinity, alignment: .leading).contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        }
        .padding(16)
        .frame(width: 193, alignment: .leading)
        .liquidGlass(RoundedRectangle(cornerRadius: Theme.radiusCard, style: .continuous))
    }

    private func menuButton(_ title: String, _ icon: IconDef, color: Color = Theme.text, _ action: @escaping () -> Void) -> some View {
        Button {
            Haptics.light()
            action()
        } label: {
            menuLabel(title, icon, color: color)
        }
        .buttonStyle(.plain)
    }

    private func closeMenu() {
        menuFor = nil
        deleteMenu = false
    }

    // MARK: - Просмотр фото

    private var photoViewerOverlay: some View {
        ZStack(alignment: .top) {
            Color.black.ignoresSafeArea()
            AsyncImage(url: photoViewerURL) { phase in
                switch phase {
                case .success(let img): img.resizable().aspectRatio(contentMode: .fit)
                case .failure: Icon(AppIcons.picture, size: 48, color: Color.white.opacity(0.3))
                default: ProgressView().tint(.white)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            HStack {
                GlassBackButton { photoViewerURL = nil }
                Spacer()
                GlassCircleButton(icon: AppIcons.downloadCloud, iconSize: 24) { openURL(photoViewerURL) }
            }
            .padding(.horizontal, Theme.screenX)
            .padding(.top, Theme.topBarY)
        }
        .ignoresSafeArea()
    }

    // MARK: - Выбор

    private func enterSelect() {
        selected = menuFor.map { Set([$0]) } ?? []
        closeMenu()
        selectMode = true
        inputFocused = false
    }
    private func exitSelect() { selectMode = false; selected = [] }
    private func toggleSelect(_ id: Int) {
        Haptics.light()
        if selected.contains(id) { selected.remove(id) } else { selected.insert(id) }
    }

    private func canDelete(_ m: ChatMessage) -> Bool { m.mine || session.isAdmin }

    private func deleteSelected() {
        let ids = selected
        exitSelect()
        Task { @MainActor in
            var local: [Int] = []
            for id in ids {
                guard let m = messages.first(where: { $0.id == id }) else { continue }
                if canDelete(m) {
                    try? await Api.deleteMessage(chatId, id)
                    messages.removeAll { $0.id == id }
                    if pinned?.id == id { pinned = nil }
                } else {
                    local.append(id)
                }
            }
            if !local.isEmpty { hideLocally(local) }
        }
    }

    private func forwardSelected() {
        let texts = messages.filter { selected.contains($0.id) }.map { m -> String in
            let who = m.mine ? (session.user?.name ?? "Я") : (m.author ?? "")
            return "\(who): \(m.text ?? replyPreview(m))"
        }
        guard !texts.isEmpty else { return }
        shareText = texts.joined(separator: "\n")
    }

    // MARK: - Действия

    private var recordClock: String {
        String(format: "%02d:%02d", audio.elapsed / 60, audio.elapsed % 60)
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
        guard !data.isEmpty else {
            showAttachError("Файл не прочитался, попробуйте ещё раз")
            return
        }
        guard data.count <= Self.maxAttachmentBytes else {
            showAttachError("Файл больше 100 МБ, выберите файл поменьше")
            return
        }
        let reply = replyTo?.id
        // Текст из поля ввода становится подписью к фото и файлу (голосовое без подписи).
        let caption = kind == "voice" ? "" : draft.trimmed
        replyTo = nil
        if !caption.isEmpty { draft = "" }
        attachOpen = false
        Task { @MainActor in
            do {
                let dto = try await Api.sendMessage(chatId, body: caption.isEmpty ? nil : caption, replyTo: reply,
                                                    attachment: (data, filename, mime), kind: kind, duration: duration)
                if let dto { messages.append(ChatMessage(dto)) }
            } catch {
                if !caption.isEmpty { draft = caption }
                let message = (error as? ApiError)?.message ?? ""
                let tooBig = message.lowercased().contains("upload") || message.lowercased().contains("maximum")
                showAttachError(tooBig ? "Файл не загрузился: максимум 100 МБ" : "Не удалось отправить вложение")
            }
        }
    }

    static let maxAttachmentBytes = 100 * 1024 * 1024

    private func showAttachError(_ text: String) {
        toast = text
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.4) { if toast == text { toast = nil } }
    }

    private func startVoice() {
        audio.requestMicPermission { granted in
            guard granted else { return }
            Task { @MainActor in
                guard micPressed else { return }
                if audio.startRecording() { Haptics.medium() }
            }
        }
    }

    private func cancelVoice() {
        audio.cancelRecording()
        recordLocked = false
        recordDrag = .zero
    }

    private func stopAndSendVoice() {
        recordLocked = false
        if let result = audio.stopRecording() {
            sendAttachment(data: result.data, filename: "voice.m4a", mime: "audio/m4a", kind: "voice", duration: result.duration)
        }
    }

    private func react(_ id: Int, _ emoji: String) {
        closeMenu()
        Task { @MainActor in
            if let dto = try? await Api.reactMessage(chatId, id, emoji: emoji) { replaceMessage(dto) }
        }
    }

    private func togglePin(_ id: Int) {
        closeMenu()
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
        closeMenu()
        Task { @MainActor in
            try? await Api.deleteMessage(chatId, id)
            messages.removeAll { $0.id == id }
            if pinned?.id == id { pinned = nil }
        }
    }

    /// «Пожаловаться»: жалоба уходит сообщением в чат с администратором.
    private func complain(_ m: ChatMessage) {
        closeMenu()
        let quote = (m.text ?? replyPreview(m)).prefix(200)
        let body = "Жалоба на сообщение в чате «\(chatTitle.title)» от \(m.author ?? "участника"): «\(quote)»"
        Task { @MainActor in
            if (try? await Api.sendMessage("admin", body: body)) != nil {
                showToast("Жалоба отправлена администратору")
            } else {
                showToast("Не удалось отправить жалобу")
            }
        }
    }

    private func hideLocally(_ ids: [Int]) {
        hiddenIds.formUnion(ids)
        UserDefaults.standard.set(Array(hiddenIds), forKey: Self.hiddenKey(chatId))
    }

    private static func hiddenKey(_ chat: String) -> String { "kl_hidden_\(chat)" }
    private static func loadHidden(_ chat: String) -> Set<Int> {
        Set((UserDefaults.standard.array(forKey: hiddenKey(chat)) as? [Int]) ?? [])
    }

    private func copyText(_ m: ChatMessage?) {
        UIPasteboard.general.string = m?.text ?? ""
    }

    private func copyLink(_ m: ChatMessage) {
        let origin = AppConfig.apiBaseURL.replacingOccurrences(of: "api/", with: "")
        UIPasteboard.general.string = "\(origin)community/chat/\(chatId)?message=\(m.id)"
    }

    private func showToast(_ text: String) {
        toast = text
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.8) { if toast == text { toast = nil } }
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

    // MARK: - Вложения

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

    // MARK: - Загрузка

    private func load() async {
        await reloadMessages()
        loaded = true
        if let parts = try? await Api.chatParticipants(chatId) { participants = parts }
        #if DEBUG
        applyDebugState()
        #endif
    }

    private func reloadMessages() async {
        do {
            let resp = try await Api.fetchMessages(chatId)
            serverChat = resp.chat
            pinned = resp.pinned
            messages = resp.data.map(ChatMessage.init)
        } catch {}
    }

    #if DEBUG
    /// -kl_chat menu|select|typing|attach — состояния для сверки скриншотов.
    private func applyDebugState() {
        switch UserDefaults.standard.string(forKey: "kl_chat") {
        case "menu": menuFor = visibleMessages.last(where: { !$0.mine })?.id ?? visibleMessages.last?.id
        case "select": selectMode = true; if let id = visibleMessages.last?.id { selected = [id] }
        case "typing": draft = "Привет всем"
        case "attach": attachOpen = true
        case "reply": replyTo = visibleMessages.last
        default: break
        }
    }
    #endif
}

// MARK: - Фон чата

/// Фон «Чат»: градиент экрана, свечения и узор из треугольников 24.6 pt
/// (шаг 87.6, ряды через один сдвинуты на 32.9; заливка — белый 2 % → 0).
struct ChatBackdrop: View {
    var body: some View {
        ZStack {
            ScreenBackdrop()
            Canvas { ctx, size in
                let step: CGFloat = 87.6, side: CGFloat = 24.6
                let rowShift: CGFloat = 32.9
                // Фаза узора как в макете: ряд A на y=21.9 с x≡70.1, ряд B — x≡37.2.
                let y0: CGFloat = 21.9 - step * 2
                var row = 0
                var y = y0
                while y < size.height + side {
                    let xBase: CGFloat = row % 2 == 0 ? 37.2 : 37.2 + rowShift
                    var x = xBase.truncatingRemainder(dividingBy: step) - step
                    while x < size.width + side {
                        var p = Path()
                        p.move(to: CGPoint(x: x + side / 2, y: y))
                        p.addLine(to: CGPoint(x: x + side * 0.933, y: y + side * 0.75))
                        p.addLine(to: CGPoint(x: x + side * 0.067, y: y + side * 0.75))
                        p.closeSubpath()
                        ctx.fill(p, with: .linearGradient(
                            Gradient(colors: [Color.white.opacity(0.02), Color.white.opacity(0)]),
                            startPoint: CGPoint(x: x, y: y), endPoint: CGPoint(x: x, y: y + side)))
                        x += step
                    }
                    y += step
                    row += 1
                }
            }
            .allowsHitTesting(false)
        }
        .ignoresSafeArea()
    }
}

private struct ShareItem: Identifiable {
    let text: String
    var id: String { text }
}

private struct ShareSheet: UIViewControllerRepresentable {
    let items: [Any]
    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }
    func updateUIViewController(_ vc: UIActivityViewController, context: Context) {}
}

private extension View {
    /// Стеклянная пилюля шапки («Выбрано N», «Отмена»): 42 pt, отступы 24.
    func glassPill() -> some View {
        self.foregroundStyle(Theme.text)
            .padding(.horizontal, 24)
            .frame(height: 42)
            .liquidGlass(RoundedRectangle(cornerRadius: 24, style: .continuous))
    }
}
