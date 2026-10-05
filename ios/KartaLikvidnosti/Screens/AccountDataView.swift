import SwiftUI

struct AccountDataView: View {
    @EnvironmentObject var router: Router
    @EnvironmentObject var session: Session

    @State private var email = ""
    @State private var phone = ""
    @State private var passwordRevealed = false
    private let passwordMask = "●●●●●●●"
    private let password = "password"

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("Данные пользователя").font(Theme.heading(Theme.fontTitle)).foregroundStyle(Theme.text)

            VStack(spacing: 0) {
                row(label: "Email:", value: email, icon: AppIcons.pencil) { router.push(.accountChange(field: "email")) }
                divider
                row(label: "Номер телефона:", value: phone, icon: AppIcons.pencil) { router.push(.accountChange(field: "phone")) }
                divider
                row(label: "Пароль:", value: passwordRevealed ? password : passwordMask,
                    icon: passwordRevealed ? AppIcons.eye : AppIcons.eyeClosed) { passwordRevealed.toggle() }
            }
            .padding(.horizontal, 16)
            .background(Theme.surface).clipShape(RoundedRectangle(cornerRadius: Theme.radiusCard, style: .continuous))

            Button("Сменить пароль") { router.push(.accountChange(field: "password")) }
                .font(.system(size: Theme.fontBase)).foregroundStyle(Theme.textMuted)
                .frame(maxWidth: .infinity, alignment: .trailing)

            VStack(spacing: 12) {
                AppButton(title: "Сменить пароль", variant: .accent) { router.push(.accountChange(field: "password")) }
                AppButton(title: "Выйти из аккаунта", variant: .danger) { logout() }
            }
            Spacer()
        }
        .padding(.horizontal, Theme.screenX).padding(.top, 16).padding(.bottom, 40)
        .screenBackground(Theme.bg)
        .task { await load() }
    }

    private var divider: some View { Rectangle().fill(Color.white.opacity(0.08)).frame(height: 1) }

    private func row(label: String, value: String, icon: IconDef, action: @escaping () -> Void) -> some View {
        HStack {
            (Text(label).foregroundColor(Theme.textSecondary) + Text(" \(value)").foregroundColor(Theme.text))
                .font(.system(size: Theme.fontBase))
            Spacer()
            Button(action: action) { Icon(icon, size: 22, color: Theme.text) }.buttonStyle(.plain)
        }
        .padding(.vertical, 14)
    }

    private func load() async {
        do { let p = try await Api.fetchProfile(); email = p.user?.email ?? ""; phone = p.user?.phone ?? "" } catch {}
    }

    private func logout() {
        Task { await Api.logout(); session.clear(); router.logout() }
    }
}
