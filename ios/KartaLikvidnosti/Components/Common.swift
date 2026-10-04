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
        } else {
            Color.clear
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

/// Круглый аватар: фото по URL либо зеленоватый градиент-заглушка.
struct AvatarCircle: View {
    var url: URL? = nil
    var size: CGFloat = 56
    var initial: String? = nil

    var body: some View {
        ZStack {
            if let url = url {
                RemoteImage(url: url).frame(width: size, height: size).clipShape(Circle())
            } else {
                Circle().fill(
                    LinearGradient(colors: [Color(hex: "#6b7350"), Color(hex: "#3f4a2c")],
                                   startPoint: .topLeading, endPoint: .bottomTrailing)
                )
                if let initial = initial, !initial.isEmpty {
                    Text(initial)
                        .font(Theme.heading(size * 0.4))
                        .foregroundStyle(Theme.text)
                }
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
                .font(.system(size: size * 0.23, weight: .bold))
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
