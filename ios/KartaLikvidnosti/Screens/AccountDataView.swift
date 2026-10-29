import SwiftUI

struct AccountDataView: View {
    @EnvironmentObject var router: Router
    @EnvironmentObject var session: Session

    @State private var email = ""
    @State private var loggingOut = false
    @State private var phone = ""

    // «Профиль Данные аккаунта» (Figma 337:27019 / 337:27032): заголовок на
    // y=160, блок 361 с отступом 12 и строками 48 (подпись 14 pt белый 65 %,
    // значение белым, справа иконка 24: карандаш или стрелка). Пароль не
    // показывается (сервер хранит только хэш): строка «Пароль: задан» со стрелкой
    // ведёт на смену. Под блоком справа «Сменить пароль» 12 pt; через 24 —
    // «Сменить пароль» и красная «Выйти из аккаунта».
    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: 0) {
                Text("Данные пользователя").font(Theme.heading(Theme.fontTitle)).foregroundStyle(Theme.text)

                VStack(spacing: 0) {
                    row(icon: AppIcons.pencil, actionLabel: "Изменить почту", action: { router.push(.accountChange(field: "email")) }) {
                        valueText("Email:", email)
                    }
                    row(icon: AppIcons.pencil, actionLabel: "Изменить номер телефона", action: { router.push(.accountChange(field: "phone")) }) {
                        valueText("Номер телефона:", phone)
                    }
                    row(icon: AppIcons.back, flip: true, actionLabel: "Сменить пароль", action: { router.push(.accountChange(field: "password")) }) {
                        valueText("Пароль:", "задан")
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

    /// Нажимается вся строка 48: иконка 24 справа, цель — во всю ширину.
    private func row<Label: View>(icon: IconDef, flip: Bool = false, actionLabel: String,
                                  action: @escaping () -> Void, @ViewBuilder label: () -> Label) -> some View {
        Button(action: { Haptics.light(); action() }) {
            HStack(spacing: 8) {
                label()
                Spacer(minLength: 8)
                Icon(icon, size: flip ? 16 : 24, color: Theme.text)
                    .scaleEffect(x: flip ? -1 : 1, y: 1)
                    .frame(width: 32, height: 32)
            }
            .frame(height: 48)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine)
        .accessibilityHint(actionLabel)
    }

    private func load() async {
        do { let p = try await Api.fetchProfile(); email = p.user?.email ?? ""; phone = p.user?.phone ?? "" } catch {}
    }

    private func logout() {
        loggingOut = true
        Task { await Api.logout(); session.clear(); router.logout() }
    }
}
