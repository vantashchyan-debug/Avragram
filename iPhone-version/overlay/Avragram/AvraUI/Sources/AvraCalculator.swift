import Foundation
import SwiftUI
import SGSwiftUI
import AvraCore

struct AvraCalculatorScreen: View {
    @Environment(\.sgTheme) var theme
    @State var input = ""
    @State var memory = 0.0
    @State var history: [String] = UserDefaults.standard.stringArray(forKey: "avra.calc.history") ?? []
    @State var inverse = false
    @State var degrees = true

    private var preview: String {
        AvraCalc.degrees = degrees
        return AvraCalc.evaluate(input).map { "= " + AvraCalc.format($0) } ?? " "
    }

    var body: some View {
        VStack(spacing: 8) {
            ScrollView {
                VStack(alignment: .trailing, spacing: 4) {
                    ForEach(Array(history.suffix(30).enumerated()), id: \.offset) { item in
                        Text(item.element).font(.footnote).foregroundColor(theme.secondaryTextColor)
                    }
                }.frame(maxWidth: .infinity, alignment: .trailing)
            }
            .frame(maxHeight: 120)
            Text(input.isEmpty ? "0" : input).font(.system(size: 34, weight: .semibold, design: .rounded))
                .lineLimit(2).minimumScaleFactor(0.4).frame(maxWidth: .infinity, alignment: .trailing)
            Text(preview).font(.title3).foregroundColor(theme.accentColor).frame(maxWidth: .infinity, alignment: .trailing)
            let rows: [[String]] = [
                [degrees ? "DEG" : "RAD", "Inv", "MC", "M+", "M−", "MR"],
                [inverse ? "asin" : "sin", inverse ? "acos" : "cos", inverse ? "atan" : "tan", "ln", "lg", "e"],
                ["x²", "x³", "xʸ", "√", "∛", "x!"],
                ["(", ")", "π", "1/x", "C", "⌫"],
                ["7", "8", "9", "÷", "eˣ", "%"],
                ["4", "5", "6", "×", "ʸ√x", "±"],
                ["1", "2", "3", "−", "", ""],
                ["0", ",", "=", "+", "", ""]
            ]
            VStack(spacing: 6) {
                ForEach(0..<rows.count, id: \.self) { r in
                    HStack(spacing: 6) {
                        ForEach(rows[r].filter { !$0.isEmpty }, id: \.self) { key in
                            Button(action: { press(key) }) {
                                Text(key).font(.system(size: 17, weight: .semibold)).frame(maxWidth: .infinity, minHeight: 40)
                                    .background(RoundedRectangle(cornerRadius: 10).fill(key == "=" ? theme.accentColor : theme.secondaryBackgroundColor))
                                    .foregroundColor(key == "=" ? theme.accentForegroundColor : theme.primaryTextColor)
                            }.buttonStyle(.plain)
                        }
                    }
                }
            }
        }
        .padding(12)
        .background(theme.backgroundColor.ignoresSafeArea())
        .sgNavigationTitle("Калькулятор")
    }

    private func press(_ k: String) {
        AvraCalc.degrees = degrees
        switch k {
        case "DEG", "RAD": degrees.toggle()
        case "Inv": inverse.toggle()
        case "MC": memory = 0
        case "M+": memory += AvraCalc.evaluate(input) ?? 0
        case "M−": memory -= AvraCalc.evaluate(input) ?? 0
        case "MR": input += AvraCalc.format(memory).replacingOccurrences(of: ",", with: ".")
        case "C": input = ""
        case "⌫": if !input.isEmpty { input.removeLast() }
        case "x²": input += "^2"
        case "x³": input += "^3"
        case "xʸ": input += "^"
        case "x!": input += "!"
        case "1/x": input = "1/(" + input + ")"
        case "eˣ": input += "e^"
        case "ʸ√x": input += "^(1/"
        case "±": input = input.hasPrefix("-") ? String(input.dropFirst()) : "-" + input
        case "sin", "cos", "tan", "asin", "acos", "atan", "ln", "lg": input += k + "("
        case "=":
            if let v = AvraCalc.evaluate(input) {
                history.append(input + " = " + AvraCalc.format(v))
                history = Array(history.suffix(30))
                UserDefaults.standard.set(history, forKey: "avra.calc.history")
                input = AvraCalc.format(v).replacingOccurrences(of: ",", with: ".")
            }
        default: input += k == "," ? "." : k
        }
        AvraHaptics.tap()
    }
}
