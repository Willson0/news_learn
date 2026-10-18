import SwiftUI

@main
struct KartaLikvidnostiApp: App {
    @StateObject private var session = Session()
    @StateObject private var router = Router()
    @StateObject private var appData = AppData()

    init() {
        Self.configureAppearance()
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(session)
                .environmentObject(router)
                .environmentObject(appData)
                .preferredColorScheme(.dark)
                .tint(Theme.green)
        }
    }

    /// Навигационная панель в стиле «жидкое стекло»: родной системный блюр
    /// (как Liquid Glass в iOS 26), тёмная тема, без разделителя.
    private static func configureAppearance() {
        let appearance = UINavigationBarAppearance()
        appearance.configureWithDefaultBackground()
        appearance.backgroundEffect = UIBlurEffect(style: .systemUltraThinMaterialDark)
        appearance.backgroundColor = UIColor(Theme.bg).withAlphaComponent(0.3)
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
