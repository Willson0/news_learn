import SwiftUI

/// Модель сообщения в чате (из MessageDto бэкенда, 1:1 с вебом и Android).
struct ChatMessage: Identifiable {
    let id: Int
    var userId: Int? = nil
    var mine: Bool = false
    var author: String? = nil
    var role: String? = nil
    var avatarUrl: String? = nil
    var text: String? = nil
    var time: String? = nil
    var createdAt: String? = nil
    var read: Bool = false
    var pinned: Bool = false
    var reply: Reply? = nil
    var image: ImageAtt? = nil
    /// Все фото сообщения (альбом); для одиночного фото совпадает с `image`.
    var images: [ImageAtt] = []
    /// Фото, которые ещё уходят на сервер: показываются сразу с индикатором загрузки.
    var localImages: [UIImage] = []
    var pending: Bool = false
    /// Не ушло на сервер: красный «!», по нажатию — «Повторить / Удалить».
    var failed: Bool = false
    /// Сколько фото уже ушло на сервер (0…1) — кольцо на превью.
    var uploadProgress: Double? = nil
    /// Ключ отправки: повтор не создаёт дубль, догруженное с сервера сообщение заменяет часики.
    var clientId: String? = nil
    var file: FileAtt? = nil
    var voice: VoiceAtt? = nil
    var reactions: [Reaction] = []
    /// Служебное: "join" — зашел в чат, "leave" — вышел.
    var event: String? = nil
    /// Личное сообщение автору отчёта (/текст/) и ответы на него.
    var isPrivate: Bool = false
    var privateLabel: String? = nil
    /// Сервер разрешает удалить у всех (nil — старый сервер: решает клиент).
    var canDelete: Bool? = nil
    /// Ключ строки в ленте, пока сообщение рождено на этом устройстве: временный пузырь
    /// и пришедшее ему на смену сообщение сервера — одна и та же строка (без мигания).
    var localKey: String? = nil

    /// Постоянный ключ строки ленты: не меняется, когда часики сменяются ответом сервера.
    var uiKey: String { localKey ?? "s\(id)" }

    struct Reply { let author: String; let text: String }
    struct ImageAtt { let url: URL?; let width: Int?; let height: Int? }
    struct FileAtt { let url: URL?; let name: String; let ext: String; let size: String }
    struct VoiceAtt { let url: URL?; let duration: String }
    struct Reaction: Identifiable {
        var id: String { emoji }
        let emoji: String; let count: Int; let mine: Bool
        /// Для сравнения наборов реакций.
        var key: String { "\(emoji)\(count)\(mine)" }
    }

    init(_ dto: MessageDto) {
        id = dto.id
        userId = dto.userId
        mine = dto.mine
        author = dto.author
        role = dto.role
        avatarUrl = dto.avatarUrl
        text = dto.text
        time = ChatDates.time(dto.createdAt, fallback: dto.time)
        createdAt = dto.createdAt
        read = dto.read
        pinned = dto.pinned
        if let r = dto.reply { reply = Reply(author: r.author ?? "", text: r.text ?? "") }
        if let i = dto.image { image = ImageAtt(url: fileURL(i.url), width: i.width, height: i.height) }
        images = dto.images.map { ImageAtt(url: fileURL($0.url), width: $0.width, height: $0.height) }
        if images.isEmpty, let first = image { images = [first] }
        if let f = dto.file {
            file = FileAtt(url: fileURL(f.url), name: f.name ?? "Файл", ext: f.ext ?? "", size: f.size ?? "")
        }
        if let v = dto.voice { voice = VoiceAtt(url: fileURL(v.url), duration: v.duration ?? "0:00") }
        reactions = dto.reactions.map { Reaction(emoji: $0.emoji, count: $0.count, mine: $0.mine) }
        event = dto.event
        isPrivate = dto.isPrivate
        privateLabel = dto.privateLabel
        canDelete = dto.canDelete
        clientId = dto.clientId
    }

    var isSystem: Bool { event != nil }

    /// Текст служебного сообщения без имени («зашел в чат» / «вышел из чата»).
    var eventText: String { event == "leave" ? "вышел из чата" : "зашел в чат" }

    /// Своё сообщение, которое ещё отправляется: сразу в окончательном виде — со временем
    /// отправки и размером фото, как в Telegram, чтобы ответ сервера ничего не сдвигал.
    init(pendingId: Int, localImages: [UIImage], text: String?) {
        id = pendingId
        mine = true
        pending = true
        localKey = "l\(pendingId)"
        self.localImages = localImages
        self.text = text
        let now = ChatDates.isoNow()
        createdAt = now
        time = ChatDates.time(now)
        if let first = localImages.first, first.size.width > 0, first.size.height > 0 {
            image = ImageAtt(url: nil, width: Int(first.size.width), height: Int(first.size.height))
        }
    }

    /// Сообщение сервера на месте прежней версии той же строки: ключ ленты прежний,
    /// а уже показанные локальные фото остаются (не перезагружаются и не мигают).
    func adopting(_ old: ChatMessage) -> ChatMessage {
        var m = self
        m.localKey = old.localKey
        if !old.localImages.isEmpty && !images.isEmpty { m.localImages = old.localImages }
        m.reactions = ChatView.keepingOrder(reactions, of: old.reactions)
        return m
    }

    /// Своя реакция: поставить или снять (сервер делает так же).
    func toggledReactions(_ emoji: String) -> [Reaction] {
        var list = reactions
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
        return list
    }
}

/// message/Сообщение из макета (Figma 337:27570, 337:27969, 337:34166):
/// стекло со скруглением 24, у последнего сообщения группы «хвостовой» угол 0
/// (у своих — нижний правый, у чужих — нижний левый); свои — заливка «active»,
/// чужие — «Second color» 20 %. Отступы 12, между блоками 4. Имя SemiBold 12 и
/// роль 12 белый 60 %, цитата ответа — плашка белый 20 % с линией слева,
/// текст 14, время 10 белый 60 % и отметка прочтения.
struct MessageBubble: View {
    let message: ChatMessage
    /// Последнее сообщение в серии одного автора — с «хвостом».
    var isTail: Bool = true
    /// Показывать имя и роль (первое чужое сообщение серии в групповом чате).
    var showHeader: Bool = true
    var isPlaying: Bool = false
    /// Прогресс проигрывания голосового, 0…1 — для заполнения волны.
    var voiceProgress: Double = 0
    var onPlayVoice: (() -> Void)? = nil
    var onOpenFile: (() -> Void)? = nil
    var onOpenImage: (() -> Void)? = nil
    /// Тап по реакции под сообщением: своя — снять, чужая — поставить такую же.
    var onToggleReaction: ((String) -> Void)? = nil
    /// Поиск по чату: совпадения в тексте подсвечиваются.
    var highlight: String? = nil
    /// Реакция, которую только что поставили: её плашка проявляется на месте.
    var popReaction: String? = nil

    static let maxWidth: CGFloat = 302
    private let secondary = Color.white.opacity(0.6)

    private var bars: [CGFloat] {
        let seed = Double(message.id == 0 ? 1 : message.id)
        return (0..<44).map { i in
            let v = abs(sin(seed * 7.3 + Double(i) * 1.7))
            return CGFloat(2 + Int((v * 11).rounded()))
        }
    }

    private var imageSize: CGSize {
        let w: CGFloat = Self.maxWidth - 24
        if let iw = message.image?.width, let ih = message.image?.height, iw > 0, ih > 0 {
            return CGSize(width: w, height: min(320, w * CGFloat(ih) / CGFloat(iw)))
        }
        return CGSize(width: w, height: 180)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            if showHeader, !message.mine, let author = message.author, message.voice == nil {
                header(author)
            }
            if message.isPrivate, let label = message.privateLabel {
                Text(label).font(Theme.sans(Theme.fontBase, .semibold)).foregroundStyle(Theme.text)
            }
            if let reply = message.reply { replyQuote(reply) }
            if !message.localImages.isEmpty || message.images.count > 1 {
                albumView
            } else if let image = message.image {
                imageView(image)
            }
            if let file = message.file { fileView(file) }
            if let voice = message.voice { voiceView(voice) }
            if let text = message.text, !text.isEmpty {
                if message.isPrivate {
                    // Сообщение автору (Figma «Чат виды Сообщение автору»): текст в кавычках
                    // на тёмной плашке внутри пузыря.
                    Text(styled("\u{201C}\(text)\u{201D}"))
                        .font(Theme.sans(Theme.fontMd))
                        .foregroundStyle(Theme.text)
                        .fixedSize(horizontal: false, vertical: true)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 6)
                        .background(Color.black.opacity(0.3), in: RoundedRectangle(cornerRadius: 8, style: .continuous))
                } else {
                    Text(styled(text))
                        .font(Theme.sans(Theme.fontBase))
                        .foregroundStyle(Theme.text)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            footer
        }
        .padding(.horizontal, 12)
        .padding(.top, 16)
        .padding(.bottom, 12)
        .frame(maxWidth: Self.maxWidth, alignment: .leading)
        .fixedSize(horizontal: true, vertical: false)
        .modifier(BubbleGlass(mine: message.mine, shape: bubbleShape))
        // Размер пузыря под реакцию меняется сразу, без анимации: системное контекстное
        // меню держит пузырь в своей обёртке, и анимация размера в ней сначала сжимала
        // содержимое, а потом рывком растягивала пузырь (видно на записи экрана).
        // Плавность даёт сама новая плашка — она проявляется на месте.
    }

    /// Текст с выделенными упоминаниями «@тег» и найденным в поиске фрагментом.
    private func styled(_ text: String) -> AttributedString {
        var result = AttributedString(text)
        let ns = text as NSString
        if let regex = try? NSRegularExpression(pattern: "@[\\p{L}\\p{N}_.]+") {
            for match in regex.matches(in: text, range: NSRange(location: 0, length: ns.length)) {
                if let r = Range(match.range, in: text), let ar = Range(r, in: result) {
                    result[ar].foregroundColor = Theme.green
                    result[ar].font = Theme.sans(Theme.fontBase, .semibold)
                }
            }
        }
        if let q = highlight?.trimmingCharacters(in: .whitespaces), !q.isEmpty {
            var searchRange = text.startIndex..<text.endIndex
            while let r = text.range(of: q, options: [.caseInsensitive, .diacriticInsensitive], range: searchRange) {
                if let ar = Range(r, in: result) {
                    result[ar].backgroundColor = Color(hex: "#d9a400").opacity(0.55)
                }
                searchRange = r.upperBound..<text.endIndex
            }
        }
        return result
    }

    private func header(_ author: String) -> some View {
        HStack(spacing: 12) {
            Text(author).font(Theme.sans(Theme.fontSm, .semibold)).foregroundStyle(Theme.text).lineLimit(1)
            Spacer(minLength: 0)
            if let role = message.role, !role.isEmpty {
                Text(role).font(Theme.sans(Theme.fontSm)).foregroundStyle(secondary).lineLimit(1)
            }
        }
    }

    /// Цитата ответа: плашка белый 20 % (скругление справа 8), линия 1 pt слева.
    private func replyQuote(_ reply: ChatMessage.Reply) -> some View {
        HStack(spacing: 4) {
            Rectangle().fill(Color.white).frame(width: 1)
            VStack(alignment: .leading, spacing: 0) {
                Text(reply.author).font(Theme.sans(Theme.fontSm, .semibold)).foregroundStyle(Theme.text).lineLimit(1)
                Text(reply.text).font(Theme.sans(Theme.fontSm)).foregroundStyle(secondary).lineLimit(1)
            }
            .padding(.trailing, 8)
            Spacer(minLength: 0)
        }
        .frame(height: 32)
        .background(Color.white.opacity(0.2),
                    in: UnevenRoundedRectangle(bottomTrailingRadius: 8, topTrailingRadius: 8, style: .continuous))
    }

    /// Альбом фото (2+ в одном сообщении, как в Телеграме): сетка 2 колонки.
    /// Пока фото уходят, поверх каждого плашка с индикатором.
    private var albumView: some View {
        let count = message.localImages.isEmpty ? message.images.count : message.localImages.count
        let full = Self.maxWidth - 24
        let tile = (full - 4) / 2
        let single = count == 1
        let rows = stride(from: 0, to: count, by: 2).map { start in Array(start..<min(start + 2, count)) }
        return VStack(spacing: 4) {
            ForEach(rows, id: \.self) { row in
                HStack(spacing: 4) {
                    ForEach(row, id: \.self) { index in
                        albumTile(index, width: single ? full : tile, height: single ? imageSize.height : tile)
                    }
                    if row.count == 1 && !single { Color.clear.frame(width: tile, height: tile) }
                }
            }
        }
    }

    @ViewBuilder private func albumTile(_ index: Int, width: CGFloat, height: CGFloat) -> some View {
        ZStack {
            if !message.localImages.isEmpty {
                Image(uiImage: message.localImages[index]).resizable().scaledToFill()
            } else {
                RemoteImage(url: message.images[index].url) { Color(hex: "#d9d9d9") }
            }
        }
        .frame(width: width, height: height)
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        .overlay {
            if message.pending {
                ZStack {
                    Color.black.opacity(0.35)
                    if let p = message.uploadProgress {
                        UploadRing(progress: p)
                    } else {
                        ProgressView().tint(.white)
                    }
                }
            } else if message.failed {
                Color.black.opacity(0.35)
            }
        }
        .contentShape(Rectangle())
        .onTapGesture { if !message.pending { onOpenImage?() } }
    }

    private func imageView(_ image: ChatMessage.ImageAtt) -> some View {
        // Фото из кэша (память и диск): при повторном открытии чата не грузится заново.
        RemoteImage(url: image.url) { Color(hex: "#d9d9d9") }
        .frame(width: imageSize.width, height: imageSize.height)
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .contentShape(Rectangle())
        .onTapGesture { onOpenImage?() }
    }

    private func fileView(_ file: ChatMessage.FileAtt) -> some View {
        Button { onOpenFile?() } label: {
            HStack(spacing: 12) {
                Text(file.ext.isEmpty ? "file" : file.ext.lowercased())
                    .font(Theme.sans(Theme.fontSm, .semibold))
                    .foregroundStyle(Color(hex: "#1a1b1d"))
                    .frame(width: 30, height: 36)
                    .background(Color.white, in: RoundedRectangle(cornerRadius: 6, style: .continuous))
                VStack(alignment: .leading, spacing: 2) {
                    Text(file.name).font(Theme.sans(Theme.fontSm, .semibold)).lineLimit(1)
                    Text(file.size).font(Theme.sans(Theme.fontSm)).foregroundStyle(secondary)
                }
                Spacer(minLength: 8)
                Icon(AppIcons.downloadCloud, size: 24, color: Theme.text)
            }
            .foregroundStyle(Theme.text)
            .padding(.vertical, 4)
            .frame(minWidth: 220)
        }
        .buttonStyle(.plain)
    }

    /// Голосовое: зелёный круг 42 с «play», имя/роль, волна 13 pt и длительность 8.
    private func voiceView(_ voice: ChatMessage.VoiceAtt) -> some View {
        HStack(alignment: .top, spacing: 10) {
            Button { onPlayVoice?() } label: {
                Group {
                    if isPlaying {
                        HStack(spacing: 4) {
                            Capsule().fill(Color.white).frame(width: 4, height: 16)
                            Capsule().fill(Color.white).frame(width: 4, height: 16)
                        }
                    } else {
                        Icon(AppIcons.play, size: 18, color: .white).offset(x: 2)
                    }
                }
                .frame(width: 42, height: 42)
                .background(Theme.green, in: Circle())
            }
            .buttonStyle(.plain)
            VStack(alignment: .leading, spacing: 4) {
                if showHeader, !message.mine, let author = message.author { header(author) }
                VStack(alignment: .leading, spacing: 4) {
                    HStack(alignment: .center, spacing: 1.5) {
                        ForEach(Array(bars.enumerated()), id: \.offset) { idx, h in
                            let frac = Double(idx) / Double(max(bars.count - 1, 1))
                            let played = isPlaying && frac <= voiceProgress
                            Capsule()
                                .fill(Color.white.opacity(played || !isPlaying ? 1 : 0.4))
                                .frame(width: 2, height: h)
                        }
                    }
                    .frame(height: 13)
                    .animation(.linear(duration: 0.08), value: voiceProgress)
                    Text(voice.duration).font(Theme.sans(8)).foregroundStyle(secondary)
                }
            }
            .padding(.top, message.mine ? 7 : 0)
        }
    }

    /// Реакции переносятся по строкам и никогда не сжимаются; время и отметка — в конце
    /// последней строки, если помещаются, иначе отдельной строкой справа (как в Телеграме).
    private var footer: some View {
        BubbleFooterLayout(spacing: 4, lineSpacing: 4) {
            ForEach(message.reactions) { r in
                ReactionChip(reaction: r, isNew: r.emoji == popReaction, onTap: onToggleReaction)
            }
            HStack(alignment: .bottom, spacing: 4) {
                if message.pinned {
                    Icon(AppIcons.pinSmall, size: 10, color: secondary)
                }
                if let time = message.time {
                    Text(time).font(Theme.sans(10)).foregroundStyle(secondary)
                }
                if message.mine {
                    // Отметка в слоте постоянного размера: часики → кружок сменяются
                    // растворением, время и ширина пузыря не сдвигаются (как в Telegram).
                    ZStack(alignment: .bottomLeading) {
                        if message.failed {
                            FailedMark().padding(.bottom, 0.5).transition(.opacity)
                        } else if message.pending {
                            SendingMark().padding(.bottom, 1.5).transition(.opacity)
                        } else {
                            ReadMark(read: message.read).padding(.bottom, 2.5).transition(.opacity)
                        }
                    }
                    .frame(width: 12, height: 12.5, alignment: .bottomLeading)
                    .animation(.easeOut(duration: 0.18), value: message.pending)
                    .animation(.easeOut(duration: 0.18), value: message.failed)
                }
            }
            .fixedSize()
        }
    }

    /// Ключ реакций для плавной анимации смены размера пузыря.
    private var reactionsKey: [String] {
        message.reactions.map(\.key)
    }

    private var bubbleShape: UnevenRoundedRectangle {
        let r: CGFloat = 24
        return UnevenRoundedRectangle(topLeadingRadius: r,
                                      bottomLeadingRadius: !message.mine && isTail ? 0 : r,
                                      bottomTrailingRadius: message.mine && isTail ? 0 : r,
                                      topTrailingRadius: r, style: .continuous)
    }

    private struct BubbleGlass: ViewModifier {
        let mine: Bool
        let shape: UnevenRoundedRectangle
        func body(content: Content) -> some View {
            // Стекло отдельным слоем: при появлении реакции пузырь растёт вместе с
            // содержимым одной пружиной (обычное системное стекло обрезало текст и
            // догоняло размер рывком).
            if mine { content.modifier(LiquidGlass(shape: shape, fill: ActiveFill(), detached: true)) }
            else { content.modifier(LiquidGlass(shape: shape, fill: Theme.glassFill, detached: true)) }
        }
    }
}

/// Плашка реакции под сообщением (вид прежний: эмодзи + число, своя — зелёная с обводкой).
/// Тап по своей снимает её, по чужой — ставит такую же. Число меняется прокруткой
/// цифр, а при смене числа или своей отметки плашка коротко «подпрыгивает».
struct ReactionChip: View {
    let reaction: ChatMessage.Reaction
    let onTap: ((String) -> Void)?
    @State private var bump = false
    /// Новая плашка в два шага: сначала под неё плавно растёт место (и пузырь вместе
    /// с ним), потом в готовом месте проявляется сама плашка — она никогда не торчит
    /// за краем пузыря. Место растёт покадрово (`GrowFrame`), а не неявной анимацией
    /// размера: её системная обёртка меню показывала сжатием и рывком.
    @State private var grow: CGFloat
    @State private var shown: Bool
    @State private var natural: CGSize = .zero

    init(reaction: ChatMessage.Reaction, isNew: Bool = false, onTap: ((String) -> Void)?) {
        self.reaction = reaction
        self.onTap = onTap
        _grow = State(initialValue: isNew ? 0 : 1)
        _shown = State(initialValue: !isNew)
    }

    private static let growDuration = 0.26

    var body: some View {
        Button { onTap?(reaction.emoji) } label: {
            HStack(spacing: 4) {
                Text(reaction.emoji).font(.system(size: 14))
                Text("\(reaction.count)").font(Theme.sans(Theme.fontSm)).foregroundStyle(Theme.text)
                    .contentTransition(.numericText())
            }
            .fixedSize()
            .padding(.horizontal, 8)
            .frame(minHeight: 26)
            .background(reaction.mine ? Theme.green.opacity(0.55) : Color(hex: "#404040"), in: Capsule())
            .overlay(Capsule().stroke(reaction.mine ? Color.white.opacity(0.6) : .clear, lineWidth: 1))
            .scaleEffect(bump ? 1.1 : 1)
        }
        .buttonStyle(PressableStyle())
        .disabled(onTap == nil)
        .scaleEffect(shown ? 1 : 0.4)
        .opacity(shown ? 1 : 0)
        .background(GeometryReader { g in
            Color.clear
                .onAppear { natural = g.size }
                .onChange(of: g.size) { natural = $0 }
        })
        .modifier(GrowFrame(progress: grow, natural: natural))
        .onAppear {
            guard !shown else { return }
            withAnimation(.easeInOut(duration: Self.growDuration)) { grow = 1 }
            DispatchQueue.main.asyncAfter(deadline: .now() + Self.growDuration) {
                withAnimation(Motion.reduced ? .easeOut(duration: 0.2) : .spring(response: 0.32, dampingFraction: 0.66)) { shown = true }
            }
        }
        .onChange(of: reaction.key) { _ in pop() }
    }

    /// Место под плашку: доля её размера, пересчитывается каждый кадр анимации
    /// (настоящая раскладка, пузырь и стекло растут вместе). При 1 — без ограничений.
    private struct GrowFrame: ViewModifier, Animatable {
        var progress: CGFloat
        let natural: CGSize
        var animatableData: CGFloat {
            get { progress }
            set { progress = newValue }
        }
        func body(content: Content) -> some View {
            let growing = progress < 0.999
            content.frame(width: growing ? natural.width * progress : nil,
                          height: growing ? natural.height * progress : nil,
                          alignment: .leading)
        }
    }

    private func pop() {
        guard !Motion.reduced, shown else { return }
        withAnimation(.spring(response: 0.18, dampingFraction: 0.6)) { bump = true }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.14) {
            withAnimation(.spring(response: 0.34, dampingFraction: 0.55)) { bump = false }
        }
    }
}

/// Отметка доставки (Figma «Сообщение/Union»): не прочитано — один кружок 8 pt;
/// прочитано — к нему добавляется второй (контур), как в макете.
struct ReadMark: View {
    let read: Bool
    var body: some View {
        ZStack(alignment: .leading) {
            if read {
                Circle().stroke(Color.white, lineWidth: 1).frame(width: 7, height: 7).offset(x: 3.5)
                    .transition(.scale(scale: 0.4).combined(with: .opacity))
            }
            Circle().fill(Color.white).frame(width: 8, height: 8)
        }
        .frame(width: 11, height: 8, alignment: .leading)
        .animation(.easeOut(duration: 0.25), value: read)
    }
}

/// Часики: сообщение отправляется.
struct SendingMark: View {
    var body: some View {
        ZStack {
            Circle().stroke(Color.white, lineWidth: 1)
            Path { p in
                p.move(to: CGPoint(x: 5, y: 5)); p.addLine(to: CGPoint(x: 5, y: 2.4))
                p.move(to: CGPoint(x: 5, y: 5)); p.addLine(to: CGPoint(x: 7, y: 5))
            }
            .stroke(Color.white, style: StrokeStyle(lineWidth: 1, lineCap: .round))
        }
        .frame(width: 10, height: 10)
        .accessibilityLabel("Отправляется")
    }
}

/// Красный «!»: сообщение не отправилось.
struct FailedMark: View {
    var body: some View {
        Text("!")
            .font(Theme.sans(9, .bold))
            .foregroundStyle(Color.white)
            .frame(width: 12, height: 12)
            .background(Theme.error, in: Circle())
            .accessibilityLabel("Не отправлено")
    }
}

/// Нижняя строка пузыря: последний элемент — время и отметка, остальные — реакции.
/// Реакции идут по строкам без сжатия; время прижато вправо на последней строке, если
/// помещается рядом с реакциями, иначе переносится на новую строку.
struct BubbleFooterLayout: Layout {
    var spacing: CGFloat = 4
    var lineSpacing: CGFloat = 4
    /// Минимальный зазор между последней реакцией и временем.
    var metaGap: CGFloat = 8

    private struct Line { var items: [(index: Int, size: CGSize)] = []; var width: CGFloat = 0; var height: CGFloat = 0 }

    private func lines(_ subviews: Subviews, maxWidth: CGFloat) -> (lines: [Line], metaOnLast: Bool, width: CGFloat) {
        guard let meta = subviews.last else { return ([], false, 0) }
        let metaSize = meta.sizeThatFits(.unspecified)
        var result: [Line] = [Line()]
        for i in 0..<(subviews.count - 1) {
            let size = subviews[i].sizeThatFits(.unspecified)
            var line = result[result.count - 1]
            let x = line.items.isEmpty ? 0 : line.width + spacing
            if x + size.width > maxWidth, !line.items.isEmpty {
                result.append(Line())
                line = Line()
            }
            let start = line.items.isEmpty ? 0 : line.width + spacing
            line.items.append((i, size))
            line.width = start + size.width
            line.height = max(line.height, size.height)
            result[result.count - 1] = line
        }
        let last = result[result.count - 1]
        let metaOnLast = last.items.isEmpty || last.width + metaGap + metaSize.width <= maxWidth
        var width = result.map(\.width).max() ?? 0
        if metaOnLast {
            let w = last.items.isEmpty ? metaSize.width : last.width + metaGap + metaSize.width
            width = max(width, w)
            result[result.count - 1].height = max(last.height, metaSize.height)
        } else {
            width = max(width, metaSize.width)
            var metaLine = Line(); metaLine.width = metaSize.width; metaLine.height = metaSize.height
            result.append(metaLine)
        }
        return (result, metaOnLast, width)
    }

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout Void) -> CGSize {
        let maxWidth = proposal.width ?? .infinity
        let info = lines(subviews, maxWidth: maxWidth)
        let height = info.lines.map(\.height).reduce(0, +) + lineSpacing * CGFloat(max(info.lines.count - 1, 0))
        // С заданной шириной занимаем её целиком — время прижимается к правому краю пузыря.
        let width = proposal.width.flatMap { $0.isFinite ? $0 : nil } ?? info.width
        return CGSize(width: width, height: height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout Void) {
        guard let meta = subviews.last else { return }
        let metaSize = meta.sizeThatFits(.unspecified)
        let info = lines(subviews, maxWidth: bounds.width)
        var y = bounds.minY
        for (n, line) in info.lines.enumerated() {
            var x = bounds.minX
            for item in line.items {
                // Реакции в строке выравниваются по низу строки.
                subviews[item.index].place(at: CGPoint(x: x, y: y + line.height - item.size.height),
                                           anchor: .topLeading, proposal: ProposedViewSize(item.size))
                x += item.size.width + spacing
            }
            if n == info.lines.count - 1 {
                meta.place(at: CGPoint(x: bounds.maxX - metaSize.width, y: y + line.height - metaSize.height),
                           anchor: .topLeading, proposal: ProposedViewSize(metaSize))
            }
            y += line.height + lineSpacing
        }
    }
}


/// Кольцо загрузки фото: сколько уже отправлено.
struct UploadRing: View {
    let progress: Double

    var body: some View {
        ZStack {
            Circle().stroke(Color.white.opacity(0.3), lineWidth: 3)
            Circle()
                .trim(from: 0, to: max(0.03, progress))
                .stroke(Color.white, style: StrokeStyle(lineWidth: 3, lineCap: .round))
                .rotationEffect(.degrees(-90))
                .animation(.linear(duration: 0.15), value: progress)
        }
        .frame(width: 32, height: 32)
        .accessibilityLabel("Загрузка \(Int(progress * 100)) процентов")
    }
}
