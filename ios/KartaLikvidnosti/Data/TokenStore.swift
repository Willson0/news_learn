import Foundation

/// Хранилище токена Sanctum (аналог localStorage['kl_token'] в вебе).
enum TokenStore {
    private static let key = "kl_token"

    static var token: String? {
        get { UserDefaults.standard.string(forKey: key) }
        set {
            if let value = newValue, !value.isEmpty {
                UserDefaults.standard.set(value, forKey: key)
            } else {
                UserDefaults.standard.removeObject(forKey: key)
            }
        }
    }

    static var isAuthenticated: Bool { !(token ?? "").isEmpty }
}
