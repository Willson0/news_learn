import SwiftUI
import UIKit

/// Дизайн-токены «Карта Ликвидности» — 1:1 со стилями макета Figma
/// (страница «Дизайн» / «дизайн система / uikit»). Тёмная тема, макет 393×852.
enum Theme {

    // MARK: - Цвета (стили Figma)

    /// «Фон» — вертикальный градиент экрана.
    static let bgTop = Color(hex: "#1a1b1d")
    static let bgMid = Color(hex: "#1e1f21")
    static let bgBottom = Color(hex: "#161719")
    static let bg = bgTop
    static let background = LinearGradient(
        stops: [
            .init(color: bgTop, location: 0.0),
            .init(color: bgMid, location: 0.476),
            .init(color: bgBottom, location: 1.0),
        ],
        startPoint: .top, endPoint: .bottom
    )

    /// «фон плашки» — карточки и блоки.
    static let surface = Color(hex: "#25262a")
    /// «Фон плашки 2».
    static let surface2Plate = Color(hex: "#2d2e35")
    /// «Second color» (#313131, 20%) — заливка стеклянных элементов.
    static let surface2 = Color(hex: "#313131")
    static let surfaceMuted = Color(hex: "#313131").opacity(0.2)
    static let glassFill = Color(hex: "#313131").opacity(0.2)
    static let navbarBg = Color(hex: "#161616").opacity(0.2)
    static let badge = Color(hex: "#555555")
    /// «черный 2» (#000, 40%).
    static let black2 = Color.black.opacity(0.4)

    // MARK: - Текст
    /// «Белый».
    static let text = Color.white
    /// «белый неактив» (65%).
    static let textSecondary = Color.white.opacity(0.65)
    /// «Белый неактив 2». Для текста поднят с 40 до 60 %: на #25262A это ≈ 4,6:1 (WCAG AA).
    static let textMuted = Color.white.opacity(0.6)
    /// Неактивные иконки (навбар): 40 % — для крупных значков достаточно 3:1.
    static let iconMuted = Color.white.opacity(0.4)

    // MARK: - Акценты
    /// «red 100».
    static let error = Color(hex: "#ff2a2a")
    /// «red» (#CF0E12, 54%) — кнопка «Выйти из аккаунта».
    static let danger = Color(hex: "#cf0e12").opacity(0.54)
    /// Variable «Color» — зелёный статус «Активна».
    static let green = Color(hex: "#629f00")
    static let greenSend = Color(hex: "#4e8f10")

    /// «active» — два слоя по 20%: жёлто-зелёный по диагонали + золото→тёмно-зелёный.
    /// Активная вкладка, основная кнопка, выбранные чипы.
    static let gradientAccentSoft = LinearGradient(
        stops: [
            .init(color: Color(hex: "#ffd900").opacity(0.2), location: 0.0),
            .init(color: Color(hex: "#1c4a00").opacity(0.2), location: 1.0),
        ],
        startPoint: .leading, endPoint: .trailing
    )
    static let gradientAccentSoft2 = LinearGradient(
        stops: [
            .init(color: Color(hex: "#ffff00").opacity(0.2), location: 0.043),
            .init(color: Color(hex: "#1beb00").opacity(0.2), location: 0.704),
        ],
        startPoint: .bottomLeading, endPoint: .topTrailing
    )

    /// Плотный вариант «active» (обе заливки слоями поверх стекла дают такой цвет).
    static let gradientAccent = LinearGradient(
        stops: [
            .init(color: Color(hex: "#6b6a1c"), location: 0.0),
            .init(color: Color(hex: "#3f5a18"), location: 0.55),
            .init(color: Color(hex: "#1f4a12"), location: 1.0),
        ],
        startPoint: .leading, endPoint: .trailing
    )

    /// «акцент 3» — пурпурный (активный чип «Все», вкладки, «Неделя»).
    static let gradientAccentPurple = LinearGradient(
        stops: [
            .init(color: Color(hex: "#572546").opacity(0.6), location: 0.304),
            .init(color: Color(hex: "#bd5197").opacity(0.6), location: 1.11),
        ],
        startPoint: .leading, endPoint: .trailing
    )

    /// Насыщенный пурпур для кнопок-пилюль (Отвязать/Привязать/Добавить).
    static let gradientPurpleSolid = LinearGradient(
        colors: [Color(hex: "#572546"), Color(hex: "#bd5197")],
        startPoint: .leading, endPoint: .trailing
    )

    /// «second color» — розово-малиновый градиент (бейдж кнопки «Обсудить»).
    static let gradientPink = LinearGradient(
        colors: [Color(hex: "#da517d"), Color(hex: "#ff0052")],
        startPoint: .topLeading, endPoint: .bottomTrailing
    )

    /// «затемнениние» — растушёвка снизу под нижним меню.
    static let bottomFade = LinearGradient(
        colors: [Color(hex: "#1a1b1d").opacity(0), Color(hex: "#1a1b1d")],
        startPoint: .top, endPoint: .bottom
    )

    // MARK: - Типографика (размеры из макета)
    static let fontTitle: CGFloat = 24
    static let fontLg: CGFloat = 17
    static let fontMd: CGFloat = 16
    static let fontBase: CGFloat = 14
    static let fontSm: CGFloat = 12

    /// Заголовки экранов — Kreadon Demi (24 / 20 в макете).
    static func heading(_ size: CGFloat, weight: Font.Weight = .semibold) -> Font {
        AppFont.kreadon(size)
    }

    /// Основной текст — Min Sans.
    static func sans(_ size: CGFloat, _ weight: Font.Weight = .regular) -> Font {
        AppFont.sans(size, weight)
    }

    // MARK: - Радиусы
    static let radiusCard: CGFloat = 24
    static let radiusChip: CGFloat = 20
    static let radiusPill: CGFloat = 60
    static let radiusInput: CGFloat = 24

    // MARK: - Отступы и размеры
    static let screenX: CGFloat = 16
    static let appWidth: CGFloat = 393
    static let appHeight: CGFloat = 852
    /// Высота нижнего меню + отступ под ним (контенту нужен запас снизу).
    static let tabBarInset: CGFloat = 104
    /// Координаты из макета (кадр 393×852, статус-бар 54): заголовок вкладки,
    /// верхняя панель внутреннего экрана и отступ под навбар.
    static let titleTop: CGFloat = 94
    static let topBarY: CGFloat = 78
    static let navBarClearance: CGFloat = 130
}

// MARK: - Шрифты

/// Шрифты макета. Kreadon Demi — заголовки; Min Sans — весь остальной текст.
/// Размеры фиксированные (как в макете), без масштабирования Dynamic Type.
enum AppFont {
    static func kreadon(_ size: CGFloat) -> Font {
        .custom("Kreadon-D", fixedSize: size)
    }

    static func sans(_ size: CGFloat, _ weight: Font.Weight = .regular) -> Font {
        .custom(sansName(weight), fixedSize: size)
    }

    static func uiSans(_ size: CGFloat, _ weight: Font.Weight = .regular) -> UIFont {
        UIFont(name: sansName(weight), size: size) ?? .systemFont(ofSize: size)
    }

    private static let hasSemiBold = UIFont(name: "MinSans-SemiBold", size: 12) != nil

    static func sansName(_ weight: Font.Weight) -> String {
        switch weight {
        case .ultraLight, .thin, .light: return "MinSans-Light"
        case .medium: return "MinSans-Medium"
        case .semibold: return hasSemiBold ? "MinSans-SemiBold" : "MinSans-Medium"
        case .bold, .heavy, .black: return "MinSans-Bold"
        default: return "MinSans-Regular"
        }
    }
}

// MARK: - Фон экрана

/// Фон экрана из макета: градиент «Фон» + мягкое свечение сверху (Group 2:
/// розовое справа, зелёно-красное слева, сильный блюр, 30%).
struct ScreenBackdrop: View {
    var glow: Bool = true

    /// Свечение в макете — 393×474 в кадре 393×852. Высота масштабируется
    /// пропорционально экрану (и не меньше ширины × 474/393), чтобы на больших
    /// и маленьких iPhone огоньки оставались в той же доле экрана, а не
    /// обрезались сверху при scaledToFill.
    static func glowHeight(_ size: CGSize) -> CGFloat {
        guard size.width > 0, size.height > 0 else { return 474 }
        return max(size.width * 474 / Theme.appWidth, size.height * 474 / 852)
    }

    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .top) {
                Theme.background
                if glow {
                    AnimatedGlow()
                        .frame(width: geo.size.width, height: Self.glowHeight(geo.size))
                        .clipped()
                        .allowsHitTesting(false)
                }
            }
        }
        .ignoresSafeArea()
    }
}

/// Свечение вверху экрана (Figma: компонент «Frame» 498:22347 у каждого экрана).
/// В прототипе инстанс бесконечно перебирает пять вариантов: после каждого —
/// DISSOLVE, LINEAR, 6 с (последний возвращается к первому так же). Кадры
/// отрисованы из геометрии вариантов: два слоя «горы», layer blur 226, группа 30%.
/// Фаза считается от общего времени, поэтому при переходах между экранами фон
/// продолжается без скачка. При «Уменьшении движения» — статичный первый кадр.
struct AnimatedGlow: View {
    private static let frames = (1...5).map { "BgGlow\($0)" }
    private static let step: TimeInterval = 6
    /// Общая точка отсчёта для всех экранов.
    private static let epoch = Date()

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        TimelineView(.animation(minimumInterval: 1.0 / 30, paused: reduceMotion)) { context in
            let (from, to, f) = phase(at: context.date)
            ZStack {
                frame(from).opacity(1 - f)
                frame(to).opacity(f)
            }
        }
    }

    private func phase(at date: Date) -> (Int, Int, Double) {
        if reduceMotion { return (0, 1, 0) }
        let t = date.timeIntervalSince(Self.epoch) / Self.step
        let k = Int(floor(t))
        let n = Self.frames.count
        let from = ((k % n) + n) % n
        return (from, (from + 1) % n, t - floor(t))
    }

    private func frame(_ i: Int) -> some View {
        Image(Self.frames[i])
            .resizable()
            .interpolation(.high)
            .scaledToFill()
    }
}

// MARK: - Удобные модификаторы

extension View {
    /// Стандартный фон экрана макета (градиент + свечение сверху).
    func screenBackground() -> some View {
        self.frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(ScreenBackdrop())
    }

    func screenBackground(_ background: some ShapeStyle) -> some View {
        self.frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(background, ignoresSafeAreaEdges: .all)
    }

    func screenBackground<B: View>(@ViewBuilder _ background: () -> B) -> some View {
        self.frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(background().ignoresSafeArea())
    }

    func cardSurface(radius: CGFloat = Theme.radiusCard) -> some View {
        self.background(Theme.surface, in: RoundedRectangle(cornerRadius: radius, style: .continuous))
    }

    /// «Жидкое стекло» в форме капсулы (стиль «glass» макета).
    func liquidGlass(strong: Bool = false) -> some View {
        modifier(LiquidGlass(shape: Capsule(), fill: Theme.glassFill))
    }

    /// «Жидкое стекло» в произвольной форме с заливкой «Second color» 20%.
    func liquidGlass<S: Shape>(_ shape: S, strong: Bool = false, interactive: Bool = false) -> some View {
        modifier(LiquidGlass(shape: shape, fill: Theme.glassFill, interactive: interactive))
    }

    /// Стекло с собственной заливкой (например, зелёный «active» или пурпур «акцент 3»).
    func liquidGlass<S: Shape, F: View>(_ shape: S, fill: F, interactive: Bool = false) -> some View {
        modifier(LiquidGlass(shape: shape, fill: fill, interactive: interactive))
    }

    /// Стекло, которое можно включать и выключать без смены структуры вида: элемент
    /// не пересоздаётся, а плавно меняет вид и двигается вместе с соседями.
    func liquidGlass<S: Shape>(_ shape: S, isOn: Bool) -> some View {
        modifier(LiquidGlass(shape: shape, fill: Theme.glassFill, isOn: isOn))
    }
}

/// Эффект «glass» из макета (GLASS: blur 10, refraction 0.75, depth 9, свет −45°).
/// iOS 26+ — системный Liquid Glass (`.glassEffect`). Ниже iOS 26 — имитация той же
/// формы: размытие подложки, полупрозрачная заливка, внутреннее свечение кромки и
/// блик-обводка, яркая сверху-слева и снизу-справа, как в макете.
struct LiquidGlass<S: Shape, F: View>: ViewModifier {
    let shape: S
    let fill: F
    var interactive: Bool = false
    var isOn: Bool = true

    @ViewBuilder
    func body(content: Content) -> some View {
        #if compiler(>=6.2)
        if #available(iOS 26.0, *) {
            content
                .background(fill.clipShape(shape).opacity(isOn ? 1 : 0))
                .glassEffect(isOn ? (interactive ? .regular.interactive() : .regular) : .identity, in: shape)
        } else {
            legacy(content)
        }
        #else
        legacy(content)
        #endif
    }

    private func legacy(_ content: Content) -> some View {
        content
            .background(fill.clipShape(shape).opacity(isOn ? 1 : 0))
            .background(BackdropBlur(radius: 10).clipShape(shape).opacity(isOn ? 1 : 0))
            .overlay(GlassRim(shape: shape).opacity(isOn ? 1 : 0).allowsHitTesting(false))
    }
}

/// Кромка стекла: мягкое внутреннее свечение по краю + тонкий блик.
struct GlassRim<S: Shape>: View {
    let shape: S

    var body: some View {
        ZStack {
            // Преломление у края (depth): светлая полоса внутри формы, размытая.
            shape
                .stroke(Color.white.opacity(0.07), lineWidth: 6)
                .blur(radius: 3)
                .clipShape(shape)
            // Блик: свет падает слева сверху (−45°), отражение — снизу справа.
            shape
                .stroke(
                    AngularGradient(
                        stops: [
                            .init(color: .white.opacity(0.30), location: 0.0),
                            .init(color: .white.opacity(0.42), location: 0.125),
                            .init(color: .white.opacity(0.06), location: 0.375),
                            .init(color: .white.opacity(0.28), location: 0.5),
                            .init(color: .white.opacity(0.55), location: 0.625),
                            .init(color: .white.opacity(0.06), location: 0.875),
                            .init(color: .white.opacity(0.30), location: 1.0),
                        ],
                        center: .center
                    ),
                    lineWidth: 1
                )
        }
    }
}

/// Размытие того, что под элементом, с заданным радиусом (UIVisualEffectView
/// с подкрученной силой блюра). Тонировка не добавляется — цвет задаёт заливка.
struct BackdropBlur: UIViewRepresentable {
    var radius: CGFloat = 10

    func makeUIView(context: Context) -> UIVisualEffectView {
        let view = UIVisualEffectView(effect: nil)
        context.coordinator.animator = UIViewPropertyAnimator(duration: 1, curve: .linear) {
            view.effect = UIBlurEffect(style: .systemUltraThinMaterialDark)
        }
        // Системный ultraThin ≈ блюр 30; оставляем долю, близкую к радиусу макета.
        context.coordinator.animator?.fractionComplete = min(1, max(0.2, radius / 22))
        context.coordinator.animator?.pausesOnCompletion = true
        return view
    }

    func updateUIView(_ uiView: UIVisualEffectView, context: Context) {}

    static func dismantleUIView(_ uiView: UIVisualEffectView, coordinator: Coordinator) {
        coordinator.animator?.stopAnimation(true)
    }

    func makeCoordinator() -> Coordinator { Coordinator() }
    final class Coordinator { var animator: UIViewPropertyAnimator? }
}
