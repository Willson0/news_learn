import AuthenticationServices
import UIKit

/// Привязка карты через Т-Кассу: страница оплаты открывается в ASWebAuthenticationSession,
/// сервер возвращает пользователя на karta://billing?status=ok|error.
enum CardBinding {
    static let callbackScheme = "karta"
    /// Сессия и её контекст живут, пока открыт браузер оплаты.
    private static var active: (session: ASWebAuthenticationSession, presenter: CardWindowPresenter)?

    @MainActor
    static func run() async throws {
        let url = try await Api.bindCard()
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            let presenter = CardWindowPresenter()
            let session = ASWebAuthenticationSession(url: url, callbackURLScheme: callbackScheme) { callback, error in
                active = nil
                if let error = error as? ASWebAuthenticationSessionError, error.code == .canceledLogin {
                    continuation.resume(throwing: ApiError("Привязка карты отменена", status: 0))
                    return
                }
                if let error {
                    continuation.resume(throwing: ApiError(error.localizedDescription, status: 0))
                    return
                }
                let items = callback.flatMap { URLComponents(url: $0, resolvingAgainstBaseURL: false) }?.queryItems ?? []
                if items.first(where: { $0.name == "status" })?.value == "ok" {
                    continuation.resume()
                } else {
                    continuation.resume(throwing: ApiError("Не удалось привязать карту", status: 0))
                }
            }
            session.presentationContextProvider = presenter
            active = (session, presenter)
            if !session.start() {
                active = nil
                continuation.resume(throwing: ApiError("Не удалось открыть оплату", status: 0))
            }
        }
    }
}

private final class CardWindowPresenter: NSObject, ASWebAuthenticationPresentationContextProviding {
    func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor {
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        return scenes.flatMap { $0.windows }.first(where: { $0.isKeyWindow }) ?? ASPresentationAnchor()
    }
}
