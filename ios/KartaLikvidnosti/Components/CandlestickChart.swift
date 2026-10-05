import SwiftUI

/// Демонстрационный график «свечи» (analytics/CandlestickChart.vue).
struct CandlestickChart: View {
    private struct Candle { let open, close, high, low: Double; let up: Bool }

    // Параметры из макета
    private let symbol = "XAUUSD"
    private let name = "Золото / Доллар США"
    private let price = "4 419,315"
    private let change = "-54,830 (-1,20%)"
    private let time = "20:48:42 UTC+3"
    private let minV: Double = 3_900_000
    private let maxV: Double = 4_750_000
    private let step: Double = 50_000
    private let current: Double = 4_450_000

    private let vbW: CGFloat = 340, vbH: CGFloat = 360
    private let padRight: CGFloat = 58, padBottom: CGFloat = 24

    private let candles: [Candle] = {
        var result: [Candle] = []
        var price = 3_980_000.0
        var seed = 42.0
        func rnd() -> Double { seed = (seed * 9301 + 49297).truncatingRemainder(dividingBy: 233280); return seed / 233280 }
        for _ in 0..<30 {
            let open = price
            let drift = (rnd() - 0.42) * 70000
            let close = max(3_900_000, open + drift)
            let high = max(open, close) + rnd() * 40000
            let low = min(open, close) - rnd() * 40000
            result.append(Candle(open: open, close: close, high: high, low: low, up: close >= open))
            price = close
        }
        return result
    }()

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            VStack(alignment: .leading, spacing: 4) {
                Text(symbol).font(.system(size: Theme.fontMd, weight: .bold)).foregroundStyle(Theme.text)
                HStack(spacing: 6) {
                    Circle().fill(Color(hex: "#d9d9de")).frame(width: 10, height: 10)
                    Text(name).font(.system(size: Theme.fontBase)).foregroundStyle(Theme.textSecondary)
                }
                HStack(spacing: 6) {
                    Text(price).font(.system(size: Theme.fontBase, weight: .semibold)).foregroundStyle(Theme.text)
                    Text(change).font(.system(size: Theme.fontBase)).foregroundStyle(Color(hex: "#ff5a5a"))
                }
                Text("Объём - Тики ▾")
                    .font(.system(size: Theme.fontSm)).foregroundStyle(Theme.textSecondary)
                    .padding(.horizontal, 10).padding(.vertical, 6)
                    .background(Theme.surfaceMuted)
                    .clipShape(RoundedRectangle(cornerRadius: 10))
                    .padding(.top, 4)
            }

            Canvas { ctx, size in
                let s = size.width / vbW
                ctx.scaleBy(x: s, y: s)
                draw(in: &ctx)
            }
            .aspectRatio(vbW / vbH, contentMode: .fit)
            .frame(maxWidth: .infinity)

            HStack {
                Text("Диапазон дат ▾").font(.system(size: Theme.fontBase)).foregroundStyle(Theme.text)
                Spacer()
                Text(time).font(.system(size: Theme.fontBase)).foregroundStyle(Theme.textSecondary)
            }
            .padding(.top, 8)
        }
        .padding(16)
        .background(Theme.surface)
        .clipShape(RoundedRectangle(cornerRadius: Theme.radiusCard, style: .continuous))
    }

    private var plotW: CGFloat { vbW - padRight }
    private var plotH: CGFloat { vbH - padBottom }
    private func y(_ value: Double) -> CGFloat {
        let t = CGFloat((value - minV) / (maxV - minV))
        return plotH - t * plotH
    }

    private func draw(in ctx: inout GraphicsContext) {
        // Сетка + подписи цены справа
        var v = minV
        while v <= maxV {
            let gy = y(v)
            var line = Path()
            line.move(to: CGPoint(x: 0, y: gy)); line.addLine(to: CGPoint(x: plotW, y: gy))
            ctx.stroke(line, with: .color(.white.opacity(0.06)), lineWidth: 1)
            let label = Text(v.formattedRu).font(.system(size: 8)).foregroundColor(.white.opacity(0.5))
            ctx.draw(label, at: CGPoint(x: plotW + 6, y: gy), anchor: .leading)
            v += step
        }

        // Свечи
        let n = candles.count
        let slot = plotW / CGFloat(n)
        let bw = slot * 0.55
        for (i, c) in candles.enumerated() {
            let cx = slot * CGFloat(i) + slot / 2
            let color = c.up ? Color(hex: "#e9e9ee") : Color(hex: "#8a8a92")
            var wick = Path()
            wick.move(to: CGPoint(x: cx, y: y(c.high))); wick.addLine(to: CGPoint(x: cx, y: y(c.low)))
            ctx.stroke(wick, with: .color(color), lineWidth: 1)
            let bodyY = y(max(c.open, c.close))
            let bodyH = max(1, abs(y(c.open) - y(c.close)))
            let body = Path(roundedRect: CGRect(x: cx - bw / 2, y: bodyY, width: bw, height: bodyH), cornerRadius: 1)
            ctx.fill(body, with: .color(color))
        }

        // Текущая цена (пунктир + метка)
        let cy = y(current)
        var dash = Path()
        dash.move(to: CGPoint(x: 0, y: cy)); dash.addLine(to: CGPoint(x: plotW, y: cy))
        ctx.stroke(dash, with: .color(.white.opacity(0.35)), style: StrokeStyle(lineWidth: 1, dash: [3, 3]))
        let tag = Path(roundedRect: CGRect(x: plotW, y: cy - 9, width: padRight, height: 18), cornerRadius: 3)
        ctx.fill(tag, with: .color(Color(hex: "#3a3b40")))
        ctx.draw(Text(current.formattedRu).font(.system(size: 8)).foregroundColor(.white),
                 at: CGPoint(x: plotW + 5, y: cy), anchor: .leading)

        // Подписи месяцев
        for (label, frac) in [("Июль", 0.1), ("Авг", 0.45), ("Сен", 0.78)] {
            ctx.draw(Text(label).font(.system(size: 9)).foregroundColor(.white.opacity(0.6)),
                     at: CGPoint(x: plotW * CGFloat(frac), y: vbH - 10), anchor: .leading)
        }
    }
}

private extension Double {
    var formattedRu: String {
        let f = NumberFormatter()
        f.numberStyle = .decimal
        f.groupingSeparator = "\u{00A0}"
        f.maximumFractionDigits = 0
        return f.string(from: NSNumber(value: self)) ?? "\(Int(self))"
    }
}
