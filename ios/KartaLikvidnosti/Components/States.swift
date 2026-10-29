import SwiftUI
import UIKit

/// Три состояния списка: загрузка (скелетоны на экране), пусто и ошибка.
/// Ошибка не выдаёт себя за «материалов нет»: «Нет соединения» и «Повторить».
struct LoadErrorState: View {
    var title: String = "Нет соединения"
    var text: String = "Проверьте интернет и попробуйте ещё раз"
    let onRetry: () -> Void

    var body: some View {
        VStack(spacing: 8) {
            Text(title).font(Theme.sans(17, .semibold)).foregroundStyle(Theme.text)
            Text(text).font(Theme.sans(Theme.fontBase)).foregroundStyle(Theme.textSecondary)
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

    func show(_ text: String) {
        guard Thread.isMainThread else {
            DispatchQueue.main.async { self.show(text) }
            return
        }
        serial += 1
        let current = serial
        withAnimation(.easeOut(duration: 0.2)) { message = text }
        UIAccessibility.post(notification: .announcement, argument: text)
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.4) { [weak self] in
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
