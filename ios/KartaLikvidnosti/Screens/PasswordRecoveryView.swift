import SwiftUI

struct PasswordRecoveryView: View {
    @EnvironmentObject var router: Router
    @EnvironmentObject var session: Session

    @State private var email = ""
    @State private var code = ""
    @State private var newPassword = ""
    @State private var errors: [String: String] = [:]
    @State private var codeSent = false
    @State private var secondsLeft = 0
    @State private var timer: Timer?

    private let emailRE = #"^[^\s@]+@[^\s@]+\.[^\s@]+$"#
    private let resendSeconds = 5 * 60

    @State private var done = false

    private var canResend: Bool { secondsLeft <= 0 }
    private var resendLabel: String {
        if canResend { return codeSent ? "Отправить код повторно" : "Отправить код" }
        return String(format: "Повторное сообщение через %d:%02d", secondsLeft / 60, secondsLeft % 60)
    }

    // «Восстановление пароля» (Figma 349:10487 … 349:10715): почта и новый
    // пароль; «Далее» отправляет письмо с кодом и запускает таймер повторной
    // отправки 5:00, после чего кнопка становится «Сменить пароль». Бэкенд
    // подтверждает смену кодом из письма, поэтому после отправки появляется
    // поле кода. Успех — «Вы успешно сменили пароль».
    var body: some View {
        if done {
            AuthMessageView(text: "Вы успешно сменили пароль", showBack: true)
                .task {
                    try? await Task.sleep(nanoseconds: 1_500_000_000)
                    router.enterApp()
                }
        } else {
            AuthScaffold(title: "Восстановление пароля",
                         subtitle: "Временный пароль придет на указанную почту, позднее пароль можно сменить в профиле") {
                VStack(spacing: 12) {
                    AuthField(error: errors["email"]) {
                        AppInput(text: $email, placeholder: "Email", error: errors["email"] != nil,
                                 keyboard: .emailAddress, textContentType: .emailAddress)
                    }
                    AuthField(error: errors["newPassword"]) {
                        AppInput(text: $newPassword, placeholder: "Новый пароль", isSecure: true,
                                 error: errors["newPassword"] != nil, textContentType: .newPassword)
                    }
                    if codeSent {
                        AuthField(error: errors["code"]) {
                            AppInput(text: $code, placeholder: "Код из письма", error: errors["code"] != nil,
                                     keyboard: .numberPad, textContentType: .oneTimeCode)
                                .onChange(of: code) { v in
                                    let digits = String(v.filter(\.isNumber).prefix(6))
                                    if digits != v { code = digits }
                                    if errors["code"] != nil { errors["code"] = nil }
                                }
                        }
                        .transition(.opacity.combined(with: .move(edge: .top)))
                    }
                }
            } buttons: {
                AppButton(title: resendLabel, variant: .secondary, disabled: !canResend) { resend() }
                AppButton(title: codeSent ? "Сменить пароль" : "Далее", variant: .accent) {
                    codeSent ? submit() : next()
                }
            }
            .animation(.easeOut(duration: 0.2), value: codeSent)
            .animation(.easeOut(duration: 0.2), value: errors)
            .onDisappear { timer?.invalidate() }
        }
    }

    /// Первый шаг: проверяем поля и отправляем письмо с кодом.
    private func next() {
        var e: [String: String] = [:]
        if !matches(email.trimmed) { e["email"] = "Несуществующий email" }
        if newPassword.count < 8 { e["newPassword"] = "Пароль должен содержать от 8 символов" }
        errors = e
        if !e.isEmpty { Haptics.rigid(); return }
        resend()
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

    private func resend() {
        guard canResend else { return }
        if !matches(email.trimmed) { errors = ["email": "Введите email, чтобы отправить код"]; Haptics.rigid(); return }
        Haptics.light()
        Task { @MainActor in
            do {
                try await Api.requestRecovery(email: email.trimmed)
                codeSent = true
                startTimer()
            } catch let err as ApiError {
                errors = ["email": err.message]; Haptics.rigid()
            } catch {
                errors = ["email": "Не удалось отправить код"]; Haptics.rigid()
            }
        }
    }

    private func submit() {
        var e: [String: String] = [:]
        if !matches(email.trimmed) { e["email"] = "Несуществующий email" }
        if code.count < 6 { e["code"] = "Введите код из 6 цифр из письма" }
        if newPassword.count < 8 { e["newPassword"] = "Пароль должен содержать от 8 символов" }
        errors = e
        if !e.isEmpty { Haptics.rigid(); return }
        Haptics.medium()
        Task { @MainActor in
            do {
                let user = try await Api.resetPassword(email: email.trimmed, password: newPassword, code: code)
                session.setCurrent(user)
                Haptics.medium()
                done = true
            } catch let err as ApiError {
                Haptics.rigid()
                if !err.errors.isEmpty {
                    var mapped: [String: String] = [:]
                    for (k, v) in err.errors {
                        let key = k == "password" ? "newPassword" : k
                        mapped[key] = v.first ?? err.message
                    }
                    errors = mapped.isEmpty ? ["email": err.message] : mapped
                } else {
                    errors = ["email": err.message]
                }
            } catch {
                errors = ["email": "Не удалось сменить пароль"]
            }
        }
    }
}
