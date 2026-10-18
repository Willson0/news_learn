import UIKit

/// Системная навигационная панель скрыта на всех экранах (у каждого экрана своя
/// стеклянная кнопка «назад» из макета). Без панели UIKit отключает свайп
/// назад от левого края — возвращаем его: жест работает, пока в стеке есть куда
/// вернуться.
extension UINavigationController: @retroactive UIGestureRecognizerDelegate {
    override open func viewDidLoad() {
        super.viewDidLoad()
        interactivePopGestureRecognizer?.delegate = self
    }

    public func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
        viewControllers.count > 1
    }
}
