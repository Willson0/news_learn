import UIKit
import UserNotifications
import FirebaseMessaging

/// Push-уведомления (FCM). Токен регистрируется на бэкенде после входа и отзывается при выходе.
/// Без GoogleService-Info.plist Firebase не запускается, и push тихо выключен.
enum Push {
    static var isFirebaseConfigured: Bool {
        Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist") != nil
    }

    /// Просим разрешение на уведомления и регистрируемся в APNs. Вызывается после входа.
    static func activate() {
        guard isFirebaseConfigured else { return }
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { granted, _ in
            guard granted else { return }
            DispatchQueue.main.async { UIApplication.shared.registerForRemoteNotifications() }
            syncToken()
        }
    }

    /// Отправляет текущий FCM-токен на бэкенд, если пользователь вошёл.
    static func syncToken() {
        guard isFirebaseConfigured, TokenStore.isAuthenticated else { return }
        Messaging.messaging().token { token, _ in
            guard let token else { return }
            register(token)
        }
    }

    static func register(_ token: String) {
        guard TokenStore.isAuthenticated else { return }
        Task { try? await Api.registerDevice(token) }
    }

    /// Отвязываем устройство перед выходом, пока токен входа ещё действует.
    static func unregisterCurrent() async {
        guard isFirebaseConfigured, TokenStore.isAuthenticated else { return }
        let token: String? = await withCheckedContinuation { continuation in
            Messaging.messaging().token { token, _ in continuation.resume(returning: token) }
        }
        if let token { try? await Api.unregisterDevice(token) }
    }
}
