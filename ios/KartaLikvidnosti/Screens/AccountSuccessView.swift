import SwiftUI

struct AccountSuccessView: View {
    let field: String

    private var message: String {
        switch field {
        case "email": return "Вы успешно сменили почту"
        case "phone": return "Вы успешно сменили номер"
        case "password": return "Вы успешно сменили пароль"
        default: return "Готово"
        }
    }

    var body: some View {
        Text(message)
            .font(.system(size: Theme.fontLg)).foregroundStyle(Theme.text)
            .multilineTextAlignment(.center)
            .padding(Theme.screenX)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .screenBackground(Theme.bg)
    }
}
