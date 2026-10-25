import Foundation
import Security

/// Хранилище токена Sanctum (аналог localStorage['kl_token'] в вебе).
/// Токен лежит в Keychain (только на этом устройстве, после первой разблокировки),
/// а не в UserDefaults: оттуда его можно достать из резервной копии.
enum TokenStore {
    private static let key = "kl_token"
    private static let service = (Bundle.main.bundleIdentifier ?? "app.karta") + ".auth"
    /// Значение в памяти: Keychain не читаем на каждый запрос.
    private static var cached: String?
    private static var loaded = false
    private static let lock = NSLock()

    static var token: String? {
        get {
            lock.lock(); defer { lock.unlock() }
            if !loaded {
                cached = read() ?? migrateFromDefaults()
                loaded = true
            }
            return cached
        }
        set {
            lock.lock(); defer { lock.unlock() }
            let value = (newValue ?? "").isEmpty ? nil : newValue
            cached = value
            loaded = true
            if let value { write(value) } else { delete() }
        }
    }

    static var isAuthenticated: Bool { !(token ?? "").isEmpty }

    private static var query: [String: Any] {
        [kSecClass as String: kSecClassGenericPassword,
         kSecAttrService as String: service,
         kSecAttrAccount as String: key]
    }

    private static func read() -> String? {
        var q = query
        q[kSecReturnData as String] = true
        q[kSecMatchLimit as String] = kSecMatchLimitOne
        var item: CFTypeRef?
        guard SecItemCopyMatching(q as CFDictionary, &item) == errSecSuccess,
              let data = item as? Data, let value = String(data: data, encoding: .utf8), !value.isEmpty
        else { return nil }
        return value
    }

    private static func write(_ value: String) {
        let data = Data(value.utf8)
        let attrs: [String: Any] = [
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
        ]
        if SecItemUpdate(query as CFDictionary, attrs as CFDictionary) == errSecItemNotFound {
            var add = query
            attrs.forEach { add[$0.key] = $0.value }
            SecItemAdd(add as CFDictionary, nil)
        }
    }

    private static func delete() {
        SecItemDelete(query as CFDictionary)
        UserDefaults.standard.removeObject(forKey: key)
    }

    /// Токен прежних версий лежал в UserDefaults: переносим его в Keychain и стираем.
    private static func migrateFromDefaults() -> String? {
        guard let old = UserDefaults.standard.string(forKey: key), !old.isEmpty else { return nil }
        write(old)
        UserDefaults.standard.removeObject(forKey: key)
        return old
    }
}
