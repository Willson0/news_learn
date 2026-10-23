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

    // «Вход/регистрация» (Figma 337:26653, регистрация 337:26667, ошибки
    // 349:9124/9982/9056…): «16+» Kreadon 24 белый 40 % справа на y=71, круг-логотип
    // 100 (#25262A) на y=94, заголовок Kreadon 24 на y=234 — активная часть белая,
    // вторая белый 40 %; поля 48 через 12 с y=287; ошибки 12 pt красным 70 %
    // в 4 pt под полем; внизу «Войти с Yandex» и «Войти»/«Далее» (низ — 45 pt).
    var body: some View {
        ZStack(alignment: .top) {
            ScrollView(showsIndicators: false) {
                VStack(spacing: 0) {
                    Circle()
                        .fill(Theme.surface)
                        .frame(width: 100, height: 100)
                        .padding(.top, 94)

                    title
                        .padding(.top, 40)

                    Group {
                        if mode == .login { loginFields } else { registerFields }
                    }
                    .padding(.top, 24)
                }
                .padding(.horizontal, Theme.screenX)
                .padding(.bottom, 24)
            }
            .scrollDismissesKeyboard(.interactively)

            HStack {
                Spacer()
                Text("16+")
                    .font(Theme.heading(Theme.fontTitle))
                    .foregroundStyle(Theme.textMuted)
            }
            .padding(.horizontal, 18)
            .padding(.top, 71)
            .allowsHitTesting(false)
        }
        .safeAreaInset(edge: .bottom, spacing: 0) {
            VStack(spacing: 12) {
                AppButton(title: "Войти с Yandex", variant: .secondary) { signInWithYandex() }
                AppButton(title: mode == .login ? "Войти" : "Далее", variant: .accent, loading: submitting) {
                    submit()
                }
            }
            .padding(.horizontal, Theme.screenX)
            .padding(.top, 12)
            .padding(.bottom, 45)
        }
        .ignoresSafeArea(.container, edges: [.top, .bottom])
        .background(ScreenBackdrop())
        .animation(.spring(response: 0.32, dampingFraction: 0.86), value: mode)
        .animation(.easeOut(duration: 0.2), value: errors)
        #if DEBUG
        .onAppear {
            // `-kl_auth signup | loginErrors | signupErrors` — состояния для сверки с макетом.
            switch UserDefaults.standard.string(forKey: "kl_auth") {
            case "signup": mode = .register
            case "loginErrors": identifier = "BIBI@JKIJI.RU"; password = "1234567"; _ = validateLogin()
                errors["identifier"] = "Неверный email или номер телефона"; errors["password"] = "Неверный пароль"
            case "signupErrors": mode = .register; email = "bibi@"; phone = "+7 (999"; password = "123"
                passwordConfirm = "1"; _ = validateRegister()
            default: break
            }
        }
        #endif
    }

    /// «Вход / Регистрация»: косая черта принадлежит активной части.
    private var title: some View {
        HStack(spacing: 0) {
            Button { switchMode(.login) } label: {
                Text(mode == .login ? "Вход / " : "Вход ")
                    .foregroundStyle(mode == .login ? Theme.text : Theme.textMuted)
            }
            .buttonStyle(.plain)
            Button { switchMode(.register) } label: {
                Text(mode == .register ? "/ Регистрация" : "Регистрация")
                    .foregroundStyle(mode == .register ? Theme.text : Theme.textMuted)
            }
            .buttonStyle(.plain)
        }
        .font(Theme.heading(Theme.fontTitle))
    }

    private func switchMode(_ m: Mode) {
        guard mode != m else { return }
        Haptics.light()
        mode = m
        errors = [:]
    }

    @ViewBuilder private var loginFields: some View {
        VStack(spacing: 12) {
            field(error: errors["identifier"]) {
                AppInput(text: $identifier, placeholder: "Email или номер телефона",
                         error: errors["identifier"] != nil, keyboard: .emailAddress, textContentType: .username)
            }
            VStack(alignment: .leading, spacing: 4) {
                AppInput(text: $password, placeholder: "Пароль", isSecure: true,
                         error: errors["password"] != nil, textContentType: .password)
                HStack(alignment: .top) {
                    if let e = errors["password"] { errorText(e) }
                    Spacer()
                    Button("Забыли пароль?") { router.authPath.append(.passwordRecovery) }
                        .font(Theme.sans(Theme.fontSm))
                        .foregroundStyle(Color.white.opacity(0.6))
                        .padding(.top, 8)
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
                    .onChange(of: phone) { v in
                        let f = PhoneMask.format(v)
                        if f != v { phone = f }
                    }
            }
            VStack(alignment: .leading, spacing: 4) {
                AppInput(text: $password, placeholder: "Пароль", isSecure: true, error: errors["password"] != nil,
                         textContentType: .newPassword)
                Text(errors["password"] ?? "Пароль должен содержать от 8 символов")
                    .font(Theme.sans(Theme.fontSm))
                    .foregroundStyle(errors["password"] != nil ? Theme.error.opacity(0.7) : Theme.text)
            }
            field(error: errors["passwordConfirm"]) {
                AppInput(text: $passwordConfirm, placeholder: "Подтвердите пароль", isSecure: true,
                         error: errors["passwordConfirm"] != nil, textContentType: .newPassword)
            }
        }
    }

    @ViewBuilder private func field<Content: View>(error: String?, @ViewBuilder _ content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            content()
            if let error = error { errorText(error) }
        }
    }

    private func errorText(_ text: String) -> some View {
        Text(text)
            .font(Theme.sans(Theme.fontSm))
            .foregroundStyle(Theme.error.opacity(0.7))
            .transition(.opacity)
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

    private func signInWithYandex() {
        Task {
            do {
                TokenStore.token = try await YandexSignIn.token()
                await session.load(force: true)
                if session.user != nil {
                    router.enterApp()
                } else {
                    TokenStore.token = nil
                    errors = ["identifier": "Не удалось загрузить профиль"]
                }
            } catch let err as ApiError {
                Haptics.rigid()
                errors = ["identifier": err.message]
            } catch {
                errors = ["identifier": "Не удалось войти через Яндекс"]
            }
        }
    }

    private func validateLogin() -> Bool {
        var e: [String: String] = [:]
        let id = identifier.trimmed
        if id.isEmpty { e["identifier"] = "Введите email или номер телефона" }
        else if !matches(id, emailRE) && !matches(id, phoneRE) { e["identifier"] = "Неверный email или номер телефона" }
        if password.isEmpty { e["password"] = "Введите пароль" }
        errors = e
        return e.isEmpty
    }

    private func validateRegister() -> Bool {
        var e: [String: String] = [:]
        if !matches(email.trimmed, emailRE) { e["email"] = "Несуществующий email" }
        if PhoneMask.digits(phone).count != 11 { e["phone"] = "Неверный номер телефона" }
        if password.count < 8 { e["password"] = "Пароль должен содержать от 8 символов" }
        if passwordConfirm != password { e["passwordConfirm"] = "Пароль не совпадает" }
        errors = e
        return e.isEmpty
    }
}

extension String {
    var trimmed: String { trimmingCharacters(in: .whitespacesAndNewlines) }
}

/// Маска российского номера из макета: «+7 (999) 999-99-99».
enum PhoneMask {
    static func digits(_ s: String) -> String {
        var d = s.filter(\.isNumber)
        if d.first == "8" { d = "7" + d.dropFirst() }
        if !d.isEmpty && d.first != "7" { d = "7" + d }
        return String(d.prefix(11))
    }

    static func format(_ s: String) -> String {
        let d = Array(digits(s))
        guard !d.isEmpty else { return "" }
        var out = "+7"
        let rest = d.dropFirst()
        for (i, ch) in rest.enumerated() {
            switch i {
            case 0: out += " (" + String(ch)
            case 3: out += ") " + String(ch)
            case 6, 8: out += "-" + String(ch)
            default: out.append(ch)
            }
        }
        return out
    }
}
