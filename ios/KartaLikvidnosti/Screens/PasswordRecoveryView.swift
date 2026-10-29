import SwiftUI

struct PasswordRecoveryView: View {
    @EnvironmentObject var router: Router
    @EnvironmentObject var session: Session
    @Environment(\.dismiss) private var dismiss

    private enum Step { case email, code, password }

    @State private var step: Step = .email
    @State private var email = ""
    @State private var code = ""
    @State private var newPassword = ""
    @State private var errors: [String: String] = [:]
    @State private var busy = false
    @State private var secondsLeft = 0
    @State private var timer: Timer?
    @State private var done = false

    private let emailRE = #"^[^\s@]+@[^\s@]+\.[^\s@]+$"#
    private let resendSeconds = 5 * 60

    // «Восстановление пароля» — три шага по одному действию: почта → код из
    // письма (проверяется сразу на шестой цифре) → новый пароль. Таймер
    // повторной отправки — текстом под полем кода, по истечении — ссылка.
    // Успех — «Вы успешно сменили пароль».
    var body: some View {
        if done {
            AuthMessageView(text: "Вы успешно сменили пароль", showBack: true)
                .task {
                    try? await Task.sleep(nanoseconds: 1_500_000_000)
                    router.enterApp()
                }
        } else {
            AuthScaffold(title: "Восстановление пароля", subtitle: subtitle, showBack: false) {
                VStack(spacing: 12) {
                    switch step {
                    case .email: emailStep
                    case .code: codeStep
                    case .password: passwordStep
                    }
                }
            } buttons: {
                switch step {
                case .email: AppButton(title: "Получить код", variant: .accent, loading: busy) { sendCode() }
                case .code: AppButton(title: "Далее", variant: .accent, loading: busy) { checkCode() }
                case .password: AppButton(title: "Сменить пароль", variant: .accent, loading: busy) { submit() }
                }
            }
            // Своя «назад»: на шагах 2–3 возвращает на предыдущий шаг, а не закрывает экран.
            .overlay(alignment: .topLeading) {
                GlassBackButton { back() }
                    .padding(.leading, Theme.screenX)
                    .padding(.top, 78)
                    .ignoresSafeArea()
            }
            .animation(.easeOut(duration: 0.2), value: step)
            .animation(.easeOut(duration: 0.2), value: errors)
            .onDisappear { timer?.invalidate() }
        }
    }

    private var subtitle: String {
        switch step {
        case .email: return "Отправим код на почту, указанную при регистрации"
        case .code: return "Мы отправили код на \(email.trimmed). Введите его ниже"
        case .password: return "Придумайте новый пароль: не короче 8 символов"
        }
    }

    private var emailStep: some View {
        AuthField(error: errors["email"]) {
            AppInput(text: $email, placeholder: "Email", error: errors["email"] != nil,
                     keyboard: .emailAddress, textContentType: .emailAddress)
                .onChange(of: email) { _ in if !errors.isEmpty { errors = [:] } }
        }
    }

    @ViewBuilder private var codeStep: some View {
        AuthField(error: errors["code"]) {
            AppInput(text: $code, placeholder: "Код из письма", error: errors["code"] != nil,
                     keyboard: .numberPad, textContentType: .oneTimeCode)
                .onChange(of: code) { v in
                    let digits = String(v.filter(\.isNumber).prefix(6))
                    if digits != v { code = digits; return }
                    if !errors.isEmpty { errors = [:] }
                    // Шестая цифра — проверяем сразу, без кнопки.
                    if digits.count == 6 { checkCode() }
                }
        }
        Button { sendCode() } label: {
            Group {
                if secondsLeft > 0 {
                    Text(String(format: "Отправить повторно через %d:%02d", secondsLeft / 60, secondsLeft % 60))
                        .font(Theme.sans(Theme.fontBase))
                        .foregroundStyle(Theme.textSecondary)
                } else {
                    Text("Отправить код повторно")
                        .font(Theme.sans(Theme.fontBase, .semibold))
                        .foregroundStyle(Theme.green)
                }
            }
            .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(secondsLeft > 0 || busy)
    }

    @ViewBuilder private var passwordStep: some View {
        AuthField(error: errors["newPassword"]) {
            AppInput(text: $newPassword, placeholder: "Новый пароль", isSecure: true,
                     error: errors["newPassword"] != nil, textContentType: .newPassword)
                .onChange(of: newPassword) { _ in if !errors.isEmpty { errors = [:] } }
        }
        if errors["newPassword"] == nil {
            PasswordHint(password: newPassword).padding(.leading, 4)
        }
    }

    private func back() {
        errors = [:]
        switch step {
        case .email: dismiss()
        case .code: step = .email
        case .password: step = .code
        }
    }

    private func matches(_ v: String) -> Bool { v.range(of: emailRE, options: .regularExpression) != nil }

    private func startTimer() {
        timer?.invalidate()
        secondsLeft = resendSeconds
        timer = Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { _ in
            Task { @MainActor in
                if secondsLeft > 0 { secondsLeft -= 1 } else { timer?.invalidate() }
            }
        }
    }

    /// Шаг 1 и «Отправить код повторно»: письмо с кодом и таймер 5:00.
    private func sendCode() {
        if !matches(email.trimmed) {
            errors = ["email": "Проверьте адрес: например, name@mail.ru"]; Haptics.rigid(); return
        }
        guard !busy, secondsLeft <= 0 else { return }
        Haptics.light()
        busy = true
        Task { @MainActor in
            do {
                try await Api.requestRecovery(email: email.trimmed)
                code = ""
                errors = [:]
                startTimer()
                step = .code
            } catch let err as ApiError {
                errors = [step == .email ? "email" : "code": err.message]; Haptics.rigid()
            } catch {
                errors = [step == .email ? "email" : "code": "Не удалось отправить код"]; Haptics.rigid()
            }
            busy = false
        }
    }

    /// Шаг 2: код проверяется на сервере, не тратится — дальше новый пароль.
    private func checkCode() {
        if code.count < 6 { errors = ["code": "Введите код из 6 цифр из письма"]; Haptics.rigid(); return }
        guard !busy else { return }
        busy = true
        Task { @MainActor in
            do {
                try await Api.checkRecoveryCode(email: email.trimmed, code: code)
                errors = [:]
                step = .password
            } catch let err as ApiError {
                Haptics.rigid()
                errors = ["code": err.fieldError("code", "email") ?? err.message]
            } catch {
                errors = ["code": "Не удалось проверить код"]
            }
            busy = false
        }
    }

    /// Шаг 3: новый пароль.
    private func submit() {
        if newPassword.count < 8 {
            errors = ["newPassword": "Пароль должен содержать от 8 символов"]; Haptics.rigid(); return
        }
        guard !busy else { return }
        Haptics.medium()
        busy = true
        Task { @MainActor in
            do {
                let user = try await Api.resetPassword(email: email.trimmed, password: newPassword, code: code)
                await session.setCurrent(user)
                Haptics.medium()
                done = true
            } catch let err as ApiError {
                Haptics.rigid()
                if let codeError = err.fieldError("code") {
                    // Код успел истечь — возвращаемся к нему.
                    step = .code
                    errors = ["code": codeError]
                } else {
                    errors = ["newPassword": err.fieldError("password") ?? err.message]
                }
            } catch {
                errors = ["newPassword": "Не удалось сменить пароль"]
            }
            busy = false
        }
    }
}

/// Подсказка к паролю: вторичным цветом, при 8+ символах — зелёная с галочкой.
struct PasswordHint: View {
    let password: String
    var body: some View {
        let ok = password.count >= 8
        HStack(spacing: 4) {
            if ok { Icon(AppIcons.check, size: 14, color: Theme.green) }
            Text("Пароль должен содержать от 8 символов")
                .font(Theme.sans(Theme.fontSm))
                .foregroundStyle(ok ? Theme.green : Theme.textSecondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .combine)
        .accessibilityValue(ok ? "Выполнено" : "")
    }
}
