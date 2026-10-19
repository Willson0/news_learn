import SwiftUI

struct UserProfileView: View {
    let chatId: String
    let uid: String

    @State private var user: UserProfileDto? = nil
    @State private var loading = true

    private var registered: String {
        guard let u = user, let at = u.registeredAt else { return "" }
        guard let days = u.registeredDays else { return at }
        return "\(at) (\(days) \(plural(days, ["день", "дня", "дней"])))"
    }

    // «Чат Профиль пользователя» (Figma 337:30766): фон #3B5839 с размытой
    // аватаркой (blur 50 + чёрный 50 %), «назад» на y=78; «Зарегистрирован»
    // Kreadon 24 на y=274, дата 14 pt белый 65 % через 12, аватар 148 через
    // 40, имя Kreadon 20 через 12, тег 14 pt через 4.
    var body: some View {
        ZStack(alignment: .top) {
            Color(hex: "#3b5839")
            if let url = fileURL(user?.avatarUrl) {
                RemoteImage(url: url)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .clipped()
            }
            ZStack { BackdropBlur(radius: 50); Color.black.opacity(0.5) }

            if let u = user {
                VStack(spacing: 0) {
                    Text("Зарегистрирован").font(Theme.heading(Theme.fontTitle)).foregroundStyle(Theme.text)
                    Text(registered).font(Theme.sans(Theme.fontBase)).foregroundStyle(Theme.textSecondary)
                        .padding(.top, 12)
                    AvatarCircle(url: fileURL(u.avatarUrl), size: 148, name: u.name ?? u.username)
                        .padding(.top, 40)
                    Text(u.name ?? "").font(Theme.heading(20)).foregroundStyle(Theme.text)
                        .padding(.top, 12)
                    if let un = u.username, !un.isEmpty {
                        Text(un.hasPrefix("@") ? un : "@" + un)
                            .font(Theme.sans(Theme.fontBase)).foregroundStyle(Theme.text)
                            .padding(.top, 4)
                    }
                }
                .padding(.horizontal, Theme.screenX)
                .padding(.top, 274)
            } else if !loading {
                Text("Пользователь не найден").font(Theme.heading(Theme.fontTitle)).foregroundStyle(Theme.text)
                    .frame(maxHeight: .infinity)
            }
        }
        .ignoresSafeArea()
        .overlay(alignment: .topLeading) {
            GlassBackButton()
                .padding(.leading, Theme.screenX)
                .padding(.top, Theme.topBarY)
                .ignoresSafeArea(.container, edges: .top)
        }
        .toolbar(.hidden, for: .navigationBar)
        .task { await load() }
    }

    private func load() async {
        defer { loading = false }
        user = try? await Api.userProfile(uid)
    }

    private func plural(_ n: Int, _ forms: [String]) -> String {
        let a = abs(n) % 100, b = a % 10
        if a > 10 && a < 20 { return forms[2] }
        if b > 1 && b < 5 { return forms[1] }
        if b == 1 { return forms[0] }
        return forms[2]
    }
}
