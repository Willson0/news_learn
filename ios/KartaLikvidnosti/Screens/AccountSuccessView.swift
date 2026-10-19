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

    // «успешно» (Figma 363:9535 …): Kreadon 24 по центру экрана.
    var body: some View {
        AuthMessageView(text: message)
    }
}
