import Foundation
import UIKit
import AVFoundation
import SwiftUI
import PhotosUI
import SGSwiftUI

/// The launch video (port of Android AvraIntro): one of the three Avragram videos, shuffled so the
/// same one never plays twice in a row, or your own video / photo with a chosen duration.
/// Tap to skip. Shown over the app window once per launch.
public enum AvraIntro {
    static let enabledKey = "avra.intro.enabled", shuffleKey = "avra.intro.shuffle", typeKey = "avra.intro.type"
    static let photoSecondsKey = "avra.intro.photoSeconds", lastKey = "avra.intro.last"
    static let videos = ["avra_intro", "avra_intro2", "avra_intro3"]
    private static var shown = false

    static var enabled: Bool {
        get { UserDefaults.standard.object(forKey: enabledKey) as? Bool ?? true }
        set { UserDefaults.standard.set(newValue, forKey: enabledKey) }
    }
    static var shuffle: Bool {
        get { UserDefaults.standard.object(forKey: shuffleKey) as? Bool ?? true }
        set { UserDefaults.standard.set(newValue, forKey: shuffleKey) }
    }
    /// 0 Avragram videos, 1 own video, 2 own photo
    static var type: Int {
        get { UserDefaults.standard.integer(forKey: typeKey) }
        set { UserDefaults.standard.set(newValue, forKey: typeKey) }
    }
    static var photoSeconds: Int {
        get { max(1, min(10, UserDefaults.standard.object(forKey: photoSecondsKey) as? Int ?? 3)) }
        set { UserDefaults.standard.set(newValue, forKey: photoSecondsKey) }
    }

    static var directory: URL {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        let dir = base.appendingPathComponent("avra_intro", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }
    static var customVideo: URL { directory.appendingPathComponent("intro.mov") }
    static var customPhoto: URL { directory.appendingPathComponent("intro.jpg") }

    static func pickVideo() -> URL? {
        var name = videos[0]
        if shuffle {
            let last = UserDefaults.standard.string(forKey: lastKey)
            name = videos.filter { $0 != last }.randomElement() ?? videos[0]
            UserDefaults.standard.set(name, forKey: lastKey)
        }
        return Bundle.main.url(forResource: name, withExtension: "mp4")
    }

    /// Called once the main window is visible.
    public static func show(in window: UIWindow) {
        guard !shown, enabled else { return }
        shown = true
        let view = AvraIntroView(frame: window.bounds)
        view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        if type == 2, let image = UIImage(contentsOfFile: customPhoto.path) {
            view.showPhoto(image, seconds: photoSeconds)
        } else if type == 1, FileManager.default.fileExists(atPath: customVideo.path) {
            view.playVideo(customVideo)
        } else if let url = pickVideo() {
            view.playVideo(url)
        } else {
            return
        }
        window.addSubview(view)
    }

    /// Plays the intro on demand (preview in settings).
    static func preview() {
        let windows = UIApplication.shared.connectedScenes.compactMap { ($0 as? UIWindowScene)?.windows.first { $0.isKeyWindow } }
        guard let window = windows.first else { return }
        shown = false
        show(in: window)
    }

    /// Saves a picked file as your own intro; returns false if it could not be read.
    static func importFile(_ url: URL, photo: Bool) -> Bool {
        let target = photo ? customPhoto : customVideo
        try? FileManager.default.removeItem(at: target)
        do {
            if photo {
                guard let data = try? Data(contentsOf: url), let image = UIImage(data: data), let jpeg = image.jpegData(compressionQuality: 0.9) else { return false }
                try jpeg.write(to: target, options: .completeFileProtectionUntilFirstUserAuthentication)
            } else {
                try FileManager.default.copyItem(at: url, to: target)
            }
            type = photo ? 2 : 1
            return true
        } catch {
            return false
        }
    }

    static func reset() {
        try? FileManager.default.removeItem(at: customVideo)
        try? FileManager.default.removeItem(at: customPhoto)
        type = 0
    }
}

final class AvraIntroView: UIView {
    private var player: AVPlayer?
    private var layerView: AVPlayerLayer?
    private var finished = false

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .black
        addGestureRecognizer(UITapGestureRecognizer(target: self, action: #selector(skip)))
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    func playVideo(_ url: URL) {
        try? AVAudioSession.sharedInstance().setCategory(.ambient, options: [.mixWithOthers])
        let player = AVPlayer(url: url)
        let layer = AVPlayerLayer(player: player)
        layer.videoGravity = .resizeAspectFill
        layer.frame = bounds
        self.layer.addSublayer(layer)
        self.player = player
        self.layerView = layer
        NotificationCenter.default.addObserver(self, selector: #selector(skip), name: .AVPlayerItemDidPlayToEndTime, object: player.currentItem)
        player.play()
        // never keep the app hidden longer than 20 s, whatever the file
        DispatchQueue.main.asyncAfter(deadline: .now() + 20) { [weak self] in self?.skip() }
    }

    func showPhoto(_ image: UIImage, seconds: Int) {
        let iv = UIImageView(image: image)
        iv.contentMode = .scaleAspectFill
        iv.frame = bounds
        iv.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        iv.clipsToBounds = true
        addSubview(iv)
        DispatchQueue.main.asyncAfter(deadline: .now() + .seconds(seconds)) { [weak self] in self?.skip() }
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        layerView?.frame = bounds
    }

    @objc private func skip() {
        guard !finished else { return }
        finished = true
        NotificationCenter.default.removeObserver(self)
        UIView.animate(withDuration: 0.35, animations: { self.alpha = 0 }, completion: { _ in
            self.player?.pause()
            self.removeFromSuperview()
        })
    }
}

/// Avragram → Launch video.
struct AvraIntroSettingsScreen: View {
    @Environment(\.sgTheme) private var theme
    @State private var enabled = AvraIntro.enabled
    @State private var shuffle = AvraIntro.shuffle
    @State private var type = AvraIntro.type
    @State private var seconds = Double(AvraIntro.photoSeconds)
    @State private var picking: Int? = nil
    @State private var message: String? = nil

    var body: some View {
        Form {
            Section {
                Toggle("Видео при запуске", isOn: $enabled).onChange(of: enabled) { AvraIntro.enabled = $0 }
            }
            Section(header: Text("Что показывать")) {
                Picker("Заставка", selection: $type) {
                    Text("Видео Avragram").tag(0)
                    Text("Своё видео").tag(1)
                    Text("Своё фото").tag(2)
                }
                .pickerStyle(.inline)
                .onChange(of: type) { AvraIntro.type = $0 }
                if type == 0 {
                    Toggle("Перемешивать видео", isOn: $shuffle).onChange(of: shuffle) { AvraIntro.shuffle = $0 }
                }
                if type == 1 {
                    Button("Выбрать видео") { picking = 1 }
                }
                if type == 2 {
                    Button("Выбрать фото") { picking = 2 }
                    Stepper("Показывать \(Int(seconds)) с", value: $seconds, in: 1...10).onChange(of: seconds) { AvraIntro.photoSeconds = Int($0) }
                }
            }
            Section {
                Button("Посмотреть заставку") { AvraIntro.preview() }
                Button("Сбросить на видео Avragram", role: .destructive) {
                    AvraIntro.reset()
                    type = 0
                }
            }
            if let message = message {
                Section { Text(message).font(.footnote) }
            }
        }
        .sheet(item: Binding(get: { picking.map { AvraPickKind(id: $0) } }, set: { picking = $0?.id })) { kind in
            AvraMediaPicker(photo: kind.id == 2) { url in
                picking = nil
                guard let url = url else { return }
                message = AvraIntro.importFile(url, photo: kind.id == 2) ? "Готово: заставка заменена" : "Не удалось прочитать файл"
                type = AvraIntro.type
            }
        }
        .sgNavigationTitle("Видео при запуске")
    }
}

struct AvraPickKind: Identifiable { let id: Int }

/// System photo / video picker (no photo library permission needed).
struct AvraMediaPicker: UIViewControllerRepresentable {
    let photo: Bool
    let done: (URL?) -> Void

    func makeUIViewController(context: Context) -> PHPickerViewController {
        var config = PHPickerConfiguration()
        config.filter = photo ? .images : .videos
        config.selectionLimit = 1
        let picker = PHPickerViewController(configuration: config)
        picker.delegate = context.coordinator
        return picker
    }

    func updateUIViewController(_ uiViewController: PHPickerViewController, context: Context) {}

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    final class Coordinator: NSObject, PHPickerViewControllerDelegate {
        let parent: AvraMediaPicker
        init(_ parent: AvraMediaPicker) { self.parent = parent }

        func picker(_ picker: PHPickerViewController, didFinishPicking results: [PHPickerResult]) {
            guard let provider = results.first?.itemProvider else {
                parent.done(nil)
                return
            }
            let type = parent.photo ? "public.image" : "public.movie"
            provider.loadFileRepresentation(forTypeIdentifier: type) { url, _ in
                // the file is deleted when this block returns: copy it first
                var copy: URL? = nil
                if let url = url {
                    let tmp = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString + "." + url.pathExtension)
                    if (try? FileManager.default.copyItem(at: url, to: tmp)) != nil { copy = tmp }
                }
                let result = copy
                DispatchQueue.main.async { self.parent.done(result) }
            }
        }
    }
}
