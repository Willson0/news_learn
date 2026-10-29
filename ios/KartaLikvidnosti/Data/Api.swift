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

    /// Проверка кода из письма без смены пароля (код не тратится) — шаг 2 восстановления.
    static func checkRecoveryCode(email: String, code: String) async throws {
        _ = try await ApiClient.post("auth/recovery/check", body: ["email": email, "code": code], as: StatusResponse.self)
    }

    static func resetPassword(email: String, password: String, code: String) async throws -> UserDto? {
        let r = try await ApiClient.post("auth/recovery/reset",
            body: ["email": email, "password": password, "code": code], as: TokenResponse.self)
        TokenStore.token = r.token
        return r.user
    }

    static func logout() async {
        await Push.unregisterCurrent()
        _ = try? await ApiClient.post("auth/logout", as: EmptyResponse.self)
        TokenStore.token = nil
    }

    // MARK: - Push и документы

    static func registerDevice(_ token: String) async throws {
        _ = try await ApiClient.post("devices", body: ["token": token, "platform": "ios"], as: StatusResponse.self)
    }

    static func unregisterDevice(_ token: String) async throws {
        _ = try await ApiClient.delete("devices", body: ["token": token], as: StatusResponse.self)
    }

    /// Документы из админки (соглашение и др.). Пустой список — экран покажет встроенный текст.
    static func fetchDocuments() async throws -> [DocumentDto] {
        try await ApiClient.get("documents", as: DataWrapper<[DocumentDto]>.self).data ?? []
    }

    static func fetchMe() async throws -> MeResponse {
        try await ApiClient.get("user", as: MeResponse.self)
    }

    // MARK: - Отчёты / материалы

    static func fetchReports(instrument: String? = nil, instruments: [String]? = nil, period: String? = nil,
                             from: String? = nil, to: String? = nil, search: String? = nil, status: [String]? = nil) async throws -> [ReportDto] {
        var query: [String] = []
        if let instrument = instrument, instrument != "all" {
            query.append("instrument=\(instrument.urlEncoded)")
        }
        if let instruments = instruments, !instruments.isEmpty {
            query.append("instruments=\(instruments.joined(separator: ",").urlEncoded)")
        }
        if let period = period, !period.isEmpty {
            query.append("period=\(period.urlEncoded)")
        }
        if let from = from, !from.isEmpty { query.append("from=\(from.urlEncoded)") }
        if let to = to, !to.isEmpty { query.append("to=\(to.urlEncoded)") }
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

    /// Правка профиля: имя и тег (username). Бэкенд: PUT /profile.
    static func updateProfile(name: String, username: String?) async throws {
        _ = try await ApiClient.put("profile", body: ["name": name, "username": username], as: StatusResponse.self)
    }

    /// Загрузка/смена фото профиля — возвращает обновлённого пользователя.
    static func uploadAvatar(data: Data, filename: String, mime: String) async throws -> UserDto? {
        try await ApiClient.multipart("profile/avatar",
            parts: [.file("avatar", filename: filename, mime: mime, data: data)],
            as: DataWrapper<UserDto>.self).data
    }

    static func removeAvatar() async throws -> UserDto? {
        try await ApiClient.delete("profile/avatar", as: DataWrapper<UserDto>.self).data
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

    static func leaveChat(slug: String) async throws {
        _ = try await ApiClient.post("chats/\(slug)/leave", as: StatusResponse.self)
    }

    static func markChatRead(slug: String) async throws {
        _ = try await ApiClient.post("chats/\(slug)/read", as: StatusResponse.self)
    }

    static func updateSubscription(_ payload: [String: Any?]) async throws {
        _ = try await ApiClient.put("subscription", body: payload, as: StatusResponse.self)
    }

    // MARK: - Карта (Т-Касса)

    static func fetchCard() async throws -> CardDto {
        try await ApiClient.get("billing/card", as: CardDto.self)
    }

    /// Создаёт платёж привязки; возвращает страницу оплаты Т-Кассы.
    static func bindCard() async throws -> URL {
        let r = try await ApiClient.post("billing/card/bind", as: CardBindDto.self)
        guard let url = URL(string: r.confirmationUrl) else { throw ApiError("Неверная ссылка оплаты", status: 0) }
        return url
    }

    static func unbindCard() async throws {
        _ = try await ApiClient.delete("billing/card", as: EmptyResponse.self)
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

    /// Отправка сообщения: текст и/или вложение (image|file|voice), опционально ответ.
    static func sendMessage(_ slug: String, body: String? = nil, replyTo: Int? = nil,
                            attachment: (data: Data, filename: String, mime: String)? = nil,
                            attachments: [(data: Data, filename: String, mime: String)]? = nil,
                            kind: String? = nil, duration: Int? = nil) async throws -> MessageDto? {
        if let list = attachments, !list.isEmpty, let kind = kind {
            // Несколько фото одним сообщением-альбомом: поле attachments[] на каждый файл.
            var parts: [MultipartPart] = []
            if let body = body, !body.isEmpty { parts.append(.text("body", body)) }
            if let replyTo = replyTo { parts.append(.text("reply_to", String(replyTo))) }
            parts.append(.text("kind", kind))
            for a in list { parts.append(.file("attachments[]", filename: a.filename, mime: a.mime, data: a.data)) }
            return try await ApiClient.multipart("chats/\(slug)/messages", parts: parts, as: DataWrapper<MessageDto>.self).data
        }
        if let a = attachment, let kind = kind {
            var parts: [MultipartPart] = []
            if let body = body, !body.isEmpty { parts.append(.text("body", body)) }
            if let replyTo = replyTo { parts.append(.text("reply_to", String(replyTo))) }
            parts.append(.text("kind", kind))
            if let duration = duration { parts.append(.text("duration", String(duration))) }
            parts.append(.file("attachment", filename: a.filename, mime: a.mime, data: a.data))
            return try await ApiClient.multipart("chats/\(slug)/messages", parts: parts, as: DataWrapper<MessageDto>.self).data
        }
        var payload: [String: Any?] = ["body": body]
        if let replyTo = replyTo { payload["reply_to"] = replyTo }
        return try await ApiClient.post("chats/\(slug)/messages", body: payload, as: DataWrapper<MessageDto>.self).data
    }

    /// Поставить/снять реакцию — возвращает обновлённое сообщение.
    static func reactMessage(_ slug: String, _ id: Int, emoji: String) async throws -> MessageDto? {
        try await ApiClient.post("chats/\(slug)/messages/\(id)/react", body: ["emoji": emoji], as: DataWrapper<MessageDto>.self).data
    }

    /// Закрепить/открепить сообщение — возвращает все закрепы чата по порядку закрепления.
    static func pinMessage(_ slug: String, _ id: Int) async throws -> [MessageQuoteDto] {
        try await ApiClient.post("chats/\(slug)/messages/\(id)/pin", as: PinResponse.self).pins
    }

    /// Удалить сообщение: forAll — у всех, иначе только у себя (на всех своих устройствах).
    static func deleteMessage(_ slug: String, _ id: Int, forAll: Bool = true) async throws {
        _ = try await ApiClient.delete("chats/\(slug)/messages/\(id)", body: ["for": forAll ? "all" : "me"],
                                       as: StatusResponse.self)
    }

    /// Кого можно упомянуть через «@» в этом чате.
    static func chatMembers(_ slug: String, query: String) async throws -> [ParticipantDto] {
        let q = query.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
        return try await ApiClient.get("chats/\(slug)/members?q=\(q)", as: DataWrapper<[ParticipantDto]>.self).data ?? []
    }

    static func chatParticipants(_ slug: String) async throws -> [ParticipantDto] {
        try await ApiClient.get("chats/\(slug)/participants", as: DataWrapper<[ParticipantDto]>.self).data ?? []
    }

    static func chatAttachments(_ slug: String, kind: String) async throws -> [ChatAttachmentDto] {
        try await ApiClient.get("chats/\(slug)/attachments/\(kind)", as: DataWrapper<[ChatAttachmentDto]>.self).data ?? []
    }

    static func chatLinks(_ slug: String) async throws -> [ChatLinkDto] {
        try await ApiClient.get("chats/\(slug)/attachments/link", as: DataWrapper<[ChatLinkDto]>.self).data ?? []
    }

    static func userProfile(_ id: String) async throws -> UserProfileDto? {
        try await ApiClient.get("users/\(id)", as: DataWrapper<UserProfileDto>.self).data
    }

    // MARK: - Админка

    static func fetchAdmins() async throws -> [AdminDto] {
        try await ApiClient.get("admin/admins", as: DataWrapper<[AdminDto]>.self).data ?? []
    }

    static func addAdmin(email: String, name: String? = nil) async throws {
        _ = try await ApiClient.post("admin/admins",
            body: ["email": email, "name": name], as: DataWrapper<AdminDto>.self)
    }

    static func removeAdmin(id: Int) async throws {
        _ = try await ApiClient.delete("admin/admins/\(id)", as: EmptyResponse.self)
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
