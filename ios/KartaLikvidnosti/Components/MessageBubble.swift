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

    struct Reply { let author: String; let text: String }
    struct ImageAtt { let url: URL?; let width: Int?; let height: Int? }
    struct FileAtt { let url: URL?; let name: String; let ext: String; let size: String }
    struct VoiceAtt { let url: URL?; let duration: String }
    struct Reaction: Identifiable { var id: String { emoji }; let emoji: String; let count: Int; let mine: Bool }

    init(_ dto: MessageDto) {
        id = dto.id
        userId = dto.userId
        mine = dto.mine
        author = dto.author
        role = dto.role
        avatarUrl = dto.avatarUrl
        text = dto.text
        time = dto.time
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
    }

    var isSystem: Bool { event != nil }

    /// Текст служебного сообщения без имени («зашел в чат» / «вышел из чата»).
    var eventText: String { event == "leave" ? "вышел из чата" : "зашел в чат" }

    /// Локальное сообщение с фото, которое ещё отправляется.
    init(pendingId: Int, localImages: [UIImage], text: String?) {
        id = pendingId
        mine = true
        pending = true
        self.localImages = localImages
        self.text = text
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
                AsyncImage(url: message.images[index].url) { phase in
                    switch phase {
                    case .success(let img): img.resizable().scaledToFill()
                    case .failure(let error):
                        let _ = print("[chat image] load failed:", message.images[index].url?.absoluteString ?? "nil", error)
                        Color(hex: "#d9d9d9")
                    default: ZStack { Color.white.opacity(0.1); ProgressView().tint(.white) }
                    }
                }
            }
        }
        .frame(width: width, height: height)
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        .overlay {
            if message.pending {
                ZStack { Color.black.opacity(0.35); ProgressView().tint(.white) }
            }
        }
        .contentShape(Rectangle())
        .onTapGesture { if !message.pending { onOpenImage?() } }
    }

    private func imageView(_ image: ChatMessage.ImageAtt) -> some View {
        AsyncImage(url: image.url) { phase in
            switch phase {
            case .success(let img): img.resizable().aspectRatio(contentMode: .fill)
            case .failure(let error):
                // Диагностика белых фото: ошибка видна в консоли Xcode.
                let _ = print("[chat image] load failed:", image.url?.absoluteString ?? "nil", error)
                Color(hex: "#d9d9d9")
            default: ZStack { Color.white.opacity(0.1); ProgressView().tint(.white) }
            }
        }
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

    private var footer: some View {
        HStack(alignment: .bottom, spacing: 4) {
            if !message.reactions.isEmpty {
                HStack(spacing: 4) {
                    ForEach(message.reactions) { r in
                        // Своя реакция выделена; тап по ней снимает её, по чужой — ставит такую же.
                        Button { onToggleReaction?(r.emoji) } label: {
                            HStack(spacing: 4) {
                                Text(r.emoji).font(.system(size: 13))
                                Text("\(r.count)").font(Theme.sans(Theme.fontSm)).foregroundStyle(Theme.text)
                            }
                            .padding(.vertical, 4).padding(.horizontal, 8)
                            .frame(height: 24)
                            .background(r.mine ? Theme.green.opacity(0.55) : Color(hex: "#404040"), in: Capsule())
                            .overlay(Capsule().stroke(r.mine ? Color.white.opacity(0.6) : .clear, lineWidth: 1))
                        }
                        .buttonStyle(PressableStyle())
                        .disabled(onToggleReaction == nil)
                    }
                }
            }
            Spacer(minLength: 8)
            if message.pinned {
                Icon(AppIcons.pinSmall, size: 10, color: secondary)
            }
            if let time = message.time {
                Text(time).font(Theme.sans(10)).foregroundStyle(secondary)
            }
            if message.mine {
                ReadMark(read: message.read).padding(.bottom, 2.5)
            }
        }
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
            if mine { content.liquidGlass(shape, fill: ActiveFill()) }
            else { content.liquidGlass(shape) }
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
