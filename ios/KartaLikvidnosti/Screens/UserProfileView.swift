import SwiftUI

struct UserProfileView: View {
    let chatId: String
    let uid: String

    private let name = "Артем"
    private let tag = "@AlexeyRub"
    private let registered = "14.09.2026 (7 дней)"

    var body: some View {
        ZStack {
            LinearGradient(colors: [Color(hex: "#4a5a54"), Color(hex: "#2a2d2e"), Color(hex: "#1a1b1d")],
                           startPoint: .top, endPoint: .bottom)
                .overlay(RadialGradient(colors: [Color(hex: "#78825a").opacity(0.5), .clear], center: .center, startRadius: 0, endRadius: 220))
                .ignoresSafeArea()

            VStack(spacing: 6) {
                Text("Зарегистрирован").font(.system(size: Theme.fontTitle, weight: .bold)).foregroundStyle(Theme.text)
                Text(registered).font(.system(size: Theme.fontBase)).foregroundStyle(Theme.textSecondary).padding(.bottom, 20)
                Circle().fill(
                    LinearGradient(colors: [Color(hex: "#6b7350"), Color(hex: "#3f4a2c")], startPoint: .topLeading, endPoint: .bottomTrailing)
                ).frame(width: 160, height: 160).padding(.bottom, 14)
                Text(name).font(.system(size: Theme.fontTitle, weight: .bold)).foregroundStyle(Theme.text)
                Text(tag).font(.system(size: Theme.fontMd)).foregroundStyle(Theme.textSecondary)
            }
            .padding(.horizontal, Theme.screenX)
        }
    }
}
