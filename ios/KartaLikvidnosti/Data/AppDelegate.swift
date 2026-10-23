import UIKit
import UserNotifications
import FirebaseCore
import FirebaseMessaging

/// Жизненный цикл: Firebase, APNs и показ push, пока приложение открыто.
final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate, MessagingDelegate {

    func application(_ application: UIApplication,
                     didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        if Push.isFirebaseConfigured {
            FirebaseApp.configure()
            Messaging.messaging().delegate = self
        }
        UNUserNotificationCenter.current().delegate = self
        return true
    }

    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        guard Push.isFirebaseConfigured else { return }
        Messaging.messaging().apnsToken = deviceToken
    }

    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        guard let fcmToken else { return }
        Push.register(fcmToken)
    }

    /// Показываем уведомление баннером, даже когда приложение открыто.
    func userNotificationCenter(_ center: UNUserNotificationCenter,
                                willPresent notification: UNNotification,
                                withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        completionHandler([.banner, .sound, .list])
    }

    /// Нажатие на push со ссылкой (например, счёт на оплату) открывает её внутри приложения.
    func userNotificationCenter(_ center: UNUserNotificationCenter,
                                didReceive response: UNNotificationResponse,
                                withCompletionHandler completionHandler: @escaping () -> Void) {
        let info = response.notification.request.content.userInfo
        if let link = info["url"] as? String, let url = URL(string: link) {
            InAppBrowser.open(url)
        }
        completionHandler()
    }
}
