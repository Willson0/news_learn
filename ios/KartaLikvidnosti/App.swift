import SwiftUI

@main
struct KartaLikvidnostiApp: App {
    @StateObject private var session = Session()
    @StateObject private var router = Router()

    init() { Self.configureAppearance() }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(session)
                .environmentObject(router)
                .preferredColorScheme(.dark)
                .tint(Theme.green)
        }
    }

    /// Тёмная прозрачная навигационная панель (кастомный chrome макета).
    private static func configureAppearance() {
        let appearance = UINavigationBarAppearance()
        appearance.configureWithTransparentBackground()
        appearance.backgroundColor = UIColor(Theme.bg)
        appearance.shadowColor = .clear
        appearance.titleTextAttributes = [.foregroundColor: UIColor.white]
        let back = UIBarButtonItemAppearance()
        back.normal.titleTextAttributes = [.foregroundColor: UIColor.clear]
        appearance.backButtonAppearance = back
        UINavigationBar.appearance().standardAppearance = appearance
        UINavigationBar.appearance().scrollEdgeAppearance = appearance
        UINavigationBar.appearance().compactAppearance = appearance
        UINavigationBar.appearance().tintColor = .white
    }
}
