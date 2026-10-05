import SwiftUI

struct ChatInfoView: View {
    @EnvironmentObject var router: Router
    @EnvironmentObject var session: Session
    let chatId: String

    @State private var title = "Нефть 12.12.27"
    @State private var members = "15 участников"
    @State private var avatar: URL? = nil
    @State private var tab = "media"

    private let tabs = [("members", "Участники"), ("media", "Медиа"), ("voice", "Голосовые"), ("files", "Файлы"), ("links", "Ссылки")]

    private struct Member { let name: String; let seen: String; let role: String }
    private let membersData = [
        Member(name: "Владислав", seen: "Был в сети 58 минут назад", role: "Автор"),
        Member(name: "Алексей", seen: "Был в сети 15 минут назад", role: "Участник"),
        Member(name: "Борат", seen: "Был в сети 1 час назад", role: "Участник"),
        Member(name: "Владимир", seen: "Был в сети 2 часа назад", role: "Участник"),
        Member(name: "Артем в2", seen: "Был в сети 50 минут назад", role: "Участник"),
    ]
    private struct FileRow { let ext: String; let color: Color; let name: String; let meta: String }
    private let filesData = [
        FileRow(ext: "pdf", color: Color(hex: "#e8503a"), name: "pdf file for windows", meta: "1.9 мб / 6 сент. 2026 в 8:32"),
        FileRow(ext: "exe", color: Color(hex: "#3a7be8"), name: "pdf file for windows", meta: "1.9 мб / 6 сент. 2026 в 8:32"),
        FileRow(ext: "zip", color: Color(hex: "#7fbf1f"), name: "pdf file for windows", meta: "1.9 мб / 6 сент. 2026 в 8:32"),
    ]

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
                            Button { tab = t.0 } label: {
                                Text(t.1).font(.system(size: Theme.fontBase)).foregroundStyle(Theme.text)
                                    .padding(.horizontal, 18).frame(height: 40)
                                    .background(tab == t.0 ? AnyShapeStyle(Theme.gradientAccentPurple) : AnyShapeStyle(Color.clear),
                                                in: RoundedRectangle(cornerRadius: Theme.radiusChip, style: .continuous))
                            }.buttonStyle(.plain)
                        }
                    }.padding(6)
                }
                .background(Theme.surfaceMuted).clipShape(RoundedRectangle(cornerRadius: Theme.radiusCard, style: .continuous))
                .padding(.horizontal, Theme.screenX).padding(.vertical, 16)

                content.padding(.horizontal, Theme.screenX).padding(.bottom, 24)
            }
        }
        .screenBackground(Theme.bg)
        .task { await load() }
    }

    private var hero: some View {
        VStack(spacing: 4) {
            AvatarCircle(url: avatar, size: 150).padding(.top, 8)
            Text(title).font(.system(size: Theme.fontTitle, weight: .bold)).foregroundStyle(Theme.text).padding(.top, 10)
            Text(members).font(.system(size: Theme.fontBase)).foregroundStyle(Theme.textSecondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.top, 16).padding(.bottom, 20).padding(.horizontal, Theme.screenX)
        .background(
            LinearGradient(colors: [Color(hex: "#343235"), Color(hex: "#1a1b1d")], startPoint: .top, endPoint: .bottom)
        )
        .overlay(alignment: .topTrailing) {
            if session.isAdmin {
                Button { router.push(.chatEdit(id: chatId)) } label: {
                    Icon(AppIcons.gear, size: 22, color: Theme.text).frame(width: 42, height: 42).background(Theme.surfaceMuted).clipShape(Circle())
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
        case "members":
            VStack(spacing: 0) {
                ForEach(Array(membersData.enumerated()), id: \.offset) { _, m in
                    HStack(spacing: 12) {
                        Circle().fill(Color(hex: "#8a8a8a")).frame(width: 44, height: 44)
                        VStack(alignment: .leading) {
                            Text(m.name).font(.system(size: Theme.fontMd, weight: .bold))
                            Text(m.seen).font(.system(size: Theme.fontBase)).foregroundStyle(Theme.textSecondary)
                        }
                        Spacer()
                        Text(m.role).font(.system(size: Theme.fontBase)).foregroundStyle(Theme.textSecondary)
                    }
                    .foregroundStyle(Theme.text).padding(.vertical, 12)
                    .overlay(Rectangle().fill(Color.white.opacity(0.06)).frame(height: 1), alignment: .bottom)
                }
            }
        case "media":
            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 2), count: 3), spacing: 2) {
                ForEach(0..<9, id: \.self) { _ in Color(hex: "#d4d4d4").aspectRatio(1, contentMode: .fill) }
            }
        case "voice":
            VStack(spacing: 0) {
                ForEach(0..<6, id: \.self) { _ in
                    HStack(spacing: 12) {
                        ZStack { Circle().fill(Color(hex: "#4e8f10")).frame(width: 44, height: 44); Icon(AppIcons.play, size: 18, color: .white) }
                        VStack(alignment: .leading) {
                            Text("Владислав").font(.system(size: Theme.fontMd, weight: .bold))
                            Text("20:42 / 6 сент. 2026 в 8:32").font(.system(size: Theme.fontBase)).foregroundStyle(Theme.textSecondary)
                        }
                        Spacer()
                    }
                    .foregroundStyle(Theme.text).padding(.vertical, 12)
                    .overlay(Rectangle().fill(Color.white.opacity(0.06)).frame(height: 1), alignment: .bottom)
                }
            }
        case "files":
            VStack(spacing: 0) {
                ForEach(Array(filesData.enumerated()), id: \.offset) { _, f in
                    HStack(spacing: 12) {
                        Text(f.ext).font(.system(size: Theme.fontSm, weight: .bold)).foregroundStyle(.white)
                            .frame(width: 44, height: 44).background(f.color).clipShape(RoundedRectangle(cornerRadius: 10))
                        VStack(alignment: .leading) {
                            Text(f.name).font(.system(size: Theme.fontMd, weight: .bold))
                            Text(f.meta).font(.system(size: Theme.fontBase)).foregroundStyle(Theme.textSecondary)
                        }
                        Spacer()
                        Icon(AppIcons.downloadCloud, size: 24, color: Theme.textSecondary)
                    }
                    .foregroundStyle(Theme.text).padding(.vertical, 12)
                    .overlay(Rectangle().fill(Color.white.opacity(0.06)).frame(height: 1), alignment: .bottom)
                }
            }
        default:
            VStack(alignment: .leading, spacing: 10) {
                ForEach(0..<2, id: \.self) { _ in
                    HStack(spacing: 12) {
                        RoundedRectangle(cornerRadius: 10).fill(Color(hex: "#8a8a8a")).frame(width: 44, height: 44)
                        VStack(alignment: .leading, spacing: 3) {
                            Text("Telegram").font(.system(size: Theme.fontMd, weight: .bold))
                            Text("Новые функции уже в телеграм, работают не только с премиум, но и без премиума, переходите чтобы узнать больше!")
                                .font(.system(size: Theme.fontBase)).foregroundStyle(Theme.textSecondary).lineSpacing(2)
                            Text("rbk.ru/news").font(.system(size: Theme.fontBase)).foregroundStyle(Color(hex: "#6ea8e0"))
                        }
                        Spacer()
                    }
                    .foregroundStyle(Theme.text).padding(12).background(Theme.surface).clipShape(RoundedRectangle(cornerRadius: 14))
                }
            }
        }
    }

    private func load() async {
        do {
            let resp = try await Api.fetchMessages(chatId)
            if let c = resp.chat {
                title = c.title
                members = c.subtitle ?? ""
                avatar = fileURL(c.avatarUrl)
            }
        } catch {}
    }
}
