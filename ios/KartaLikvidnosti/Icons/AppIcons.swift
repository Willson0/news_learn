import SwiftUI
import CoreGraphics

// Геометрия одной части иконки.
enum IconGeom {
    case path(String)
    case rrect(CGFloat, CGFloat, CGFloat, CGFloat, CGFloat) // x,y,w,h,r
    case circle(CGFloat, CGFloat, CGFloat)                  // cx,cy,r
}

struct IconStyle {
    enum Mode {
        case fill(eoFill: Bool)
        case stroke(width: CGFloat, cap: CGLineCap, join: CGLineJoin)
    }
    var mode: Mode
    var opacity: Double = 1
    var color: Color? = nil // nil = currentColor
}

struct IconPart {
    let geom: IconGeom
    let style: IconStyle
    var translate: CGSize = .zero
}

struct IconDef {
    let box: CGSize
    let parts: [IconPart]
}

// MARK: - Shape, рисующий часть иконки с масштабированием в заданный rect.

private struct IconPartShape: Shape {
    let geom: IconGeom
    let box: CGSize
    let translate: CGSize

    func path(in rect: CGRect) -> Path {
        var base = Path()
        switch geom {
        case .path(let d):
            base = SVGPath.path(from: d)
        case .rrect(let x, let y, let w, let h, let r):
            base.addRoundedRect(in: CGRect(x: x, y: y, width: w, height: h),
                                cornerSize: CGSize(width: r, height: r), style: .continuous)
        case .circle(let cx, let cy, let r):
            base.addEllipse(in: CGRect(x: cx - r, y: cy - r, width: 2 * r, height: 2 * r))
        }
        let s = min(rect.width / box.width, rect.height / box.height)
        let tx = (rect.width - box.width * s) / 2 + translate.width * s
        let ty = (rect.height - box.height * s) / 2 + translate.height * s
        let transform = CGAffineTransform(scaleX: s, y: s)
            .concatenating(CGAffineTransform(translationX: tx, y: ty))
        return base.applying(transform)
    }
}

// MARK: - Вьюха иконки

struct Icon: View {
    let def: IconDef
    var size: CGFloat = 24
    var color: Color = Theme.text

    init(_ def: IconDef, size: CGFloat = 24, color: Color = Theme.text) {
        self.def = def; self.size = size; self.color = color
    }

    var body: some View {
        let s = min(size / def.box.width, size / def.box.height)
        ZStack {
            ForEach(Array(def.parts.enumerated()), id: \.offset) { _, part in
                let shape = IconPartShape(geom: part.geom, box: def.box, translate: part.translate)
                let c = (part.style.color ?? color)
                switch part.style.mode {
                case .fill(let eo):
                    shape.fill(c, style: FillStyle(eoFill: eo)).opacity(part.style.opacity)
                case .stroke(let w, let cap, let join):
                    shape.stroke(style: StrokeStyle(lineWidth: w * s, lineCap: cap, lineJoin: join))
                        .fill(c)
                        .opacity(part.style.opacity)
                }
            }
        }
        .frame(width: size, height: size)
    }
}

// MARK: - Хелперы для компактной записи

func fp(_ d: String, opacity: Double = 1, eo: Bool = false, color: Color? = nil) -> IconPart {
    IconPart(geom: .path(d), style: IconStyle(mode: .fill(eoFill: eo), opacity: opacity, color: color))
}
func sp(_ d: String, _ w: CGFloat = 1.6, cap: CGLineCap = .round, join: CGLineJoin = .round, color: Color? = nil) -> IconPart {
    IconPart(geom: .path(d), style: IconStyle(mode: .stroke(width: w, cap: cap, join: join), color: color))
}
private func sRRect(_ x: CGFloat, _ y: CGFloat, _ w: CGFloat, _ h: CGFloat, _ r: CGFloat, _ sw: CGFloat = 1.6) -> IconPart {
    IconPart(geom: .rrect(x, y, w, h, r), style: IconStyle(mode: .stroke(width: sw, cap: .round, join: .round)))
}
private func sCircle(_ cx: CGFloat, _ cy: CGFloat, _ r: CGFloat, _ sw: CGFloat = 1.6) -> IconPart {
    IconPart(geom: .circle(cx, cy, r), style: IconStyle(mode: .stroke(width: sw, cap: .round, join: .round)))
}
private let b24 = CGSize(width: 24, height: 24)
private let b20 = CGSize(width: 20, height: 20)
private let b16 = CGSize(width: 16, height: 16)

// MARK: - Реестр иконок (перенос SVG из frontend/src/components/icons)

enum AppIcons {
    // Табы — иконки навбара из макета.
    static let home = navHome
    static let analytics = navAnalytics
    static let community = navCommunity
    static let profile = navProfile
    static let gear = settings

    /// Крестик 12 из макета (закреп, диапазон дат, цитата ответа).
    static let close = IconDef(box: CGSize(width: 13, height: 13),
                               parts: [sp("M0.353516 0.353516L12.3535 12.3535M12.3535 0.353516L0.353516 12.3535", 1)])

    /// icon-park-outline:return из макета (переслать в режиме выбора).
    static let forward = IconDef(box: b24, parts: [
        sp("M16.5 4L20 7L16.5 10.5", 1.5),
        sp("M20 7H9.78035C6.72121 7 4.12341 9.59385 4.0043 12.7692C3.87808 16.1246 6.54788 19 9.78035 19H17.3342", 1.5),
    ])

    // --- Материалы (box 16) ---

    /// Иконка материала по ключу инструмента (аналог materialIcon.js).
    static func material(_ key: String?) -> IconDef {
        switch key {
        case "gold": return matGold
        case "silver": return matSilver
        case "platinum", "plat": return matPlat
        case "wti", "brent": return matOil
        case "usd": return matUsd
        case "eur", "eurusd": return matEur
        case "gas": return matGas
        case "btc": return matBtc
        default: return matOil
        }
    }
}
