import Foundation

/// Последние ответы основных запросов (лента, чаты, профиль, подписка, пользователь)
/// на диске. При повторном запуске — и без сети — экраны сразу показывают их, а
/// свежие данные приходят тихо в фоне. Лежит в Caches (не попадает в резервную
/// копию) и стирается при выходе из аккаунта и истёкшей сессии.
enum OfflineCache {
    private static let directory: URL = {
        let base = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
        let dir = base.appendingPathComponent("Offline", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }()

    private static let io = DispatchQueue(label: "offline-cache", qos: .utility)

    private static func file(_ path: String) -> URL {
        directory.appendingPathComponent(path.replacingOccurrences(of: "/", with: "_") + ".json")
    }

    static func save(_ data: Data, for path: String) {
        let url = file(path)
        io.async { try? data.write(to: url, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication]) }
    }

    /// Данные и время сохранения (дата изменения файла).
    static func load(_ path: String) -> (Data, Date)? {
        let url = file(path)
        guard let data = try? Data(contentsOf: url) else { return nil }
        let date = (try? FileManager.default.attributesOfItem(atPath: url.path)[.modificationDate] as? Date) ?? Date()
        return (data, date)
    }

    /// Выход из аккаунта или истёкшая сессия: стираем и кэш, и очередь неотправленного.
    static func clear() {
        ChatOutbox.clearAll()
        io.async {
            let files = (try? FileManager.default.contentsOfDirectory(at: directory, includingPropertiesForKeys: nil)) ?? []
            files.forEach { try? FileManager.default.removeItem(at: $0) }
        }
    }
}
