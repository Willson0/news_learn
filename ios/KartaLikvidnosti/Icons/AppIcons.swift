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

private func fp(_ d: String, opacity: Double = 1, eo: Bool = false, color: Color? = nil) -> IconPart {
    IconPart(geom: .path(d), style: IconStyle(mode: .fill(eoFill: eo), opacity: opacity, color: color))
}
private func sp(_ d: String, _ w: CGFloat = 1.6, cap: CGLineCap = .round, join: CGLineJoin = .round, color: Color? = nil) -> IconPart {
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
    static let home = IconDef(box: b24, parts: [
        fp("M11.3 2.5a1.1 1.1 0 0 1 1.4 0l8 6.7c.3.3.5.7.5 1.1v9.5c0 .9-.7 1.7-1.6 1.7h-3.5c-.6 0-1-.5-1-1v-5.1c0-.6-.5-1-1-1h-3.2c-.6 0-1 .4-1 1V20.5c0 .5-.5 1-1 1H4.4c-.9 0-1.6-.8-1.6-1.7v-9.5c0-.4.2-.8.5-1.1l8-6.7Z")
    ])

    static let analytics = IconDef(box: b24, parts: [
        fp("M5 3c.6 0 1 .4 1 1v15h15c.6 0 1 .4 1 1s-.4 1-1 1H5a2 2 0 0 1-2-2V4c0-.6.4-1 1-1Z"),
        fp("M8.5 13a1 1 0 0 1 1 1v3a1 1 0 1 1-2 0v-3a1 1 0 0 1 1-1Zm4-5a1 1 0 0 1 1 1v8a1 1 0 1 1-2 0V9a1 1 0 0 1 1-1Zm4 3a1 1 0 0 1 1 1v5a1 1 0 1 1-2 0v-5a1 1 0 0 1 1-1Z")
    ])

    static let community = IconDef(box: b24, parts: [
        fp("M9 11a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7Zm7 0a3 3 0 1 0 0-6 3 3 0 0 0 0 6Z"),
        fp("M9 12.5c-3.3 0-6 1.8-6 4.5v1c0 .8.7 1.5 1.5 1.5h9c.8 0 1.5-.7 1.5-1.5v-1c0-2.7-2.7-4.5-6-4.5Zm7.3.1c1.9.6 3.2 1.9 3.6 3.6.2.9-.5 1.7-1.4 1.7h-1.3c.1-.3.2-.7.2-1v-1c0-1.3-.4-2.4-1.1-3.3Z")
    ])

    static let profile = IconDef(box: b24, parts: [
        fp("M12 12a4.5 4.5 0 1 0 0-9 4.5 4.5 0 0 0 0 9Zm0 1.8c-3.9 0-7 2.2-7 5v.7c0 .9.7 1.7 1.6 1.7h10.8c.9 0 1.6-.8 1.6-1.7v-.7c0-2.8-3.1-5-7-5Z")
    ])

    static let plus = IconDef(box: b24, parts: [sp("M12 5v14M5 12h14", 2)])
    static let back = IconDef(box: b24, parts: [sp("M15 5 8 12l7 7", 1.8)])
    static let chevronRight = IconDef(box: b24, parts: [sp("M9 5l7 7-7 7", 1.8)])
    static let close = IconDef(box: b24, parts: [sp("M6 6l12 12M18 6 6 18", 1.8)])

    static let bell = IconDef(box: b24, parts: [
        sp("M18 8a6 6 0 1 0-12 0c0 7-3 9-3 9h18s-3-2-3-9", 1.6),
        sp("M13.7 21a2 2 0 0 1-3.4 0", 1.6)
    ])

    static let calendar = IconDef(box: b24, parts: [
        sRRect(3.5, 5, 17, 15, 3),
        sp("M3.5 9.5h17M8 3.5v3M16 3.5v3", 1.6, join: .miter)
    ])

    static let contact = IconDef(box: b24, parts: [
        sRRect(3, 5, 18, 14, 3),
        sCircle(9, 11, 2),
        sp("M6 16c.5-1.5 1.7-2.2 3-2.2s2.5.7 3 2.2M14.5 10h3.5M14.5 13.5h2.5", 1.6)
    ])

    static let play = IconDef(box: b24, parts: [
        fp("M8 5.5v13a1 1 0 0 0 1.5.9l10-6.5a1 1 0 0 0 0-1.7l-10-6.5A1 1 0 0 0 8 5.5z")
    ])

    static let downloadCloud = IconDef(box: b24, parts: [
        sp("M7 18a4 4 0 0 1-.5-7.97A6 6 0 0 1 18 9.5a3.5 3.5 0 0 1 0 8.5", 1.6),
        sp("M12 11v6m0 0l-2.5-2.5M12 17l2.5-2.5", 1.6)
    ])

    static let eye = IconDef(box: b24, parts: [
        sp("M2.5 12S5.5 5.5 12 5.5 21.5 12 21.5 12 18.5 18.5 12 18.5 2.5 12 2.5 12Z", 1.6),
        sCircle(12, 12, 3)
    ])

    static let eyeClosed = IconDef(box: b24, parts: [
        sp("M3 4.5 21 19.5", 1.6),
        sp("M9.2 5.9A9.9 9.9 0 0 1 12 5.5c6.5 0 9.5 6.5 9.5 6.5a16 16 0 0 1-2.4 3.2M6.3 7.6A15.7 15.7 0 0 0 2.5 12S5.5 18.5 12 18.5a9.6 9.6 0 0 0 3.3-.56", 1.6),
        sp("M9.9 9.9a3 3 0 0 0 4.2 4.2", 1.6)
    ])

    static let filter = IconDef(box: b20, parts: [sp("M3 5h14M5.5 10h9M8.5 15h3", 1.6)])
    static let forward = IconDef(box: b24, parts: [sp("M15 7l5 5-5 5M20 12H9a5 5 0 0 0-5 5v1", 1.6)])
    static let leave = IconDef(box: b24, parts: [
        sp("M14 4h4a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-4", 1.6),
        sp("M10 8l4 4-4 4M14 12H4", 1.6)
    ])
    static let link = IconDef(box: b24, parts: [
        sp("M10 13a4 4 0 0 0 5.7 0l2.3-2.3a4 4 0 0 0-5.7-5.7l-1.3 1.3", 1.6),
        sp("M14 11a4 4 0 0 0-5.7 0L6 13.3a4 4 0 0 0 5.7 5.7l1.3-1.3", 1.6)
    ])
    static let mic = IconDef(box: b24, parts: [
        sRRect(9, 3, 6, 11, 3),
        sp("M5 11a7 7 0 0 0 14 0M12 18v3", 1.6)
    ])
    static let pencil = IconDef(box: b24, parts: [
        sp("M4 20h4L18.5 9.5a2.12 2.12 0 0 0-3-3L5 17v3z", 1.6),
        sp("M13.5 6.5l3 3", 1.6)
    ])
    static let pin = IconDef(box: b24, parts: [
        fp("M14.5 2.5a1 1 0 0 0-1.4 0l-1 1a1 1 0 0 0-.1 1.3l.3.4-3.6 3.6-2.7.5a1 1 0 0 0-.5 1.7l3 3L4 19.9a1 1 0 1 0 1.4 1.4l4.4-4.4 3 3a1 1 0 0 0 1.7-.5l.5-2.7 3.6-3.6.4.3a1 1 0 0 0 1.3-.1l1-1a1 1 0 0 0 0-1.4z")
    ])
    static let report = IconDef(box: b24, parts: [
        sCircle(12, 12, 9),
        sp("M12 7v6M12 16.5v.5", 1.8)
    ])
    static let search = IconDef(box: b24, parts: [
        sCircle(11, 11, 7),
        sp("M20 20l-3.2-3.2", 1.6)
    ])
    static let settings = IconDef(box: b24, parts: [
        sp("M19.4 13a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09a1.65 1.65 0 0 0-1.08-1.51 1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09a1.65 1.65 0 0 0 1.51-1.08 1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z", 1.6),
        sCircle(12, 12, 3)
    ])
    static let sort = IconDef(box: b24, parts: [sp("M4 7h16M6.5 12h11M10 17h4", 1.8)])
    static let trash = IconDef(box: b24, parts: [
        sp("M4 7h16M9 7V5a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2v2M6 7l1 13a2 2 0 0 0 2 2h6a2 2 0 0 0 2-2l1-13", 1.6)
    ])
    static let trashX = IconDef(box: b24, parts: [
        sp("M4 6.5h16M9 6.5V4.8A1.3 1.3 0 0 1 10.3 3.5h3.4A1.3 1.3 0 0 1 15 4.8v1.7M6 6.5l.9 12.6A2 2 0 0 0 8.9 21h6.2a2 2 0 0 0 2-1.9L18 6.5", 1.7),
        sp("m10 11 4 5m0-5-4 5", 1.7)
    ])
    static let copy = IconDef(box: b24, parts: [
        sRRect(9, 9, 11, 11, 2.5),
        sp("M5 15V5a2 2 0 0 1 2-2h8", 1.6)
    ])
    static let checkSquare = IconDef(box: b24, parts: [
        sRRect(4, 4, 16, 16, 4),
        sp("M8 12l3 3 5-6", 1.6)
    ])
    static let reply = IconDef(box: b24, parts: [
        sp("M9 7L4 12l5 5M4 12h11a5 5 0 0 1 5 5v1", 1.6)
    ])
    static let chat = IconDef(box: b24, parts: [
        fp("M4 5a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H9l-4 4v-4H6a2 2 0 0 1-2-2V5Zm4 3.2h8v1.6H8V8.2Zm0 3.2h5.5V13H8v-1.6Z")
    ])
    static let chartBadge = IconDef(box: b24, parts: [
        fp("M7 13.5a1 1 0 0 1 1 1V18a1 1 0 1 1-2 0v-3.5a1 1 0 0 1 1-1Zm5-7a1 1 0 0 1 1 1V18a1 1 0 1 1-2 0V7.5a1 1 0 0 1 1-1Zm5 4a1 1 0 0 1 1 1V18a1 1 0 1 1-2 0v-6.5a1 1 0 0 1 1-1Z")
    ])
    static let doc = IconDef(box: b24, parts: [
        fp("M6 2h7l5 5v13a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2Zm7 1.5V7a1 1 0 0 0 1 1h3.5L13 3.5ZM8 12h8v1.6H8V12Zm0 3.2h8v1.6H8v-1.6ZM8 8.8h4v1.6H8V8.8Z")
    ])
    static let gear = IconDef(box: b24, parts: [
        IconPart(geom: .path("M10.3 2h3.4l.5 2.6c.6.2 1.2.5 1.7.9l2.5-.9 1.7 2.9-2 1.7c.1.6.1 1.2 0 1.8l2 1.7-1.7 2.9-2.5-.9c-.5.4-1.1.7-1.7.9l-.5 2.6h-3.4l-.5-2.6c-.6-.2-1.2-.5-1.7-.9l-2.5.9-1.7-2.9 2-1.7a5.6 5.6 0 0 1 0-1.8l-2-1.7 1.7-2.9 2.5.9c.5-.4 1.1-.7 1.7-.9L10.3 2Zm1.7 6.6a3.4 3.4 0 1 0 0 6.8 3.4 3.4 0 0 0 0-6.8Z"),
                 style: IconStyle(mode: .fill(eoFill: true)), translate: CGSize(width: 0, height: 1.2))
    ])

    // --- Материалы (box 16) ---
    static let matGold = IconDef(box: b16, parts: [
        fp("M6 3h4l1.2 2.4H4.8L6 3Z", opacity: 0.9),
        fp("M3.4 6.2h4.2l1.1 2.3H2.2l1.2-2.3Z"),
        fp("M8.4 6.2h4.2l1.2 2.3H7.3l1.1-2.3Z"),
        fp("M5.9 9.4h4.2l1.2 2.4H4.7l1.2-2.4Z")
    ])
    static let matOil = IconDef(box: b16, parts: [
        fp("M8 1.5s4.5 4.8 4.5 7.8A4.5 4.5 0 0 1 8 14.5 4.5 4.5 0 0 1 3.5 9.3C3.5 6.3 8 1.5 8 1.5Z")
    ])
    static let matUsd = IconDef(box: b16, parts: [
        sp("M10 5.2C9.5 4.3 8.8 4 8 4c-1.2 0-2.2.7-2.2 1.7 0 2.4 4.6 1.2 4.6 3.7 0 1.1-1 1.8-2.4 1.8-1 0-1.9-.4-2.4-1.3", 1.3),
        sp("M8 2.6v10.8", 1.3)
    ])
    static let matEur = IconDef(box: b16, parts: [
        sp("M11 4.8A4 4 0 0 0 8 3.5c-2.5 0-4 2-4 4.5s1.5 4.5 4 4.5a4 4 0 0 0 3-1.3", 1.3),
        sp("M3 7h6M3 9.3h6", 1.3)
    ])
    static let matGas = IconDef(box: b16, parts: [
        fp("M9 1.5c.3 2-1 2.8-2 3.9C5.8 6.6 5 7.8 5 9.4A3.9 3.9 0 0 0 11.8 12c.6-1 .7-2.3.2-3.4-.4.5-1 .8-1.6.8.8-1.6.5-3.8-1.4-5.9Z")
    ])
    static let matBtc = IconDef(box: b16, parts: [
        fp("M10.4 7.3c.5-.3.8-.8.8-1.5 0-1.1-.8-1.8-2-2V2.3h-1.2v1.4H7.2V2.3H6v1.5H3.9v1.2H5v5.9H3.9v1.2H6v1.5h1.2v-1.5h.8v1.5h1.2v-1.5c1.5-.1 2.5-.8 2.5-2.1 0-.9-.5-1.5-1.3-1.7ZM6.9 5h1.6c.7 0 1.1.3 1.1.9s-.4.9-1.1.9H6.9V5Zm1.9 5H6.9V8.1h1.9c.8 0 1.2.3 1.2 1s-.4.9-1.2.9Z", eo: true)
    ])

    /// Иконка материала по ключу инструмента (аналог materialIcon.js).
    static func material(_ key: String?) -> IconDef {
        switch key {
        case "gold", "silver", "platinum": return matGold
        case "wti", "brent": return matOil
        case "usd": return matUsd
        case "eur", "eurusd": return matEur
        case "gas": return matGas
        case "btc": return matBtc
        default: return matOil
        }
    }
}
