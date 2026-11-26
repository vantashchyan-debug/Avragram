import Foundation
import AVFoundation
import SwiftUI
import UniformTypeIdentifiers
import SGSwiftUI

/// Soundpad (port of Android AvraSoundpad): the 12 synthesized Avragram sounds plus your own files
/// (up to 30 s). Sounds play through the speaker mixed with other audio, so during a call the
/// microphone picks them up and the other person hears them.
final class AvraSoundpadModel: NSObject, ObservableObject, AVAudioPlayerDelegate {
    struct Sound: Identifiable, Equatable { let id: String; let emoji: String; let title: String; let url: URL; let custom: Bool }
    static let builtin: [(String, String, String)] = [
        ("airhorn", "📯", "Горн"), ("rimshot", "🥁", "Ба-дум-тсс"), ("fail", "🎺", "Провал"), ("fanfare", "🏆", "Победа"),
        ("beep", "🤐", "Цензура"), ("siren", "🚨", "Сирена"), ("drumroll", "🪘", "Барабанная дробь"), ("crickets", "🦗", "Сверчки"),
        ("laser", "🔫", "Лазер"), ("ding", "🔔", "Дзынь"), ("boing", "🪀", "Пружинка"), ("applause", "👏", "Аплодисменты")
    ]
    @Published var sounds: [Sound] = []
    @Published var volume: Double = UserDefaults.standard.object(forKey: "avra.soundpad.volume") as? Double ?? 1.0 {
        didSet { UserDefaults.standard.set(volume, forKey: "avra.soundpad.volume") }
    }
    @Published var playing: String? = nil
    private var player: AVAudioPlayer?

    static var directory: URL {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        let dir = base.appendingPathComponent("avra_soundpad", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }

    override init() {
        super.init()
        reload()
    }

    func reload() {
        var list: [Sound] = []
        for (id, emoji, title) in Self.builtin {
            if let url = Bundle.main.url(forResource: "avra_sp_" + id, withExtension: "m4a") {
                list.append(Sound(id: id, emoji: emoji, title: title, url: url, custom: false))
            }
        }
        let files = (try? FileManager.default.contentsOfDirectory(at: Self.directory, includingPropertiesForKeys: nil)) ?? []
        for url in files.sorted(by: { $0.lastPathComponent < $1.lastPathComponent }) {
            list.append(Sound(id: "c_" + url.lastPathComponent, emoji: "🎵", title: url.deletingPathExtension().lastPathComponent, url: url, custom: true))
        }
        sounds = list
    }

    func play(_ s: Sound) {
        stop()
        do {
            try AVAudioSession.sharedInstance().setCategory(.playAndRecord, mode: .default, options: [.mixWithOthers, .defaultToSpeaker, .allowBluetooth])
            try AVAudioSession.sharedInstance().setActive(true, options: [])
            let p = try AVAudioPlayer(contentsOf: s.url)
            p.volume = Float(min(1, volume))
            p.delegate = self
            if volume > 1 { p.enableRate = false }
            p.play()
            player = p
            playing = s.id
        } catch {
            playing = nil
        }
    }

    func stop() {
        player?.stop()
        player = nil
        playing = nil
    }

    func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) {
        DispatchQueue.main.async { self.playing = nil }
    }

    /// Adds an audio file (max 30 s, max 10 MB).
    func add(_ url: URL) -> Bool {
        let scoped = url.startAccessingSecurityScopedResource()
        defer { if scoped { url.stopAccessingSecurityScopedResource() } }
        guard let attrs = try? FileManager.default.attributesOfItem(atPath: url.path), let size = attrs[.size] as? NSNumber, size.intValue <= 10_000_000 else { return false }
        guard let p = try? AVAudioPlayer(contentsOf: url), p.duration > 0, p.duration <= 30.5 else { return false }
        let name = url.deletingPathExtension().lastPathComponent.replacingOccurrences(of: "/", with: "_").prefix(40)
        let target = Self.directory.appendingPathComponent(String(name) + "." + url.pathExtension.prefix(5))
        try? FileManager.default.removeItem(at: target)
        guard (try? FileManager.default.copyItem(at: url, to: target)) != nil else { return false }
        reload()
        return true
    }

    func remove(_ s: Sound) {
        guard s.custom else { return }
        try? FileManager.default.removeItem(at: s.url)
        reload()
    }
}

struct AvraSoundpadScreen: View {
    @StateObject var model = AvraSoundpadModel()
    @Environment(\.sgTheme) var theme
    @State var importing = false
    @State var message: String? = nil

    var body: some View {
        ScrollView {
            VStack(spacing: 14) {
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 96), spacing: 10)], spacing: 10) {
                    ForEach(model.sounds) { s in
                        Button(action: { model.playing == s.id ? model.stop() : model.play(s) }) {
                            VStack(spacing: 6) {
                                Text(s.emoji).font(.system(size: 30))
                                Text(s.title).font(.footnote.bold()).lineLimit(2).multilineTextAlignment(.center).foregroundColor(theme.primaryTextColor)
                            }
                            .frame(maxWidth: .infinity, minHeight: 86)
                            .background(RoundedRectangle(cornerRadius: 16).fill(model.playing == s.id ? theme.accentColor.opacity(0.3) : theme.secondaryBackgroundColor))
                            .scaleEffect(model.playing == s.id ? 1.05 : 1)
                            .animation(.spring(response: 0.3, dampingFraction: 0.6), value: model.playing)
                        }
                        .buttonStyle(.plain)
                        .contextMenu {
                            if s.custom { Button(role: .destructive, action: { model.remove(s) }) { Label("Удалить", systemImage: "trash") } }
                        }
                    }
                    Button(action: { importing = true }) {
                        VStack(spacing: 6) {
                            Image(systemName: "plus").font(.system(size: 28, weight: .bold))
                            Text("Свой звук").font(.footnote.bold())
                        }
                        .frame(maxWidth: .infinity, minHeight: 86)
                        .background(RoundedRectangle(cornerRadius: 16).stroke(theme.accentColor, style: StrokeStyle(lineWidth: 2, dash: [6])))
                    }.buttonStyle(.plain)
                }
                HStack {
                    Image(systemName: "speaker.wave.1")
                    Slider(value: $model.volume, in: 0.1...1)
                    Image(systemName: "speaker.wave.3")
                }
                Button(role: .destructive, action: { model.stop() }) { Label("Стоп", systemImage: "stop.fill").frame(maxWidth: .infinity) }
                    .buttonStyle(.bordered)
                if let message = message { Text(message).font(.footnote) }
                Text("Во время звонка звук играет через динамик вместе со звонком, и собеседник его слышит. Свои звуки: любой аудиофайл до 30 секунд, удержание удаляет.")
                    .font(.footnote).foregroundColor(theme.secondaryTextColor)
            }
            .padding(16)
        }
        .background(theme.backgroundColor.ignoresSafeArea())
        .fileImporter(isPresented: $importing, allowedContentTypes: [UTType.audio]) { result in
            if case let .success(url) = result {
                message = model.add(url) ? "Звук добавлен" : "Не подошёл: нужен аудиофайл до 30 секунд и до 10 МБ"
            }
        }
        .sgNavigationTitle("Саундпад")
    }
}
