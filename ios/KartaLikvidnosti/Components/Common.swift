import SwiftUI

/// Загруженные картинки в памяти: при повторном показе (возврат на экран, скролл
/// ленты) картинка рисуется сразу, без мигания загрузки.
enum RemoteImageCache {
    static let shared: NSCache<NSURL, UIImage> = {
        let cache = NSCache<NSURL, UIImage>()
        cache.totalCostLimit = 120 * 1024 * 1024
        return cache
    }()

    static func image(for url: URL) -> UIImage? { shared.object(forKey: url as NSURL) }

    static func store(_ image: UIImage, for url: URL) {
        let cost = Int(image.size.width * image.size.height * image.scale * image.scale * 4)
        shared.setObject(image, forKey: url as NSURL, cost: cost)
    }
}

/// Загрузчик одной картинки: кэш проверяется синхронно, поэтому уже загруженная
/// картинка появляется в первом же кадре.
@MainActor
final class RemoteImageLoader: ObservableObject {
    @Published private(set) var image: UIImage?
    @Published private(set) var failed = false
    private var url: URL?
    private var task: Task<Void, Never>?

    init(url: URL?) {
        self.url = url
        if let url { image = RemoteImageCache.image(for: url) }
    }

    func load(_ newURL: URL?) {
        if newURL != url {
            task?.cancel()
            task = nil
            url = newURL
            failed = false
            image = newURL.flatMap(RemoteImageCache.image(for:))
        }
        guard let url, image == nil, !failed, task == nil else { return }
        task = Task { [weak self] in
            do {
                let (data, response) = try await URLSession.shared.data(from: url)
                if let http = response as? HTTPURLResponse, !(200..<300).contains(http.statusCode) {
                    throw URLError(.badServerResponse)
                }
                guard let decoded = UIImage(data: data) else { throw URLError(.cannotDecodeContentData) }
                // Декодируем заранее, чтобы первый кадр с картинкой не подтормаживал.
                let ready = await decoded.byPreparingForDisplay() ?? decoded
                RemoteImageCache.store(ready, for: url)
                guard let self, self.url == url else { return }
                self.image = ready
                self.task = nil
            } catch {
                guard let self, self.url == url, !Task.isCancelled else { return }
                self.failed = true
                self.task = nil
            }
        }
    }
}

/// Мигающий блок на месте загружающейся картинки (обложка, фото, аватар):
/// мягко пульсирует, пока картинка не пришла, — вместо заглушки, которая сменялась бы фото.
struct LoadingBlock: View {
    var body: some View {
        TimelineView(.animation(minimumInterval: 1.0 / 30)) { context in
            let t = context.date.timeIntervalSinceReferenceDate
            let pulse = (sin(t * 2 * .pi / 1.4) + 1) / 2
            Color.white.opacity(0.05 + 0.08 * pulse)
        }
        .allowsHitTesting(false)
    }
}

/// Картинка по URL (аналог <img :src>). Пока грузится — мигающий блок, если не
/// загрузилась — `fallback` (заглушка обложки, буква аватара).
struct RemoteImage<Fallback: View>: View {
    let url: URL?
    var contentMode: ContentMode = .fill
    /// Мигающий блок во время загрузки; false — прозрачно (под картинкой уже есть свой фон).
    var showsLoading: Bool = true
    @ViewBuilder var fallback: () -> Fallback

    @StateObject private var loader: RemoteImageLoader

    init(url: URL?, contentMode: ContentMode = .fill, showsLoading: Bool = true,
         @ViewBuilder fallback: @escaping () -> Fallback) {
        self.url = url
        self.contentMode = contentMode
        self.showsLoading = showsLoading
        self.fallback = fallback
        _loader = StateObject(wrappedValue: RemoteImageLoader(url: url))
    }

    var body: some View {
        ZStack {
            if let image = loader.image {
                Image(uiImage: image).resizable().aspectRatio(contentMode: contentMode)
            } else if url == nil || loader.failed {
                fallback()
            } else if showsLoading {
                LoadingBlock()
            } else {
                Color.clear
            }
        }
        .clipped()
        .allowsHitTesting(false)
        .onAppear { loader.load(url) }
        .onChange(of: url) { loader.load($0) }
    }
}

extension RemoteImage where Fallback == Color {
    init(url: URL?, contentMode: ContentMode = .fill, showsLoading: Bool = true) {
        self.init(url: url, contentMode: contentMode, showsLoading: showsLoading) { Color.clear }
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

/// Круглый аватар: фото по URL; пока фото грузится — мигающий кружок,
/// если фото нет или оно не загрузилось — буква имени на градиенте.
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
            if let url = url {
                // Фото: пока грузится — мигающий кружок, без промелькивания буквы.
                RemoteImage(url: url) { letterView }
                    .frame(width: size, height: size)
                    .clipShape(Circle())
            } else {
                letterView
            }
        }
        .frame(width: size, height: size)
    }

    private var letterView: some View {
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
        }
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
