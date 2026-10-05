import Foundation

/// Высокоуровневые вызовы API (аналог frontend/src/api/auth.js и resources.js).
enum Api {

    // MARK: - Авторизация

    static func loginWithPassword(identifier: String, password: String) async throws -> UserDto? {
        let r = try await ApiClient.post("auth/login",
            body: ["identifier": identifier, "password": password], as: TokenResponse.self)
        TokenStore.token = r.token
        return r.user
    }

    static func register(email: String, phone: String, password: String, name: String? = nil) async throws {
        _ = try await ApiClient.post("auth/register",
            body: ["email": email, "phone": phone, "password": password, "name": name], as: StatusResponse.self)
    }

    static func confirmRegistration(email: String, code: String) async throws -> UserDto? {
        let r = try await ApiClient.post("auth/register/confirm",
            body: ["email": email, "code": code], as: TokenResponse.self)
        TokenStore.token = r.token
        return r.user
    }

    static func resendRegistrationCode(email: String) async throws {
        _ = try await ApiClient.post("auth/register/resend", body: ["email": email], as: StatusResponse.self)
    }

    static func requestRecovery(email: String) async throws {
        _ = try await ApiClient.post("auth/recovery", body: ["email": email], as: StatusResponse.self)
    }

    static func resetPassword(email: String, password: String) async throws -> UserDto? {
        let r = try await ApiClient.post("auth/recovery/reset",
            body: ["email": email, "password": password], as: TokenResponse.self)
        TokenStore.token = r.token
        return r.user
    }

    static func logout() async {
        _ = try? await ApiClient.post("auth/logout", as: EmptyResponse.self)
        TokenStore.token = nil
    }

    static func fetchMe() async throws -> MeResponse {
        try await ApiClient.get("user", as: MeResponse.self)
    }

    // MARK: - Отчёты / материалы

    static func fetchReports(instrument: String? = nil, search: String? = nil, status: [String]? = nil) async throws -> [ReportDto] {
        var query: [String] = []
        if let instrument = instrument, instrument != "all" {
            query.append("instrument=\(instrument.urlEncoded)")
        }
        if let search = search, !search.isEmpty {
            query.append("search=\(search.urlEncoded)")
        }
        if let status = status, !status.isEmpty {
            query.append("status=\(status.joined(separator: ",").urlEncoded)")
        }
        let qs = query.isEmpty ? "" : "?" + query.joined(separator: "&")
        let r = try await ApiClient.get("reports\(qs)", as: DataWrapper<[ReportDto]>.self)
        return r.data ?? []
    }

    static func fetchReport(id: String) async throws -> ReportDto? {
        try await ApiClient.get("reports/\(id)", as: DataWrapper<ReportDto>.self).data
    }

    static func fetchInstruments() async throws -> [InstrumentDto] {
        try await ApiClient.get("instruments", as: DataWrapper<[InstrumentDto]>.self).data ?? []
    }

    // MARK: - Профиль

    static func fetchProfile() async throws -> ProfileResponse {
        try await ApiClient.get("profile", as: ProfileResponse.self)
    }

    static func syncInstruments(_ keys: [String]) async throws {
        _ = try await ApiClient.put("profile/instruments", body: ["instruments": keys], as: InstrumentsResponse.self)
    }

    static func updateNotifications(_ payload: [String: Any?]) async throws {
        _ = try await ApiClient.put("profile/notifications", body: payload, as: StatusResponse.self)
    }

    // MARK: - Подписка

    static func fetchSubscription() async throws -> SubscriptionDto? {
        try await ApiClient.get("subscription", as: DataWrapper<SubscriptionDto>.self).data
    }

    static func updateSubscription(_ payload: [String: Any?]) async throws {
        _ = try await ApiClient.put("subscription", body: payload, as: StatusResponse.self)
    }

    // MARK: - Данные аккаунта

    static func changeEmail(_ email: String) async throws {
        _ = try await ApiClient.put("account/email", body: ["email": email], as: StatusResponse.self)
    }

    static func changePhone(_ phone: String) async throws {
        _ = try await ApiClient.put("account/phone", body: ["phone": phone], as: StatusResponse.self)
    }

    static func changePassword(_ password: String, _ confirmation: String, _ current: String?) async throws {
        _ = try await ApiClient.put("account/password", body: [
            "password": password,
            "password_confirmation": confirmation,
            "current_password": current,
        ], as: StatusResponse.self)
    }

    // MARK: - Сообщество

    static func fetchChats() async throws -> [ChatSectionDto] {
        try await ApiClient.get("chats", as: DataWrapper<[ChatSectionDto]>.self).data ?? []
    }

    static func fetchMessages(_ slug: String) async throws -> MessagesResponse {
        try await ApiClient.get("chats/\(slug)/messages", as: MessagesResponse.self)
    }

    static func sendMessage(_ slug: String, body: String) async throws -> MessageDto? {
        try await ApiClient.post("chats/\(slug)/messages", body: ["body": body], as: DataWrapper<MessageDto>.self).data
    }

    // MARK: - Админка

    static func fetchAdmins() async throws -> [AdminDto] {
        try await ApiClient.get("admin/admins", as: DataWrapper<[AdminDto]>.self).data ?? []
    }

    static func addAdmin(telegramId: String, name: String? = nil) async throws {
        _ = try await ApiClient.post("admin/admins",
            body: ["telegram_id": telegramId, "name": name], as: DataWrapper<AdminDto>.self)
    }

    static func removeAdmin(telegramId: String) async throws {
        _ = try await ApiClient.delete("admin/admins/\(telegramId)", as: EmptyResponse.self)
    }

    static func createReport(_ payload: ReportFormPayload) async throws -> ReportDto? {
        try await ApiClient.multipart("admin/reports", parts: payload.parts(), as: DataWrapper<ReportDto>.self).data
    }

    static func updateReport(id: Int, _ payload: ReportFormPayload) async throws -> ReportDto? {
        try await ApiClient.multipart("admin/reports/\(id)", parts: payload.parts(), as: DataWrapper<ReportDto>.self).data
    }

    static func deleteReport(id: Int) async throws {
        _ = try await ApiClient.delete("admin/reports/\(id)", as: EmptyResponse.self)
    }

    static func updateChat(slug: String, title: String, avatar: (data: Data, filename: String, mime: String)?) async throws {
        var parts: [MultipartPart] = [.text("title", title)]
        if let a = avatar { parts.append(.file("avatar", filename: a.filename, mime: a.mime, data: a.data)) }
        _ = try await ApiClient.multipart("admin/chats/\(slug)", parts: parts, as: DataWrapper<ChatItemDto>.self)
    }
}

/// Данные формы отчёта (админка), собираются в multipart как в resources.js.
struct ReportFormPayload {
    var title: String = ""
    var description: String = ""
    var chartUrl: String = ""
    var instrument: String = ""
    var cover: (data: Data, filename: String, mime: String)?
    var html: (data: Data, filename: String, mime: String)?
    var removeCover: Bool = false
    var removeHtml: Bool = false

    func parts() -> [MultipartPart] {
        var parts: [MultipartPart] = [
            .text("title", title),
            .text("description", description),
            .text("chart_url", chartUrl),
            .text("instrument", instrument),
        ]
        if let c = cover { parts.append(.file("cover", filename: c.filename, mime: c.mime, data: c.data)) }
        if let h = html { parts.append(.file("html", filename: h.filename, mime: h.mime, data: h.data)) }
        if removeCover { parts.append(.text("remove_cover", "1")) }
        if removeHtml { parts.append(.text("remove_html", "1")) }
        return parts
    }
}

private extension String {
    var urlEncoded: String {
        addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? self
    }
}
