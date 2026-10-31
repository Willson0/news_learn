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
    /// Неотправленные сообщения: id временного пузыря → запрос, которым его можно отправить ещё раз.
    @State private var retryRequests: [Int: () async throws -> MessageDto?] = [:]
    @State private var failedMenuFor: Int? = nil
    /// Попап «Удалить у всех / у меня» над корзиной в режиме выбора.
    @State private var selectDeleteMenu = false
    /// Переход к сообщению (закреп, поиск): прокрутка и короткая подсветка.
    @State private var scrollRequest = ScrollRequest()
    /// Номер последнего нажатия реакции по сообщению (старый ответ сервера не перетирает новое).
    @State private var reactionSeq: [Int: Int] = [:]
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
    /// Лента внизу (видно последнее сообщение). Если пользователь читает старые,
    /// новые сообщения не утаскивают его вниз — появляется плашка «Новые сообщения».
    @State private var nearBottom = true
    @State private var newBelow = 0
    @State private var lastCount = 0
    @Environment(\.scenePhase) private var scenePhase
    @State private var keyboardShown = false
    @State private var toast: String? = nil
    @State private var shareText: String? = nil
    /// Сообщения, удалённые «у меня» на этом устройстве в старых версиях (теперь скрытие хранит сервер).
    @State private var hiddenIds: Set<Int> = []

    // Запись голосового.
    @State private var micPressed = false
    @State private var recordLocked = false
    /// Смещение пальца при записи. Отдельный объект, на который подписаны только
    /// элементы плашки записи, — весь экран чата не перерисовывается на каждый кадр жеста.
    @State private var recordDrag = RecordDragModel()

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

    /// «/текст/» уходит лично администраторам (в чате отчёта — и автору отчёта).
    private var draftIsAuthorMessage: Bool {
        guard serverChat?.authorMessages == true else { return false }
        let t = draft.trimmed
        return t.count >= 2 && t.hasPrefix("/")
    }

    /// Подсказка над полем ввода: кто увидит личное сообщение или ответ в личной ветке.
    private var privateHint: String? {
        if replyTo?.isPrivate == true { return "Ответ увидят только участники личной переписки" }
        guard draftIsAuthorMessage else { return nil }
        return serverChat?.type == "report"
            ? "Сообщение увидят только автор отчета и администраторы"
            : "Сообщение увидят только администраторы"
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
        .confirmationDialog("Сообщение не отправлено",
                            isPresented: Binding(get: { failedMenuFor != nil }, set: { if !$0 { failedMenuFor = nil } }),
                            titleVisibility: .visible, presenting: failedMenuFor) { id in
            Button("Повторить") { retryFailed(id) }
            Button("Удалить", role: .destructive) { discardFailed(id) }
            Button("Отмена", role: .cancel) {}
        } message: { _ in
            Text("Проверьте соединение и попробуйте ещё раз")
        }
        .alert("Открепить сообщение?", isPresented: Binding(get: { unpinConfirm != nil }, set: { if !$0 { unpinConfirm = nil } }),
               presenting: unpinConfirm) { p in
            Button("Открепить", role: .destructive) { unpin(p.id) }
            Button("Отмена", role: .cancel) {}
        } message: { p in
            Text(p.text ?? "")
        }
        // Долгое нажатие на сообщение при открытой клавиатуре: клавиатура прячется
        // сразу, ещё до подъёма пузыря системным меню. Иначе меню убирает её само,
        // лента съезжает вниз, а поднятое сообщение остаётся там, где было над
        // клавиатурой (и так же после «Выбрать»).
        .background(LongPressKeyboardDismiss(active: keyboardShown && !searchMode) { inputFocused = false })
        .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardWillShowNotification)) { _ in
            withAnimation(.easeOut(duration: 0.25)) { keyboardShown = true }
        }
        .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardWillHideNotification)) { _ in
            withAnimation(.easeOut(duration: 0.25)) { keyboardShown = false }
        }
        // В режиме выбора поля ввода нет — фокус ему не возвращаем (его восстанавливает
        // закрывшееся системное меню сообщения).
        .onChange(of: inputFocused) { focused in if focused && selectMode { inputFocused = false } }
        .onDisappear { inputFocused = false; searchFocused = false }
        .onAppear {
            hiddenIds = Self.loadHidden(chatId)
            if router.chatSearchRequest == chatId { router.chatSearchRequest = nil; openSearch() }
        }
        .task { await load() }
        // Пока чат на экране и приложение активно — тихо догружаем новые сообщения.
        .task(id: scenePhase) {
            guard scenePhase == .active else { return }
            if loaded { await pollNew() }
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 8_000_000_000)
                if Task.isCancelled { break }
                await pollNew()
            }
        }
        .slowNetworkHint(active: !loaded, bottom: 120)
    }

    // MARK: - Шапка

    private var topBar: some View {
        VStack(spacing: 24) {
            if searchMode {
                searchTopBar.keepsKeyboardOnTap()
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
                    Button { openInfo() } label: {
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
                    // Иконка чата открывает то же инфо, что и название.
                    Button { openInfo() } label: {
                        chatAvatar
                            // Почти прозрачная подложка: картинка сама нажатия не ловит.
                            .background(Circle().fill(Color.white.opacity(0.001)))
                            .contentShape(Circle())
                    }
                    .buttonStyle(PressableStyle())
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
                TextField("", text: $searchQuery, prompt: Text("Поиск").foregroundColor(Theme.textMuted))
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
                            let list = visibleMessages
                            // Ключ строки постоянный: ответ сервера не пересоздаёт пузырь.
                            ForEach(Array(list.enumerated()), id: \.element.uiKey) { idx, m in
                                // Разделитель по дням над первым сообщением каждого дня.
                                let day = ChatDates.day(m.createdAt)
                                let newDay = idx == 0 || ChatDates.day(list[idx - 1].createdAt) != day
                                VStack(spacing: 0) {
                                    if newDay {
                                        Text(ChatDates.dayLabel(day))
                                            .font(Theme.sans(Theme.fontSm))
                                            .foregroundStyle(Theme.text)
                                            .padding(.horizontal, 12)
                                            .frame(height: 24)
                                            .liquidGlass(Capsule())
                                            .padding(.top, idx == 0 ? 0 : 12)
                                            .padding(.bottom, 12)
                                            .accessibilityAddTraits(.isHeader)
                                    }
                                    Group {
                                        if m.isSystem {
                                            systemRow(m)
                                        } else {
                                            messageRow(m, prev: idx > 0 && !newDay ? list[idx - 1] : nil,
                                                       next: idx + 1 < list.count && ChatDates.day(list[idx + 1].createdAt) == day ? list[idx + 1] : nil)
                                        }
                                    }
                                    .padding(.top, idx == 0 || newDay ? 0 : (sameAuthor(list[idx - 1], m) ? 4 : 12))
                                }
                                .id(m.uiKey)
                            }
                        }
                        Color.clear.frame(height: 12).id("bottom")
                            .background(GeometryReader { g in
                                Color.clear.preference(key: ChatBottomKey.self, value: g.frame(in: .named("chatScroll")).maxY)
                            })
                    }
                    .padding(.horizontal, Theme.screenX)
                    .padding(.top, contentTop)
                    .frame(minHeight: geo.size.height, alignment: .bottom)
                    // Новое сообщение проявляется на месте, остальные сдвигаются вверх
                    // (прототип: Чат 495:16591 → 495:17748 → 495:19021) — быстрой пружиной,
                    // как в Telegram: у back-кривой первые ~200 мс почти нет движения.
                    .animation(Motion.chatInsert, value: visibleMessages.count)
                }
                .scrollDismissesKeyboard(.interactively)
                .coordinateSpace(name: "chatScroll")
                .onPreferenceChange(ChatBottomKey.self) { y in
                    let near = y <= geo.size.height + 120
                    if near != nearBottom { nearBottom = near }
                    if near && newBelow > 0 { newBelow = 0 }
                }
                .onChange(of: visibleMessages.count) { count in
                    let added = count - lastCount
                    lastCount = count
                    guard !searchMode else { return }
                    // Своё сообщение или лента и так внизу — едем к последнему. Читаете
                    // старые — позиция не меняется, снизу плашка «Новые сообщения».
                    if nearBottom || visibleMessages.last?.mine == true || added <= 0 {
                        // Та же пружина, что у появления строки: лента и пузырь едут вместе.
                        withAnimation(Motion.chatInsert) { proxy.scrollTo("bottom", anchor: .bottom) }
                    } else {
                        newBelow += added
                    }
                }
                .overlay(alignment: .bottom) {
                    if newBelow > 0 && !searchMode && !selectMode {
                        Button {
                            newBelow = 0
                            withAnimation(.easeOut(duration: 0.3)) { proxy.scrollTo("bottom", anchor: .bottom) }
                        } label: {
                            HStack(spacing: 6) {
                                Text(newBelow == 1 ? "Новое сообщение" : "Новые сообщения · \(newBelow)")
                                    .font(Theme.sans(Theme.fontBase))
                                Text("↓").font(Theme.sans(Theme.fontBase, .semibold))
                            }
                            .foregroundStyle(Theme.text)
                            .padding(.horizontal, 16)
                            .frame(height: 36)
                            .liquidGlass(Capsule())
                        }
                        .buttonStyle(PressableStyle())
                        .padding(.bottom, 8)
                        .transition(.opacity)
                    }
                }
                .animation(.easeOut(duration: 0.2), value: newBelow > 0)
                .onChange(of: loaded) { _ in
                    lastCount = visibleMessages.count
                    DispatchQueue.main.async { proxy.scrollTo("bottom", anchor: .bottom) }
                }
                .onChange(of: keyboardShown) { shown in
                    // Только при появлении клавиатуры: когда она убрана (зажатие, выбор),
                    // лента остаётся на месте, а не уезжает к последнему сообщению.
                    guard shown, !searchMode else { return }
                    withAnimation(.easeOut(duration: 0.25)) { proxy.scrollTo("bottom", anchor: .bottom) }
                }
                .onChange(of: scrollRequest) { req in
                    guard let key = messages.first(where: { $0.id == req.id })?.uiKey else { return }
                    withAnimation(.easeInOut(duration: 0.35)) { proxy.scrollTo(key, anchor: .center) }
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
                    .modifier(MessageContextMenu(enabled: !selectMode && !searchMode && !m.pending && !m.failed && !m.isSystem) {
                        systemMenuItems(m)
                    })
                if !m.mine { Spacer(minLength: 0) }
            }
        }
        .contentShape(Rectangle())
        .onTapGesture { if selectMode { toggleSelect(m.id) } }
        .transition(.asymmetric(insertion: .opacity.combined(with: .offset(y: 8)), removal: .opacity))
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
                      onOpenImage: { if m.failed { failedMenuFor = m.id } else if !selectMode { openPhoto(m.image?.url) } },
                      onToggleReaction: m.pending || selectMode ? nil : { emoji in react(m.id, emoji) },
                      highlight: searchMode ? searchQuery : nil)
    }

    /// Тап: в режиме выбора — отметить. Меню — только по долгому нажатию.
    private func onTap(_ m: ChatMessage) {
        if selectMode { toggleSelect(m.id) } else if m.failed { failedMenuFor = m.id }
    }

    /// Долгое нажатие — реакции и меню действий (как в макете «зажатие сообщения»).
    private func onLongPress(_ m: ChatMessage) {
        if selectMode { toggleSelect(m.id); return }
        if m.failed { failedMenuFor = m.id; return }
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
                    GlassCircleButton(icon: AppIcons.trashX, label: "Удалить выбранные", iconSize: 24, iconColor: Theme.error) {
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
                    GlassCircleButton(icon: AppIcons.forward, label: "Переслать", iconSize: 24) { forwardSelected() }
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
                            .transition(.asymmetric(
                                insertion: .opacity.combined(with: .scale(scale: 0.96, anchor: .trailing)),
                                removal: .opacity
                            ))
                    }
                }
            }
        }
        .padding(.horizontal, Theme.screenX)
        .padding(.top, 12)
        // Тап по полю, «+», «Отправить», упоминаниям не прячет клавиатуру;
        // тап по ленте — прячет (KeyboardDismiss), свайп ленты вниз — тоже.
        .keepsKeyboardOnTap()
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
                if let hint = privateHint {
                    HStack(spacing: 6) {
                        Icon(AppIcons.lockOpen, size: 12, color: secondary)
                        Text(hint).font(Theme.sans(Theme.fontSm)).foregroundStyle(secondary)
                    }
                    .padding(.top, 10)
                    .transition(.opacity)
                }
                HStack(spacing: 10) {
                    TextField("", text: $draft, prompt: Text("Напишите что-нибудь").foregroundColor(Theme.textMuted), axis: .vertical)
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
                    // Вибрация сразу при касании, до включения микрофона: когда запись
                    // уже идёт, аудиосессия может её заглушить.
                    Haptics.medium()
                    startVoice()
                }
                guard audio.recording, !recordLocked else { return }
                recordDrag.update(v.translation)
                if v.translation.width < -110 {
                    Haptics.warning()
                    cancelVoice()
                } else if v.translation.height < -90 {
                    Haptics.rigid()
                    withAnimation(.spring(response: 0.35, dampingFraction: 0.8)) { recordLocked = true }
                    recordDrag.reset()
                }
            }
            .onEnded { _ in
                micPressed = false
                recordDrag.reset()
                if audio.recording && !recordLocked { stopAndSendVoice() }
            }
    }

    /// Идёт запись (Figma 337:32380 / замок 337:32783): стеклянная плашка с
    /// красной точкой и временем, большая зелёная кнопка 75 с ореолом 96.
    private var recordingBar: some View {
        HStack(spacing: 0) {
            HStack(spacing: 4) {
                RecordingDot()
                    .padding(.trailing, 4)
                Text(recordClock).font(Theme.sans(Theme.fontBase)).foregroundStyle(Theme.text).monospacedDigit()
                Spacer(minLength: 12)
                if recordLocked {
                    Button { Haptics.warning(); cancelVoice() } label: {
                        Text("Отмена").font(Theme.sans(Theme.fontBase)).foregroundStyle(Theme.text)
                    }
                    .buttonStyle(.plain)
                    Spacer(minLength: 0)
                } else {
                    RecordDragReader(model: recordDrag) { drag in
                        HStack(spacing: 4) {
                            Icon(AppIcons.back, size: 14, color: Theme.text)
                            Text("Влево — отмена").font(Theme.sans(Theme.fontBase)).foregroundStyle(Theme.text)
                        }
                        .offset(x: min(0, drag.width / 2.2))
                        .opacity(1 + Double(min(0, drag.width)) / 130)
                    }
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
                    Button { Haptics.light(); audio.pauseRecording() } label: {
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
                    RecordDragReader(model: recordDrag) { drag in
                        VStack(spacing: 6) {
                            Icon(AppIcons.lockOpen, size: 20, color: Theme.text)
                            LockHintArrow()
                        }
                        .padding(.vertical, 12)
                        .frame(width: 48)
                        .liquidGlass(Capsule())
                        .offset(x: -13, y: -118 + max(-36, min(0, drag.height / 2.5)))
                    }
                    .transition(.scale(scale: 0.6, anchor: .bottom).combined(with: .opacity))
                }
            }
            .transition(.opacity)
        }
    }

    private var bigRecordButton: some View {
        ZStack {
            // Ореол «дышит» в такт голосу (как в Телеграме), плавно сглаживая громкость.
            RecordHalo(audio: audio)
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
        .transition(.scale(scale: 0.4).combined(with: .opacity))
    }

    /// Запись на паузе (Figma 337:33184): корзина, волна с плашкой «▶ 1:30»,
    /// зелёная «отправить»; над ней — микрофон для продолжения записи.
    private var pausedRecordingBar: some View {
        HStack(spacing: 12) {
            GlassCircleButton(icon: AppIcons.trashX, label: "Удалить запись", iconSize: 24, iconColor: Theme.error) { Haptics.warning(); cancelVoice() }
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
            GlassCircleButton(icon: AppIcons.mic, label: "Продолжить запись", iconSize: 24) { Haptics.medium(); audio.resumeRecording() }
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

    /// Системное меню по долгому нажатию (как в Сообщениях и Телеграме): пузырь
    /// приподнимается, фон размывается, снизу — меню действий; реакции — строкой
    /// эмодзи в меню (iOS 17+) или подменю «Реакция» на iOS 16.
    @ViewBuilder private func systemMenuItems(_ m: ChatMessage) -> some View {
        let mineReactions = Set(m.reactions.filter(\.mine).map(\.emoji))
        if #available(iOS 17.0, *) {
            ControlGroup {
                ForEach(reactions, id: \.self) { r in
                    Button { react(m.id, r, afterSystemMenu: true) } label: { Text(r) }
                }
            }
            .controlGroupStyle(.palette)
            // Выбрал реакцию — меню закрывается, реакция ставится (палитра по умолчанию остаётся открытой).
            .menuActionDismissBehavior(.enabled)
        } else {
            Menu {
                ForEach(reactions, id: \.self) { r in
                    Button { react(m.id, r, afterSystemMenu: true) } label: {
                        Text(mineReactions.contains(r) ? "\(r) ✓" : r)
                    }
                }
            } label: {
                Label("Реакция", systemImage: "face.smiling")
            }
        }
        if let mine = reactions.first(where: { mineReactions.contains($0) }) {
            Button { react(m.id, mine, afterSystemMenu: true) } label: {
                Label("Убрать реакцию \(mine)", systemImage: "xmark.circle")
            }
        }
        Button {
            replyTo = m
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) { inputFocused = true }
        } label: { Label("Ответить", systemImage: "arrowshape.turn.up.left") }
        if let text = m.text, !text.isEmpty {
            Button { copyText(m); showToast("Скопировано") } label: { Label("Скопировать", systemImage: "doc.on.doc") }
        }
        if canPin && !m.isPrivate {
            Button {
                if m.pinned {
                    unpinConfirm = pins.first(where: { $0.id == m.id })
                } else {
                    togglePin(m.id)
                }
            } label: {
                Label(m.pinned ? "Открепить" : "Закрепить", systemImage: m.pinned ? "pin.slash" : "pin")
            }
        }
        Button { copyLink(m); showToast("Ссылка скопирована") } label: { Label("Копировать ссылку", systemImage: "link") }
        Button {
            selected = [m.id]
            selectMode = true
            inputFocused = false
        } label: { Label("Выбрать", systemImage: "checkmark.circle") }
        if !session.isAdmin && !m.mine {
            Button { complain(m) } label: { Label("Пожаловаться", systemImage: "exclamationmark.bubble") }
        }
        if canDeleteAll(m) {
            Menu {
                Button(role: .destructive) { delete(m.id, forAll: true) } label: {
                    Label("Удалить у всех", systemImage: "trash")
                }
                Button(role: .destructive) { delete(m.id, forAll: false) } label: {
                    Label("Удалить у меня", systemImage: "trash")
                }
            } label: {
                Label("Удалить", systemImage: "trash")
            }
        } else {
            Button(role: .destructive) { delete(m.id, forAll: false) } label: {
                Label("Удалить у меня", systemImage: "trash")
            }
        }
    }

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
            RemoteImage(url: photoViewerURL, contentMode: .fit, showsLoading: false) {
                Icon(AppIcons.picture, size: 48, color: Color.white.opacity(0.3))
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            HStack {
                GlassBackButton { photoViewerURL = nil }
                Spacer()
                GlassCircleButton(icon: AppIcons.downloadCloud, label: "Скачать", iconSize: 24) { openURL(photoViewerURL) }
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
    private func exitSelect() {
        selectMode = false; selected = []; selectDeleteMenu = false
        // Поле ввода возвращается без фокуса: системное меню «Выбрать» при закрытии
        // восстанавливает прежний фокус, и без этого клавиатура всплывала на выходе.
        inputFocused = false
    }

    /// Инфо чата: клавиатура прячется до перехода и не всплывает при возврате.
    private func openInfo() {
        inputFocused = false
        searchFocused = false
        router.push(.chatInfo(id: chatId))
    }
    private func toggleSelect(_ id: Int) {
        // Временные пузыри (ещё не на сервере) выбрать нельзя.
        guard id > 0 else { return }
        Haptics.light()
        if selected.contains(id) { selected.remove(id) } else { selected.insert(id) }
    }

    /// Удалить у всех: своё, а у админа и автора отчёта — любое (решает сервер).
    private func canDeleteAll(_ m: ChatMessage) -> Bool {
        if m.pending || m.failed { return false }
        return m.canDelete ?? (m.mine || session.isAdmin)
    }

    private func deleteSelected(forAll: Bool) {
        let ids = selected.filter { id in messages.contains { $0.id == id && !$0.pending && !$0.failed } }
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

    /// Отправка с часиками: пузырь сразу в ленте, после ответа сервера — настоящий, при ошибке — «!».
    private func runSend(_ localId: Int, _ request: @escaping () async throws -> MessageDto?) {
        retryRequests[localId] = request
        Task { @MainActor in
            do {
                let dto = try await request()
                retryRequests[localId] = nil
                ChatOutbox.remove(localId, chat: chatId)
                guard let index = messages.firstIndex(where: { $0.id == localId }) else {
                    if let dto, !messages.contains(where: { $0.id == dto.id }) { messages.append(ChatMessage(dto)) }
                    return
                }
                if let dto, !messages.contains(where: { $0.id == dto.id }) {
                    // Та же строка ленты: меняются только часики на кружок, без перерисовки.
                    messages[index] = ChatMessage(dto).adopting(messages[index])
                } else {
                    messages.remove(at: index)
                }
            } catch {
                if let index = messages.firstIndex(where: { $0.id == localId }) {
                    messages[index].pending = false
                    messages[index].failed = true
                    messages[index].uploadProgress = nil
                }
                let message = (error as? ApiError)?.message ?? ""
                showToast(!message.isEmpty && message.count < 120 ? message : "Сообщение не отправлено")
            }
        }
    }

    private func sendOptimistic(_ temp: ChatMessage, outbox: ChatOutbox.Entry? = nil,
                                _ request: @escaping () async throws -> MessageDto?) {
        // Текст и фото — в очередь на диске: не ушло — не потеряется при выходе из чата.
        if let outbox { ChatOutbox.add(outbox, chat: chatId) }
        var m = temp
        m.mine = true
        m.author = session.user?.name
        m.pending = true
        m.failed = false
        messages.append(m)
        runSend(m.id, request)
    }

    private func retryFailed(_ id: Int) {
        failedMenuFor = nil
        guard let request = retryRequests[id], let index = messages.firstIndex(where: { $0.id == id }) else { return }
        messages[index].failed = false
        messages[index].pending = true
        messages[index].uploadProgress = messages[index].localImages.isEmpty ? nil : 0
        runSend(id, request)
    }

    private func discardFailed(_ id: Int) {
        failedMenuFor = nil
        retryRequests[id] = nil
        ChatOutbox.remove(id, chat: chatId)
        messages.removeAll { $0.id == id }
    }

    private static func localMessageId() -> Int { -Int(Date().timeIntervalSince1970 * 1000) - Int.random(in: 0..<1000) }

    private func send() {
        if !pendingPhotos.isEmpty { sendPendingPhotos(); return }
        let body = draft.trimmed
        guard !body.isEmpty else { return }
        draft = ""
        let reply = replyTo
        replyTo = nil
        let chat = chatId
        let clientId = UUID().uuidString
        var temp = ChatMessage(pendingId: Self.localMessageId(), localImages: [], text: body)
        temp.clientId = clientId
        if let reply { temp.reply = ChatMessage.Reply(author: reply.mine ? "Вы" : (reply.author ?? ""), text: reply.text ?? "") }
        temp.isPrivate = reply?.isPrivate == true
        let entry = ChatOutbox.Entry(localId: temp.id, clientId: clientId, text: body, replyTo: reply?.id,
                                     replyAuthor: temp.reply?.author, replyText: temp.reply?.text,
                                     isPrivate: temp.isPrivate, photos: [])
        sendOptimistic(temp, outbox: entry) { try await Api.sendMessage(chat, body: body, replyTo: reply?.id, clientId: clientId) }
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
        // Файл сразу в ленте с часиками; не ушёл — «!» и «Повторить» (тем же ключом, без дубля).
        let chat = chatId
        let clientId = UUID().uuidString
        var temp = ChatMessage(pendingId: Self.localMessageId(), localImages: [], text: caption.isEmpty ? nil : caption)
        temp.clientId = clientId
        temp.file = ChatMessage.FileAtt(url: nil, name: filename, ext: (filename as NSString).pathExtension,
                                        size: ByteCountFormatter.string(fromByteCount: Int64(data.count), countStyle: .file))
        sendOptimistic(temp) {
            try await Api.sendMessage(chat, body: caption.isEmpty ? nil : caption, replyTo: reply,
                                      attachment: (data, filename, mime), kind: kind, duration: duration, clientId: clientId)
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
                if audio.startRecording() { Haptics.rigid() }
            }
        }
    }

    private func cancelVoice() {
        audio.cancelRecording()
        recordLocked = false
        recordDrag.reset()
    }

    private func stopAndSendVoice() {
        recordLocked = false
        if let result = audio.stopRecording() {
            Haptics.soft()
            guard !result.data.isEmpty else { return }
            // Голосовое тоже сразу в ленте с часиками; не ушло — «!» и «Повторить».
            let reply = replyTo
            replyTo = nil
            let chat = chatId
            let data = result.data
            let duration = result.duration
            let clientId = UUID().uuidString
            var temp = ChatMessage(pendingId: Self.localMessageId(), localImages: [], text: nil)
            temp.clientId = clientId
            temp.voice = ChatMessage.VoiceAtt(url: nil, duration: String(format: "%d:%02d", duration / 60, duration % 60))
            if let reply { temp.reply = ChatMessage.Reply(author: reply.mine ? "Вы" : (reply.author ?? ""), text: reply.text ?? "") }
            temp.isPrivate = reply?.isPrivate == true
            sendOptimistic(temp) {
                try await Api.sendMessage(chat, body: nil, replyTo: reply?.id,
                                          attachment: (data, "voice.m4a", "audio/m4a"), kind: "voice", duration: duration,
                                          clientId: clientId)
            }
        }
    }

    /// Реакция: поставить или снять свою (сервер переключает). Сразу меняем локально,
    /// как в Telegram; не удалось — нажатие откатывается и показывается тост.
    /// Пружина смены реакций — та же, что у размера пузыря (MessageBubble).
    private static let reactionSpring = Animation.spring(response: 0.38, dampingFraction: 0.86)

    /// - Parameter afterSystemMenu: реакция выбрана в системном меню. Пузырь меняется,
    ///   когда меню почти закрылось: иначе система возвращает старый снимок пузыря,
    ///   а он рывком подменяется новым.
    private func react(_ id: Int, _ emoji: String, closing: Bool = false, afterSystemMenu: Bool = false) {
        if closing { closeMenu() }
        Haptics.light()
        let seq = (reactionSeq[id] ?? 0) + 1
        reactionSeq[id] = seq
        let delay: Double = afterSystemMenu ? Self.systemMenuDismiss : 0
        if delay > 0 {
            DispatchQueue.main.asyncAfter(deadline: .now() + delay) { applyReaction(id, emoji) }
        } else {
            applyReaction(id, emoji)
        }
        Task { @MainActor in
            do {
                let dto = try await Api.reactMessage(chatId, id, emoji: emoji)
                // Ответ на старое нажатие не перетирает новое; совпадает — ничего не трогаем.
                guard let dto, reactionSeq[id] == seq else { return }
                if delay > 0 { try? await Task.sleep(nanoseconds: UInt64((delay + 0.1) * 1_000_000_000)) }
                if let i = messages.firstIndex(where: { $0.id == id }) {
                    let fresh = ChatMessage(dto).reactions
                    if fresh.map(\.key) != messages[i].reactions.map(\.key) {
                        withAnimation(Self.reactionSpring) { messages[i].reactions = fresh }
                    }
                }
            } catch {
                if error is CancellationError { return }
                if delay > 0 { try? await Task.sleep(nanoseconds: UInt64((delay + 0.1) * 1_000_000_000)) }
                applyReaction(id, emoji)
                showToast("Не удалось поставить реакцию")
            }
        }
    }

    /// Сколько закрывается системное контекстное меню.
    private static let systemMenuDismiss: Double = 0.35

    private func applyReaction(_ id: Int, _ emoji: String) {
        guard let i = messages.firstIndex(where: { $0.id == id }) else { return }
        let list = messages[i].toggledReactions(emoji)
        withAnimation(Self.reactionSpring) { messages[i].reactions = list }
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
        // Альбом сразу в ленте: на превью кольцо загрузки. Не ушёл — остаётся с «!»
        // (и на диске: не пропадёт при выходе из чата), «Повторить» шлёт его один раз.
        let localId = Self.localMessageId()
        let clientId = UUID().uuidString
        let jpegs = photos.map(\.jpeg)
        var temp = ChatMessage(pendingId: localId, localImages: photos.map(\.image), text: caption.isEmpty ? nil : caption)
        temp.clientId = clientId
        temp.uploadProgress = 0
        let entry = ChatOutbox.Entry(localId: localId, clientId: clientId, text: caption.isEmpty ? nil : caption,
                                     replyTo: reply, replyAuthor: nil, replyText: nil, isPrivate: false,
                                     photos: ChatOutbox.storePhotos(jpegs, localId: localId))
        sendOptimistic(temp, outbox: entry,
                       photoRequest(localId: localId, clientId: clientId, caption: caption, reply: reply, jpegs: jpegs))
    }

    /// Запрос отправки альбома с прогрессом на превью.
    private func photoRequest(localId: Int, clientId: String, caption: String, reply: Int?, jpegs: [Data]) -> () async throws -> MessageDto? {
        let chat = chatId
        let progress: @Sendable (Double) -> Void = { p in
            Task { @MainActor in setUploadProgress(localId, p) }
        }
        return {
            try await Api.sendMessage(chat, body: caption.isEmpty ? nil : caption, replyTo: reply,
                                      attachments: jpegs.map { (data: $0, filename: "photo.jpg", mime: "image/jpeg") },
                                      kind: "image", clientId: clientId, progress: progress)
        }
    }

    private func setUploadProgress(_ localId: Int, _ p: Double) {
        guard let index = messages.firstIndex(where: { $0.id == localId }), messages[index].pending else { return }
        messages[index].uploadProgress = p
    }

    /// Неотправленное с прошлого раза (вышли из чата или закрыли приложение) —
    /// снова в ленте со статусом «не отправлено» и «Повторить».
    private func restoreOutbox() {
        let serverKeys = Set(messages.compactMap { $0.id > 0 ? $0.clientId : nil })
        for e in ChatOutbox.entries(chatId) where !messages.contains(where: { $0.id == e.localId }) {
            // Сообщение на самом деле дошло (потерялся только ответ) — дубль не показываем.
            if serverKeys.contains(e.clientId) { ChatOutbox.remove(e.localId, chat: chatId); continue }
            let jpegs = e.photos.compactMap(ChatOutbox.photoData)
            if !e.photos.isEmpty && jpegs.count != e.photos.count { ChatOutbox.remove(e.localId, chat: chatId); continue }
            var m = ChatMessage(pendingId: e.localId, localImages: jpegs.compactMap { UIImage(data: $0) }, text: e.text)
            m.pending = false
            m.failed = true
            m.clientId = e.clientId
            m.author = session.user?.name
            if let author = e.replyAuthor { m.reply = ChatMessage.Reply(author: author, text: e.replyText ?? "") }
            m.isPrivate = e.isPrivate
            messages.append(m)
            if jpegs.isEmpty {
                let chat = chatId
                retryRequests[e.localId] = { try await Api.sendMessage(chat, body: e.text, replyTo: e.replyTo, clientId: e.clientId) }
            } else {
                retryRequests[e.localId] = photoRequest(localId: e.localId, clientId: e.clientId, caption: e.text ?? "",
                                                        reply: e.replyTo, jpegs: jpegs)
            }
        }
    }

    /// Новые сообщения с сервера, пока чат открыт: добавляются снизу, свои
    /// (ушедшие с этого или другого устройства) заменяют «часики».
    private func pollNew() async {
        guard loaded, !selectMode, let lastId = messages.last(where: { $0.id > 0 })?.id else { return }
        guard let resp = try? await Api.fetchMessages(chatId, after: lastId) else { return }
        serverChat = resp.chat ?? serverChat
        if resp.pins.map(\.id) != pins.map(\.id) { pins = resp.pins; clampPinIndex() }
        let known = Set(messages.map(\.id))
        let fresh = resp.data.filter { $0.id > lastId && !known.contains($0.id) }.map(ChatMessage.init)
        guard !fresh.isEmpty else { return }
        var list = messages
        // Своё сообщение, пришедшее раньше ответа на отправку, занимает строку своих
        // часиков (тот же ключ ленты) — без исчезновения и повторного появления.
        let ordered = fresh.map { m -> ChatMessage in
            guard let cid = m.clientId, let i = list.firstIndex(where: { $0.id < 0 && $0.clientId == cid }) else { return m }
            let old = list.remove(at: i)
            retryRequests[old.id] = nil
            ChatOutbox.remove(old.id, chat: chatId)
            return m.adopting(old)
        }
        let local = list.filter { $0.id < 0 }
        messages = list.filter { $0.id >= 0 } + ordered + local
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
        restoreOutbox()
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
            // Строки ленты сохраняют ключи: сообщение, уже стоящее на экране, обновляется
            // на месте. Отправляемые и неотправленные не пропадают; то, что сервер уже
            // принял (потерялся только ответ), сменяется серверным без дубля.
            let byId = Dictionary(messages.map { ($0.id, $0) }, uniquingKeysWith: { a, _ in a })
            let outgoing = messages.filter { $0.pending || $0.failed }
            let outgoingByCid = Dictionary(outgoing.compactMap { m in m.clientId.map { ($0, m) } }, uniquingKeysWith: { a, _ in a })
            let server = resp.data.map { dto -> ChatMessage in
                let m = ChatMessage(dto)
                guard let old = byId[m.id] ?? m.clientId.flatMap({ outgoingByCid[$0] }) else { return m }
                return m.adopting(old)
            }
            let serverCids = Set(server.compactMap(\.clientId))
            let pending = outgoing.filter { m in
                guard let cid = m.clientId, serverCids.contains(cid) else { return true }
                retryRequests[m.id] = nil
                ChatOutbox.remove(m.id, chat: chatId)
                return false
            }
            messages = server + pending
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

/// Нижняя граница ленты в координатах экрана чата — чтобы понять, внизу ли пользователь.
private struct ChatBottomKey: PreferenceKey {
    static var defaultValue: CGFloat = 0
    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) { value = nextValue() }
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

/// Системное контекстное меню сообщения: при долгом нажатии пузырь приподнимается
/// по своей форме (скругление 24), как в Сообщениях iPhone. В режиме выбора и
/// поиска меню выключено.
private struct MessageContextMenu<Items: View>: ViewModifier {
    let enabled: Bool
    @ViewBuilder let items: () -> Items

    func body(content: Content) -> some View {
        if enabled {
            content
                .contentShape(.contextMenuPreview, RoundedRectangle(cornerRadius: 24, style: .continuous))
                .contextMenu { items() }
        } else {
            content
        }
    }
}

/// Ловит долгое нажатие в ленте (поверх жестов SwiftUI, ничего не перехватывая)
/// и сообщает о нём через 0.2 с — раньше, чем системное меню поднимет пузырь.
/// Поле ввода (UITextView) и места вне прокрутки не считаются.
private struct LongPressKeyboardDismiss: UIViewRepresentable {
    let active: Bool
    let onPress: () -> Void

    func makeCoordinator() -> Coordinator { Coordinator() }

    func makeUIView(context: Context) -> HostView {
        let v = HostView()
        v.isUserInteractionEnabled = false
        v.coordinator = context.coordinator
        return v
    }

    func updateUIView(_ v: HostView, context: Context) {
        context.coordinator.active = active
        context.coordinator.onPress = onPress
    }

    static func dismantleUIView(_ v: HostView, coordinator: Coordinator) {
        coordinator.detach()
    }

    final class HostView: UIView {
        weak var coordinator: Coordinator?
        override func didMoveToWindow() {
            super.didMoveToWindow()
            coordinator?.attach(to: window)
        }
    }

    final class Coordinator: NSObject, UIGestureRecognizerDelegate {
        var active = false
        var onPress: () -> Void = {}
        private lazy var recognizer: UILongPressGestureRecognizer = {
            let r = UILongPressGestureRecognizer(target: self, action: #selector(handle(_:)))
            r.minimumPressDuration = 0.2
            r.cancelsTouchesInView = false
            r.delaysTouchesBegan = false
            r.delaysTouchesEnded = false
            r.delegate = self
            return r
        }()

        func attach(to window: UIWindow?) {
            recognizer.view?.removeGestureRecognizer(recognizer)
            window?.addGestureRecognizer(recognizer)
        }

        func detach() { recognizer.view?.removeGestureRecognizer(recognizer) }

        @objc private func handle(_ r: UILongPressGestureRecognizer) {
            guard r.state == .began, active, let window = r.view else { return }
            var v = window.hitTest(r.location(in: window), with: nil)
            var inScroll = false
            while let cur = v {
                if cur is UITextView || cur is UITextField { return }
                if cur is UIScrollView { inScroll = true }
                v = cur.superview
            }
            if inScroll { onPress() }
        }

        func gestureRecognizer(_ g: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer) -> Bool { true }
        func gestureRecognizerShouldBegin(_ g: UIGestureRecognizer) -> Bool { active }
    }
}

// MARK: - Запись голосового: плавные элементы

/// Смещение пальца при записи голосового. Публикует только при заметном изменении,
/// чтобы не перерисовывать плашку на каждый пиксель.
@MainActor
final class RecordDragModel: ObservableObject {
    @Published private(set) var drag: CGSize = .zero
    /// Палец прошёл половину пути до отмены или до замка — один лёгкий щелчок.
    private var hintedCancel = false
    private var hintedLock = false

    func update(_ t: CGSize) {
        if abs(t.width - drag.width) + abs(t.height - drag.height) >= 0.5 { drag = t }
        if t.width < -55, !hintedCancel { hintedCancel = true; Haptics.tick() }
        if t.width > -40 { hintedCancel = false }
        if t.height < -45, !hintedLock { hintedLock = true; Haptics.tick() }
        if t.height > -30 { hintedLock = false }
    }

    func reset() {
        hintedCancel = false
        hintedLock = false
        if drag != .zero {
            withAnimation(.spring(response: 0.3, dampingFraction: 0.85)) { drag = .zero }
        }
    }
}

/// Перерисовывает только своё содержимое при движении пальца.
private struct RecordDragReader<Content: View>: View {
    @ObservedObject var model: RecordDragModel
    @ViewBuilder let content: (CGSize) -> Content
    var body: some View { content(model.drag) }
}

/// Красная точка записи мягко пульсирует.
private struct RecordingDot: View {
    @State private var dim = false
    var body: some View {
        Circle().fill(Theme.error).frame(width: 8, height: 8)
            .opacity(dim ? 0.25 : 1)
            .animation(.easeInOut(duration: 0.8).repeatForever(autoreverses: true), value: dim)
            .onAppear { dim = true }
    }
}

/// Стрелка подсказки «вверх — замок» мягко покачивается вверх.
private struct LockHintArrow: View {
    @State private var up = false
    var body: some View {
        Icon(AppIcons.back, size: 16, color: Theme.text).rotationEffect(.degrees(90))
            .offset(y: up ? -3 : 1)
            .animation(.easeInOut(duration: 0.9).repeatForever(autoreverses: true), value: up)
            .onAppear { up = true }
    }
}

/// Ореол большой кнопки записи: масштаб следует за громкостью голоса со сглаживанием.
private struct RecordHalo: View {
    @ObservedObject var audio: ChatAudio
    /// Сглаженная громкость между кадрами (класс — изменение не перерисовывает вид).
    @State private var smoother = LevelSmoother()

    var body: some View {
        TimelineView(.animation(minimumInterval: 1.0 / 30.0)) { context in
            let level = audio.recordLevel()
            let t = context.date.timeIntervalSinceReferenceDate
            // Спокойное «дыхание» даже в тишине + отклик на голос.
            let breath = 0.03 * sin(t * 2.4)
            ActiveFill()
                .clipShape(Circle())
                .opacity(0.55)
                .frame(width: 96, height: 96)
                .scaleEffect(1 + breath + 0.28 * smoother.next(level))
        }
    }
}

/// Экспоненциальное сглаживание громкости, чтобы ореол не дёргался.
private final class LevelSmoother {
    private var value: CGFloat = 0
    func next(_ level: CGFloat) -> CGFloat {
        value += (level - value) * 0.25
        return value
    }
}
