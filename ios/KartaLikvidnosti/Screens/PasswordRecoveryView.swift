import SwiftUI

struct PasswordRecoveryView: View {
    @EnvironmentObject var router: Router
    @EnvironmentObject var session: Session

    @State private var email = ""
    @State private var newPassword = ""
    @State private var errors: [String: String] = [:]
    @State private var secondsLeft = 5 * 60
    @State private var timer: Timer?

    private let emailRE = #"^[^\s@]+@[^\s@]+\.[^\s@]+$"#

    private var canResend: Bool { secondsLeft <= 0 }
    private var resendLabel: String {
        if canResend { return "Отправить повторно" }
        return String(format: "Повторное сообщение через %d:%02d", secondsLeft / 60, secondsLeft % 60)
    }

    var body: some View {
        VStack(spacing: 0) {
            Spacer().frame(height: 76)
            Text("Восстановление пароля").font(Theme.heading(Theme.fontTitle)).foregroundStyle(Theme.text)
                .padding(.bottom, 12)
            Text("Временный пароль придёт на указанную почту, позднее пароль можно сменить в профиле")
                .font(.system(size: Theme.fontSm)).foregroundStyle(Theme.textSecondary)
                .multilineTextAlignment(.center).lineSpacing(3).frame(maxWidth: 300)
                .padding(.bottom, 28)

            VStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 6) {
                    AppInput(text: $email, placeholder: "Email", error: errors["email"] != nil,
                             keyboard: .emailAddress, textContentType: .emailAddress)
                    if let e = errors["email"] { Text(e).font(.system(size: Theme.fontSm)).foregroundStyle(Theme.error) }
                }
                VStack(alignment: .leading, spacing: 6) {
                    AppInput(text: $newPassword, placeholder: "Новый пароль", isSecure: true, error: errors["newPassword"] != nil)
                    if let e = errors["newPassword"] { Text(e).font(.system(size: Theme.fontSm)).foregroundStyle(Theme.error) }
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
        .onAppear { startTimer() }
        .onDisappear { timer?.invalidate() }
    }

    private func matches(_ v: String) -> Bool { v.range(of: emailRE, options: .regularExpression) != nil }

    private func startTimer() {
        timer?.invalidate()
        timer = Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { _ in
            if secondsLeft > 0 { secondsLeft -= 1 } else { timer?.invalidate() }
        }
    }

    private func resend() {
        if !matches(email.trimmed) { errors = ["email": "Введите email, чтобы отправить код"]; Haptics.rigid(); return }
        Haptics.light()
        Task { try? await Api.requestRecovery(email: email.trimmed) }
        secondsLeft = 5 * 60
        startTimer()
    }

    private func submit() {
        var e: [String: String] = [:]
        if !matches(email.trimmed) { e["email"] = "Неверный формат email" }
        if newPassword.count < 8 { e["newPassword"] = "Пароль должен содержать от 8 символов" }
        errors = e
        if !e.isEmpty { Haptics.rigid(); return }
        Haptics.medium()
        Task {
            do {
                try await Api.requestRecovery(email: email.trimmed)
                let user = try await Api.resetPassword(email: email.trimmed, password: newPassword)
                session.setCurrent(user)
                router.enterApp()
            } catch let err as ApiError {
                Haptics.rigid(); errors = ["email": err.message]
            } catch { errors = ["email": "Не удалось сменить пароль"] }
        }
    }
}
