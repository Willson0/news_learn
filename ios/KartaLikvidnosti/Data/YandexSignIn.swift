import AuthenticationServices
import CryptoKit
import Security
import UIKit

/// Вход через Яндекс: открывает /auth/yandex/start?challenge=… во встроенном браузере.
/// Сервер возвращает пользователя в приложение на karta://auth?code=… или ?error=…,
/// одноразовый code меняется на токен вместе с verifier, который знает только это приложение.
enum YandexSignIn {
    static let callbackScheme = "karta"
    /// Сессия и её контекст живут, пока открыт браузер входа.
    private static var active: (session: ASWebAuthenticationSession, presenter: WindowPresenter)?

    @MainActor
    static func token() async throws -> String {
        let verifier = makeVerifier()
        let code = try await authorize(challenge: challenge(for: verifier))
        let response = try await ApiClient.post("auth/yandex/exchange", body: ["code": code, "verifier": verifier], as: TokenResponse.self)
        guard !response.token.isEmpty else { throw ApiError("Не удалось войти через Яндекс", status: 0) }
        return response.token
    }

    /// Случайная строка 43+ символов (base64url от 32 байт).
    private static func makeVerifier() -> String {
        var bytes = [UInt8](repeating: 0, count: 32)
        if SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes) != errSecSuccess {
            bytes = (0..<32).map { _ in UInt8.random(in: 0...255) }
        }
        return base64url(Data(bytes))
    }

    private static func challenge(for verifier: String) -> String {
        base64url(Data(SHA256.hash(data: Data(verifier.utf8))))
    }

    private static func base64url(_ data: Data) -> String {
        data.base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }

    /// Открывает страницу входа и возвращает одноразовый code из karta://auth?code=….
    @MainActor
    private static func authorize(challenge: String) async throws -> String {
        guard var parts = URLComponents(string: AppConfig.apiBaseURL + "auth/yandex/start") else {
            throw ApiError("Неверный адрес сервера", status: 0)
        }
        parts.queryItems = [URLQueryItem(name: "challenge", value: challenge)]
        guard let url = parts.url else { throw ApiError("Неверный адрес сервера", status: 0) }
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
                } else if let code = items.first(where: { $0.name == "code" })?.value, !code.isEmpty {
                    continuation.resume(returning: code)
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
