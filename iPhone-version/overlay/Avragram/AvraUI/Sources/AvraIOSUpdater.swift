import Foundation
import SwiftUI
import SGSwiftUI

/// Checks GitHub for a newer iPhone build (pre-releases "ios-v<version>" of vantashchyan-debug/Avragram).
/// Installing is up to SideStore / AltStore: the hub only shows that a new IPA is out, with a link.
final class AvraIOSUpdater: ObservableObject {
    @Published var latest: String? = nil
    @Published var url: URL? = nil
    @Published var checking = false

    func check() {
        guard !checking, let api = URL(string: "https://api.github.com/repos/vantashchyan-debug/Avragram/releases?per_page=30") else { return }
        checking = true
        var request = URLRequest(url: api, timeoutInterval: 15)
        request.setValue("application/vnd.github+json", forHTTPHeaderField: "Accept")
        request.setValue("Avragram-Updater", forHTTPHeaderField: "User-Agent")
        URLSession.shared.dataTask(with: request) { data, _, _ in
            var best: (String, URL)? = nil
            if let data = data, let list = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]] {
                for r in list {
                    guard let tag = r["tag_name"] as? String, tag.hasPrefix("ios-v"), let page = (r["html_url"] as? String).flatMap(URL.init(string:)),
                          page.host == "github.com" else { continue }
                    let v = String(tag.dropFirst(5))
                    if best == nil || AvraIOSUpdater.compare(v, best!.0) > 0 { best = (v, page) }
                }
            }
            DispatchQueue.main.async {
                self.checking = false
                if let best = best, AvraIOSUpdater.compare(best.0, avragramIOSVersion) > 0 {
                    self.latest = best.0
                    self.url = best.1
                }
            }
        }.resume()
    }

    static func compare(_ a: String, _ b: String) -> Int {
        let x = a.split(separator: ".").map { Int($0) ?? 0 }, y = b.split(separator: ".").map { Int($0) ?? 0 }
        for i in 0..<max(x.count, y.count) {
            let p = i < x.count ? x[i] : 0, q = i < y.count ? y[i] : 0
            if p != q { return p < q ? -1 : 1 }
        }
        return 0
    }
}
