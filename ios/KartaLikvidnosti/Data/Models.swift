import Foundation

// Модели ответов API (Laravel). snake_case отображается через CodingKeys.
// Отсутствующие ключи допускаются (decodeIfPresent), как в Android-версии.

struct DataWrapper<T: Decodable>: Decodable {
    let data: T?
}

struct StatusResponse: Decodable {
    let status: String?
    let message: String?
}

struct MeResponse: Decodable {
    let user: UserDto?
}

struct TokenResponse: Decodable {
    let token: String
    let user: UserDto?

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        token = (try c.decodeIfPresent(String.self, forKey: .token)) ?? ""
        user = try c.decodeIfPresent(UserDto.self, forKey: .user)
    }
    enum CodingKeys: String, CodingKey { case token, user }
}

struct UserDto: Decodable, Equatable {
    let id: Int
    let name: String?
    let email: String?
    let phone: String?
    let username: String?
    let avatarUrl: String?
    let telegramId: String?
    let isAdmin: Bool
    let isRootAdmin: Bool

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = (try c.decodeIfPresent(Int.self, forKey: .id)) ?? 0
        name = try c.decodeIfPresent(String.self, forKey: .name)
        email = try c.decodeIfPresent(String.self, forKey: .email)
        phone = try c.decodeIfPresent(String.self, forKey: .phone)
        username = try c.decodeIfPresent(String.self, forKey: .username)
        avatarUrl = try c.decodeIfPresent(String.self, forKey: .avatarUrl)
        telegramId = try c.decodeIfPresent(String.self, forKey: .telegramId)
        isAdmin = (try c.decodeIfPresent(Bool.self, forKey: .isAdmin)) ?? false
        isRootAdmin = (try c.decodeIfPresent(Bool.self, forKey: .isRootAdmin)) ?? false
    }
    enum CodingKeys: String, CodingKey {
        case id, name, email, phone, username
        case avatarUrl = "avatar_url"
        case telegramId = "telegram_id"
        case isAdmin = "is_admin"
        case isRootAdmin = "is_root_admin"
    }
}

struct InstrumentDto: Decodable, Identifiable, Equatable {
    var id: String { key }
    let key: String
    let label: String
    let category: String?
    var on: Bool

    init(key: String, label: String, category: String? = nil, on: Bool = false) {
        self.key = key; self.label = label; self.category = category; self.on = on
    }
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        key = (try c.decodeIfPresent(String.self, forKey: .key)) ?? ""
        label = (try c.decodeIfPresent(String.self, forKey: .label)) ?? ""
        category = try c.decodeIfPresent(String.self, forKey: .category)
        on = (try c.decodeIfPresent(Bool.self, forKey: .on)) ?? false
    }
    enum CodingKeys: String, CodingKey { case key, label, category, on }
}

struct ReportDto: Decodable, Identifiable, Equatable {
    let id: Int
    let title: String
    let description: String
    let badge: String?
    let status: String?
    let date: String?
    let material: String?
    let instrument: String?
    let chartUrl: String?
    let coverUrl: String?
    let coverName: String?
    let htmlUrl: String?
    let htmlName: String?
    let chat: String?
    let body: String?

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = (try c.decodeIfPresent(Int.self, forKey: .id)) ?? 0
        title = (try c.decodeIfPresent(String.self, forKey: .title)) ?? ""
        description = (try c.decodeIfPresent(String.self, forKey: .description)) ?? ""
        badge = try c.decodeIfPresent(String.self, forKey: .badge)
        status = try c.decodeIfPresent(String.self, forKey: .status)
        date = try c.decodeIfPresent(String.self, forKey: .date)
        material = try c.decodeIfPresent(String.self, forKey: .material)
        instrument = try c.decodeIfPresent(String.self, forKey: .instrument)
        chartUrl = try c.decodeIfPresent(String.self, forKey: .chartUrl)
        coverUrl = try c.decodeIfPresent(String.self, forKey: .coverUrl)
        coverName = try c.decodeIfPresent(String.self, forKey: .coverName)
        htmlUrl = try c.decodeIfPresent(String.self, forKey: .htmlUrl)
        htmlName = try c.decodeIfPresent(String.self, forKey: .htmlName)
        chat = try c.decodeIfPresent(String.self, forKey: .chat)
        body = try c.decodeIfPresent(String.self, forKey: .body)
    }
    enum CodingKeys: String, CodingKey {
        case id, title, description, badge, status, date, material, instrument, chat, body
        case chartUrl = "chart_url"
        case coverUrl = "cover_url"
        case coverName = "cover_name"
        case htmlUrl = "html_url"
        case htmlName = "html_name"
    }
}

struct ProfileResponse: Decodable {
    let user: UserDto?
    let subscription: SubscriptionDto?
    let notifications: NotificationsDto?
    let instruments: [InstrumentDto]

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        user = try c.decodeIfPresent(UserDto.self, forKey: .user)
        subscription = try c.decodeIfPresent(SubscriptionDto.self, forKey: .subscription)
        notifications = try c.decodeIfPresent(NotificationsDto.self, forKey: .notifications)
        instruments = (try c.decodeIfPresent([InstrumentDto].self, forKey: .instruments)) ?? []
    }
    enum CodingKeys: String, CodingKey { case user, subscription, notifications, instruments }
}

struct NotificationsDto: Decodable {
    let all: Bool
    let byInstrument: Bool

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        all = (try c.decodeIfPresent(Bool.self, forKey: .all)) ?? true
        byInstrument = (try c.decodeIfPresent(Bool.self, forKey: .byInstrument)) ?? false
    }
    enum CodingKeys: String, CodingKey { case all; case byInstrument = "by_instrument" }
}

struct InstrumentsResponse: Decodable {
    let instruments: [InstrumentDto]
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        instruments = (try c.decodeIfPresent([InstrumentDto].self, forKey: .instruments)) ?? []
    }
    enum CodingKeys: String, CodingKey { case instruments }
}

struct SubscriptionDto: Decodable {
    let active: Bool
    let plan: String?
    /// Стоимость для отображения, например «990 ₽ / 1 мес.».
    let price: String?
    let until: String?
    let autoPay: Bool
    let paymentMethod: String?

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        active = (try c.decodeIfPresent(Bool.self, forKey: .active)) ?? false
        plan = try c.decodeIfPresent(String.self, forKey: .plan)
        price = try c.decodeIfPresent(String.self, forKey: .price)
        until = try c.decodeIfPresent(String.self, forKey: .until)
        autoPay = (try c.decodeIfPresent(Bool.self, forKey: .autoPay)) ?? false
        paymentMethod = try c.decodeIfPresent(String.self, forKey: .paymentMethod)
    }
    enum CodingKeys: String, CodingKey {
        case active, plan, price, until
        case autoPay = "auto_pay"
        case paymentMethod = "payment_method"
    }
}

struct ChatSectionDto: Decodable, Identifiable {
    var id: String { key }
    let key: String
    let title: String
    let items: [ChatItemDto]

    init(key: String, title: String, items: [ChatItemDto]) {
        self.key = key; self.title = title; self.items = items
    }
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        key = (try c.decodeIfPresent(String.self, forKey: .key)) ?? ""
        title = (try c.decodeIfPresent(String.self, forKey: .title)) ?? ""
        items = (try c.decodeIfPresent([ChatItemDto].self, forKey: .items)) ?? []
    }
    enum CodingKeys: String, CodingKey { case key, title, items }
}

struct ChatItemDto: Decodable, Identifiable {
    let id: String
    let type: String?
    let title: String
    let subtitle: String?
    let pinned: Bool
    let avatarUrl: String?
    let report: Int?
    let sender: String?
    let preview: String?
    let time: String?
    var unread: Int
    /// Чат нельзя покинуть вручную (личный чат и чат отчёта).
    let permanent: Bool
    /// Может ли текущий пользователь управлять чатом (у чата отчёта — только создатель отчёта).
    let canManage: Bool?
    /// Может ли закреплять/откреплять сообщения (админ или автор отчёта).
    var canPin: Bool = false
    /// В чате отчёта можно написать автору лично через «/текст/».
    var authorMessages: Bool = false

    init(id: String, type: String? = nil, title: String, subtitle: String? = nil,
         pinned: Bool = false, avatarUrl: String? = nil, report: Int? = nil,
         sender: String? = nil, preview: String? = nil, time: String? = nil, unread: Int = 0,
         permanent: Bool = false, canManage: Bool? = nil) {
        self.id = id; self.type = type; self.title = title; self.subtitle = subtitle
        self.pinned = pinned; self.avatarUrl = avatarUrl; self.report = report
        self.sender = sender; self.preview = preview; self.time = time; self.unread = unread
        self.permanent = permanent; self.canManage = canManage
    }
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = (try c.decodeIfPresent(String.self, forKey: .id)) ?? ""
        type = try c.decodeIfPresent(String.self, forKey: .type)
        title = (try c.decodeIfPresent(String.self, forKey: .title)) ?? ""
        subtitle = try c.decodeIfPresent(String.self, forKey: .subtitle)
        pinned = (try c.decodeIfPresent(Bool.self, forKey: .pinned)) ?? false
        avatarUrl = try c.decodeIfPresent(String.self, forKey: .avatarUrl)
        report = try c.decodeIfPresent(Int.self, forKey: .report)
        sender = try c.decodeIfPresent(String.self, forKey: .sender)
        preview = try c.decodeIfPresent(String.self, forKey: .preview)
        time = try c.decodeIfPresent(String.self, forKey: .time)
        unread = (try c.decodeIfPresent(Int.self, forKey: .unread)) ?? 0
        permanent = (try c.decodeIfPresent(Bool.self, forKey: .permanent)) ?? false
        canManage = try c.decodeIfPresent(Bool.self, forKey: .canManage)
        canPin = (try c.decodeIfPresent(Bool.self, forKey: .canPin)) ?? false
        authorMessages = (try c.decodeIfPresent(Bool.self, forKey: .authorMessages)) ?? false
    }
    enum CodingKeys: String, CodingKey {
        case id, type, title, subtitle, pinned, report, sender, preview, time, unread, permanent
        case avatarUrl = "avatar_url"
        case canManage = "can_manage"
        case canPin = "can_pin"
        case authorMessages = "author_messages"
    }
}

struct MessagesResponse: Decodable {
    let chat: ChatItemDto?
    let pinned: MessageQuoteDto?
    /// Все закреплённые сообщения по порядку закрепления (старые → новые).
    let pins: [MessageQuoteDto]
    let data: [MessageDto]

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        chat = try c.decodeIfPresent(ChatItemDto.self, forKey: .chat)
        pinned = try c.decodeIfPresent(MessageQuoteDto.self, forKey: .pinned)
        let list = (try c.decodeIfPresent([MessageQuoteDto].self, forKey: .pins)) ?? []
        pins = list.isEmpty ? (pinned.map { [$0] } ?? []) : list
        data = (try c.decodeIfPresent([MessageDto].self, forKey: .data)) ?? []
    }
    enum CodingKeys: String, CodingKey { case chat, pinned, pins, data }
}

/// Ответ на закреп/открепление сообщения.
struct PinResponse: Decodable {
    let pinned: MessageQuoteDto?
    let pins: [MessageQuoteDto]

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        pinned = try c.decodeIfPresent(MessageQuoteDto.self, forKey: .pinned)
        let list = (try c.decodeIfPresent([MessageQuoteDto].self, forKey: .pins)) ?? []
        pins = list.isEmpty ? (pinned.map { [$0] } ?? []) : list
    }
    enum CodingKeys: String, CodingKey { case pinned, pins }
}

/// Короткая цитата сообщения (ответ / закреп).
struct MessageQuoteDto: Decodable {
    let id: Int
    let author: String?
    let text: String?

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = (try c.decodeIfPresent(Int.self, forKey: .id)) ?? 0
        author = try c.decodeIfPresent(String.self, forKey: .author)
        text = try c.decodeIfPresent(String.self, forKey: .text)
    }
    enum CodingKeys: String, CodingKey { case id, author, text }
}

struct MessageImageDto: Decodable {
    let url: String?
    let width: Int?
    let height: Int?
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        url = try c.decodeIfPresent(String.self, forKey: .url)
        width = try c.decodeIfPresent(Int.self, forKey: .width)
        height = try c.decodeIfPresent(Int.self, forKey: .height)
    }
    enum CodingKeys: String, CodingKey { case url, width, height }
}

struct MessageFileDto: Decodable {
    let url: String?
    let name: String?
    let ext: String?
    let size: String?
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        url = try c.decodeIfPresent(String.self, forKey: .url)
        name = try c.decodeIfPresent(String.self, forKey: .name)
        ext = try c.decodeIfPresent(String.self, forKey: .ext)
        size = try c.decodeIfPresent(String.self, forKey: .size)
    }
    enum CodingKeys: String, CodingKey { case url, name, ext, size }
}

struct MessageVoiceDto: Decodable {
    let url: String?
    let duration: String?
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        url = try c.decodeIfPresent(String.self, forKey: .url)
        duration = try c.decodeIfPresent(String.self, forKey: .duration)
    }
    enum CodingKeys: String, CodingKey { case url, duration }
}

struct ReactionDto: Decodable {
    let emoji: String
    let count: Int
    let mine: Bool
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        emoji = (try c.decodeIfPresent(String.self, forKey: .emoji)) ?? ""
        count = (try c.decodeIfPresent(Int.self, forKey: .count)) ?? 0
        mine = (try c.decodeIfPresent(Bool.self, forKey: .mine)) ?? false
    }
    enum CodingKeys: String, CodingKey { case emoji, count, mine }
}

struct MessageDto: Decodable, Identifiable {
    let id: Int
    let userId: Int?
    let mine: Bool
    let author: String?
    let role: String?
    let avatarUrl: String?
    let text: String?
    let time: String?
    let createdAt: String?
    let reply: MessageQuoteDto?
    let image: MessageImageDto?
    let images: [MessageImageDto]
    let file: MessageFileDto?
    let voice: MessageVoiceDto?
    let reactions: [ReactionDto]
    let pinned: Bool
    let read: Bool
    /// Служебное сообщение: "join" — зашел в чат, "leave" — вышел.
    let event: String?
    /// Личное сообщение (автору отчёта и ответы на него) — видят только двое.
    let isPrivate: Bool
    let privateLabel: String?
    /// Можно ли удалить у всех (своё; у админа и автора отчёта — любое).
    let canDelete: Bool?

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = (try c.decodeIfPresent(Int.self, forKey: .id)) ?? 0
        event = try c.decodeIfPresent(String.self, forKey: .event)
        isPrivate = (try c.decodeIfPresent(Bool.self, forKey: .isPrivate)) ?? false
        privateLabel = try c.decodeIfPresent(String.self, forKey: .privateLabel)
        canDelete = try c.decodeIfPresent(Bool.self, forKey: .canDelete)
        userId = try c.decodeIfPresent(Int.self, forKey: .userId)
        mine = (try c.decodeIfPresent(Bool.self, forKey: .mine)) ?? false
        author = try c.decodeIfPresent(String.self, forKey: .author)
        role = try c.decodeIfPresent(String.self, forKey: .role)
        avatarUrl = try c.decodeIfPresent(String.self, forKey: .avatarUrl)
        text = try c.decodeIfPresent(String.self, forKey: .text)
        time = try c.decodeIfPresent(String.self, forKey: .time)
        createdAt = try c.decodeIfPresent(String.self, forKey: .createdAt)
        reply = try c.decodeIfPresent(MessageQuoteDto.self, forKey: .reply)
        image = try c.decodeIfPresent(MessageImageDto.self, forKey: .image)
        images = (try c.decodeIfPresent([MessageImageDto].self, forKey: .images)) ?? []
        file = try c.decodeIfPresent(MessageFileDto.self, forKey: .file)
        voice = try c.decodeIfPresent(MessageVoiceDto.self, forKey: .voice)
        reactions = (try c.decodeIfPresent([ReactionDto].self, forKey: .reactions)) ?? []
        pinned = (try c.decodeIfPresent(Bool.self, forKey: .pinned)) ?? false
        read = (try c.decodeIfPresent(Bool.self, forKey: .read)) ?? false
    }
    enum CodingKeys: String, CodingKey {
        case id, mine, author, role, text, time, reply, image, images, file, voice, reactions, pinned, read, event
        case isPrivate = "private"
        case privateLabel = "private_label"
        case canDelete = "can_delete"
        case userId = "user_id"
        case avatarUrl = "avatar_url"
        case createdAt = "created_at"
    }
}

/// Участник чата (вкладка «Участники», упоминания).
struct ParticipantDto: Decodable, Identifiable {
    let id: Int
    let name: String?
    let username: String?
    let avatarUrl: String?
    let role: String?
    let seen: String?

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = (try c.decodeIfPresent(Int.self, forKey: .id)) ?? 0
        name = try c.decodeIfPresent(String.self, forKey: .name)
        username = try c.decodeIfPresent(String.self, forKey: .username)
        avatarUrl = try c.decodeIfPresent(String.self, forKey: .avatarUrl)
        role = try c.decodeIfPresent(String.self, forKey: .role)
        seen = try c.decodeIfPresent(String.self, forKey: .seen)
    }
    enum CodingKeys: String, CodingKey {
        case id, name, username, role, seen
        case avatarUrl = "avatar_url"
    }
}

/// Вложение чата (вкладки Медиа/Голосовые/Файлы инфо-экрана).
struct ChatAttachmentDto: Decodable, Identifiable {
    let id: Int
    let url: String?
    let name: String?
    let ext: String?
    let size: String?
    let duration: Int?
    let author: String?
    let date: String?
    let meta: String?

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = (try c.decodeIfPresent(Int.self, forKey: .id)) ?? 0
        url = try c.decodeIfPresent(String.self, forKey: .url)
        name = try c.decodeIfPresent(String.self, forKey: .name)
        ext = try c.decodeIfPresent(String.self, forKey: .ext)
        size = try c.decodeIfPresent(String.self, forKey: .size)
        duration = try c.decodeIfPresent(Int.self, forKey: .duration)
        author = try c.decodeIfPresent(String.self, forKey: .author)
        date = try c.decodeIfPresent(String.self, forKey: .date)
        meta = try c.decodeIfPresent(String.self, forKey: .meta)
    }
    enum CodingKeys: String, CodingKey { case id, url, name, ext, size, duration, author, date, meta }
}

/// Ссылка, найденная в сообщениях чата (вкладка «Ссылки»).
struct ChatLinkDto: Decodable, Identifiable {
    var id: String { url + (date ?? "") }
    let url: String
    let title: String?
    let text: String?
    let author: String?
    let date: String?
    let month: String?

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        url = (try c.decodeIfPresent(String.self, forKey: .url)) ?? ""
        title = try c.decodeIfPresent(String.self, forKey: .title)
        text = try c.decodeIfPresent(String.self, forKey: .text)
        author = try c.decodeIfPresent(String.self, forKey: .author)
        date = try c.decodeIfPresent(String.self, forKey: .date)
        month = try c.decodeIfPresent(String.self, forKey: .month)
    }
    enum CodingKeys: String, CodingKey { case url, title, text, author, date, month }
}

/// Публичный профиль другого пользователя (/users/{id}).
struct UserProfileDto: Decodable {
    let id: Int
    let name: String?
    let username: String?
    let avatarUrl: String?
    let registeredAt: String?
    let registeredDays: Int?
    let seen: String?
    let isAdmin: Bool

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = (try c.decodeIfPresent(Int.self, forKey: .id)) ?? 0
        name = try c.decodeIfPresent(String.self, forKey: .name)
        username = try c.decodeIfPresent(String.self, forKey: .username)
        avatarUrl = try c.decodeIfPresent(String.self, forKey: .avatarUrl)
        registeredAt = try c.decodeIfPresent(String.self, forKey: .registeredAt)
        registeredDays = try c.decodeIfPresent(Int.self, forKey: .registeredDays)
        seen = try c.decodeIfPresent(String.self, forKey: .seen)
        isAdmin = (try c.decodeIfPresent(Bool.self, forKey: .isAdmin)) ?? false
    }
    enum CodingKeys: String, CodingKey {
        case id, name, username, seen
        case avatarUrl = "avatar_url"
        case registeredAt = "registered_at"
        case registeredDays = "registered_days"
        case isAdmin = "is_admin"
    }
}

struct AdminDto: Decodable, Identifiable {
    var id: String { adminId.map { String($0) } ?? telegramId ?? "root" }
    /// id записи админа для DELETE admin/admins/{id}; у главного админа его нет.
    let adminId: Int?
    let telegramId: String?
    let name: String?
    let email: String?
    let username: String?
    let root: Bool

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        adminId = try c.decodeIfPresent(Int.self, forKey: .adminId)
        telegramId = try c.decodeIfPresent(String.self, forKey: .telegramId)
        name = try c.decodeIfPresent(String.self, forKey: .name)
        email = try c.decodeIfPresent(String.self, forKey: .email)
        username = try c.decodeIfPresent(String.self, forKey: .username)
        root = (try c.decodeIfPresent(Bool.self, forKey: .root)) ?? false
    }
    enum CodingKeys: String, CodingKey {
        case name, email, username, root
        case adminId = "id"
        case telegramId = "telegram_id"
    }
}

/// Привязанная карта Т-Кассы (GET /billing/card).
struct CardDto: Decodable {
    let bound: Bool
    let brand: String?
    let last4: String?
}

/// Ответ POST /billing/card/bind: ссылка на страницу оплаты.
struct CardBindDto: Decodable {
    let confirmationUrl: String
    enum CodingKeys: String, CodingKey { case confirmationUrl = "confirmation_url" }
}

/// Документ из админки: открывается по ссылке внутри приложения.
struct DocumentDto: Decodable, Identifiable, Equatable {
    let title: String
    let url: String

    var id: String { url }
}
