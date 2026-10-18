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

    static let chevronRight = IconDef(box: b24, parts: [sp("M9 5l7 7-7 7", 1.8)])
    static let close = IconDef(box: b24, parts: [sp("M6 6l12 12M18 6 6 18", 1.8)])

    static let calendar = IconDef(box: b24, parts: [
        sRRect(3.5, 5, 17, 15, 3),
        sp("M3.5 9.5h17M8 3.5v3M16 3.5v3", 1.6, join: .miter)
    ])

    static let contact = IconDef(box: b24, parts: [
        sRRect(3, 5, 18, 14, 3),
        sCircle(9, 11, 2),
        sp("M6 16c.5-1.5 1.7-2.2 3-2.2s2.5.7 3 2.2M14.5 10h3.5M14.5 13.5h2.5", 1.6)
    ])

    static let forward = IconDef(box: b24, parts: [sp("M15 7l5 5-5 5M20 12H9a5 5 0 0 0-5 5v1", 1.6)])
    static let sort = IconDef(box: b24, parts: [sp("M4 7h16M6.5 12h11M10 17h4", 1.8)])
    static let checkSquare = IconDef(box: b24, parts: [
        sRRect(4, 4, 16, 16, 4),
        sp("M8 12l3 3 5-6", 1.6)
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
