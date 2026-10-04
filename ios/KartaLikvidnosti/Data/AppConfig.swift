import Foundation

/// Конфигурация приложения. Базовый адрес API задаётся параметром сборки
/// `API_BASE_URL` (см. project.yml / Info.plist) и должен оканчиваться на `/api/`.
enum AppConfig {
    static let apiBaseURL: String = {
        let raw = (Bundle.main.object(forInfoDictionaryKey: "API_BASE_URL") as? String)?
            .trimmingCharacters(in: .whitespacesAndNewlines)
        let base = (raw?.isEmpty == false ? raw! : "http://localhost:8000/api/")
        // Гарантируем ровно один завершающий слэш.
        return base.hasSuffix("/") ? base : base + "/"
    }()
}
