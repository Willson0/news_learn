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

    private var canResend: Bool { secondsLeft <= 0 }
    private var resendLabel: String {
        if canResend { return codeSent ? "Отправить код повторно" : "Отправить код" }
        return String(format: "Повторное сообщение через %d:%02d", secondsLeft / 60, secondsLeft % 60)
    }

    var body: some View {
        VStack(spacing: 0) {
            Spacer().frame(height: 76)
            Text("Восстановление пароля").font(Theme.heading(Theme.fontTitle)).foregroundStyle(Theme.text)
                .padding(.bottom, 12)
            Text("Отправим код на указанную почту. Введите его вместе с новым паролем — позднее пароль можно сменить в профиле.")
                .font(Theme.sans(Theme.fontSm)).foregroundStyle(Theme.textSecondary)
                .multilineTextAlignment(.center).lineSpacing(3).frame(maxWidth: 300)
                .padding(.bottom, 28)

            VStack(spacing: 12) {
                field(error: errors["email"]) {
                    AppInput(text: $email, placeholder: "Email", error: errors["email"] != nil,
                             keyboard: .emailAddress, textContentType: .emailAddress)
                }
                field(error: errors["code"]) {
                    AppInput(text: $code, placeholder: "Код из письма", error: errors["code"] != nil,
                             keyboard: .numberPad, textContentType: .oneTimeCode)
                        .onChange(of: code) { v in
                            let digits = v.filter(\.isNumber)
                            code = String(digits.prefix(6))
                            if errors["code"] != nil { errors["code"] = nil }
                        }
                }
                field(error: errors["newPassword"]) {
                    AppInput(text: $newPassword, placeholder: "Новый пароль", isSecure: true, error: errors["newPassword"] != nil)
                }
            }

            Spacer()

            VStack(spacing: 12) {
                AppButton(title: resendLabel, variant: .secondary, disabled: !canResend) { resend() }
                AppButton(title: "Далее", variant: .accent) { submit() }
            }
            .padding(.top, 24)
        }
        .padding(.horizontal, Theme.screenX)
        .padding(.top, 24)
        .padding(.bottom, 24)
        .screenBackground(
            LinearGradient(colors: [Color(hex: "#1a1b1d"), Color(hex: "#1e1f21"), Color(hex: "#161719")],
                           startPoint: .top, endPoint: .bottom)
        )
        .onDisappear { timer?.invalidate() }
    }

    private func field<Content: View>(error: String?, @ViewBuilder _ content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            content()
            if let e = error { Text(e).font(Theme.sans(Theme.fontSm)).foregroundStyle(Theme.error) }
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
        if !matches(email.trimmed) { e["email"] = "Неверный формат email" }
        if code.count < 6 { e["code"] = "Введите код из 6 цифр из письма" }
        if newPassword.count < 8 { e["newPassword"] = "Пароль должен содержать от 8 символов" }
        errors = e
        if !e.isEmpty { Haptics.rigid(); return }
        Haptics.medium()
        Task { @MainActor in
            do {
                let user = try await Api.resetPassword(email: email.trimmed, password: newPassword, code: code)
                session.setCurrent(user)
                router.enterApp()
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
