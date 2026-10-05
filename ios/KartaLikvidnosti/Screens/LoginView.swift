import SwiftUI

struct LoginView: View {
    @EnvironmentObject var router: Router
    @EnvironmentObject var session: Session

    private enum Mode { case login, register }
    @State private var mode: Mode = .login
    @State private var identifier = ""
    @State private var email = ""
    @State private var phone = ""
    @State private var password = ""
    @State private var passwordConfirm = ""
    @State private var errors: [String: String] = [:]
    @State private var submitting = false

    private let emailRE = #"^[^\s@]+@[^\s@]+\.[^\s@]+$"#
    private let phoneRE = #"^\+?[0-9\s\-()]{7,}$"#

    var body: some View {
        VStack(spacing: 0) {
            ScrollView(showsIndicators: false) {
                VStack(spacing: 0) {
                    HStack {
                        Spacer()
                        Text("16+").font(Theme.heading(Theme.fontTitle)).foregroundStyle(Theme.textMuted)
                    }
                    AvatarCircle(size: 100)
                        .overlay(Circle().stroke(Color.white.opacity(0.06), lineWidth: 1))
                        .padding(.top, 24)

                    HStack(spacing: 0) {
                        toggle("Вход", .login)
                        Text(" / ").foregroundStyle(Theme.textMuted)
                        toggle("Регистрация", .register)
                    }
                    .font(Theme.heading(Theme.fontTitle))
                    .padding(.vertical, 26)

                    if mode == .login { loginFields } else { registerFields }
                }
                .padding(.horizontal, Theme.screenX)
                .padding(.top, 24)
            }

            VStack(spacing: 12) {
                AppButton(title: "Войти с Yandex", variant: .secondary) { Haptics.light() }
                AppButton(title: mode == .login ? "Войти" : "Далее", variant: .accent, disabled: submitting) {
                    submit()
                }
            }
            .padding(.horizontal, Theme.screenX)
            .padding(.top, 16)
            .padding(.bottom, 16)
        }
        .screenBackground { authGradient }
    }

    private func toggle(_ title: String, _ m: Mode) -> some View {
        Button {
            if mode != m { mode = m; errors = [:]; Haptics.light() }
        } label: {
            Text(title).foregroundStyle(mode == m ? Theme.text : Theme.textMuted)
        }
        .buttonStyle(.plain)
    }

    @ViewBuilder private var loginFields: some View {
        VStack(spacing: 12) {
            field(error: errors["identifier"]) {
                AppInput(text: $identifier, placeholder: "Email или номер телефона",
                         error: errors["identifier"] != nil, textContentType: .username)
            }
            VStack(alignment: .leading, spacing: 6) {
                AppInput(text: $password, placeholder: "Пароль", isSecure: true,
                         error: errors["password"] != nil, textContentType: .password)
                HStack {
                    if let e = errors["password"] { Text(e).font(.system(size: Theme.fontSm)).foregroundStyle(Theme.error) }
                    Spacer()
                    Button("Забыли пароль?") { router.authPath.append(.passwordRecovery) }
                        .font(.system(size: Theme.fontSm)).foregroundStyle(Theme.textSecondary)
                }
            }
        }
    }

    @ViewBuilder private var registerFields: some View {
        VStack(spacing: 12) {
            field(error: errors["email"]) {
                AppInput(text: $email, placeholder: "Email", error: errors["email"] != nil,
                         keyboard: .emailAddress, textContentType: .emailAddress)
            }
            field(error: errors["phone"]) {
                AppInput(text: $phone, placeholder: "Номер телефона", error: errors["phone"] != nil,
                         keyboard: .phonePad, textContentType: .telephoneNumber)
            }
            VStack(alignment: .leading, spacing: 6) {
                AppInput(text: $password, placeholder: "Пароль", isSecure: true, error: errors["password"] != nil)
                Text("Пароль должен содержать от 8 символов")
                    .font(.system(size: Theme.fontSm))
                    .foregroundStyle(errors["password"] != nil ? Theme.error : Theme.textSecondary)
            }
            field(error: errors["passwordConfirm"]) {
                AppInput(text: $passwordConfirm, placeholder: "Подтвердите пароль", isSecure: true,
                         error: errors["passwordConfirm"] != nil)
            }
        }
    }

    @ViewBuilder private func field<Content: View>(error: String?, @ViewBuilder _ content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            content()
            if let error = error { Text(error).font(.system(size: Theme.fontSm)).foregroundStyle(Theme.error) }
        }
    }

    private var authGradient: some View {
        ZStack {
            Theme.bg
            LinearGradient(colors: [Color(hex: "#1a1b1d"), Color(hex: "#1e1f21"), Color(hex: "#161719")],
                           startPoint: .top, endPoint: .bottom)
            RadialGradient(colors: [Color(hex: "#78285a").opacity(0.35), .clear],
                           center: .init(x: 0.85, y: 0.06), startRadius: 0, endRadius: 320)
        }
        .ignoresSafeArea()
    }

    private func matches(_ value: String, _ pattern: String) -> Bool {
        value.range(of: pattern, options: .regularExpression) != nil
    }

    private func submit() {
        let ok = mode == .login ? validateLogin() : validateRegister()
        if !ok { Haptics.rigid(); return }
        Haptics.medium()
        submitting = true
        Task {
            do {
                if mode == .login {
                    let user = try await Api.loginWithPassword(identifier: identifier.trimmed, password: password)
                    session.setCurrent(user)
                    router.enterApp()
                } else {
                    let e = email.trimmed
                    try await Api.register(email: e, phone: phone.trimmed, password: password)
                    router.authPath.append(.registerConfirm(email: e))
                }
            } catch let err as ApiError {
                Haptics.rigid()
                var mapped: [String: String] = [:]
                for (k, v) in err.errors { mapped[k] = v.first }
                errors = mapped.isEmpty ? ["identifier": err.message, "email": err.message] : mapped
            } catch {
                errors = ["identifier": "Не удалось выполнить запрос"]
            }
            submitting = false
        }
    }

    private func validateLogin() -> Bool {
        var e: [String: String] = [:]
        let id = identifier.trimmed
        if id.isEmpty { e["identifier"] = "Введите email или номер телефона" }
        else if !matches(id, emailRE) && !matches(id, phoneRE) { e["identifier"] = "Неверный формат email или телефона" }
        if password.isEmpty { e["password"] = "Введите пароль" }
        errors = e
        return e.isEmpty
    }

    private func validateRegister() -> Bool {
        var e: [String: String] = [:]
        if !matches(email.trimmed, emailRE) { e["email"] = "Неверный формат email" }
        if !matches(phone.trimmed, phoneRE) { e["phone"] = "Неверный формат телефона" }
        if password.count < 8 { e["password"] = "Пароль должен содержать от 8 символов" }
        if passwordConfirm != password { e["passwordConfirm"] = "Пароль не совпадает" }
        errors = e
        return e.isEmpty
    }
}

extension String {
    var trimmed: String { trimmingCharacters(in: .whitespacesAndNewlines) }
}
