import SwiftUI

/// Общий кэш основных данных приложения. Предзагружается на старте (при наличии
/// токена) и при входе, чтобы при базовой навигации между вкладками не было
/// прогрузок: экраны берут данные отсюда мгновенно, а затем обновляют их.
@MainActor
final class AppData: ObservableObject {
    @Published var reports: [ReportDto] = []
    @Published var chats: [ChatSectionDto] = []
    @Published var profile: ProfileResponse? = nil
    @Published var preloaded = false

    /// Тихо подгружает ленту, чаты и профиль. Ошибки гасим — экраны обновят сами.
    func preload() async {
        guard TokenStore.isAuthenticated else {
            reports = []; chats = []; profile = nil; preloaded = false
            return
        }
        if let r = try? await Api.fetchReports() { reports = r }
        if let c = try? await Api.fetchChats(), !c.isEmpty { chats = c }
        if let p = try? await Api.fetchProfile() { profile = p }
        preloaded = true
    }

    func clear() {
        reports = []; chats = []; profile = nil; preloaded = false
    }
}
