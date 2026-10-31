import UIKit

/// Неотправленные сообщения чата (текст и фото) на диске: сообщение со статусом
/// «не отправлено» не теряется при выходе из чата и перезапуске, а повтор уходит
/// с тем же ключом `clientId` — сервер не создаст дубль.
enum ChatOutbox {
    struct Entry: Codable, Equatable {
        let localId: Int
        let clientId: String
        let text: String?
        let replyTo: Int?
        let replyAuthor: String?
        let replyText: String?
        let isPrivate: Bool
        /// Имена jpeg-файлов фото в папке очереди.
        let photos: [String]
    }

    private static let directory: URL = {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        let dir = base.appendingPathComponent("ChatOutbox", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        var url = dir
        try? url.setResourceValues(values)
        return dir
    }()

    private static func listFile(_ chat: String) -> URL {
        let safe = chat.addingPercentEncoding(withAllowedCharacters: .alphanumerics) ?? chat
        return directory.appendingPathComponent("chat-\(safe).json")
    }

    static func entries(_ chat: String) -> [Entry] {
        guard let data = try? Data(contentsOf: listFile(chat)) else { return [] }
        return (try? JSONDecoder().decode([Entry].self, from: data)) ?? []
    }

    private static func write(_ list: [Entry], _ chat: String) {
        let url = listFile(chat)
        if list.isEmpty { try? FileManager.default.removeItem(at: url); return }
        if let data = try? JSONEncoder().encode(list) { try? data.write(to: url, options: .atomic) }
    }

    static func add(_ entry: Entry, chat: String) {
        var list = entries(chat).filter { $0.localId != entry.localId }
        list.append(entry)
        write(list, chat)
    }

    static func remove(_ localId: Int, chat: String) {
        var list = entries(chat)
        guard let entry = list.first(where: { $0.localId == localId }) else { return }
        entry.photos.forEach { try? FileManager.default.removeItem(at: directory.appendingPathComponent($0)) }
        list.removeAll { $0.localId == localId }
        write(list, chat)
    }

    /// Сохраняет фото очереди и возвращает имена файлов.
    static func storePhotos(_ jpegs: [Data], localId: Int) -> [String] {
        jpegs.enumerated().compactMap { i, data in
            let name = "photo-\(abs(localId))-\(i).jpg"
            return (try? data.write(to: directory.appendingPathComponent(name), options: .atomic)) != nil ? name : nil
        }
    }

    static func photoData(_ name: String) -> Data? {
        try? Data(contentsOf: directory.appendingPathComponent(name))
    }

    /// При выходе из аккаунта очередь стирается целиком.
    static func clearAll() {
        let files = (try? FileManager.default.contentsOfDirectory(at: directory, includingPropertiesForKeys: nil)) ?? []
        files.forEach { try? FileManager.default.removeItem(at: $0) }
    }
}
