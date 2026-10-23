import UIKit
import SafariServices

/// Открывает ссылку внутри приложения (SFSafariViewController), без выхода в Safari.
enum InAppBrowser {
    static func open(_ url: URL) {
        DispatchQueue.main.async {
            let scene = UIApplication.shared.connectedScenes
                .compactMap { $0 as? UIWindowScene }
                .first { $0.activationState == .foregroundActive }
            guard var top = scene?.windows.first(where: { $0.isKeyWindow })?.rootViewController else { return }
            while let presented = top.presentedViewController { top = presented }
            top.present(SFSafariViewController(url: url), animated: true)
        }
    }
}
