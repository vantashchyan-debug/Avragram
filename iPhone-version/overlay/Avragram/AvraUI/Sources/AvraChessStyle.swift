import Foundation
import UIKit
import AVFoundation
import SwiftUI
import SGSwiftUI

/// AvraChess look and sounds (same sets as on Android): 11 boards, 12 piece sets drawn from the
/// bundled chess_<set>_<piece>.webp artwork, wood / marble / marimba sounds.
enum AvraChessStyle {
    static let boardNames = ["Зелёная", "Дерево", "Синяя", "Мрамор", "Неон", "Розовая", "Ночь", "Лёд", "Конфетка", "Нейрослоп"]
    static let boards: [(UInt32, UInt32)] = [(0xEEEED2, 0x769656), (0xF0D9B5, 0xB58863), (0xDEE3E6, 0x8CA2AD), (0xEDEDED, 0x9E9E9E), (0x2D1B69, 0x120B33),
                                             (0xFCE4EC, 0xF06292), (0x4B5563, 0x1F2937), (0xE0F7FF, 0x7FB3D5), (0xFFF59D, 0x4DD0E1), (0xC6FF00, 0xFF00E5)]
    static let setFiles = ["classic", "merida", "chessnut", "fantasy", "spatial", "celtic", "papercut", "candy", "minimal", "neon", "gold"]
    static let setNames = ["Классика", "Мерида", "Каштан", "Фэнтези", "Космос", "Кельтские", "Бумага", "Конфетки", "Минимал", "Неон", "Золото и сталь"]
    static let soundNames = ["Без звука", "Дерево", "Мрамор", "Маримба"]
    private static let soundFiles = ["", "wood", "marble", "marimba"]

    static var board: Int {
        get { min(boards.count - 1, max(0, UserDefaults.standard.integer(forKey: "avra.chess.board"))) }
        set { UserDefaults.standard.set(newValue, forKey: "avra.chess.board") }
    }
    static var pieceSet: Int {
        get { min(setFiles.count - 1, max(0, UserDefaults.standard.integer(forKey: "avra.chess.pieces"))) }
        set { UserDefaults.standard.set(newValue, forKey: "avra.chess.pieces"); images.removeAll() }
    }
    static var sound: Int {
        get { min(soundNames.count - 1, max(0, UserDefaults.standard.object(forKey: "avra.chess.sound") as? Int ?? 1)) }
        set { UserDefaults.standard.set(newValue, forKey: "avra.chess.sound") }
    }

    static func color(_ v: UInt32) -> Color {
        Color(red: Double((v >> 16) & 0xFF) / 255, green: Double((v >> 8) & 0xFF) / 255, blue: Double(v & 0xFF) / 255)
    }

    private static var images: [Int: UIImage] = [:]

    static func image(_ piece: Int) -> UIImage? {
        if let i = images[piece] { return i }
        let letters = Array("?pnbrqk")
        let name = "chess_\(setFiles[pieceSet])_\(piece > 0 ? "w" : "b")\(letters[abs(piece)])"
        guard let path = Bundle.main.path(forResource: name, ofType: "webp"), let img = UIImage(contentsOfFile: path) else { return nil }
        images[piece] = img
        return img
    }

    enum Event: String { case move, capture, check, castle, end, lowtime, notify, start }
    private static var player: AVAudioPlayer?

    static func play(_ e: Event) {
        let s = sound
        guard s > 0, let url = Bundle.main.url(forResource: "chess_\(soundFiles[s])_\(e.rawValue)", withExtension: "m4a") else { return }
        try? AVAudioSession.sharedInstance().setCategory(.ambient, options: [.mixWithOthers])
        player = try? AVAudioPlayer(contentsOf: url)
        player?.play()
    }

    static func playSea(_ name: String) {
        guard UserDefaults.standard.object(forKey: "avra.sea.sound") as? Bool ?? true,
              let url = Bundle.main.url(forResource: "sea_" + name, withExtension: "m4a") else { return }
        try? AVAudioSession.sharedInstance().setCategory(.ambient, options: [.mixWithOthers])
        seaPlayer = try? AVAudioPlayer(contentsOf: url)
        seaPlayer?.play()
    }
    private static var seaPlayer: AVAudioPlayer?
}

struct AvraChessSettingsScreen: View {
    @Environment(\.sgTheme) private var theme
    @State private var board = AvraChessStyle.board
    @State private var pieces = AvraChessStyle.pieceSet
    @State private var sound = AvraChessStyle.sound

    var body: some View {
        Form {
            Section(header: Text("Доска")) {
                Picker("Доска", selection: $board) {
                    ForEach(0..<AvraChessStyle.boardNames.count, id: \.self) { i in
                        HStack {
                            AvraChessStyle.color(AvraChessStyle.boards[i].0).frame(width: 18, height: 18)
                            AvraChessStyle.color(AvraChessStyle.boards[i].1).frame(width: 18, height: 18)
                            Text(AvraChessStyle.boardNames[i])
                        }.tag(i)
                    }
                }
                .pickerStyle(.inline).labelsHidden()
                .onChange(of: board) { AvraChessStyle.board = $0 }
            }
            Section(header: Text("Фигуры")) {
                Picker("Фигуры", selection: $pieces) {
                    ForEach(0..<AvraChessStyle.setNames.count, id: \.self) { i in Text(AvraChessStyle.setNames[i]).tag(i) }
                }
                .pickerStyle(.inline).labelsHidden()
                .onChange(of: pieces) { AvraChessStyle.pieceSet = $0 }
            }
            Section(header: Text("Звуки")) {
                Picker("Звуки", selection: $sound) {
                    ForEach(0..<AvraChessStyle.soundNames.count, id: \.self) { i in Text(AvraChessStyle.soundNames[i]).tag(i) }
                }
                .pickerStyle(.segmented)
                .onChange(of: sound) { AvraChessStyle.sound = $0; AvraChessStyle.play(.move) }
            }
        }
        .sgNavigationTitle("Оформление шахмат")
    }
}
