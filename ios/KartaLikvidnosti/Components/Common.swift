import SwiftUI

/// Загрузка картинки по URL с плейсхолдером (аналог <img :src>).
struct RemoteImage: View {
    let url: URL?
    var contentMode: ContentMode = .fill

    var body: some View {
        if let url = url {
            AsyncImage(url: url) { phase in
                switch phase {
                case .success(let image):
                    image.resizable().aspectRatio(contentMode: contentMode)
                default:
                    Color.clear
                }
            }
            .clipped()
            .allowsHitTesting(false)
        } else {
            Color.clear
                .allowsHitTesting(false)
        }
    }
}

/// «Голографическая фольга» обложки отчёта — приближение из макета.
struct HoloFoil: View {
    var body: some View {
        ZStack {
            LinearGradient(
                stops: [
                    .init(color: Color(hex: "#c9c6d6"), location: 0.0),
                    .init(color: Color(hex: "#a9adbd"), location: 0.18),
                    .init(color: Color(hex: "#cfc3cf"), location: 0.34),
                    .init(color: Color(hex: "#b7c2bf"), location: 0.52),
                    .init(color: Color(hex: "#c7bcc9"), location: 0.70),
                    .init(color: Color(hex: "#a7adba"), location: 0.86),
                    .init(color: Color(hex: "#c4c7d2"), location: 1.0),
                ],
                startPoint: .topLeading, endPoint: .bottomTrailing
            )
            RadialGradient(colors: [Color(hex: "#be96aa").opacity(0.5), .clear],
                           center: .init(x: 0.25, y: 0.2), startRadius: 0, endRadius: 160)
            RadialGradient(colors: [Color(hex: "#96aa96").opacity(0.5), .clear],
                           center: .init(x: 0.8, y: 0.7), startRadius: 0, endRadius: 160)
        }
    }
}

/// Круглый аватар: фото по URL поверх градиента с первой буквой имени.
/// Пока фото грузится (или если его нет / не загрузилось) — видна буква,
/// а не пустой кружок-заглушка.
struct AvatarCircle: View {
    var url: URL? = nil
    var size: CGFloat = 56
    var initial: String? = nil
    /// Имя/название, из которого берётся буква, если `initial` не задан.
    var name: String? = nil

    private var letter: String {
        if let initial = initial, !initial.isEmpty { return initial }
        guard let ch = name?.first(where: { $0.isLetter || $0.isNumber }) else { return "" }
        return String(ch).uppercased()
    }

    var body: some View {
        ZStack {
            Circle().fill(
                LinearGradient(colors: [Color(hex: "#6b7350"), Color(hex: "#3f4a2c")],
                               startPoint: .topLeading, endPoint: .bottomTrailing)
            )
            if !letter.isEmpty {
                Text(letter)
                    .font(Theme.heading(size * 0.4))
                    .foregroundStyle(Theme.text)
            }
            if let url = url {
                RemoteImage(url: url).frame(width: size, height: size).clipShape(Circle())
            }
        }
        .frame(width: size, height: size)
    }
}

/// Иконка html-файла (отдельная, т.к. содержит текст) — из IconFileHtml.vue.
struct FileHtmlIcon: View {
    var size: CGFloat = 32
    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: size * 0.08)
                .fill(Color(hex: "#7ff5ef"))
            Text("html")
                .font(Theme.sans(size * 0.23, .bold))
                .foregroundStyle(.white)
                .padding(.horizontal, size * 0.04)
                .padding(.vertical, size * 0.06)
                .frame(maxWidth: .infinity)
                .background(Color.black)
                .offset(y: size * 0.12)
                .frame(width: size * 0.78)
        }
        .frame(width: size, height: size)
        .clipShape(RoundedRectangle(cornerRadius: size * 0.08))
    }
}

/// Пресс-эффект для кликабельных элементов (аналог :active { opacity }).
struct PressableStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.opacity(configuration.isPressed ? 0.85 : 1)
    }
}

// MARK: - Скелетоны загрузки (чтобы вёрстка не прыгала)

/// Лёгкий «shimmer» — бегущий блик поверх плейсхолдера.
///
/// Фаза считается от времени в `TimelineView`, а не через
/// `withAnimation(.repeatForever)`: бесконечная анимация в `onAppear` захватывала
/// и удаление скелетона из иерархии — заглушки «зависали» поверх реального контента.
struct Shimmer<S: Shape>: ViewModifier {
    /// Форма заглушки: блик режется по ней, а не по прямоугольнику (иначе выходит за скругления).
    let shape: S
    private let period: Double = 1.2

    func body(content: Content) -> some View {
        content
            .overlay(
                TimelineView(.animation) { context in
                    let t = context.date.timeIntervalSinceReferenceDate
                    let phase = CGFloat(t.truncatingRemainder(dividingBy: period) / period) * 2 - 1
                    GeometryReader { geo in
                        LinearGradient(
                            colors: [.clear, Color.white.opacity(0.14), .clear],
                            startPoint: .leading, endPoint: .trailing
                        )
                        .frame(width: geo.size.width * 0.5)
                        .offset(x: phase * geo.size.width * 1.5)
                    }
                }
                .allowsHitTesting(false)
            )
            .clipShape(shape)
    }
}

extension View {
    func shimmer<S: Shape>(_ shape: S) -> some View { modifier(Shimmer(shape: shape)) }
}

/// Прямоугольник-заглушка с лёгким shimmer.
struct SkeletonBlock: View {
    var width: CGFloat? = nil
    var height: CGFloat
    var radius: CGFloat = 10

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: radius, style: .continuous)
        shape
            .fill(Color.white.opacity(0.07))
            .frame(width: width, height: height)
            .shimmer(shape)
    }
}

/// Заглушка карточки отчёта (лента/аналитика/история).
struct ReportCardSkeleton: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            SkeletonBlock(height: 162, radius: Theme.radiusCard)
            VStack(alignment: .leading, spacing: 12) {
                SkeletonBlock(width: 220, height: 16)
                SkeletonBlock(width: 90, height: 12)
                SkeletonBlock(width: 280, height: 12)
                SkeletonBlock(width: 240, height: 12)
            }
            .padding(.horizontal, 16)
            SkeletonBlock(height: 48, radius: Theme.radiusInput).padding(.horizontal, 16)
        }
        .padding(.bottom, 16)
        .background(Theme.surface)
        .clipShape(RoundedRectangle(cornerRadius: Theme.radiusCard, style: .continuous))
    }
}

/// Заглушка строки чата в сообществе.
struct ChatRowSkeleton: View {
    var body: some View {
        HStack(spacing: 12) {
            Circle().fill(Color.white.opacity(0.07)).frame(width: 56, height: 56).shimmer(Circle())
            VStack(alignment: .leading, spacing: 8) {
                SkeletonBlock(width: 140, height: 14)
                SkeletonBlock(width: 220, height: 12)
            }
            Spacer()
        }
        .padding(.vertical, 12)
    }
}

/// Заглушка пузыря сообщения в чате.
struct MessageSkeleton: View {
    var mine: Bool = false
    var wide: Bool = false

    var body: some View {
        HStack {
            if mine { Spacer(minLength: 40) }
            SkeletonBlock(width: wide ? 230 : 150, height: 44, radius: 16)
            if !mine { Spacer(minLength: 40) }
        }
    }
}
