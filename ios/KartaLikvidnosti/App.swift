import SwiftUI
import UIKit

@main
struct KartaLikvidnostiApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate
    @StateObject private var session = Session()
    @StateObject private var router = Router()
    @StateObject private var appData = AppData()
    @Environment(\.scenePhase) private var scenePhase

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
                // Подписка могла закончиться (или продлиться), пока приложение было в фоне.
                .onChange(of: scenePhase) { phase in
                    if phase == .active { Task { await session.loadSubscription() } }
                }
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
        // Системный кружок обновления прячем: вместо него стеклянный (PullToRefresh.swift).
        UIRefreshControl.appearance().tintColor = .clear
    }
}
