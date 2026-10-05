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
    let until: String?
    let autoPay: Bool
    let paymentMethod: String?

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        active = (try c.decodeIfPresent(Bool.self, forKey: .active)) ?? false
        plan = try c.decodeIfPresent(String.self, forKey: .plan)
        until = try c.decodeIfPresent(String.self, forKey: .until)
        autoPay = (try c.decodeIfPresent(Bool.self, forKey: .autoPay)) ?? false
        paymentMethod = try c.decodeIfPresent(String.self, forKey: .paymentMethod)
    }
    enum CodingKeys: String, CodingKey {
        case active, plan, until
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
    let unread: Int

    init(id: String, type: String? = nil, title: String, subtitle: String? = nil,
         pinned: Bool = false, avatarUrl: String? = nil, report: Int? = nil,
         sender: String? = nil, preview: String? = nil, time: String? = nil, unread: Int = 0) {
        self.id = id; self.type = type; self.title = title; self.subtitle = subtitle
        self.pinned = pinned; self.avatarUrl = avatarUrl; self.report = report
        self.sender = sender; self.preview = preview; self.time = time; self.unread = unread
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
    }
    enum CodingKeys: String, CodingKey {
        case id, type, title, subtitle, pinned, report, sender, preview, time, unread
        case avatarUrl = "avatar_url"
    }
}

struct MessagesResponse: Decodable {
    let chat: ChatItemDto?
    let data: [MessageDto]

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        chat = try c.decodeIfPresent(ChatItemDto.self, forKey: .chat)
        data = (try c.decodeIfPresent([MessageDto].self, forKey: .data)) ?? []
    }
    enum CodingKeys: String, CodingKey { case chat, data }
}

struct MessageDto: Decodable, Identifiable {
    let id: Int
    let mine: Bool
    let author: String?
    let role: String?
    let text: String?
    let time: String?

    init(id: Int, mine: Bool, author: String? = nil, role: String? = nil, text: String? = nil, time: String? = nil) {
        self.id = id; self.mine = mine; self.author = author; self.role = role; self.text = text; self.time = time
    }
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = (try c.decodeIfPresent(Int.self, forKey: .id)) ?? 0
        mine = (try c.decodeIfPresent(Bool.self, forKey: .mine)) ?? false
        author = try c.decodeIfPresent(String.self, forKey: .author)
        role = try c.decodeIfPresent(String.self, forKey: .role)
        text = try c.decodeIfPresent(String.self, forKey: .text)
        time = try c.decodeIfPresent(String.self, forKey: .time)
    }
    enum CodingKeys: String, CodingKey { case id, mine, author, role, text, time }
}

struct AdminDto: Decodable, Identifiable {
    var id: String { telegramId }
    let telegramId: String
    let name: String?
    let username: String?
    let root: Bool

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        telegramId = (try c.decodeIfPresent(String.self, forKey: .telegramId)) ?? ""
        name = try c.decodeIfPresent(String.self, forKey: .name)
        username = try c.decodeIfPresent(String.self, forKey: .username)
        root = (try c.decodeIfPresent(Bool.self, forKey: .root)) ?? false
    }
    enum CodingKeys: String, CodingKey {
        case name, username, root
        case telegramId = "telegram_id"
    }
}
