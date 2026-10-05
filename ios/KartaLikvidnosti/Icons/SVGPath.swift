import SwiftUI
import CoreGraphics

/// Минимальный парсер атрибута `d` SVG-пути в SwiftUI Path.
/// Поддерживает команды M m L l H h V v C c S s Q q T t A a Z z — всё, что
/// встречается в иконках веб-фронта. Это даёт попиксельно точные иконки.
enum SVGPath {
    static func path(from d: String) -> Path {
        var path = Path()
        let tokens = tokenize(d)
        var i = 0
        var current = CGPoint.zero
        var start = CGPoint.zero
        var lastControl: CGPoint?
        var lastCmd: Character = " "

        func readNum() -> CGFloat? {
            guard i < tokens.count, case let .number(v) = tokens[i] else { return nil }
            i += 1
            return CGFloat(v)
        }

        while i < tokens.count {
            var cmd: Character
            if case let .command(c) = tokens[i] {
                cmd = c
                i += 1
            } else {
                // Повтор предыдущей команды для последующих наборов координат.
                cmd = implicitRepeat(of: lastCmd)
            }
            let relative = cmd.isLowercase
            let upper = Character(cmd.uppercased())

            switch upper {
            case "M":
                guard let x = readNum(), let y = readNum() else { i = tokens.count; break }
                var p = CGPoint(x: x, y: y)
                if relative { p = CGPoint(x: current.x + x, y: current.y + y) }
                path.move(to: p)
                current = p; start = p; lastControl = nil
            case "L":
                guard let x = readNum(), let y = readNum() else { i = tokens.count; break }
                var p = CGPoint(x: x, y: y)
                if relative { p = CGPoint(x: current.x + x, y: current.y + y) }
                path.addLine(to: p); current = p; lastControl = nil
            case "H":
                guard let x = readNum() else { i = tokens.count; break }
                let nx = relative ? current.x + x : x
                let p = CGPoint(x: nx, y: current.y)
                path.addLine(to: p); current = p; lastControl = nil
            case "V":
                guard let y = readNum() else { i = tokens.count; break }
                let ny = relative ? current.y + y : y
                let p = CGPoint(x: current.x, y: ny)
                path.addLine(to: p); current = p; lastControl = nil
            case "C":
                guard let x1 = readNum(), let y1 = readNum(), let x2 = readNum(), let y2 = readNum(),
                      let x = readNum(), let y = readNum() else { i = tokens.count; break }
                let c1 = pt(x1, y1, current, relative)
                let c2 = pt(x2, y2, current, relative)
                let p = pt(x, y, current, relative)
                path.addCurve(to: p, control1: c1, control2: c2)
                lastControl = c2; current = p
            case "S":
                guard let x2 = readNum(), let y2 = readNum(), let x = readNum(), let y = readNum() else { i = tokens.count; break }
                let c1 = reflected(lastControl, about: current, afterCubic: "CS".contains(Character(lastCmd.uppercased())))
                let c2 = pt(x2, y2, current, relative)
                let p = pt(x, y, current, relative)
                path.addCurve(to: p, control1: c1, control2: c2)
                lastControl = c2; current = p
            case "Q":
                guard let x1 = readNum(), let y1 = readNum(), let x = readNum(), let y = readNum() else { i = tokens.count; break }
                let c = pt(x1, y1, current, relative)
                let p = pt(x, y, current, relative)
                path.addQuadCurve(to: p, control: c)
                lastControl = c; current = p
            case "T":
                guard let x = readNum(), let y = readNum() else { i = tokens.count; break }
                let c = reflected(lastControl, about: current, afterCubic: "QT".contains(Character(lastCmd.uppercased())))
                let p = pt(x, y, current, relative)
                path.addQuadCurve(to: p, control: c)
                lastControl = c; current = p
            case "A":
                guard let rx = readNum(), let ry = readNum(), let rot = readNum(),
                      let laf = readNum(), let sf = readNum(), let x = readNum(), let y = readNum() else { i = tokens.count; break }
                let p = pt(x, y, current, relative)
                addArc(&path, from: current, to: p, rx: rx, ry: ry,
                       xRotDeg: rot, largeArc: laf != 0, sweep: sf != 0)
                current = p; lastControl = nil
            case "Z":
                path.closeSubpath(); current = start; lastControl = nil
            default:
                i = tokens.count
            }
            lastCmd = cmd
        }
        return path
    }

    private static func pt(_ x: CGFloat, _ y: CGFloat, _ current: CGPoint, _ relative: Bool) -> CGPoint {
        relative ? CGPoint(x: current.x + x, y: current.y + y) : CGPoint(x: x, y: y)
    }

    private static func reflected(_ control: CGPoint?, about p: CGPoint, afterCubic: Bool) -> CGPoint {
        guard afterCubic, let c = control else { return p }
        return CGPoint(x: 2 * p.x - c.x, y: 2 * p.y - c.y)
    }

    private static func implicitRepeat(of cmd: Character) -> Character {
        // После M последующие пары координат трактуются как L (как в SVG).
        if cmd == "M" { return "L" }
        if cmd == "m" { return "l" }
        return cmd
    }

    // MARK: - Токенизация

    private enum Token { case command(Character); case number(Double) }

    private static func tokenize(_ d: String) -> [Token] {
        var tokens: [Token] = []
        let chars = Array(d)
        var i = 0
        func isCmd(_ c: Character) -> Bool { "MmLlHhVvCcSsQqTtAaZz".contains(c) }
        while i < chars.count {
            let c = chars[i]
            if isCmd(c) {
                tokens.append(.command(c)); i += 1
            } else if c == "," || c == " " || c == "\n" || c == "\t" || c == "\r" {
                i += 1
            } else if c.isNumber || c == "-" || c == "+" || c == "." {
                var s = ""
                // Знак
                if c == "-" || c == "+" { s.append(c); i += 1 }
                var seenDot = false
                var seenExp = false
                while i < chars.count {
                    let ch = chars[i]
                    if ch.isNumber {
                        s.append(ch); i += 1
                    } else if ch == "." && !seenDot && !seenExp {
                        seenDot = true; s.append(ch); i += 1
                    } else if (ch == "e" || ch == "E") && !seenExp {
                        seenExp = true; s.append(ch); i += 1
                        if i < chars.count, chars[i] == "-" || chars[i] == "+" { s.append(chars[i]); i += 1 }
                    } else {
                        break
                    }
                }
                if let v = Double(s) { tokens.append(.number(v)) }
            } else {
                i += 1
            }
        }
        return tokens
    }

    // MARK: - Эллиптическая дуга → кубические кривые

    private static func addArc(_ path: inout Path, from p0: CGPoint, to p1: CGPoint,
                               rx rxIn: CGFloat, ry ryIn: CGFloat,
                               xRotDeg: CGFloat, largeArc: Bool, sweep: Bool) {
        if rxIn == 0 || ryIn == 0 { path.addLine(to: p1); return }
        var rx = abs(rxIn), ry = abs(ryIn)
        let phi = xRotDeg * .pi / 180
        let cosP = cos(phi), sinP = sin(phi)

        let dx = (p0.x - p1.x) / 2, dy = (p0.y - p1.y) / 2
        let x1p = cosP * dx + sinP * dy
        let y1p = -sinP * dx + cosP * dy

        var lambda = (x1p * x1p) / (rx * rx) + (y1p * y1p) / (ry * ry)
        if lambda > 1 {
            let s = sqrt(lambda)
            rx *= s; ry *= s
            lambda = 1
        }

        let sign: CGFloat = (largeArc != sweep) ? 1 : -1
        let num = max(0, rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p)
        let den = rx * rx * y1p * y1p + ry * ry * x1p * x1p
        let co = sign * sqrt(den == 0 ? 0 : num / den)
        let cxp = co * (rx * y1p / ry)
        let cyp = co * (-ry * x1p / rx)

        let cx = cosP * cxp - sinP * cyp + (p0.x + p1.x) / 2
        let cy = sinP * cxp + cosP * cyp + (p0.y + p1.y) / 2

        func angle(_ ux: CGFloat, _ uy: CGFloat, _ vx: CGFloat, _ vy: CGFloat) -> CGFloat {
            let dot = ux * vx + uy * vy
            let len = sqrt((ux * ux + uy * uy) * (vx * vx + vy * vy))
            var a = acos(min(1, max(-1, dot / len)))
            if ux * vy - uy * vx < 0 { a = -a }
            return a
        }

        let theta1 = angle(1, 0, (x1p - cxp) / rx, (y1p - cyp) / ry)
        var dTheta = angle((x1p - cxp) / rx, (y1p - cyp) / ry, (-x1p - cxp) / rx, (-y1p - cyp) / ry)
        if !sweep && dTheta > 0 { dTheta -= 2 * .pi }
        if sweep && dTheta < 0 { dTheta += 2 * .pi }

        let segments = Int(ceil(abs(dTheta) / (.pi / 2)))
        let delta = dTheta / CGFloat(segments)
        let t = 4.0 / 3.0 * tan(delta / 4)

        var angleStart = theta1
        for _ in 0..<max(segments, 1) {
            let a1 = angleStart
            let a2 = angleStart + delta
            let cosA1 = cos(a1), sinA1 = sin(a1)
            let cosA2 = cos(a2), sinA2 = sin(a2)

            func toUser(_ ex: CGFloat, _ ey: CGFloat) -> CGPoint {
                CGPoint(x: cx + cosP * (rx * ex) - sinP * (ry * ey),
                        y: cy + sinP * (rx * ex) + cosP * (ry * ey))
            }
            let end = toUser(cosA2, sinA2)
            let c1 = toUser(cosA1 - t * sinA1, sinA1 + t * cosA1)
            let c2 = toUser(cosA2 + t * sinA2, sinA2 - t * cosA2)
            path.addCurve(to: end, control1: c1, control2: c2)
            angleStart = a2
        }
    }
}
