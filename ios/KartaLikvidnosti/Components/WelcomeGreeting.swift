import SwiftUI

/// Приветствие при входе в приложение: «Доброе утро, Никита» и т. п. по местному времени.
enum Greeting {
    /// После регистрации уже было «Приветствуем, имя!» — второе приветствие подряд не нужно.
    static var skipNext = false

    static func text(name: String, date: Date = Date()) -> String {
        let hour = Calendar.current.component(.hour, from: date)
        let part: String
        switch hour {
        case 5..<12: part = "Доброе утро"
        case 12..<17: part = "Добрый день"
        case 17..<23: part = "Добрый вечер"
        default: part = "Доброй ночи"
        }
        let first = name.split(separator: " ").first.map(String.init) ?? name
        return first.isEmpty ? part : "\(part), \(first)"
    }
}

/// Полноэкранное приветствие в стиле экрана «Приветствуем, имя!» (заголовок 24 на фоне
/// приложения). Текст печатается: буквы по очереди проявляются мягким фейдом, раскладка
/// не прыгает (ещё не напечатанные буквы просто прозрачны). Потом пауза, и экран аккуратно
/// растворяется: текст чуть всплывает и размывается, фон тает, под ним — приложение.
/// Тап в любом месте закрывает сразу.
struct WelcomeGreetingView: View {
    let text: String
    let onFinish: () -> Void

    @State private var start: Date?
    @State private var leaving = false

    private static let delay = 0.25      // пауза перед первой буквой
    private static let perChar = 0.045   // шаг печати
    private static let fade = 0.22       // проявление одной буквы
    private static let hold = 1.3        // сколько текст стоит целиком
    private static let exit = 0.55       // растворение

    var body: some View {
        TimelineView(.animation(paused: leaving)) { ctx in
            Text(typed(at: start.map { ctx.date.timeIntervalSince($0) } ?? 0))
                .font(Theme.heading(Theme.fontTitle))
                .multilineTextAlignment(.center)
                .padding(.horizontal, Theme.screenX)
        }
        .offset(y: leaving ? -10 : 0)
        .blur(radius: leaving ? 6 : 0)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(ScreenBackdrop())
        .opacity(leaving ? 0 : 1)
        .ignoresSafeArea()
        .contentShape(Rectangle())
        .onTapGesture { leave() }
        .accessibilityElement()
        .accessibilityLabel(text)
        .task {
            start = Date()
            let typing = Self.delay + Double(text.count) * Self.perChar + Self.fade
            try? await Task.sleep(nanoseconds: UInt64((typing + Self.hold) * 1_000_000_000))
            leave()
        }
    }

    private func typed(at t: Double) -> AttributedString {
        var out = AttributedString()
        for (i, ch) in text.enumerated() {
            var a = AttributedString(String(ch))
            let p = min(max((t - Self.delay - Double(i) * Self.perChar) / Self.fade, 0), 1)
            a.foregroundColor = Theme.text.opacity(p)
            out += a
        }
        return out
    }

    private func leave() {
        guard !leaving else { return }
        withAnimation(.easeInOut(duration: Self.exit)) { leaving = true }
        DispatchQueue.main.asyncAfter(deadline: .now() + Self.exit) { onFinish() }
    }
}
