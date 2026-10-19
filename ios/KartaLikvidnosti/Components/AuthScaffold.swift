import SwiftUI

/// Общий каркас экранов авторизации из макета («Восстановление пароля»,
/// «Регистрация» шаг 2, подтверждение почты): стеклянная «назад» на y=78,
/// заголовок Kreadon 24 по центру на y=234, подзаголовок 12 pt белый 60 %,
/// поля с отступом 24 под текстом; кнопки прижаты к низу (45 pt).
struct AuthScaffold<Content: View, Buttons: View>: View {
    var title: String? = nil
    var subtitle: String? = nil
    var showBack: Bool = true
    var contentTop: CGFloat = 234
    @ViewBuilder var content: () -> Content
    @ViewBuilder var buttons: () -> Buttons

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(spacing: 0) {
                if let title {
                    Text(title)
                        .font(Theme.heading(Theme.fontTitle))
                        .foregroundStyle(Theme.text)
                        .multilineTextAlignment(.center)
                }
                if let subtitle {
                    Text(subtitle)
                        .font(Theme.sans(Theme.fontSm))
                        .foregroundStyle(Color.white.opacity(0.6))
                        .multilineTextAlignment(.center)
                        .frame(maxWidth: 300)
                        .padding(.top, 8)
                }
                content()
                    .padding(.top, title == nil ? 0 : 24)
            }
            .frame(maxWidth: .infinity)
            .padding(.horizontal, Theme.screenX)
            .padding(.top, contentTop)
            .padding(.bottom, 24)
        }
        .scrollDismissesKeyboard(.interactively)
        .safeAreaInset(edge: .bottom, spacing: 0) {
            VStack(spacing: 12) { buttons() }
                .padding(.horizontal, Theme.screenX)
                .padding(.top, 12)
                .padding(.bottom, 45)
        }
        .overlay(alignment: .topLeading) {
            if showBack {
                GlassBackButton()
                    .padding(.leading, Theme.screenX)
                    .padding(.top, 78)
            }
        }
        .ignoresSafeArea(.container, edges: [.top, .bottom])
        .background(ScreenBackdrop())
        .toolbar(.hidden, for: .navigationBar)
    }
}

/// Текст ошибки под полем: 12 pt, красный 70 %, отступ 4 (349:9982).
struct AuthFieldError: View {
    let text: String
    var body: some View {
        Text(text)
            .font(Theme.sans(Theme.fontSm))
            .foregroundStyle(Theme.error.opacity(0.7))
            .frame(maxWidth: .infinity, alignment: .leading)
            .transition(.opacity)
    }
}

/// Поле с ошибкой под ним.
struct AuthField<Content: View>: View {
    let error: String?
    @ViewBuilder var content: () -> Content
    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            content()
            if let error { AuthFieldError(text: error) }
        }
    }
}

/// Экран-сообщение по центру («Приветствуем, …!», «Вы успешно сменили пароль»).
struct AuthMessageView: View {
    let text: String
    var showBack: Bool = false
    var body: some View {
        Text(text)
            .font(Theme.heading(Theme.fontTitle))
            .foregroundStyle(Theme.text)
            .multilineTextAlignment(.center)
            .padding(.horizontal, Theme.screenX)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .overlay(alignment: .topLeading) {
                if showBack {
                    GlassBackButton()
                        .padding(.leading, Theme.screenX)
                        .padding(.top, 78)
                }
            }
            .ignoresSafeArea()
            .background(ScreenBackdrop())
            .toolbar(.hidden, for: .navigationBar)
    }
}
