import Foundation

/// Ошибка API: несёт http-статус и поля валидации (errors) от Laravel —
/// аналог ApiError из frontend/src/api/client.js.
struct ApiError: LocalizedError {
    let message: String
    let status: Int
    let errors: [String: [String]]

    init(_ message: String, status: Int, errors: [String: [String]] = [:]) {
        self.message = message
        self.status = status
        self.errors = errors
    }

    var errorDescription: String? { message }

    /// Первое сообщение для одного из указанных полей.
    func fieldError(_ fields: String...) -> String? {
        for f in fields { if let m = errors[f]?.first { return m } }
        return nil
    }

    /// Первое сообщение любой ошибки валидации либо общее message.
    var firstError: String { errors.values.first?.first ?? message }
}

/// Часть multipart-запроса: текстовое поле или файл.
struct MultipartPart {
    let name: String
    let filename: String?
    let mime: String?
    let data: Data

    static func text(_ name: String, _ value: String) -> MultipartPart {
        MultipartPart(name: name, filename: nil, mime: nil, data: Data(value.utf8))
    }
    static func file(_ name: String, filename: String, mime: String, data: Data) -> MultipartPart {
        MultipartPart(name: name, filename: filename, mime: mime, data: data)
    }
}

/// Низкоуровневый клиент REST API. Токен Sanctum берётся из TokenStore.
enum ApiClient {
    private static let decoder = JSONDecoder()

    private static func makeRequest(_ method: String, _ path: String) -> URLRequest {
        let url = URL(string: AppConfig.apiBaseURL + path.trimmingPrefixSlash())!
        var req = URLRequest(url: url)
        req.httpMethod = method
        req.setValue("application/json", forHTTPHeaderField: "Accept")
        if let token = TokenStore.token, !token.isEmpty {
            req.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        return req
    }

    private static func send<T: Decodable>(_ req: URLRequest, as type: T.Type) async throws -> T {
        let data: Data
        let response: URLResponse
        do {
            (data, response) = try await URLSession.shared.data(for: req)
        } catch {
            throw ApiError("Нет связи с сервером", status: 0)
        }
        guard let http = response as? HTTPURLResponse else {
            throw ApiError("Нет связи с сервером", status: 0)
        }
        if http.statusCode == 401 { TokenStore.token = nil }

        if !(200..<300).contains(http.statusCode) {
            throw parseError(data: data, status: http.statusCode)
        }
        if T.self == EmptyResponse.self {
            return EmptyResponse() as! T
        }
        do {
            return try decoder.decode(T.self, from: data)
        } catch {
            throw ApiError("Не удалось разобрать ответ сервера", status: http.statusCode)
        }
    }

    private static func parseError(data: Data, status: Int) -> ApiError {
        var message = "Ошибка запроса (\(status))"
        var errors: [String: [String]] = [:]
        if let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
            if let m = obj["message"] as? String, !m.isEmpty { message = m }
            if let errs = obj["errors"] as? [String: Any] {
                for (key, value) in errs {
                    if let arr = value as? [String] {
                        errors[key] = arr
                    } else if let arr = value as? [Any] {
                        errors[key] = arr.compactMap { $0 as? String }
                    } else if let s = value as? String {
                        errors[key] = [s]
                    }
                }
            }
        }
        return ApiError(message, status: status, errors: errors)
    }

    // MARK: - JSON verbs

    static func get<T: Decodable>(_ path: String, as type: T.Type) async throws -> T {
        try await send(makeRequest("GET", path), as: type)
    }

    static func post<T: Decodable>(_ path: String, body: [String: Any?]? = nil, as type: T.Type) async throws -> T {
        try await send(jsonRequest("POST", path, body: body), as: type)
    }

    static func put<T: Decodable>(_ path: String, body: [String: Any?]? = nil, as type: T.Type) async throws -> T {
        try await send(jsonRequest("PUT", path, body: body), as: type)
    }

    static func delete<T: Decodable>(_ path: String, as type: T.Type) async throws -> T {
        try await send(makeRequest("DELETE", path), as: type)
    }

    private static func jsonRequest(_ method: String, _ path: String, body: [String: Any?]?) -> URLRequest {
        var req = makeRequest(method, path)
        if let body = body {
            // Убираем nil-значения (как JSON.stringify опускает undefined).
            var clean: [String: Any] = [:]
            for (k, v) in body { if let v = v { clean[k] = v } }
            req.setValue("application/json", forHTTPHeaderField: "Content-Type")
            req.httpBody = try? JSONSerialization.data(withJSONObject: clean)
        }
        return req
    }

    // MARK: - Multipart (загрузка файлов)

    static func multipart<T: Decodable>(_ path: String, parts: [MultipartPart], as type: T.Type) async throws -> T {
        var req = makeRequest("POST", path)
        let boundary = "Boundary-\(UUID().uuidString)"
        req.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")
        var body = Data()
        func append(_ s: String) { body.append(Data(s.utf8)) }
        for part in parts {
            append("--\(boundary)\r\n")
            if let filename = part.filename {
                append("Content-Disposition: form-data; name=\"\(part.name)\"; filename=\"\(filename)\"\r\n")
                append("Content-Type: \(part.mime ?? "application/octet-stream")\r\n\r\n")
                body.append(part.data)
                append("\r\n")
            } else {
                append("Content-Disposition: form-data; name=\"\(part.name)\"\r\n\r\n")
                body.append(part.data)
                append("\r\n")
            }
        }
        append("--\(boundary)--\r\n")
        req.httpBody = body
        return try await send(req, as: type)
    }
}

/// Маркер для запросов без тела ответа (DELETE / logout).
struct EmptyResponse: Decodable {}

private extension String {
    func trimmingPrefixSlash() -> String {
        hasPrefix("/") ? String(dropFirst()) : self
    }
}

/// Полный URL файла, который бэкенд отдаёт относительной ссылкой.
func fileURL(_ path: String?) -> URL? {
    guard let path = path, !path.isEmpty else { return nil }
    if path.hasPrefix("http://") || path.hasPrefix("https://") { return URL(string: path) }
    return URL(string: AppConfig.apiBaseURL + (path.hasPrefix("/") ? String(path.dropFirst()) : path))
}
