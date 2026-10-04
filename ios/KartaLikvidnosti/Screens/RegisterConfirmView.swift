import SwiftUI

struct RegisterConfirmView: View {
    @EnvironmentObject var router: Router
    @EnvironmentObject var session: Session
    let email: String

    @State private var code = ""
    @State private var error = ""
    @State private var submitting = false
    @State private var resendIn = 60
    @State private var timer: Timer?

    private var maskedEmail: String {
        let parts = email.split(separator: "@", maxSplits: 1)
        guard parts.count == 2 else { return email }
        let name = String(parts[0])
        let head = name.count <= 2 ? name : String(name.prefix(2)) + "***"
        return "\(head)@\(parts[1])"
    }

    var body: some View {
        VStack(spacing: 0) {
            Circle().fill(Color.white.opacity(0.04))
                .overlay(Circle().stroke(Color.white.opacity(0.06), lineWidth: 1))
                .frame(width: 72, height: 72)
                .padding(.top, 8).padding(.bottom, 28)

            Text("Подтверждение почты").font(Theme.heading(Theme.fontTitle)).foregroundStyle(Theme.text)
                .padding(.bottom, 12)
            (Text("Мы отправили код подтверждения на ") + Text(maskedEmail).bold() + Text(". Введите его ниже."))
                .font(.system(size: Theme.fontSm)).foregroundStyle(Theme.textSecondary)
                .multilineTextAlignment(.center).lineSpacing(4)
                .padding(.bottom, 28)

            VStack(alignment: .leading, spacing: 6) {
                AppInput(text: $code, placeholder: "Код из письма", error: !error.isEmpty, keyboard: .numberPad)
                    .onChange(of: code) { newValue in
                        code = String(newValue.filter(\.isNumber).prefix(6))
                        error = ""
                    }
                if !error.isEmpty { Text(error).font(.system(size: Theme.fontSm)).foregroundStyle(Theme.error) }
            }

            Button {
                resend()
            } label: {
                Text(resendIn > 0 ? "Отправить код повторно (\(resendIn))" : "Отправить код повторно")
                    .font(.system(size: Theme.fontSm)).foregroundStyle(Theme.textSecondary)
            }
            .disabled(resendIn > 0)
            .opacity(resendIn > 0 ? 0.5 : 1)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.top, 16)

            Spacer()

            AppButton(title: "Подтвердить", variant: .accent, disabled: submitting) { confirm() }
        }
        .padding(.horizontal, Theme.screenX)
        .padding(.top, 40)
        .padding(.bottom, 24)
        .screenBackground(Theme.bg)
        .onAppear { startCooldown() }
        .onDisappear { timer?.invalidate() }
    }

    private func startCooldown() {
        resendIn = 60
        timer?.invalidate()
        timer = Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { _ in
            if resendIn > 0 { resendIn -= 1 } else { timer?.invalidate() }
        }
    }

    private func confirm() {
        if code.count < 6 { error = "Введите код из 6 цифр"; Haptics.rigid(); return }
        Haptics.medium()
        submitting = true
        Task {
            do {
                let user = try await Api.confirmRegistration(email: email, code: code)
                session.setCurrent(user)
                router.enterApp()
            } catch let e as ApiError {
                Haptics.rigid(); error = e.firstError
            } catch { error = "Не удалось подтвердить код" }
            submitting = false
        }
    }

    private func resend() {
        guard resendIn <= 0, !submitting else { return }
        Haptics.light()
        Task {
            do { try await Api.resendRegistrationCode(email: email); code = ""; error = ""; startCooldown() }
            catch let e as ApiError { error = e.firstError }
            catch { error = "Не удалось отправить код" }
        }
    }
}
