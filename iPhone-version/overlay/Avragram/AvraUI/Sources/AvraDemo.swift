import Foundation
import UIKit
import SwiftUI

/// Screenshot mode for CI: launched with "-avraDemo <screen>", the app shows that Avragram screen over
/// everything (no account needed). Used only by the simulator workflow; never triggered otherwise.
enum AvraDemo {
    static let screens = ["fun", "chess", "chess2", "chessSettings", "review", "sea", "seaBattle", "blast", "snake", "drop", "run", "bird", "g2048",
                          "calc", "soundpad", "voice", "intro", "online", "seaOnline", "privacy"]

    private static var host: UIViewController?

    static var requested: String? {
        let args = ProcessInfo.processInfo.arguments
        if let i = args.firstIndex(of: "-avraDemo"), i + 1 < args.count, screens.contains(args[i + 1]) { return args[i + 1] }
        if let env = ProcessInfo.processInfo.environment["AVRA_DEMO"], screens.contains(env) { return env }
        return nil
    }

    static func showIfRequested(in mainWindow: UIWindow) -> Bool {
        guard let name = requested else { return false }
        NSLog("[Avra] demo requested: %@", name)
        let controller = UIHostingController(rootView: NavigationView { view(name) }.navigationViewStyle(.stack))
        host = controller
        func attach(_ attempt: Int) {
            let windows = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.flatMap { $0.windows }
            let target = windows.first { $0.isKeyWindow } ?? mainWindow
            if controller.view.superview !== target {
                controller.view.removeFromSuperview()
                controller.view.frame = target.bounds
                controller.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
                target.addSubview(controller.view)
            }
            target.bringSubviewToFront(controller.view)
            NSLog("[Avra] demo shown in %@ (attempt %d)", String(describing: type(of: target)), attempt)
            if attempt < 6 {
                DispatchQueue.main.asyncAfter(deadline: .now() + 1) { attach(attempt + 1) }
            }
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) { attach(0) }
        return true
    }

    @ViewBuilder
    private static func view(_ name: String) -> some View {
        switch name {
        case "fun": AvraDemoFun()
        case "chess": AvraChessScreen(level: 1)
        case "chess2": AvraChessScreen(level: 0, twoPlayers: true)
        case "chessSettings": AvraChessSettingsScreen()
        case "review": AvraChessReviewScreen(moves: ["e2e4", "e7e5", "g1f3", "b8c6", "f1c4", "g8f6", "f3g5", "d7d5", "e4d5", "f6d5", "g5f7", "e8f7", "d1f3", "f7e6"])
        case "sea": AvraSeaScreen(level: 2)
        case "seaBattle": AvraDemoSeaBattle()
        case "blast": AvraBlastScreen()
        case "snake": AvraSnakeScreen()
        case "drop": AvraDropScreen()
        case "run": AvraRunScreen()
        case "bird": AvraBirdScreen()
        case "g2048": Avra2048Screen()
        case "calc": AvraCalculatorScreen()
        case "soundpad": AvraSoundpadScreen()
        case "voice": AvraVoiceScreen()
        case "intro": AvraIntroSettingsScreen()
        case "online": AvraChessOnlineScreen()
        case "seaOnline": AvraSeaOnlineLobby()
        default: AvraPrivacyScreen()
        }
    }
}

/// The Fun list as in the hub (without account-dependent rows).
struct AvraDemoFun: View {
    var body: some View {
        List {
            Section(header: Text("Развлекуха")) {
                Label("AvraChess — бот, вдвоём, онлайн", systemImage: "crown")
                Label("Морской бой — бот и онлайн", systemImage: "ferry")
                ForEach(AvraGameKind.allCases) { g in Text("\(g.emoji)  \(g.title) — \(g.info)") }
                Label("Калькулятор", systemImage: "function")
                Label("Спам, саундпад, voice changer", systemImage: "waveform")
            }
            Section(header: Text("Оформление")) {
                Text("🎨 35 тем Avragram · 20 шрифтов · скругления")
                Text("🎬 Видео при запуске")
            }
            Section(header: Text("Темы")) {
                ForEach(AvraStyle.all.prefix(12)) { s in
                    HStack {
                        LinearGradient(colors: s.wallpaper.map { AvraAppearance.color($0) }, startPoint: .leading, endPoint: .trailing)
                            .frame(width: 60, height: 24).cornerRadius(6)
                        Text(s.title)
                        Spacer()
                        Text(s.font).font(.custom(s.font, size: 14))
                    }
                }
            }
        }
        .navigationTitle("Avragram")
    }
}

/// Sea battle already in progress (for the screenshot).
struct AvraDemoSeaBattle: View {
    @StateObject var game: AvraSeaGame = {
        let g = AvraSeaGame(level: 2)
        g.start()
        for c in [44, 45, 46, 12, 77, 3, 60, 61, 88] { g.fire(c) }
        return g
    }()

    var body: some View {
        AvraSeaScreenBody(game: game)
    }
}
