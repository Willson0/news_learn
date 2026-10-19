import SwiftUI

struct AccountChangeView: View {
    @EnvironmentObject var router: Router
    let field: String

    @State private var values: [String: String] = ["old": "", "new": "", "confirm": ""]
    @State private var error = ""

    private struct FieldDef { let key: String; let placeholder: String; let secure: Bool; let keyboard: UIKeyboardType }
    private struct Config { let title: String; let fields: [FieldDef]; let button: String }

    private var config: Config {
        switch field {
        case "phone":
            return Config(title: "Смена номера телефона", fields: [
                FieldDef(key: "old", placeholder: "Прежний номер телефона", secure: false, keyboard: .phonePad),
                FieldDef(key: "new", placeholder: "Новый номер телефона", secure: false, keyboard: .phonePad),
            ], button: "Сменить номер")
        case "password":
            return Config(title: "Смена пароля", fields: [
                FieldDef(key: "new", placeholder: "Новый пароль", secure: true, keyboard: .default),
                FieldDef(key: "confirm", placeholder: "Подтвердите пароль", secure: true, keyboard: .default),
            ], button: "Сменить пароль")
        default:
            return Config(title: "Смена почты", fields: [
                FieldDef(key: "old", placeholder: "Прежняя почта", secure: false, keyboard: .emailAddress),
                FieldDef(key: "new", placeholder: "Новая почта", secure: false, keyboard: .emailAddress),
            ], button: "Сменить почту")
        }
    }

    // «Смена пароля / номера / почты» (Figma 363:9417, 363:9499, 363:9459):
    // заголовок Kreadon 24 по центру на y=278, поля 48 через 12 с y=331,
    // внизу кнопка (низ — 45 pt).
    var body: some View {
        AuthScaffold(title: config.title, showBack: false, contentTop: 278) {
            VStack(spacing: 12) {
                ForEach(config.fields, id: \.key) { f in
                    AppInput(text: binding(f.key), placeholder: f.placeholder, isSecure: f.secure,
                             error: !error.isEmpty, keyboard: f.keyboard)
                }
                if !error.isEmpty { AuthFieldError(text: error) }
            }
        } buttons: {
            AppButton(title: config.button, variant: .accent) { submit() }
        }
        .animation(.easeOut(duration: 0.2), value: error)
    }

    private func binding(_ key: String) -> Binding<String> {
        Binding(get: { values[key] ?? "" },
                set: { values[key] = field == "phone" ? PhoneMask.format($0) : $0; error = "" })
    }

    private func submit() {
        error = ""
        if field == "password" {
            if (values["new"] ?? "").count < 8 { error = "Пароль должен содержать от 8 символов"; Haptics.rigid(); return }
            if values["new"] != values["confirm"] { error = "Пароль не совпадает"; Haptics.rigid(); return }
        }
        Task {
            do {
                switch field {
                case "email": try await Api.changeEmail((values["new"] ?? "").trimmed)
                case "phone": try await Api.changePhone((values["new"] ?? "").trimmed)
                default: try await Api.changePassword(values["new"] ?? "", values["confirm"] ?? "", values["old"])
                }
                Haptics.medium()
                router.push(.accountSuccess(field: field))
            } catch let e as ApiError { self.error = e.message }
            catch { self.error = "Не удалось сохранить изменения" }
        }
    }
}
