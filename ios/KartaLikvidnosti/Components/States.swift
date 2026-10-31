import SwiftUI
import UIKit

/// Три состояния списка: загрузка (скелетоны на экране), пусто и ошибка.
/// Ошибка не выдаёт себя за «материалов нет»: «Нет соединения» и «Повторить».
struct LoadErrorState: View {
    var title: String? = nil
    var text: String? = nil
    let onRetry: () -> Void
    @ObservedObject private var net = NetStatus.shared

    init(title: String? = nil, text: String? = nil, onRetry: @escaping () -> Void) {
        self.title = title
        self.text = text
        self.onRetry = onRetry
    }

    /// Текст по причине сбоя: нет интернета, сервер не ответил вовремя или упал.
    private var reason: (String, String) {
        switch net.lastFailure {
        case .timeout?: return ("Сервер долго не отвечает", "Соединение слишком медленное. Попробуйте ещё раз")
        case .server?: return ("Сервер временно недоступен", "Мы уже разбираемся. Попробуйте чуть позже")
        default: return ("Нет соединения", "Проверьте интернет и попробуйте ещё раз")
        }
    }

    var body: some View {
        VStack(spacing: 8) {
            Text(title ?? reason.0).font(Theme.sans(17, .semibold)).foregroundStyle(Theme.text)
            Text(text ?? reason.1).font(Theme.sans(Theme.fontBase)).foregroundStyle(Theme.textSecondary)
            AppButton(title: "Повторить", action: onRetry)
                .frame(width: 200)
                .padding(.top, 8)
        }
        .multilineTextAlignment(.center)
        .frame(maxWidth: .infinity)
        .padding(.vertical, 32)
    }
}

/// Пустой результат: пояснение и, если есть, действие («Сбросить фильтры»).
struct EmptyStateView: View {
    let text: String
    var actionTitle: String? = nil
    var action: () -> Void = {}

    var body: some View {
        VStack(spacing: 16) {
            Text(text).font(Theme.sans(17)).foregroundStyle(Theme.textSecondary).multilineTextAlignment(.center)
            if let actionTitle {
                AppButton(title: actionTitle, variant: .secondary, action: action).frame(width: 220)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 40)
    }
}

/// Плашка над списком из кэша, когда обновить не удалось: «Показаны данные от 14:05 · Повторить».
struct StaleDataBanner: View {
    let savedAt: Date?
    let onRetry: () -> Void

    private var label: String {
        guard let savedAt else { return "Нет соединения. Показаны сохранённые данные" }
        let f = DateFormatter()
        f.dateFormat = "HH:mm"
        return "Нет соединения. Показаны данные от \(f.string(from: savedAt))"
    }

    var body: some View {
        Button(action: onRetry) {
            HStack(spacing: 12) {
                Text(label).font(Theme.sans(Theme.fontBase)).foregroundStyle(Theme.text)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Text("Повторить").font(Theme.sans(Theme.fontBase, .semibold)).foregroundStyle(Theme.green)
            }
            .padding(.horizontal, 16).padding(.vertical, 8)
            .frame(minHeight: 48)
            .liquidGlass(RoundedRectangle(cornerRadius: 24, style: .continuous))
        }
        .buttonStyle(PressableStyle())
    }
}

/// Общий тост приложения: «Не удалось сохранить, попробуйте ещё раз» и т. п.
/// Показывается из любого экрана через `AppToast.shared.show(...)`; хост — в корне.
final class AppToast: ObservableObject {
    static let shared = AppToast()
    @Published private(set) var message: String? = nil
    private var serial = 0

    /// Сколько тост на экране: 3,5 с — успеть прочитать.
    static let duration: TimeInterval = 3.5

    func show(_ text: String) {
        guard Thread.isMainThread else {
            DispatchQueue.main.async { self.show(text) }
            return
        }
        serial += 1
        let current = serial
        // Тот же текст ещё на экране — не мигаем и не складываем стопкой, только продлеваем.
        if message != text {
            withAnimation(.easeOut(duration: 0.2)) { message = text }
            UIAccessibility.post(notification: .announcement, argument: text)
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + Self.duration) { [weak self] in
            guard let self, self.serial == current else { return }
            withAnimation(.easeIn(duration: 0.2)) { self.message = nil }
        }
    }
}

private struct AppToastHost: ViewModifier {
    @ObservedObject private var toast = AppToast.shared

    func body(content: Content) -> some View {
        content.overlay(alignment: .top) {
            if let text = toast.message {
                Text(text)
                    .font(Theme.sans(Theme.fontBase))
                    .foregroundStyle(Theme.text)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 12).padding(.vertical, 7)
                    .frame(minHeight: 32)
                    .liquidGlass(RoundedRectangle(cornerRadius: 24, style: .continuous))
                    .padding(.horizontal, Theme.screenX)
                    .padding(.top, 60)
                    .transition(.move(edge: .top).combined(with: .opacity))
                    .allowsHitTesting(false)
            }
        }
    }
}

extension View {
    /// Хост общего тоста (ставится один раз в корне приложения).
    func appToastHost() -> some View { modifier(AppToastHost()) }
}

/// Подсказка «Соединение медленное…»: появляется, если загрузка идёт дольше 8 с.
/// Рисуется поверх (overlay) и ничего не сдвигает; по таймауту запроса её сменяет
/// экран ошибки с «Повторить».
private struct SlowNetworkHint: ViewModifier {
    let active: Bool
    let bottom: CGFloat
    @State private var shown = false

    func body(content: Content) -> some View {
        content
            .overlay(alignment: .bottom) {
                if shown && active {
                    HStack(spacing: 8) {
                        ProgressView().tint(Theme.text).scaleEffect(0.8)
                        Text("Соединение медленное…")
                            .font(Theme.sans(Theme.fontBase))
                            .foregroundStyle(Theme.text)
                    }
                    .padding(.horizontal, 16)
                    .frame(height: 40)
                    .liquidGlass(Capsule())
                    .padding(.bottom, bottom)
                    .transition(.opacity)
                    .allowsHitTesting(false)
                    .accessibilityElement(children: .combine)
                }
            }
            .animation(.easeOut(duration: 0.25), value: shown && active)
            .task(id: active) {
                shown = false
                guard active else { return }
                try? await Task.sleep(nanoseconds: 8_000_000_000)
                if !Task.isCancelled {
                    shown = true
                    UIAccessibility.post(notification: .announcement, argument: "Соединение медленное")
                }
            }
    }
}

extension View {
    /// Пока `active` (идёт первая загрузка) дольше 8 с — подсказка о медленной сети.
    func slowNetworkHint(active: Bool, bottom: CGFloat = 24) -> some View {
        modifier(SlowNetworkHint(active: active, bottom: bottom))
    }
}

extension Error {
    /// Запрос отменён (ушли с экрана) — ошибку не показываем.
    var isCancellation: Bool { self is CancellationError }
}
