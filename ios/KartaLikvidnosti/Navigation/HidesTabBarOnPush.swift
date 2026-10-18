import SwiftUI
import UIKit

/// Плавное появление таб-бара при возврате на корневой экран вкладки.
///
/// На внутренних экранах таб-бар скрыт (`.toolbar(.hidden, for: .tabBar)`), и при
/// возврате SwiftUI показывает его рывком в конце перехода. Этот мост живёт на
/// корневом экране вкладки: когда экран начинает появляться (в т.ч. при
/// интерактивном свайпе слева), таб-бар сразу ставится на место, сдвинутым влево
/// за край, и «выезжает» вместе с экраном по системному transitionCoordinator —
/// следует за пальцем, а при отмене свайпа уезжает обратно.
extension View {
    func tabBarSlidesWithScreen() -> some View {
        background(TabBarSlideBridge().frame(width: 0, height: 0))
    }
}

private struct TabBarSlideBridge: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> Controller { Controller() }
    func updateUIViewController(_ vc: Controller, context: Context) {}

    final class Controller: UIViewController {
        /// Пока идёт переход, SwiftUI ещё считает активным внутренний экран и
        /// снова прячет таб-бар — удерживаем его видимым до конца перехода.
        private var observations: [NSKeyValueObservation] = []

        override func viewWillAppear(_ animated: Bool) {
            super.viewWillAppear(animated)
            guard let tabBar = tabBarController?.tabBar,
                  let coordinator = transitionCoordinator,
                  coordinator.isAnimated,
                  // Только возврат из pushed-экрана этого же стека, не смена вкладки.
                  coordinator.viewController(forKey: .from)?.navigationController != nil
            else { return }

            let width = tabBar.window?.bounds.width ?? UIScreen.main.bounds.width
            UIView.performWithoutAnimation {
                tabBar.isHidden = false
                tabBar.alpha = 1
                tabBar.transform = CGAffineTransform(translationX: -width, y: 0)
            }
            // SwiftUI прячет таб-бар и через isHidden, и через alpha (iOS 26+).
            observations = [
                tabBar.observe(\.isHidden, options: [.new]) { bar, _ in
                    if bar.isHidden { bar.isHidden = false }
                },
                tabBar.observe(\.alpha, options: [.new]) { bar, _ in
                    if bar.alpha < 1 { bar.alpha = 1 }
                },
            ]
            coordinator.animate(alongsideTransition: { _ in
                tabBar.transform = .identity
            }, completion: { [weak self] ctx in
                self?.observations.forEach { $0.invalidate() }
                self?.observations = []
                tabBar.transform = .identity
                // Свайп отменён — остаёмся на внутреннем экране без таб-бара.
                if ctx.isCancelled { tabBar.alpha = 0; tabBar.isHidden = true }
            })
        }
    }
}
