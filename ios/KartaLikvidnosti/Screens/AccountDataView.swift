import SwiftUI

struct AccountDataView: View {
    @EnvironmentObject var router: Router
    @EnvironmentObject var session: Session

    @State private var email = ""
    @State private var loggingOut = false
    @State private var phone = ""
    @State private var passwordRevealed = false
    private let passwordMask = "●●●●●●●"
    private let password = "password"

    // «Профиль Данные аккаунта» (Figma 337:27019 / 337:27032): заголовок на
    // y=160, блок 361 с отступом 12 и строками 48 (подпись 14 pt белый 65 %,
    // значение белым, справа иконка 24: карандаш или глаз), пароль — семь
    // точек 12 (#D9D9D9) через 2; под блоком справа «Сменить пароль» 12 pt;
    // через 24 — «Сменить пароль» и красная «Выйти из аккаунта».
    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: 0) {
                Text("Данные пользователя").font(Theme.heading(Theme.fontTitle)).foregroundStyle(Theme.text)

                VStack(spacing: 0) {
                    row(icon: AppIcons.pencil, action: { router.push(.accountChange(field: "email")) }) {
                        valueText("Email:", email)
                    }
                    row(icon: AppIcons.pencil, action: { router.push(.accountChange(field: "phone")) }) {
                        valueText("Номер телефона:", phone)
                    }
                    row(icon: passwordRevealed ? AppIcons.eye : AppIcons.eyeClosed, action: {
                        // Прототип: «Профиль Данные аккаунта» 484:21316 ↔ 484:21330 — 800 мс.
                        withAnimation(Motion.standard) { passwordRevealed.toggle() }
                    }) {
                        HStack(spacing: 4) {
                            Text("Пароль:").foregroundStyle(Theme.textSecondary)
                            if passwordRevealed {
                                Text(password).foregroundStyle(Theme.text)
                            } else {
                                HStack(spacing: 2) {
                                    ForEach(0..<7, id: \.self) { _ in
                                        Circle().fill(Color(hex: "#d9d9d9")).frame(width: 12, height: 12)
                                    }
                                }
                            }
                        }
                        .font(Theme.sans(Theme.fontBase))
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
                .background(Theme.surface, in: RoundedRectangle(cornerRadius: Theme.radiusCard, style: .continuous))
                .padding(.top, 24)

                Button("Сменить пароль") { router.push(.accountChange(field: "password")) }
                    .font(Theme.sans(Theme.fontSm)).foregroundStyle(Theme.textSecondary)
                    .buttonStyle(.plain)
                    .frame(maxWidth: .infinity, alignment: .trailing)
                    .padding(.top, 12)

                VStack(spacing: 12) {
                    AppButton(title: "Сменить пароль", variant: .accent) { router.push(.accountChange(field: "password")) }
                    AppButton(title: "Выйти из аккаунта", variant: .danger, loading: loggingOut) { logout() }
                }
                .padding(.top, 24)
            }
            .padding(.horizontal, Theme.screenX)
            .padding(.top, 32)
            .padding(.bottom, 40)
        }
        .screenBackground()
        .task { await load() }
    }

    private func valueText(_ label: String, _ value: String) -> some View {
        (Text(label + " ").foregroundColor(Theme.textSecondary) + Text(value).foregroundColor(Theme.text))
            .font(Theme.sans(Theme.fontBase))
            .lineLimit(1)
    }

    private func row<Label: View>(icon: IconDef, action: @escaping () -> Void, @ViewBuilder label: () -> Label) -> some View {
        HStack(spacing: 8) {
            label()
            Spacer(minLength: 8)
            Button(action: { Haptics.light(); action() }) {
                Icon(icon, size: 24, color: Theme.text).frame(width: 32, height: 32).contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        }
        .frame(height: 48)
    }

    private func load() async {
        do { let p = try await Api.fetchProfile(); email = p.user?.email ?? ""; phone = p.user?.phone ?? "" } catch {}
    }

    private func logout() {
        loggingOut = true
        Task { await Api.logout(); session.clear(); router.logout() }
    }
}
