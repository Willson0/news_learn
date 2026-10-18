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

    var body: some View {
        ZStack {
            LinearGradient(colors: [Color(hex: "#4a5a54"), Color(hex: "#2a2d2e"), Color(hex: "#1a1b1d")],
                           startPoint: .top, endPoint: .bottom)
                .overlay(RadialGradient(colors: [Color(hex: "#78825a").opacity(0.5), .clear], center: .center, startRadius: 0, endRadius: 220))
                .ignoresSafeArea()

            if let u = user {
                VStack(spacing: 6) {
                    Text("Зарегистрирован").font(Theme.heading(Theme.fontTitle)).foregroundStyle(Theme.text)
                    Text(registered).font(Theme.sans(Theme.fontBase)).foregroundStyle(Theme.textSecondary).padding(.bottom, 20)
                    AvatarCircle(url: fileURL(u.avatarUrl), size: 160, name: u.name ?? u.username).padding(.bottom, 14)
                    Text(u.name ?? "").font(Theme.heading(Theme.fontTitle)).foregroundStyle(Theme.text)
                    if let un = u.username { Text(un).font(Theme.sans(Theme.fontMd)).foregroundStyle(Theme.textSecondary) }
                    if let seen = u.seen { Text(seen).font(Theme.sans(Theme.fontSm)).foregroundStyle(Theme.textMuted).padding(.top, 4) }
                }
                .padding(.horizontal, Theme.screenX)
            } else if !loading {
                Text("Пользователь не найден").font(Theme.heading(Theme.fontTitle)).foregroundStyle(Theme.text)
            }
        }
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
