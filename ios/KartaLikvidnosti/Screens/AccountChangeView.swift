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

    var body: some View {
        VStack(spacing: 0) {
            Spacer().frame(height: 100)
            VStack(spacing: 16) {
                Text(config.title).font(.system(size: Theme.fontTitle, weight: .bold)).foregroundStyle(Theme.text)
                VStack(spacing: 12) {
                    ForEach(config.fields, id: \.key) { f in
                        AppInput(text: binding(f.key), placeholder: f.placeholder, isSecure: f.secure, keyboard: f.keyboard)
                    }
                }
                if !error.isEmpty { Text(error).font(.system(size: Theme.fontSm)).foregroundStyle(Theme.error) }
            }
            Spacer()
            AppButton(title: config.button, variant: .accent) { submit() }
        }
        .padding(.horizontal, Theme.screenX).padding(.top, 16).padding(.bottom, 24)
        .screenBackground(Theme.bg)
    }

    private func binding(_ key: String) -> Binding<String> {
        Binding(get: { values[key] ?? "" }, set: { values[key] = $0 })
    }

    private func submit() {
        error = ""
        Task {
            do {
                switch field {
                case "email": try await Api.changeEmail((values["new"] ?? "").trimmed)
                case "phone": try await Api.changePhone((values["new"] ?? "").trimmed)
                default: try await Api.changePassword(values["new"] ?? "", values["confirm"] ?? "", values["old"])
                }
                router.push(.accountSuccess(field: field))
            } catch let e as ApiError { error = e.message }
            catch { error = "Не удалось сохранить изменения" }
        }
    }
}
