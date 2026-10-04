import SwiftUI

/// Модель сообщения в чате (объединяет серверные и демо-сообщения).
struct ChatMessage: Identifiable {
    let id: Int
    var mine: Bool = false
    var author: String? = nil
    var role: String? = nil
    var text: String? = nil
    var time: String? = nil
    var read: Bool = false
    var toAuthor: Bool = false
    var reply: Reply? = nil
    var file: FileAttachment? = nil
    var voice: Voice? = nil
    var image: Bool = false
    var reactions: [Reaction] = []

    struct Reply { let author: String; let text: String }
    struct FileAttachment { let ext: String; let name: String; let size: String }
    struct Voice { let duration: String }
    struct Reaction: Identifiable { let id = UUID(); let emoji: String; let count: Int }

    init(id: Int, mine: Bool = false, author: String? = nil, role: String? = nil,
         text: String? = nil, time: String? = nil, read: Bool = false, toAuthor: Bool = false,
         reply: Reply? = nil, file: FileAttachment? = nil, voice: Voice? = nil,
         image: Bool = false, reactions: [Reaction] = []) {
        self.id = id; self.mine = mine; self.author = author; self.role = role
        self.text = text; self.time = time; self.read = read; self.toAuthor = toAuthor
        self.reply = reply; self.file = file; self.voice = voice; self.image = image
        self.reactions = reactions
    }

    init(_ dto: MessageDto) {
        self.init(id: dto.id, mine: dto.mine, author: dto.author, role: dto.role,
                  text: dto.text, time: dto.time, read: true)
    }
}

struct MessageBubble: View {
    let message: ChatMessage

    private var bars: [CGFloat] {
        let seed = Double(message.id == 0 ? 1 : message.id)
        return (0..<26).map { i in
            let v = abs(sin(seed * 7.3 + Double(i) * 1.7))
            return CGFloat(4 + Int((v * 14).rounded()))
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            if !message.mine, let author = message.author {
                HStack(spacing: 16) {
                    Text(author).font(.system(size: Theme.fontBase, weight: .bold))
                    if let role = message.role {
                        Text(role).font(.system(size: Theme.fontSm)).foregroundStyle(Theme.textSecondary)
                    }
                }
            }
            if message.toAuthor {
                Text("Автору").font(.system(size: Theme.fontBase, weight: .bold))
            }
            if let reply = message.reply {
                VStack(alignment: .leading, spacing: 2) {
                    Text(reply.author).font(.system(size: Theme.fontSm, weight: .bold))
                    Text(reply.text).font(.system(size: Theme.fontSm)).foregroundStyle(Theme.textSecondary)
                        .lineLimit(1)
                }
                .padding(.vertical, 6).padding(.horizontal, 10)
                .frame(maxWidth: 230, alignment: .leading)
                .background(Color.black.opacity(0.18))
                .overlay(Rectangle().fill(Color.white.opacity(0.5)).frame(width: 3), alignment: .leading)
                .clipShape(RoundedRectangle(cornerRadius: 8))
            }
            if message.image {
                RoundedRectangle(cornerRadius: 12)
                    .fill(LinearGradient(colors: [Color(hex: "#cfc6d6"), Color(hex: "#a9adbd"), Color(hex: "#b7c2bf")],
                                         startPoint: .topLeading, endPoint: .bottomTrailing))
                    .frame(width: 240, height: 150)
            }
            if let file = message.file {
                HStack(spacing: 10) {
                    Text(file.ext)
                        .font(.system(size: Theme.fontSm, weight: .bold)).foregroundStyle(Color(hex: "#1a1b1d"))
                        .frame(width: 38, height: 44).background(Color.white)
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                    VStack(alignment: .leading) {
                        Text(file.name).font(.system(size: Theme.fontBase))
                        Text(file.size).font(.system(size: Theme.fontSm)).foregroundStyle(Theme.textSecondary)
                    }
                    Icon(AppIcons.downloadCloud, size: 22, color: Theme.text)
                }
                .padding(.vertical, 6)
            }
            if let voice = message.voice {
                HStack(spacing: 10) {
                    ZStack {
                        Circle().fill(Color(hex: "#4e8f10")).frame(width: 40, height: 40)
                        Icon(AppIcons.play, size: 18, color: .white)
                    }
                    HStack(alignment: .center, spacing: 2) {
                        ForEach(Array(bars.enumerated()), id: \.offset) { _, h in
                            Capsule().fill(Color.white.opacity(0.55)).frame(width: 2, height: h)
                        }
                    }
                    .frame(height: 24)
                    Text(voice.duration).font(.system(size: Theme.fontSm)).foregroundStyle(Theme.textSecondary)
                }
                .padding(.vertical, 2)
            }
            if let text = message.text {
                Text(text).font(.system(size: Theme.fontBase)).foregroundStyle(Theme.text)
            }
            if !message.reactions.isEmpty {
                HStack(spacing: 6) {
                    ForEach(message.reactions) { r in
                        HStack(spacing: 3) {
                            Text(r.emoji)
                            Text("\(r.count)").font(.system(size: Theme.fontSm))
                        }
                        .padding(.vertical, 2).padding(.horizontal, 8)
                        .background(Color.black.opacity(0.25))
                        .clipShape(Capsule())
                    }
                }
                .padding(.top, 6)
            }
            if let time = message.time {
                HStack(spacing: 6) {
                    Spacer(minLength: 0)
                    Text(time).font(.system(size: Theme.fontSm)).foregroundStyle(Theme.textSecondary)
                    if message.mine {
                        ZStack(alignment: .trailing) {
                            Capsule().fill(Color.white.opacity(0.25)).frame(width: 18, height: 10)
                            Circle().fill(Color.white).frame(width: 8, height: 8)
                        }
                    }
                }
            }
        }
        .foregroundStyle(Theme.text)
        .frame(maxWidth: 300, alignment: .leading)
        .padding(.vertical, 10).padding(.horizontal, 14)
        .background(bubbleBackground)
        .clipShape(bubbleShape)
    }

    @ViewBuilder private var bubbleBackground: some View {
        if message.mine { Theme.gradientAccent }
        else { Theme.surface.overlay(Color.white.opacity(0.0)) }
    }

    private var bubbleShape: UnevenRoundedRectangle {
        UnevenRoundedRectangle(topLeadingRadius: 18,
                               bottomLeadingRadius: message.mine ? 18 : 6,
                               bottomTrailingRadius: message.mine ? 6 : 18,
                               topTrailingRadius: 18, style: .continuous)
    }
}
