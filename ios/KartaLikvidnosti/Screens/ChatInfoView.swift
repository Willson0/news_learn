import SwiftUI

struct ChatInfoView: View {
    @EnvironmentObject var router: Router
    @EnvironmentObject var session: Session
    let chatId: String

    @StateObject private var audio = ChatAudio()

    @State private var title = ""
    @State private var members = ""
    @State private var avatar: URL? = nil
    @State private var tab = "members"

    @State private var participants: [ParticipantDto] = []
    @State private var media: [ChatAttachmentDto] = []
    @State private var voices: [ChatAttachmentDto] = []
    @State private var files: [ChatAttachmentDto] = []
    @State private var links: [ChatLinkDto] = []
    @State private var loaded: Set<String> = []

    private let tabs = [("members", "Участники"), ("media", "Медиа"), ("voice", "Голосовые"), ("files", "Файлы"), ("links", "Ссылки")]

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(spacing: 0) {
                hero
                HStack(spacing: 12) {
                    actionButton(AppIcons.bell); actionButton(AppIcons.search); actionButton(AppIcons.leave)
                }
                .padding(.horizontal, Theme.screenX).padding(.top, 16)

                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 4) {
                        ForEach(tabs, id: \.0) { t in
                            Button { switchTab(t.0) } label: {
                                Text(t.1).font(Theme.sans(Theme.fontBase)).foregroundStyle(Theme.text)
                                    .padding(.horizontal, 18).frame(height: 40)
                                    .background(tab == t.0 ? AnyShapeStyle(Theme.gradientAccentPurple) : AnyShapeStyle(Color.clear),
                                                in: RoundedRectangle(cornerRadius: Theme.radiusChip, style: .continuous))
                            }.buttonStyle(.plain)
                        }
                    }.padding(6)
                }
                .liquidGlass(RoundedRectangle(cornerRadius: Theme.radiusCard, style: .continuous))
                .padding(.horizontal, Theme.screenX).padding(.vertical, 16)

                content.padding(.horizontal, Theme.screenX).padding(.bottom, 24)
            }
        }
        .screenBackground()
        .task { await load() }
    }

    private var hero: some View {
        VStack(spacing: 4) {
            AvatarCircle(url: avatar, size: 150, name: title).padding(.top, 8)
            Text(title).font(Theme.heading(Theme.fontTitle)).foregroundStyle(Theme.text).padding(.top, 10)
            Text(members).font(Theme.sans(Theme.fontBase)).foregroundStyle(Theme.textSecondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.top, 16).padding(.bottom, 20).padding(.horizontal, Theme.screenX)
        .background(
            LinearGradient(colors: [Color(hex: "#343235"), Color(hex: "#1a1b1d")], startPoint: .top, endPoint: .bottom)
        )
        .overlay(alignment: .topTrailing) {
            if session.isAdmin {
                Button { router.push(.chatEdit(id: chatId)) } label: {
                    Icon(AppIcons.gear, size: 22, color: Theme.text).frame(width: 42, height: 42).liquidGlass()
                }.buttonStyle(.plain).padding(.trailing, Theme.screenX).padding(.top, 16)
            }
        }
    }

    private func actionButton(_ icon: IconDef) -> some View {
        Icon(icon, size: 22, color: Theme.text)
            .frame(maxWidth: .infinity).frame(height: 52)
            .background(Theme.surface).clipShape(RoundedRectangle(cornerRadius: Theme.radiusCard, style: .continuous))
    }

    @ViewBuilder private var content: some View {
        switch tab {
        case "members": membersList
        case "media": mediaGrid
        case "voice": voiceList
        case "files": filesList
        default: linksList
        }
    }

    private var membersList: some View {
        VStack(spacing: 0) {
            if participants.isEmpty { emptyRow("Пока нет участников") }
            ForEach(participants) { m in
                Button { router.push(.chatUser(id: chatId, uid: String(m.id))) } label: {
                    HStack(spacing: 12) {
                        AvatarCircle(url: fileURL(m.avatarUrl), size: 44, name: m.name ?? m.username)
                        VStack(alignment: .leading) {
                            Text(m.name ?? "").font(Theme.sans(Theme.fontMd, .bold))
                            Text(m.seen ?? "").font(Theme.sans(Theme.fontBase)).foregroundStyle(Theme.textSecondary)
                        }
                        Spacer()
                        Text(m.role ?? "").font(Theme.sans(Theme.fontBase)).foregroundStyle(Theme.textSecondary)
                    }
                    .foregroundStyle(Theme.text).padding(.vertical, 12)
                    .overlay(Rectangle().fill(Color.white.opacity(0.06)).frame(height: 1), alignment: .bottom)
                }.buttonStyle(.plain)
            }
        }
    }

    private var mediaGrid: some View {
        Group {
            if media.isEmpty { emptyRow("Нет медиа") }
            else {
                LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 2), count: 3), spacing: 2) {
                    ForEach(media) { a in
                        Button { open(a.url) } label: {
                            AsyncImage(url: fileURL(a.url)) { phase in
                                switch phase {
                                case .success(let img): img.resizable().aspectRatio(contentMode: .fill)
                                default: Color(hex: "#2c2d30")
                                }
                            }
                            .aspectRatio(1, contentMode: .fill)
                            .clipped()
                        }.buttonStyle(.plain)
                    }
                }
            }
        }
    }

    private var voiceList: some View {
        VStack(spacing: 0) {
            if voices.isEmpty { emptyRow("Нет голосовых") }
            ForEach(voices) { a in
                HStack(spacing: 12) {
                    Button { audio.togglePlay(id: a.id, url: fileURL(a.url)) } label: {
                        ZStack {
                            Circle().fill(Color(hex: "#4e8f10")).frame(width: 44, height: 44)
                            Image(systemName: audio.playingId == a.id ? "pause.fill" : "play.fill")
                                .font(Theme.sans(16, .bold)).foregroundStyle(.white)
                        }
                    }.buttonStyle(.plain)
                    VStack(alignment: .leading) {
                        Text(a.author ?? "Участник").font(Theme.sans(Theme.fontMd, .bold))
                        Text(a.meta ?? "").font(Theme.sans(Theme.fontBase)).foregroundStyle(Theme.textSecondary)
                    }
                    Spacer()
                }
                .foregroundStyle(Theme.text).padding(.vertical, 12)
                .overlay(Rectangle().fill(Color.white.opacity(0.06)).frame(height: 1), alignment: .bottom)
            }
        }
    }

    private var filesList: some View {
        VStack(spacing: 0) {
            if files.isEmpty { emptyRow("Нет файлов") }
            ForEach(files) { f in
                Button { open(f.url) } label: {
                    HStack(spacing: 12) {
                        Text((f.ext ?? "").isEmpty ? "file" : (f.ext ?? "")).font(Theme.sans(Theme.fontSm, .bold)).foregroundStyle(.white)
                            .frame(width: 44, height: 44).background(colorFor(f.ext)).clipShape(RoundedRectangle(cornerRadius: 10))
                        VStack(alignment: .leading) {
                            Text(f.name ?? "Файл").font(Theme.sans(Theme.fontMd, .bold)).lineLimit(1)
                            Text(f.meta ?? "").font(Theme.sans(Theme.fontBase)).foregroundStyle(Theme.textSecondary)
                        }
                        Spacer()
                        Icon(AppIcons.downloadCloud, size: 24, color: Theme.textSecondary)
                    }
                    .foregroundStyle(Theme.text).padding(.vertical, 12)
                    .overlay(Rectangle().fill(Color.white.opacity(0.06)).frame(height: 1), alignment: .bottom)
                }.buttonStyle(.plain)
            }
        }
    }

    private var linksList: some View {
        VStack(alignment: .leading, spacing: 10) {
            if links.isEmpty { emptyRow("Нет ссылок") }
            ForEach(links) { l in
                Button { open(l.url) } label: {
                    HStack(spacing: 12) {
                        RoundedRectangle(cornerRadius: 10).fill(Color(hex: "#3a7be8")).frame(width: 44, height: 44)
                            .overlay(Icon(AppIcons.link, size: 22, color: .white))
                        VStack(alignment: .leading, spacing: 3) {
                            Text(l.title ?? l.url).font(Theme.sans(Theme.fontMd, .bold)).lineLimit(1)
                            if let t = l.text { Text(t).font(Theme.sans(Theme.fontBase)).foregroundStyle(Theme.textSecondary).lineLimit(2) }
                            Text(l.url).font(Theme.sans(Theme.fontBase)).foregroundStyle(Color(hex: "#6ea8e0")).lineLimit(1)
                        }
                        Spacer()
                    }
                    .foregroundStyle(Theme.text).padding(12).background(Theme.surface).clipShape(RoundedRectangle(cornerRadius: 14))
                }.buttonStyle(.plain)
            }
        }
    }

    private func emptyRow(_ text: String) -> some View {
        Text(text).font(Theme.sans(Theme.fontBase)).foregroundStyle(Theme.textMuted)
            .frame(maxWidth: .infinity).padding(.vertical, 28)
    }

    private func colorFor(_ ext: String?) -> Color {
        switch (ext ?? "").lowercased() {
        case "pdf": return Color(hex: "#e8503a")
        case "zip", "rar", "7z": return Color(hex: "#7fbf1f")
        case "exe", "dmg": return Color(hex: "#3a7be8")
        default: return Color(hex: "#6b6f76")
        }
    }

    private func open(_ url: String?) {
        guard let u = fileURL(url) else { return }
        UIApplication.shared.open(u)
    }

    private func switchTab(_ t: String) {
        tab = t
        Task { await loadTab(t) }
    }

    private func load() async {
        do {
            let resp = try await Api.fetchMessages(chatId)
            if let c = resp.chat {
                title = c.title
                avatar = fileURL(c.avatarUrl)
                members = c.subtitle ?? ""
            }
        } catch {}
        await loadTab(tab)
    }

    private func loadTab(_ t: String) async {
        if loaded.contains(t) { return }
        switch t {
        case "members":
            if let p = try? await Api.chatParticipants(chatId) {
                participants = p
                if members.isEmpty || !members.contains("участ") {
                    members = "\(p.count) \(plural(p.count, ["участник", "участника", "участников"]))"
                }
            }
        case "media":
            if let a = try? await Api.chatAttachments(chatId, kind: "image") { media = a }
        case "voice":
            if let a = try? await Api.chatAttachments(chatId, kind: "voice") { voices = a }
        case "files":
            if let a = try? await Api.chatAttachments(chatId, kind: "file") { files = a }
        case "links":
            if let a = try? await Api.chatLinks(chatId) { links = a }
        default: break
        }
        loaded.insert(t)
    }

    private func plural(_ n: Int, _ forms: [String]) -> String {
        let a = abs(n) % 100, b = a % 10
        if a > 10 && a < 20 { return forms[2] }
        if b > 1 && b < 5 { return forms[1] }
        if b == 1 { return forms[0] }
        return forms[2]
    }
}
