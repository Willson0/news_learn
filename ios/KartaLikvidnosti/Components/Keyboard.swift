import SwiftUI
import UIKit

/// Клавиатура во всём приложении: тап мимо поля ввода убирает её, переход
/// между экранами — тоже (см. RootView). Чтобы тап не прятал клавиатуру
/// (поле ввода чата с «Отправить» и «+», строка поиска), область помечается
/// `.keepsKeyboardOnTap()`.
final class KeyboardDismiss: NSObject, UIGestureRecognizerDelegate {
    static let shared = KeyboardDismiss()

    private(set) var visible = false
    /// Области в координатах окна, где тап клавиатуру не убирает.
    private var keepZones: [UUID: CGRect] = [:]
    private weak var installedWindow: UIWindow?

    private override init() {
        super.init()
        let center = NotificationCenter.default
        center.addObserver(self, selector: #selector(keyboardWillShow), name: UIResponder.keyboardWillShowNotification, object: nil)
        center.addObserver(self, selector: #selector(keyboardWillHide), name: UIResponder.keyboardWillHideNotification, object: nil)
    }

    @objc private func keyboardWillShow() { visible = true }
    @objc private func keyboardWillHide() { visible = false }

    static func hide() {
        UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
    }

    func install(in window: UIWindow) {
        guard installedWindow !== window else { return }
        installedWindow = window
        let tap = UITapGestureRecognizer(target: self, action: #selector(handleTap(_:)))
        tap.cancelsTouchesInView = false
        tap.delaysTouchesBegan = false
        tap.delaysTouchesEnded = false
        tap.delegate = self
        window.addGestureRecognizer(tap)
    }

    func setKeepZone(_ id: UUID, _ rect: CGRect?) {
        keepZones[id] = rect
    }

    @objc private func handleTap(_ r: UITapGestureRecognizer) {
        r.view?.endEditing(true)
    }

    func gestureRecognizer(_ g: UIGestureRecognizer, shouldReceive touch: UITouch) -> Bool {
        guard visible else { return false }
        // Тап по самому полю (или другому полю) — это перенос курсора, а не «закрыть».
        var v: UIView? = touch.view
        while let view = v {
            if view is UITextField || view is UITextView { return false }
            v = view.superview
        }
        let p = touch.location(in: nil)
        return !keepZones.values.contains { $0.contains(p) }
    }

    func gestureRecognizer(_ g: UIGestureRecognizer,
                           shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer) -> Bool {
        true
    }
}

/// Ставит распознаватель тапа на окно приложения (один раз).
private struct KeyboardDismissInstaller: UIViewRepresentable {
    func makeUIView(context: Context) -> UIView { InstallerView() }
    func updateUIView(_ uiView: UIView, context: Context) {}

    private final class InstallerView: UIView {
        override func didMoveToWindow() {
            super.didMoveToWindow()
            isUserInteractionEnabled = false
            if let window { KeyboardDismiss.shared.install(in: window) }
        }
    }
}

private struct KeepsKeyboardOnTap: ViewModifier {
    @State private var id = UUID()

    func body(content: Content) -> some View {
        content
            .background(
                GeometryReader { geo in
                    let frame = geo.frame(in: .global)
                    Color.clear
                        .onAppear { KeyboardDismiss.shared.setKeepZone(id, frame) }
                        .onChange(of: frame) { KeyboardDismiss.shared.setKeepZone(id, $0) }
                }
            )
            .onDisappear { KeyboardDismiss.shared.setKeepZone(id, nil) }
    }
}

extension View {
    /// Тап мимо полей ввода убирает клавиатуру; подключается один раз у корня.
    func dismissesKeyboardOnTap() -> some View {
        background(KeyboardDismissInstaller().frame(width: 0, height: 0))
    }

    /// Тап в этой области клавиатуру не убирает (поле ввода с кнопками, поиск).
    func keepsKeyboardOnTap() -> some View {
        modifier(KeepsKeyboardOnTap())
    }
}
