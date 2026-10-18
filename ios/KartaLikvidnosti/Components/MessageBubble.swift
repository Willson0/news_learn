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
    var read: Bool = false
    var pinned: Bool = false
    var reply: Reply? = nil
    var image: ImageAtt? = nil
    var file: FileAtt? = nil
    var voice: VoiceAtt? = nil
    var reactions: [Reaction] = []

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
        read = dto.read
        pinned = dto.pinned
        if let r = dto.reply { reply = Reply(author: r.author ?? "", text: r.text ?? "") }
        if let i = dto.image { image = ImageAtt(url: fileURL(i.url), width: i.width, height: i.height) }
        if let f = dto.file {
            file = FileAtt(url: fileURL(f.url), name: f.name ?? "Файл", ext: f.ext ?? "", size: f.size ?? "")
        }
        if let v = dto.voice { voice = VoiceAtt(url: fileURL(v.url), duration: v.duration ?? "0:00") }
        reactions = dto.reactions.map { Reaction(emoji: $0.emoji, count: $0.count, mine: $0.mine) }
    }
}

struct MessageBubble: View {
    let message: ChatMessage
    var isPlaying: Bool = false
    /// Прогресс проигрывания голосового, 0…1 — для заполнения волны.
    var voiceProgress: Double = 0
    var onPlayVoice: (() -> Void)? = nil
    var onOpenFile: (() -> Void)? = nil

    private var bars: [CGFloat] {
        let seed = Double(message.id == 0 ? 1 : message.id)
        return (0..<26).map { i in
            let v = abs(sin(seed * 7.3 + Double(i) * 1.7))
            return CGFloat(4 + Int((v * 14).rounded()))
        }
    }

    private var imageSize: CGSize {
        let maxW: CGFloat = 240
        if let w = message.image?.width, let h = message.image?.height, w > 0, h > 0 {
            let ratio = CGFloat(h) / CGFloat(w)
            return CGSize(width: maxW, height: min(320, maxW * ratio))
        }
        return CGSize(width: maxW, height: 160)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            if !message.mine, let author = message.author {
                HStack(spacing: 16) {
                    Text(author).font(Theme.sans(Theme.fontBase, .bold))
                    if let role = message.role {
                        Text(role).font(Theme.sans(Theme.fontSm)).foregroundStyle(Theme.textSecondary)
                    }
                }
            }
            if let reply = message.reply {
                VStack(alignment: .leading, spacing: 2) {
                    Text(reply.author).font(Theme.sans(Theme.fontSm, .bold))
                    Text(reply.text).font(Theme.sans(Theme.fontSm)).foregroundStyle(Theme.textSecondary)
                        .lineLimit(1)
                }
                .padding(.vertical, 6).padding(.horizontal, 10)
                .frame(maxWidth: 230, alignment: .leading)
                .background(Color.black.opacity(0.18))
                .overlay(Rectangle().fill(Color.white.opacity(0.5)).frame(width: 3), alignment: .leading)
                .clipShape(RoundedRectangle(cornerRadius: 8))
            }
            if let image = message.image {
                AsyncImage(url: image.url) { phase in
                    switch phase {
                    case .success(let img): img.resizable().aspectRatio(contentMode: .fill)
                    case .failure: placeholderImage
                    default: ZStack { placeholderImage; ProgressView().tint(.white) }
                    }
                }
                .frame(width: imageSize.width, height: imageSize.height)
                .clipShape(RoundedRectangle(cornerRadius: 12))
            }
            if let file = message.file {
                Button { onOpenFile?() } label: {
                    HStack(spacing: 10) {
                        Text(file.ext.isEmpty ? "file" : file.ext)
                            .font(Theme.sans(Theme.fontSm, .bold)).foregroundStyle(Color(hex: "#1a1b1d"))
                            .frame(width: 38, height: 44).background(Color.white)
                            .clipShape(RoundedRectangle(cornerRadius: 8))
                        VStack(alignment: .leading) {
                            Text(file.name).font(Theme.sans(Theme.fontBase)).lineLimit(1)
                            Text(file.size).font(Theme.sans(Theme.fontSm)).foregroundStyle(Theme.textSecondary)
                        }
                        Icon(AppIcons.downloadCloud, size: 22, color: Theme.text)
                    }
                    .foregroundStyle(Theme.text)
                    .padding(.vertical, 6)
                }
                .buttonStyle(.plain)
            }
            if let voice = message.voice {
                HStack(spacing: 10) {
                    Button { onPlayVoice?() } label: {
                        ZStack {
                            Circle().fill(Color(hex: "#4e8f10")).frame(width: 40, height: 40)
                            Image(systemName: isPlaying ? "pause.fill" : "play.fill")
                                .font(Theme.sans(15, .bold)).foregroundStyle(.white)
                        }
                    }.buttonStyle(.plain)
                    HStack(alignment: .center, spacing: 2) {
                        ForEach(Array(bars.enumerated()), id: \.offset) { idx, h in
                            let frac = Double(idx) / Double(max(bars.count - 1, 1))
                            let played = isPlaying && frac <= voiceProgress
                            Capsule()
                                .fill(Color.white.opacity(played ? 0.98 : (isPlaying ? 0.4 : 0.55)))
                                .frame(width: 2, height: h)
                        }
                    }
                    .frame(height: 24)
                    .animation(.linear(duration: 0.08), value: voiceProgress)
                    Text(voice.duration).font(Theme.sans(Theme.fontSm)).foregroundStyle(Theme.textSecondary)
                }
                .padding(.vertical, 2)
            }
            if let text = message.text, !text.isEmpty {
                Text(text).font(Theme.sans(Theme.fontBase)).foregroundStyle(Theme.text)
            }
            if !message.reactions.isEmpty {
                HStack(spacing: 6) {
                    ForEach(message.reactions) { r in
                        HStack(spacing: 3) {
                            Text(r.emoji)
                            Text("\(r.count)").font(Theme.sans(Theme.fontSm))
                        }
                        .padding(.vertical, 2).padding(.horizontal, 8)
                        .background(r.mine ? AnyShapeStyle(Theme.green.opacity(0.35)) : AnyShapeStyle(Color.black.opacity(0.25)))
                        .overlay(Capsule().stroke(r.mine ? Theme.green.opacity(0.8) : Color.clear, lineWidth: 1))
                        .clipShape(Capsule())
                    }
                }
                .padding(.top, 6)
            }
            if let time = message.time {
                HStack(spacing: 6) {
                    Spacer(minLength: 0)
                    if message.pinned {
                        Icon(AppIcons.pin, size: 12, color: Theme.textSecondary)
                    }
                    Text(time).font(Theme.sans(Theme.fontSm)).foregroundStyle(Theme.textSecondary)
                    if message.mine { readMark }
                }
            }
        }
        .foregroundStyle(Theme.text)
        .frame(maxWidth: 300, alignment: .leading)
        .padding(.vertical, 10).padding(.horizontal, 14)
        .background(bubbleBackground)
        .clipShape(bubbleShape)
    }

    private var placeholderImage: some View {
        LinearGradient(colors: [Color(hex: "#cfc6d6"), Color(hex: "#a9adbd"), Color(hex: "#b7c2bf")],
                       startPoint: .topLeading, endPoint: .bottomTrailing)
    }

    /// Отметка о прочтении: две галочки (прочитано) / одна (доставлено).
    private var readMark: some View {
        Image(systemName: message.read ? "checkmark.circle.fill" : "checkmark.circle")
            .font(Theme.sans(12, .bold))
            .foregroundStyle(message.read ? Color.white : Color.white.opacity(0.5))
    }

    @ViewBuilder private var bubbleBackground: some View {
        if message.mine { Theme.gradientAccent }
        else { Theme.surface }
    }

    private var bubbleShape: UnevenRoundedRectangle {
        UnevenRoundedRectangle(topLeadingRadius: 18,
                               bottomLeadingRadius: message.mine ? 18 : 6,
                               bottomTrailingRadius: message.mine ? 6 : 18,
                               topTrailingRadius: 18, style: .continuous)
    }
}
