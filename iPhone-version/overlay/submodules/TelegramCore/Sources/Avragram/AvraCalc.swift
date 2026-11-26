import Foundation

/// Engineering calculator (port of the Android one): brackets, memory, x!, powers, roots, e, π, ln, lg,
/// trigonometry in degrees or radians; the result is shown before "=", history of 30 calculations.
/// Also used by /calc"…" in messages. Only a small own parser: nothing is ever evaluated as code.
public enum AvraCalc {
    public static func evaluate(_ input: String, degrees: Bool = true) -> Double? {
        var p = Parser(Array(normalize(input)), degrees: degrees)
        guard let v = p.expression(), p.done, v.isFinite else { return nil }
        return v
    }

    public static func format(_ v: Double) -> String {
        if v == v.rounded() && abs(v) < 1e15 { return String(format: "%.0f", v) }
        var s = String(format: "%.9f", v)
        while s.hasSuffix("0") { s.removeLast() }
        if s.hasSuffix(".") { s.removeLast() }
        return s.replacingOccurrences(of: ".", with: ",")
    }

    static func normalize(_ s: String) -> String {
        var t = s.replacingOccurrences(of: "×", with: "*").replacingOccurrences(of: "÷", with: "/")
            .replacingOccurrences(of: "−", with: "-").replacingOccurrences(of: ",", with: ".")
            .replacingOccurrences(of: " ", with: "")
        t = t.replacingOccurrences(of: "π", with: "p")
        return t
    }

    private struct Parser {
        let c: [Character]
        let degrees: Bool
        var i = 0
        init(_ c: [Character], degrees: Bool) {
            self.c = c
            self.degrees = degrees
        }
        var done: Bool { i >= c.count }

        func peek() -> Character? { i < c.count ? c[i] : nil }

        mutating func expression() -> Double? {
            guard var v = term() else { return nil }
            while let ch = peek(), ch == "+" || ch == "-" {
                i += 1
                guard let r = term() else { return nil }
                v = ch == "+" ? v + r : v - r
            }
            return v
        }

        mutating func term() -> Double? {
            guard var v = power() else { return nil }
            while let ch = peek(), ch == "*" || ch == "/" || ch == "%" {
                i += 1
                guard let r = power() else { return nil }
                if ch == "*" { v *= r } else if ch == "/" { if r == 0 { return nil }; v /= r } else { v = v.truncatingRemainder(dividingBy: r) }
            }
            return v
        }

        mutating func power() -> Double? {
            guard let base = unary() else { return nil }
            if peek() == "^" {
                i += 1
                guard let e = power() else { return nil }
                return pow(base, e)
            }
            return base
        }

        mutating func unary() -> Double? {
            if peek() == "-" { i += 1; return unary().map { -$0 } }
            if peek() == "+" { i += 1; return unary() }
            return postfix()
        }

        mutating func postfix() -> Double? {
            guard var v = primary() else { return nil }
            while peek() == "!" {
                i += 1
                guard v >= 0, v <= 170, v == v.rounded() else { return nil }
                var f = 1.0
                var k = 2.0
                while k <= v { f *= k; k += 1 }
                v = f
            }
            return v
        }

        mutating func primary() -> Double? {
            guard let ch = peek() else { return nil }
            if ch == "(" {
                i += 1
                let v = expression()
                if peek() == ")" { i += 1 }
                return v
            }
            if ch.isNumber || ch == "." {
                var s = ""
                while let d = peek(), d.isNumber || d == "." { s.append(d); i += 1 }
                if let e = peek(), e == "E" {
                    s.append("e"); i += 1
                    if let sign = peek(), sign == "-" || sign == "+" { s.append(sign); i += 1 }
                    while let d = peek(), d.isNumber { s.append(d); i += 1 }
                }
                return Double(s)
            }
            if ch == "p" { i += 1; return Double.pi }
            if ch == "√" { i += 1; return unary().flatMap { $0 >= 0 ? sqrt($0) : nil } }
            if ch == "∛" { i += 1; return unary().map { cbrt($0) } }
            if ch.isLetter {
                var name = ""
                while let l = peek(), l.isLetter { name.append(l); i += 1 }
                if name == "e" && peek() != "(" { return M_E }
                guard let arg = primary() else { return nil }
                let r = degrees ? arg * Double.pi / 180 : arg
                let toDegrees = degrees ? 180 / Double.pi : 1
                switch name {
                case "sin": return sin(r)
                case "cos": return cos(r)
                case "tan": return tan(r)
                case "asin": return asin(arg) * toDegrees
                case "acos": return acos(arg) * toDegrees
                case "atan": return atan(arg) * toDegrees
                case "ln": return arg > 0 ? log(arg) : nil
                case "lg", "log": return arg > 0 ? log10(arg) : nil
                case "exp": return exp(arg)
                case "sqrt": return arg >= 0 ? sqrt(arg) : nil
                default: return nil
                }
            }
            return nil
        }
    }

    /// Replaces every /calc"…" (also «…») in a message with its result; unchanged if none.
    public static func replaceCommands(_ text: String) -> String {
        let pattern = "/calc[\"«“]([^\"»”]{1,200})[\"»”]"
        guard let re = try? NSRegularExpression(pattern: pattern) else { return text }
        let ns = text as NSString
        var out = text
        for m in re.matches(in: text, range: NSRange(location: 0, length: ns.length)).reversed() {
            let expr = ns.substring(with: m.range(at: 1))
            if let v = evaluate(expr) {
                out = (out as NSString).replacingCharacters(in: m.range, with: format(v))
            }
        }
        return out
    }
}

}


/// /calc"…" in outgoing messages (on unless switched off in Avragram → Fun).
public enum AvraCalcCommand {
    public static var enabled: Bool {
        get { UserDefaults.standard.object(forKey: "avra.calc.command") as? Bool ?? true }
        set { UserDefaults.standard.set(newValue, forKey: "avra.calc.command") }
    }

    /// The text to send: commands replaced by results, or nil when nothing changes.
    public static func process(_ text: String) -> String? {
        guard enabled, text.contains("/calc") else { return nil }
        let out = AvraCalc.replaceCommands(text)
        return out == text ? nil : out
    }
}
