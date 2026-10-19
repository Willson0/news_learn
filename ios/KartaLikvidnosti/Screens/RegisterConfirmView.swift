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

    // Подтверждение почты — в макете отдельного кадра нет, экран собран
    // на каркасе «Восстановления пароля»: заголовок на y=234, поле кода,
    // внизу «Повторное сообщение через m:ss» и «Далее».
    var body: some View {
        AuthScaffold(title: "Подтверждение почты",
                     subtitle: "Мы отправили код подтверждения на \(maskedEmail). Введите его ниже") {
            AuthField(error: error.isEmpty ? nil : error) {
                AppInput(text: $code, placeholder: "Код из письма", error: !error.isEmpty,
                         keyboard: .numberPad, textContentType: .oneTimeCode)
                    .onChange(of: code) { newValue in
                        let digits = String(newValue.filter(\.isNumber).prefix(6))
                        if digits != newValue { code = digits }
                        error = ""
                    }
            }
        } buttons: {
            AppButton(title: resendIn > 0 ? String(format: "Повторное сообщение через %d:%02d", resendIn / 60, resendIn % 60)
                                          : "Отправить код повторно",
                      variant: .secondary, disabled: resendIn > 0) { resend() }
            AppButton(title: "Далее", variant: .accent, disabled: submitting) { confirm() }
        }
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
        if code.count < 6 { self.error = "Введите код из 6 цифр"; Haptics.rigid(); return }
        Haptics.medium()
        submitting = true
        Task {
            do {
                let user = try await Api.confirmRegistration(email: email, code: code)
                session.setCurrent(user)
                router.authPath.append(.registerProfile)
            } catch let e as ApiError {
                Haptics.rigid(); error = e.firstError
            } catch { self.error = "Не удалось подтвердить код" }
            submitting = false
        }
    }

    private func resend() {
        guard resendIn <= 0, !submitting else { return }
        Haptics.light()
        Task {
            do { try await Api.resendRegistrationCode(email: email); code = ""; error = ""; startCooldown() }
            catch let e as ApiError { self.error = e.firstError }
            catch { self.error = "Не удалось отправить код" }
        }
    }
}
