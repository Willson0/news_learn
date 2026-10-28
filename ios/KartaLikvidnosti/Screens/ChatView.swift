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
/// переслать. Закрепов несколько (как в Телеграме): тап по плашке ведёт к
/// сообщению и листает закрепы. Поиск по всем сообщениям — как в Телеграме:
/// строка поиска сверху, внизу «N из M» со стрелками и список результатов.
struct ChatView: View {
    @EnvironmentObject var router: Router
    @EnvironmentObject var session: Session
    let chatId: String

    @StateObject private var audio = ChatAudio()

    @State private var draft = ""
    @State private var messages: [ChatMessage] = []
    @State private var serverChat: ChatItemDto? = nil
    /// Кандидаты для «@»: все, у кого есть доступ к чату (с сервера по мере ввода).
    @State private var mentionCandidates: [ParticipantDto] = []
    @State private var mentionTask: Task<Void, Never>? = nil
    /// Закреплённые сообщения по порядку закрепления; на плашке — pins[pinIndex].
    @State private var pins: [MessageQuoteDto] = []
    @State private var pinIndex = 0
    /// Открепление спрашивает подтверждение.
    @State private var unpinConfirm: MessageQuoteDto? = nil
    /// Попап «Удалить у всех / у меня» над корзиной в режиме выбора.
    @State private var selectDeleteMenu = false
    /// Переход к сообщению (закреп, поиск): прокрутка и короткая подсветка.
    @State private var scrollRequest = ScrollRequest()
    @State private var flashId: Int? = nil
    // Поиск по сообщениям.
    @State private var searchMode = false
    @State private var searchQuery = ""
    @State private var searchIndex = 0
    @State private var searchListOpen = false
    @FocusState private var searchFocused: Bool
    @State private var replyTo: ChatMessage? = nil
    @State private var menuFor: Int? = nil
    @State private var deleteMenu = false
    @State private var selectMode = false
    @State private var selected: Set<Int> = []
    @State private var attachOpen = false
    @State private var photoItems: [PhotosPickerItem] = []
    /// Выбранные фото, которые ещё не отправлены: показываются превью над полем ввода.
    @State private var pendingPhotos: [PendingPhoto] = []
    @State private var fileImporterOpen = false
    @State private var photoViewerURL: URL? = nil
    @State private var loaded = false
    @State private var keyboardShown = false
    @State private var toast: String? = nil
    @State private var shareText: String? = nil
    /// Сообщения, удалённые «у меня» на этом устройстве в старых версиях (теперь скрытие хранит сервер).
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

    /// Набирается упоминание: «@» в начале слова в конце текста.
    private var mentionQuery: String? {
        guard let r = draft.range(of: #"(^|\s)@[^\s@]*$"#, options: .regularExpression) else { return nil }
        let token = draft[r].trimmingCharacters(in: .whitespaces)
        return String(token.dropFirst())
    }

    private var mentionMatches: [ParticipantDto] {
        guard let q = mentionQuery?.lowercased() else { return [] }
        let list = mentionCandidates.filter { p in
            q.isEmpty
                || (p.username?.lowercased().contains(q) ?? false)
                || (p.name?.lowercased().contains(q) ?? false)
        }
        return Array(list.prefix(5))
    }

    private var showSend: Bool { !draft.trimmed.isEmpty || !pendingPhotos.isEmpty }

    private var canPin: Bool { serverChat?.canPin ?? false }

    /// Текущий закреп на плашке.
    private var currentPin: MessageQuoteDto? {
        guard !pins.isEmpty else { return nil }
        return pins[min(max(pinIndex, 0), pins.count - 1)]
    }

    /// «/текст/» в чате отчёта уходит автору лично.
    private var draftIsAuthorMessage: Bool {
        guard serverChat?.authorMessages == true else { return false }
        let t = draft.trimmed
        return t.count >= 2 && t.hasPrefix("/")
    }

    /// Высота шапки (и закрепа), под которой начинаются сообщения.
    private var contentTop: CGFloat {
        Theme.topBarY + 42 + 24 + (currentPin != nil && !selectMode && !searchMode ? 65 + 12 : 0)
    }

    // MARK: Поиск

    /// Найденные сообщения (id, от старых к новым): текст, подпись, имя файла.
    private var searchResults: [Int] {
        let q = searchQuery.trimmed
        guard !q.isEmpty else { return [] }
        return visibleMessages.filter { m in
            guard !m.isSystem else { return false }
            let haystack = [m.text, m.file?.name].compactMap { $0 }
            return haystack.contains { $0.range(of: q, options: [.caseInsensitive, .diacriticInsensitive]) != nil }
        }.map(\.id)
    }

    /// Текущий результат: счёт идёт от самого нового (1 из N), стрелка вверх — к более старым.
    private var currentSearchId: Int? {
        let r = searchResults
        guard !r.isEmpty else { return nil }
        return r[r.count - 1 - min(searchIndex, r.count - 1)]
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
        .overlay { if searchMode && searchListOpen { searchListOverlay.transition(.opacity) } }
        .overlay { if menuFor != nil { contextMenuOverlay.transition(.opacity) } }
        .overlay { if photoViewerURL != nil { photoViewerOverlay.transition(.opacity) } }
        .overlay(alignment: .top) {
            if let toast {
                GlassLabel(text: toast, height: 32, font: Theme.sans(Theme.fontBase))
                    .padding(.top, 132)
                    .transition(.opacity)
            }
        }
        .animation(.spring(response: 0.3, dampingFraction: 0.85), value: menuFor)
        .animation(.spring(response: 0.3, dampingFraction: 0.85), value: deleteMenu)
        .animation(.spring(response: 0.32, dampingFraction: 0.85), value: selectMode)
        .animation(.spring(response: 0.32, dampingFraction: 0.85), value: searchMode)
        .animation(.easeOut(duration: 0.2), value: searchListOpen)
        .animation(.easeOut(duration: 0.2), value: selectDeleteMenu)
        .animation(Motion.standard, value: pins.count)
        // Прототип: запись голосового (Чат 495:16053 → 495:16591), замок/пауза и
        // попап «+» (popup/отправка) — SMART_ANIMATE / DISSOLVE 800 мс, ease-in-out-back.
        .animation(Motion.standard, value: audio.recording)
        .animation(Motion.standard, value: recordLocked)
        .animation(Motion.standard, value: audio.paused)
        .animation(Motion.standard, value: attachOpen)
        // Просмотр фото (Чат 495:20480 → «Чат нажатие на фото» и назад) — 800 мс.
        .animation(Motion.standard, value: photoViewerURL)
        .animation(.easeOut(duration: 0.2), value: toast)
        .fileImporter(isPresented: $fileImporterOpen, allowedContentTypes: [.item], allowsMultipleSelection: false) { result in
            handleFileImport(result)
        }
        .sheet(item: Binding(get: { shareText.map { ShareItem(text: $0) } }, set: { shareText = $0?.text })) { item in
            ShareSheet(items: [item.text])
        }
        .onChange(of: photoItems) { _ in loadPickedPhotos() }
        .onChange(of: mentionQuery) { q in fetchMentions(q) }
        .onChange(of: searchQuery) { _ in
            searchIndex = 0
            if let id = currentSearchId { jump(to: id, flash: false) }
        }
        .onChange(of: router.chatSearchRequest) { req in if req == chatId { router.chatSearchRequest = nil; openSearch() } }
        .alert("Открепить сообщение?", isPresented: Binding(get: { unpinConfirm != nil }, set: { if !$0 { unpinConfirm = nil } }),
               presenting: unpinConfirm) { p in
            Button("Открепить", role: .destructive) { unpin(p.id) }
            Button("Отмена", role: .cancel) {}
        } message: { p in
            Text(p.text ?? "")
        }
        .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardWillShowNotification)) { _ in
            withAnimation(.easeOut(duration: 0.25)) { keyboardShown = true }
        }
        .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardWillHideNotification)) { _ in
            withAnimation(.easeOut(duration: 0.25)) { keyboardShown = false }
        }
        .onAppear {
            hiddenIds = Self.loadHidden(chatId)
            if router.chatSearchRequest == chatId { router.chatSearchRequest = nil; openSearch() }
        }
        .task { await load() }
    }

    // MARK: - Шапка

    private var topBar: some View {
        VStack(spacing: 24) {
            if searchMode {
                searchTopBar
            } else if selectMode {
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
                if let pin = currentPin { pinnedBar(pin).transition(.move(edge: .top).combined(with: .opacity)) }
            }
        }
        .padding(.horizontal, Theme.screenX)
        .padding(.top, Theme.topBarY)
    }

    @ViewBuilder private var chatAvatar: some View {
        Group {
            if let url = fileURL(serverChat?.avatarUrl) {
                RemoteImage(url: url) { ReportCoverPlaceholder() }
            } else if serverChat != nil {
                ReportCoverPlaceholder()
            } else {
                LoadingBlock()
            }
        }
        .frame(width: 42, height: 42)
        .clipShape(Circle())
    }

    /// Закреплённое сообщение: стекло, радиус 24, линия 1 pt слева, заголовок
    /// SemiBold 14 и текст 14; крестик 12 справа. Закрепов несколько — линия
    /// делится на отрезки (как в Телеграме), текущий ярче; тап ведёт к сообщению
    /// и переключает на предыдущий закреп. Крестик — только у админа и автора отчёта.
    private func pinnedBar(_ p: MessageQuoteDto) -> some View {
        HStack(spacing: 10) {
            Button { openPin(p) } label: {
                HStack(spacing: 12) {
                    pinIndicator.frame(width: 2, height: 40)
                    VStack(alignment: .leading, spacing: 4) {
                        Text(pins.count > 1 ? "Закрепленное сообщение #\(pinIndex + 1)" : "Закрепленное сообщение")
                            .font(Theme.sans(Theme.fontBase, .semibold))
                        Text(p.text ?? "").font(Theme.sans(Theme.fontBase)).lineLimit(1)
                            .id(p.id)
                            .transition(.asymmetric(insertion: .move(edge: .bottom).combined(with: .opacity),
                                                    removal: .move(edge: .top).combined(with: .opacity)))
                    }
                    .foregroundStyle(Theme.text)
                    .clipped()
                    Spacer(minLength: 0)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            if canPin {
                Button { unpinConfirm = p } label: {
                    Icon(AppIcons.close, size: 12, color: Theme.text)
                        .frame(width: 38, height: 36)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
        }
        .animation(.easeOut(duration: 0.22), value: p.id)
        .padding(.vertical, 12)
        .padding(.leading, 16)
        .padding(.trailing, 12)
        .frame(height: 65)
        .liquidGlass(RoundedRectangle(cornerRadius: Theme.radiusCard, style: .continuous))
    }

    /// Линия слева у закрепа: при нескольких закрепах — отрезки, текущий белый.
    private var pinIndicator: some View {
        GeometryReader { geo in
            let count = max(pins.count, 1)
            let gap: CGFloat = count > 1 ? 2 : 0
            let h = (geo.size.height - gap * CGFloat(count - 1)) / CGFloat(count)
            VStack(spacing: gap) {
                ForEach(0..<count, id: \.self) { i in
                    Capsule().fill(Color.white.opacity(i == pinIndex || count == 1 ? 1 : 0.35))
                        .frame(height: max(h, 2))
                }
            }
        }
    }

    /// Шапка поиска: стеклянное поле с лупой и «Отмена».
    private var searchTopBar: some View {
        HStack(spacing: 12) {
            HStack(spacing: 8) {
                Icon(AppIcons.search, size: 18, color: secondary)
                TextField("", text: $searchQuery, prompt: Text("Поиск").foregroundColor(Color.white.opacity(0.4)))
                    .font(Theme.sans(Theme.fontBase))
                    .foregroundStyle(Theme.text)
                    .tint(Theme.green)
                    .focused($searchFocused)
                    .submitLabel(.search)
                    .autocorrectionDisabled()
                if !searchQuery.isEmpty {
                    Button { searchQuery = "" } label: {
                        Icon(AppIcons.close, size: 10, color: Theme.text)
                            .frame(width: 20, height: 20)
                            .background(Color.white.opacity(0.2), in: Circle())
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 16)
            .frame(height: 42)
            .liquidGlass(RoundedRectangle(cornerRadius: 24, style: .continuous), interactive: true)
            Button { closeSearch() } label: {
                Text("Отмена").font(Theme.sans(Theme.fontBase)).glassPill()
            }
            .buttonStyle(PressableStyle())
        }
    }

    /// Нижняя панель поиска (вместо поля ввода): список результатов, «N из M», стрелки.
    private var searchBottomBar: some View {
        let total = searchResults.count
        return HStack(spacing: 12) {
            Button {
                searchFocused = false
                searchListOpen.toggle()
            } label: {
                Image(systemName: searchListOpen ? "text.bubble" : "list.bullet")
                    .font(.system(size: 18, weight: .regular))
                    .foregroundStyle(Theme.text)
                    .frame(width: 42, height: 42)
                    .liquidGlass(Circle(), interactive: true)
            }
            .buttonStyle(PressableStyle())
            .disabled(total == 0)
            .opacity(total == 0 ? 0.5 : 1)

            Spacer(minLength: 0)
            Text(searchQuery.trimmed.isEmpty ? "Введите текст для поиска"
                 : total == 0 ? "Ничего не найдено" : "\(min(searchIndex, total - 1) + 1) из \(total)")
                .font(Theme.sans(Theme.fontBase))
                .foregroundStyle(Theme.text)
                .monospacedDigit()
            Spacer(minLength: 0)

            HStack(spacing: 0) {
                searchArrow("chevron.up", enabled: searchIndex + 1 < total) { stepSearch(1) }
                searchArrow("chevron.down", enabled: searchIndex > 0 && total > 0) { stepSearch(-1) }
            }
            .liquidGlass(Capsule(), interactive: true)
        }
    }

    private func searchArrow(_ symbol: String, enabled: Bool, _ action: @escaping () -> Void) -> some View {
        Button { Haptics.light(); action() } label: {
            Image(systemName: symbol)
                .font(.system(size: 17, weight: .semibold))
                .foregroundStyle(Theme.text.opacity(enabled ? 1 : 0.3))
                .frame(width: 44, height: 42)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
    }

    /// Результаты поиска списком (как в Телеграме): от новых к старым.
    private var searchListOverlay: some View {
        let ids = Array(searchResults.reversed())
        return ScrollView(showsIndicators: false) {
            LazyVStack(spacing: 0) {
                ForEach(Array(ids.enumerated()), id: \.element) { idx, id in
                    if let m = messages.first(where: { $0.id == id }) {
                        Button {
                            searchIndex = idx
                            searchListOpen = false
                            jump(to: id)
                        } label: { searchRow(m) }
                        .buttonStyle(.plain)
                        if idx < ids.count - 1 {
                            Rectangle().fill(Color.white.opacity(0.08)).frame(height: 1).padding(.leading, 58)
                        }
                    }
                }
            }
            .padding(.top, Theme.topBarY + 42 + 16)
            .padding(.bottom, 120)
        }
        .background(Theme.background.ignoresSafeArea())
        .ignoresSafeArea(.container, edges: [.top, .bottom])
        .overlay(alignment: .top) { searchTopBar.padding(.horizontal, Theme.screenX).padding(.top, Theme.topBarY) }
        .overlay(alignment: .bottom) {
            searchBottomBar.padding(.horizontal, Theme.screenX).padding(.bottom, keyboardShown ? 12 : 45)
        }
    }

    private func searchRow(_ m: ChatMessage) -> some View {
        HStack(alignment: .top, spacing: 12) {
            AvatarCircle(url: fileURL(m.avatarUrl), size: 36, name: m.mine ? (session.user?.name ?? "Вы") : m.author)
            VStack(alignment: .leading, spacing: 2) {
                HStack {
                    Text(m.mine ? "Вы" : (m.author ?? "Участник"))
                        .font(Theme.sans(Theme.fontBase, .semibold)).foregroundStyle(Theme.text).lineLimit(1)
                    Spacer(minLength: 8)
                    Text(searchDate(m)).font(Theme.sans(Theme.fontSm)).foregroundStyle(secondary)
                }
                Text(highlighted(m.text ?? m.file?.name ?? replyPreview(m)))
                    .font(Theme.sans(Theme.fontBase)).foregroundStyle(secondary).lineLimit(2)
            }
        }
        .padding(.horizontal, Theme.screenX)
        .padding(.vertical, 10)
        .contentShape(Rectangle())
    }

    private func searchDate(_ m: ChatMessage) -> String {
        guard let iso = m.createdAt, let date = ISO8601DateFormatter().date(from: iso) else { return m.time ?? "" }
        if Calendar.current.isDateInToday(date) { return m.time ?? "" }
        let f = DateFormatter()
        f.locale = Locale(identifier: "ru_RU")
        f.dateFormat = Calendar.current.isDate(date, equalTo: Date(), toGranularity: .year) ? "d MMM" : "dd.MM.yy"
        return f.string(from: date)
    }

    private func highlighted(_ text: String) -> AttributedString {
        var result = AttributedString(text)
        let q = searchQuery.trimmed
        guard !q.isEmpty, let r = text.range(of: q, options: [.caseInsensitive, .diacriticInsensitive]),
              let ar = Range(r, in: result) else { return result }
        result[ar].foregroundColor = Theme.text
        result[ar].font = Theme.sans(Theme.fontBase, .semibold)
        return result
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
                                Group {
                                    if m.isSystem {
                                        systemRow(m)
                                    } else {
                                        messageRow(m, prev: idx > 0 ? list[idx - 1] : nil,
                                                   next: idx + 1 < list.count ? list[idx + 1] : nil)
                                    }
                                }
                                .padding(.top, idx == 0 ? 0 : (sameAuthor(list[idx - 1], m) ? 4 : 12))
                                .id(m.id)
                            }
                        }
                        Color.clear.frame(height: 12).id("bottom")
                    }
                    .padding(.horizontal, Theme.screenX)
                    .padding(.top, contentTop)
                    .frame(minHeight: geo.size.height, alignment: .bottom)
                    // Новое сообщение проявляется на месте, остальные сдвигаются вверх
                    // (прототип: Чат 495:16591 → 495:17748 → 495:19021, 800 мс, ease-in-out-back).
                    .animation(Motion.standard, value: visibleMessages.count)
                }
                .scrollDismissesKeyboard(.interactively)
                .onChange(of: visibleMessages.count) { _ in
                    guard !searchMode else { return }
                    withAnimation(.easeOut(duration: 0.25)) { proxy.scrollTo("bottom", anchor: .bottom) }
                }
                .onChange(of: loaded) { _ in
                    DispatchQueue.main.async { proxy.scrollTo("bottom", anchor: .bottom) }
                }
                .onChange(of: keyboardShown) { _ in
                    guard !searchMode else { return }
                    withAnimation(.easeOut(duration: 0.25)) { proxy.scrollTo("bottom", anchor: .bottom) }
                }
                .onChange(of: scrollRequest) { req in
                    withAnimation(.easeInOut(duration: 0.35)) { proxy.scrollTo(req.id, anchor: .center) }
                }
            }
        }
    }

    private func sameAuthor(_ a: ChatMessage, _ b: ChatMessage) -> Bool {
        if a.isSystem || b.isSystem { return false }
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
                    .overlay {
                        // Подсветка сообщения после перехода к нему (закреп, поиск).
                        if flashId == m.id {
                            RoundedRectangle(cornerRadius: 24, style: .continuous)
                                .fill(Color.white.opacity(0.18))
                                .allowsHitTesting(false)
                                .transition(.opacity)
                        }
                    }
                    .opacity(menuFor == m.id ? 0 : 1)
                    .onTapGesture { onTap(m) }
                    .onLongPressGesture(minimumDuration: 0.3) { onLongPress(m) }
                if !m.mine { Spacer(minLength: 0) }
            }
        }
        .contentShape(Rectangle())
        .onTapGesture { if selectMode { toggleSelect(m.id) } }
        .transition(.opacity)
    }

    /// Служебное сообщение по центру: «Имя зашел в чат» / «Имя вышел из чата»;
    /// тап по имени открывает профиль пользователя.
    private func systemRow(_ m: ChatMessage) -> some View {
        HStack(spacing: 0) {
            Spacer(minLength: 0)
            HStack(spacing: 4) {
                Button { openUser(m) } label: {
                    Text(m.author ?? "Пользователь").font(Theme.sans(Theme.fontSm, .semibold)).lineLimit(1)
                }
                .buttonStyle(PressableStyle())
                .disabled(m.userId == nil)
                Text(m.eventText).font(Theme.sans(Theme.fontSm))
            }
            .foregroundStyle(Theme.text)
            .padding(.horizontal, 12)
            .frame(height: 24)
            .liquidGlass(Capsule())
            Spacer(minLength: 0)
        }
        .transition(.opacity)
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
                      onOpenImage: { if !selectMode { openPhoto(m.image?.url) } },
                      onToggleReaction: m.pending || selectMode ? nil : { emoji in react(m.id, emoji) },
                      highlight: searchMode ? searchQuery : nil)
    }

    /// Тап: в режиме выбора — отметить. Меню — только по долгому нажатию.
    private func onTap(_ m: ChatMessage) {
        if selectMode { toggleSelect(m.id) }
    }

    /// Долгое нажатие — реакции и меню действий (как в макете «зажатие сообщения»).
    private func onLongPress(_ m: ChatMessage) {
        if selectMode { toggleSelect(m.id); return }
        guard !m.pending, !m.isSystem else { return }
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
            if searchMode {
                searchBottomBar
            } else if selectMode {
                HStack {
                    GlassCircleButton(icon: AppIcons.trashX, iconSize: 24, iconColor: Theme.error) {
                        guard !selected.isEmpty else { return }
                        selectDeleteMenu.toggle()
                    }
                    .overlay(alignment: .bottomLeading) {
                        if selectDeleteMenu {
                            selectDeleteMenuPanel
                                .fixedSize()
                                .offset(y: -54)
                                .transition(.scale(scale: 0.95, anchor: .bottomLeading).combined(with: .opacity))
                        }
                    }
                    .zIndex(1)
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
                        .transition(.opacity)
                }
            }
            .zIndex(1)

            VStack(alignment: .leading, spacing: 8) {
                // Упоминание (Figma «Чат при Упоминании»): список над текстом в том же стекле.
                if !mentionMatches.isEmpty {
                    mentionsList
                        .padding(.trailing, 12)
                        .transition(.opacity)
                }
                if !pendingPhotos.isEmpty { pendingPhotosStrip }
                if let reply = replyTo { composerQuote(reply) }
                if draftIsAuthorMessage {
                    HStack(spacing: 6) {
                        Icon(AppIcons.lockOpen, size: 12, color: secondary)
                        Text("Сообщение увидит только автор отчета").font(Theme.sans(Theme.fontSm)).foregroundStyle(secondary)
                    }
                    .padding(.top, 10)
                    .transition(.opacity)
                }
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
                        .transition(.opacity)
                    }
                }
            }
            .padding(.leading, 16)
            .padding(.trailing, showSend ? 0 : 12)
            .padding(.top, replyTo != nil ? 12 : 0)
            .frame(minHeight: 42)
            .liquidGlass(RoundedRectangle(cornerRadius: mentionMatches.isEmpty ? Theme.radiusInput : Theme.radiusCard,
                                          style: .continuous))
            .animation(.easeOut(duration: 0.2), value: mentionMatches.map(\.id))
            .animation(.easeOut(duration: 0.2), value: draftIsAuthorMessage)

            if !showSend {
                micButton.transition(.opacity)
            }
        }
        // Кнопка отправки появляется в поле ввода, кнопка голосового растворяется
        // (прототип: Чат 495:14425 → 495:15022, SMART_ANIMATE 800 мс).
        .animation(Motion.standard, value: showSend)
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
            PhotosPicker(selection: $photoItems, maxSelectionCount: 10, matching: .images) {
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

    /// Строки 40 pt: аватар 24, имя SemiBold 14 и тег 14 белый 60 %, между строками линия 10 %.
    private var mentionsList: some View {
        VStack(alignment: .leading, spacing: 0) {
            ForEach(Array(mentionMatches.enumerated()), id: \.element.id) { i, u in
                Button { applyMention(u) } label: {
                    HStack(spacing: 12) {
                        AvatarCircle(url: fileURL(u.avatarUrl), size: 24, name: u.name ?? u.username)
                        Text(u.name ?? "").font(Theme.sans(Theme.fontBase, .semibold)).foregroundStyle(Theme.text).lineLimit(1)
                        if let un = u.username {
                            Text(un).font(Theme.sans(Theme.fontBase)).foregroundStyle(secondary).lineLimit(1)
                        }
                        Spacer(minLength: 0)
                    }
                    .frame(height: 40)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                Rectangle().fill(Color.white.opacity(0.1)).frame(height: 1).padding(.leading, -16).padding(.trailing, -12)
            }
        }
        .padding(.top, 4)
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
                        // Уже поставленная реакция подсвечена: повторный тап её снимает.
                        let mine = Set(m.reactions.filter(\.mine).map(\.emoji))
                        ForEach(reactions, id: \.self) { r in
                            Button { react(m.id, r, closing: true) } label: {
                                Text(r).font(.system(size: 20)).frame(width: 30, height: 30)
                                    .background(mine.contains(r) ? Color.white.opacity(0.22) : .clear, in: Circle())
                            }
                            .buttonStyle(PressableStyle())
                        }
                    }
                    .padding(.horizontal, 9)
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
                if canPin && !m.isPrivate {
                    menuButton(m.pinned ? "Открепить" : "Закрепить", AppIcons.pin) {
                        if m.pinned {
                            closeMenu()
                            unpinConfirm = pins.first(where: { $0.id == m.id })
                        } else {
                            togglePin(m.id)
                        }
                    }
                }
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
            if canDeleteAll(m) {
                Button { delete(m.id, forAll: true) } label: {
                    Text("Удалить у всех").font(Theme.sans(Theme.fontBase)).foregroundStyle(Theme.error)
                        .frame(maxWidth: .infinity, alignment: .leading).contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                Rectangle().fill(Color.white.opacity(0.1)).frame(height: 1)
            }
            Button { delete(m.id, forAll: false) } label: {
                Text("Удалить у меня").font(Theme.sans(Theme.fontBase)).foregroundStyle(Theme.error)
                    .frame(maxWidth: .infinity, alignment: .leading).contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        }
        .padding(16)
        .frame(width: 193, alignment: .leading)
        .liquidGlass(RoundedRectangle(cornerRadius: Theme.radiusCard, style: .continuous))
    }

    /// «Чат Выбор сообщения Удаление» (Figma 337:29172): попап над корзиной.
    private var selectDeleteMenuPanel: some View {
        let chosen = messages.filter { selected.contains($0.id) }
        return VStack(alignment: .leading, spacing: 12) {
            if !chosen.isEmpty && chosen.allSatisfy(canDeleteAll) {
                Button { deleteSelected(forAll: true) } label: {
                    Text("Удалить у всех").font(Theme.sans(Theme.fontBase)).foregroundStyle(Theme.error)
                        .frame(maxWidth: .infinity, alignment: .leading).contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                Rectangle().fill(Color.white.opacity(0.1)).frame(height: 1)
            }
            Button { deleteSelected(forAll: false) } label: {
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
    private func exitSelect() { selectMode = false; selected = []; selectDeleteMenu = false }
    private func toggleSelect(_ id: Int) {
        Haptics.light()
        if selected.contains(id) { selected.remove(id) } else { selected.insert(id) }
    }

    /// Удалить у всех: своё, а у админа и автора отчёта — любое (решает сервер).
    private func canDeleteAll(_ m: ChatMessage) -> Bool {
        if m.pending { return false }
        return m.canDelete ?? (m.mine || session.isAdmin)
    }

    private func deleteSelected(forAll: Bool) {
        let ids = selected.filter { id in messages.contains { $0.id == id && !$0.pending } }
        exitSelect()
        withAnimation(Motion.standard) { messages.removeAll { ids.contains($0.id) } }
        pins.removeAll { ids.contains($0.id) }
        clampPinIndex()
        Task { @MainActor in
            for id in ids { try? await Api.deleteMessage(chatId, id, forAll: forAll) }
            if forAll { await reloadMessages() }
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
        if !pendingPhotos.isEmpty { sendPendingPhotos(); return }
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

    /// Реакция: поставить или снять свою (сервер переключает). Сразу меняем локально.
    private func react(_ id: Int, _ emoji: String, closing: Bool = false) {
        if closing { closeMenu() }
        Haptics.light()
        if let i = messages.firstIndex(where: { $0.id == id }) {
            var list = messages[i].reactions
            if let j = list.firstIndex(where: { $0.emoji == emoji }) {
                let r = list[j]
                if r.mine {
                    if r.count <= 1 { list.remove(at: j) } else { list[j] = .init(emoji: emoji, count: r.count - 1, mine: false) }
                } else {
                    list[j] = .init(emoji: emoji, count: r.count + 1, mine: true)
                }
            } else {
                list.append(.init(emoji: emoji, count: 1, mine: true))
            }
            withAnimation(.easeOut(duration: 0.2)) { messages[i].reactions = list }
        }
        Task { @MainActor in
            if let dto = try? await Api.reactMessage(chatId, id, emoji: emoji) { replaceMessage(dto) }
        }
    }

    private func togglePin(_ id: Int) {
        closeMenu()
        Task { @MainActor in
            do {
                let list = try await Api.pinMessage(chatId, id)
                pins = list
                pinIndex = max(list.count - 1, 0)
                await reloadMessages()
            } catch {
                showToast((error as? ApiError)?.message ?? "Не удалось закрепить")
            }
        }
    }

    private func unpin(_ id: Int) {
        Task { @MainActor in
            do {
                pins = try await Api.pinMessage(chatId, id)
                clampPinIndex()
                await reloadMessages()
            } catch {
                showToast((error as? ApiError)?.message ?? "Не удалось открепить")
            }
        }
    }

    private func clampPinIndex() {
        pinIndex = pins.isEmpty ? 0 : min(max(pinIndex, 0), pins.count - 1)
    }

    /// Тап по закрепу: к сообщению, а плашка переключается на предыдущий закреп (как в Телеграме).
    private func openPin(_ p: MessageQuoteDto) {
        Haptics.light()
        jump(to: p.id)
        if pins.count > 1 {
            pinIndex = pinIndex > 0 ? pinIndex - 1 : pins.count - 1
        }
    }

    /// Прокрутить к сообщению и коротко подсветить его.
    private func jump(to id: Int, flash: Bool = true) {
        guard messages.contains(where: { $0.id == id }) else {
            showToast("Сообщение не найдено")
            return
        }
        scrollRequest = ScrollRequest(id: id, token: scrollRequest.token + 1)
        guard flash else { return }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) {
            withAnimation(.easeOut(duration: 0.2)) { flashId = id }
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.9) {
                if flashId == id { withAnimation(.easeOut(duration: 0.4)) { flashId = nil } }
            }
        }
    }

    private func delete(_ id: Int, forAll: Bool) {
        closeMenu()
        withAnimation(Motion.standard) { messages.removeAll { $0.id == id } }
        pins.removeAll { $0.id == id }
        clampPinIndex()
        Task { @MainActor in
            do {
                try await Api.deleteMessage(chatId, id, forAll: forAll)
            } catch {
                showToast((error as? ApiError)?.message ?? "Не удалось удалить")
                await reloadMessages()
            }
        }
    }

    // MARK: Поиск

    private func openSearch() {
        closeMenu()
        exitSelect()
        inputFocused = false
        searchQuery = ""
        searchIndex = 0
        searchListOpen = false
        searchMode = true
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) { searchFocused = true }
    }

    private func closeSearch() {
        searchFocused = false
        searchListOpen = false
        searchMode = false
        searchQuery = ""
        flashId = nil
    }

    /// +1 — к более старому совпадению, −1 — к более новому.
    private func stepSearch(_ delta: Int) {
        let total = searchResults.count
        guard total > 0 else { return }
        searchIndex = min(max(searchIndex + delta, 0), total - 1)
        searchFocused = false
        if let id = currentSearchId { jump(to: id) }
    }

    // MARK: Упоминания

    /// Кандидаты для «@» — с сервера (все, у кого есть доступ к чату), с небольшой задержкой.
    private func fetchMentions(_ query: String?) {
        mentionTask?.cancel()
        guard let query else { return }
        mentionTask = Task { @MainActor in
            try? await Task.sleep(nanoseconds: 180_000_000)
            guard !Task.isCancelled else { return }
            if let list = try? await Api.chatMembers(chatId, query: query), !Task.isCancelled {
                mentionCandidates = list
            }
        }
    }

    /// Открыть фото: клавиатура прячется, чтобы не закрывать просмотр.
    private func openPhoto(_ url: URL?) {
        inputFocused = false
        searchFocused = false
        UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
        photoViewerURL = url
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
        guard let raw = u.username, !raw.isEmpty else { return }
        let handle = raw.hasPrefix("@") ? raw : "@" + raw
        Haptics.light()
        draft = draft.replacingOccurrences(of: #"@[^\s@]*$"#, with: handle + " ", options: .regularExpression)
        mentionCandidates = []
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

    /// Фото из системного пикера сразу перекодируются в JPEG: HEIC с iPhone
    /// браузер и часть клиентов не показывают (белые квадраты).
    private func loadPickedPhotos() {
        let items = photoItems
        guard !items.isEmpty else { return }
        photoItems = []
        attachOpen = false
        Task { @MainActor in
            for item in items {
                guard let raw = try? await item.loadTransferable(type: Data.self),
                      let original = UIImage(data: raw) else {
                    showAttachError("Не удалось открыть одно из фото")
                    continue
                }
                // Длинная сторона до 2048 px и JPEG 0.8: файл в разы меньше оригинала (3–4 МБ),
                // поэтому чат грузится быстрее.
                let image = Self.downscaled(original, maxSide: 2048)
                guard let jpeg = image.jpegData(compressionQuality: 0.8) else {
                    showAttachError("Не удалось открыть одно из фото")
                    continue
                }
                pendingPhotos.append(PendingPhoto(image: image, jpeg: jpeg))
            }
        }
    }

    /// Уменьшает фото так, чтобы длинная сторона не превышала maxSide (ориентация сохраняется).
    private static func downscaled(_ image: UIImage, maxSide: CGFloat) -> UIImage {
        let size = image.size
        let longest = max(size.width, size.height)
        guard longest > maxSide else { return image }
        let scale = maxSide / longest
        let target = CGSize(width: (size.width * scale).rounded(), height: (size.height * scale).rounded())
        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 1
        return UIGraphicsImageRenderer(size: target, format: format).image { _ in
            image.draw(in: CGRect(origin: .zero, size: target))
        }
    }

    /// Отправка выбранных фото одним сообщением-альбомом. Сообщение сразу появляется
    /// в ленте с индикатором загрузки, затем заменяется ответом сервера.
    private func sendPendingPhotos() {
        let photos = pendingPhotos
        pendingPhotos = []
        let caption = draft.trimmed
        draft = ""
        let reply = replyTo?.id
        replyTo = nil
        guard photos.allSatisfy({ $0.jpeg.count <= Self.maxAttachmentBytes }) else {
            showAttachError("Фото больше 100 МБ, выберите фото поменьше")
            pendingPhotos = photos
            draft = caption
            return
        }
        let localId = -Int(Date().timeIntervalSince1970 * 1000)
        messages.append(ChatMessage(pendingId: localId, localImages: photos.map(\.image),
                                    text: caption.isEmpty ? nil : caption))
        Task { @MainActor in
            do {
                let dto = try await Api.sendMessage(chatId, body: caption.isEmpty ? nil : caption, replyTo: reply,
                                                    attachments: photos.map { (data: $0.jpeg, filename: "photo.jpg", mime: "image/jpeg") },
                                                    kind: "image")
                if let index = messages.firstIndex(where: { $0.id == localId }) {
                    if let dto { messages[index] = ChatMessage(dto) } else { messages.remove(at: index) }
                }
            } catch {
                messages.removeAll { $0.id == localId }
                if !caption.isEmpty { draft = caption }
                showAttachError("Не удалось отправить фото")
            }
        }
    }

    /// Превью выбранных фото над полем ввода; крестик убирает фото до отправки.
    private var pendingPhotosStrip: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(pendingPhotos) { photo in
                    Image(uiImage: photo.image)
                        .resizable()
                        .scaledToFill()
                        .frame(width: 64, height: 64)
                        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                        .overlay(alignment: .topTrailing) {
                            Button { pendingPhotos.removeAll { $0.id == photo.id } } label: {
                                Icon(AppIcons.close, size: 10, color: .white)
                                    .frame(width: 22, height: 22)
                                    .background(Color.black.opacity(0.6), in: Circle())
                            }
                            .buttonStyle(.plain)
                            .offset(x: 6, y: -6)
                        }
                }
            }
            .padding(.horizontal, 6)
            .padding(.top, 6)
        }
        .frame(height: 76)
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
        pinIndex = max(pins.count - 1, 0)
        loaded = true
        #if DEBUG
        applyDebugState()
        #endif
    }

    private func reloadMessages() async {
        do {
            let resp = try await Api.fetchMessages(chatId)
            serverChat = resp.chat
            pins = resp.pins
            clampPinIndex()
            // Фото, которые ещё отправляются, не пропадают при обновлении ленты.
            let pending = messages.filter(\.pending)
            messages = resp.data.map(ChatMessage.init) + pending
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

/// Запрос прокрутки к сообщению; token меняется, чтобы повторный переход к тому же сообщению срабатывал.
struct ScrollRequest: Equatable {
    var id: Int = 0
    var token: Int = 0
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


/// Фото, выбранное в пикере и ещё не отправленное.
struct PendingPhoto: Identifiable {
    let id = UUID()
    let image: UIImage
    let jpeg: Data
}
