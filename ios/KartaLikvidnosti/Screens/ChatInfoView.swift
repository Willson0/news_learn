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

    @State private var muted = false
    @State private var searchOpen = false
    @State private var query = ""
    @State private var reportId: Int? = nil

    // «Чат Инфа» (Figma 337:36376 … 337:36561): шапка #383838 с размытой
    // обложкой чата (blur 50 + чёрный 50 %, высота 376), «назад» и стеклянная
    // кнопка-новость 42 на y=78, аватар 148 по центру с y=78, название Kreadon 20
    // и «N участников» 14 pt белый 65 % слева на y=250. С y=321 — лист с
    // радиусом 24 (градиент #1A1B1D → #161719): три стеклянные кнопки 60
    // (звук, поиск, выход), сегменты-чипы 56 (выбранный — пурпур), ниже
    // содержимое вкладки: блоки #25262A со строками 34 (иконка 30, имя
    // SemiBold 12, подпись 12 белый 65 %), сетка медиа 3×131 во всю ширину.
    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(spacing: 0) {
                header
                sheet
            }
        }
        .ignoresSafeArea(.container, edges: .top)
        .background(Color(hex: "#161719").ignoresSafeArea())
        .overlay(alignment: .top) {
            HStack {
                GlassBackButton()
                Spacer()
                Button { newsTapped() } label: {
                    Icon(AppIcons.news, size: 24, color: Theme.text)
                        .frame(width: 42, height: 42)
                        .liquidGlass(Circle(), interactive: true)
                }
                .buttonStyle(PressableStyle())
            }
            .padding(.horizontal, Theme.screenX)
            .padding(.top, Theme.topBarY)
            .ignoresSafeArea(.container, edges: .top)
        }
        .toolbar(.hidden, for: .navigationBar)
        .task { await load() }
        .onAppear { muted = UserDefaults.standard.bool(forKey: "kl_muted_\(chatId)") }
    }

    private var header: some View {
        ZStack(alignment: .topLeading) {
            Color(hex: "#383838")
            ZStack {
                ReportCoverPlaceholder()
                if let avatar { RemoteImage(url: avatar) }
            }
            .frame(height: 376)
            .clipped()
            .overlay { ZStack { BackdropBlur(radius: 50); Color.black.opacity(0.5) } }

            ZStack {
                Circle().fill(Color(hex: "#d9d9d9"))
                ReportCoverPlaceholder().clipShape(Circle())
                if let avatar { RemoteImage(url: avatar).clipShape(Circle()) }
            }
            .frame(width: 148, height: 148)
            .frame(maxWidth: .infinity)
            .padding(.top, 78)

            VStack(alignment: .leading, spacing: 4) {
                Text(title).font(Theme.heading(20)).foregroundStyle(Theme.text).lineLimit(2)
                Text(members).font(Theme.sans(Theme.fontBase)).foregroundStyle(Theme.textSecondary)
            }
            .padding(.horizontal, Theme.screenX)
            .padding(.top, 250)
        }
        .frame(height: 345, alignment: .top)
        .clipped()
    }

    private var sheet: some View {
        VStack(spacing: 0) {
            HStack(spacing: 4) {
                actionButton(muted ? AppIcons.bellSlash : AppIcons.bell) {
                    muted.toggle()
                    UserDefaults.standard.set(muted, forKey: "kl_muted_\(chatId)")
                }
                actionButton(AppIcons.search) { searchOpen.toggle(); if !searchOpen { query = "" } }
                actionButton(AppIcons.leave) {
                    Task {
                        do {
                            try await Api.leaveChat(slug: chatId)
                            router.popToRoot()
                        } catch {}
                    }
                }
            }
            .padding(.top, 16)
            .padding(.horizontal, Theme.screenX)

            if searchOpen {
                AppInput(text: $query, placeholder: "Поиск")
                    .padding(.horizontal, Theme.screenX)
                    .padding(.top, 12)
                    .transition(.opacity.combined(with: .move(edge: .top)))
            }

            tabsBar
                .padding(.top, 40)

            content
                .padding(.top, 24)
                .padding(.bottom, 40)
        }
        .frame(maxWidth: .infinity, minHeight: 535, alignment: .top)
        .background(
            LinearGradient(stops: [.init(color: Color(hex: "#1a1b1d"), location: 0),
                                   .init(color: Color(hex: "#1e1f21"), location: 0.48),
                                   .init(color: Color(hex: "#161719"), location: 1)],
                           startPoint: .top, endPoint: .bottom),
            in: UnevenRoundedRectangle(topLeadingRadius: Theme.radiusCard, topTrailingRadius: Theme.radiusCard, style: .continuous)
        )
        .padding(.top, -24)
        .animation(.easeOut(duration: 0.2), value: searchOpen)
    }

    private var tabsBar: some View {
        ScrollViewReader { proxy in
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 0) {
                    ForEach(tabs, id: \.0) { t in
                        Button { switchTab(t.0); withAnimation { proxy.scrollTo(t.0, anchor: .center) } } label: {
                            Text(t.1).font(Theme.sans(Theme.fontBase)).foregroundStyle(Theme.text)
                                .padding(.horizontal, 12).frame(height: 48)
                                .background {
                                    if tab == t.0 {
                                        SelectionLens(radius: 20)
                                    }
                                }
                        }
                        .buttonStyle(.plain)
                        .id(t.0)
                    }
                }
                .padding(4)
            }
            .clipShape(RoundedRectangle(cornerRadius: Theme.radiusCard, style: .continuous))
            .liquidGlass(RoundedRectangle(cornerRadius: Theme.radiusCard, style: .continuous))
            .padding(.horizontal, Theme.screenX)
        }
    }

    private func actionButton(_ icon: IconDef, action: @escaping () -> Void) -> some View {
        Button { Haptics.light(); action() } label: {
            Icon(icon, size: 24, color: Theme.text)
                .frame(maxWidth: .infinity).frame(height: 60)
                .contentShape(Rectangle())
                .liquidGlass(RoundedRectangle(cornerRadius: Theme.radiusCard, style: .continuous), interactive: true)
        }
        .buttonStyle(PressableStyle())
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

    private func matches(_ parts: String?...) -> Bool {
        let q = query.trimmed.lowercased()
        guard !q.isEmpty else { return true }
        return parts.contains { ($0 ?? "").lowercased().contains(q) }
    }

    /// Блок списка: #25262A, радиус 24, строки 34 через линию (12 + 1 + 16).
    private func listBlock<Item: Identifiable, Row: View>(_ items: [Item], empty: String,
                                                         @ViewBuilder row: @escaping (Item) -> Row) -> some View {
        VStack(spacing: 0) {
            if items.isEmpty { emptyRow(empty) }
            ForEach(Array(items.enumerated()), id: \.element.id) { i, item in
                if i > 0 {
                    Rectangle().fill(Color.white.opacity(0.1)).frame(height: 1)
                        .padding(.top, 15).padding(.bottom, 12)
                }
                row(item).padding(.horizontal, 16)
            }
        }
        .padding(.vertical, 16)
        .frame(maxWidth: .infinity)
        .background(Theme.surface, in: RoundedRectangle(cornerRadius: Theme.radiusCard, style: .continuous))
        .padding(.horizontal, Theme.screenX)
    }

    private func twoLines(_ top: String, _ bottom: String) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(top).font(Theme.sans(Theme.fontSm, .semibold)).foregroundStyle(Theme.text).lineLimit(1)
            Text(bottom).font(Theme.sans(Theme.fontSm)).foregroundStyle(Theme.textSecondary).lineLimit(1)
        }
    }

    private var membersList: some View {
        listBlock(participants.filter { matches($0.name, $0.username) }, empty: "Пока нет участников") { m in
            Button { router.push(.chatUser(id: chatId, uid: String(m.id))) } label: {
                HStack(spacing: 12) {
                    AvatarCircle(url: fileURL(m.avatarUrl), size: 30, name: m.name ?? m.username)
                    twoLines(m.name ?? "", m.seen ?? "")
                    Spacer(minLength: 8)
                    let role = m.role ?? ""
                    Text(role).font(Theme.sans(Theme.fontSm))
                        .foregroundStyle(role == "Автор" ? Theme.text : Theme.textSecondary)
                }
                .frame(height: 34)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        }
    }

    private var mediaGrid: some View {
        Group {
            if media.isEmpty { emptyRow("Нет медиа") }
            else {
                LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 1), count: 3), spacing: 1) {
                    ForEach(media) { a in
                        Button { open(a.url) } label: {
                            Color(hex: "#d9d9d9")
                                .aspectRatio(1, contentMode: .fill)
                                .overlay { RemoteImage(url: fileURL(a.url)) }
                                .clipped()
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
    }

    private var voiceList: some View {
        listBlock(voices.filter { matches($0.author, $0.meta) }, empty: "Нет голосовых") { a in
            HStack(spacing: 12) {
                Button { audio.togglePlay(id: a.id, url: fileURL(a.url)) } label: {
                    ZStack {
                        Circle().fill(Theme.green)
                        if audio.playingId == a.id {
                            HStack(spacing: 3) {
                                Capsule().fill(.white).frame(width: 3, height: 12)
                                Capsule().fill(.white).frame(width: 3, height: 12)
                            }
                        } else {
                            Icon(AppIcons.play, size: 16, color: .white).offset(x: 1)
                        }
                    }
                    .frame(width: 30, height: 30)
                }
                .buttonStyle(.plain)
                twoLines(a.author ?? "Участник", a.meta ?? "")
                Spacer(minLength: 0)
            }
            .frame(height: 34)
        }
    }

    private var filesList: some View {
        listBlock(files.filter { matches($0.name, $0.ext) }, empty: "Нет файлов") { f in
            Button { open(f.url) } label: {
                HStack(spacing: 12) {
                    fileBadge(f.ext)
                    twoLines(f.name ?? "Файл", f.meta ?? "")
                    Spacer(minLength: 8)
                    Icon(AppIcons.downloadCloud, size: 32, color: Theme.text)
                }
                .frame(height: 34)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        }
    }

    /// Значок файла 30×30: цвет по расширению, подпись Bold 12 чёрным.
    private func fileBadge(_ ext: String?) -> some View {
        let e = (ext ?? "").lowercased()
        return ZStack {
            RoundedRectangle(cornerRadius: 6, style: .continuous).fill(colorFor(e))
            if ["pdf", "zip", "rar", "7z", "exe", "dmg", "doc", "docx"].contains(e) {
                Text(e).font(Theme.sans(Theme.fontSm, .bold)).foregroundStyle(.black)
                    .minimumScaleFactor(0.6).lineLimit(1).padding(.horizontal, 2)
            }
        }
        .frame(width: 30, height: 30)
    }

    private var linksList: some View {
        listBlock(links.filter { matches($0.title, $0.text, $0.url) }, empty: "Нет ссылок") { l in
            Button { open(l.url) } label: {
                HStack(alignment: .top, spacing: 12) {
                    RoundedRectangle(cornerRadius: 6, style: .continuous).fill(Color(hex: "#d9d9d9"))
                        .frame(width: 30, height: 30)
                    VStack(alignment: .leading, spacing: 4) {
                        VStack(alignment: .leading, spacing: 0) {
                            Text(l.title ?? "Ссылка").font(Theme.sans(Theme.fontSm, .semibold)).foregroundStyle(Theme.text)
                            if let t = l.text, !t.isEmpty {
                                Text(t).font(Theme.sans(Theme.fontSm)).foregroundStyle(Theme.textSecondary).lineLimit(3)
                            }
                        }
                        Text(l.url).font(Theme.sans(Theme.fontBase)).foregroundStyle(Theme.text).lineLimit(1)
                    }
                    Spacer(minLength: 0)
                }
                .multilineTextAlignment(.leading)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        }
    }

    private func newsTapped() {
        if session.isAdmin { router.push(.chatEdit(id: chatId)) }
        else if let r = reportId { router.push(.reportDetail(id: r)) }
    }

    private func emptyRow(_ text: String) -> some View {
        Text(text).font(Theme.sans(Theme.fontBase)).foregroundStyle(Theme.textMuted)
            .frame(maxWidth: .infinity).padding(.vertical, 28)
    }

    private func colorFor(_ ext: String?) -> Color {
        switch (ext ?? "").lowercased() {
        case "pdf": return Color(hex: "#ff784e")
        case "zip", "rar", "7z": return Color(hex: "#d4f04b")
        case "exe", "dmg", "doc", "docx": return Color(hex: "#4ea6ff")
        default: return Color(hex: "#d9d9d9")
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
                reportId = c.report
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
