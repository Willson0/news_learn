import AuthenticationServices
import UIKit

/// Вход через Яндекс: открывает /auth/yandex/start во встроенном браузере.
/// Сервер возвращает пользователя в приложение на karta://auth?token=… или ?error=….
enum YandexSignIn {
    static let callbackScheme = "karta"
    /// Сессия и её контекст живут, пока открыт браузер входа.
    private static var active: (session: ASWebAuthenticationSession, presenter: WindowPresenter)?

    @MainActor
    static func token() async throws -> String {
        guard let url = URL(string: AppConfig.apiBaseURL + "auth/yandex/start") else {
            throw ApiError("Неверный адрес сервера", status: 0)
        }
        return try await withCheckedThrowingContinuation { continuation in
            let presenter = WindowPresenter()
            let session = ASWebAuthenticationSession(url: url, callbackURLScheme: callbackScheme) { callback, error in
                active = nil
                if let error = error as? ASWebAuthenticationSessionError, error.code == .canceledLogin {
                    continuation.resume(throwing: ApiError("Вход через Яндекс отменён", status: 0))
                    return
                }
                if let error {
                    continuation.resume(throwing: ApiError(error.localizedDescription, status: 0))
                    return
                }
                let items = callback.flatMap { URLComponents(url: $0, resolvingAgainstBaseURL: false) }?.queryItems ?? []
                if let message = items.first(where: { $0.name == "error" })?.value {
                    continuation.resume(throwing: ApiError(message, status: 0))
                } else if let token = items.first(where: { $0.name == "token" })?.value, !token.isEmpty {
                    continuation.resume(returning: token)
                } else {
                    continuation.resume(throwing: ApiError("Не удалось войти через Яндекс", status: 0))
                }
            }
            session.presentationContextProvider = presenter
            active = (session, presenter)
            if !session.start() {
                active = nil
                continuation.resume(throwing: ApiError("Не удалось открыть страницу входа", status: 0))
            }
        }
    }
}

private final class WindowPresenter: NSObject, ASWebAuthenticationPresentationContextProviding {
    func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor {
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        return scenes.flatMap { $0.windows }.first(where: { $0.isKeyWindow }) ?? ASPresentationAnchor()
    }
}
