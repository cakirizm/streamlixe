import AVFoundation
import SwiftUI
import UIKit
import WebKit

@MainActor
final class WebPlayerModel: ObservableObject {
    @Published var fullScreenRequest: PlaybackRequest?
    @Published var previewRequest: PlaybackRequest?
    @Published var previewBounds: PreviewBounds?
    @Published var webError: String?
    weak var webView: WKWebView?
    let player = NativePlayerController()

    func handle(_ command: BridgeCommand) {
        switch command {
        case .play(let request):
            previewRequest = nil; previewBounds = nil
            fullScreenRequest = request
            player.load(request, autoplay: true)
        case .preview(let request, let bounds):
            previewRequest = request; previewBounds = bounds
            player.load(request, autoplay: true)
        case .previewLayout(let id, let bounds):
            if previewRequest?.id == id { previewBounds = bounds }
        case .promotePreview(let id):
            if previewRequest?.id == id { fullScreenRequest = previewRequest }
        case .close(let id):
            if id == nil || fullScreenRequest?.id == id { closePlayer(notifyWeb: false) }
        case .closePreview(let id):
            if id == nil || previewRequest?.id == id { previewRequest = nil; previewBounds = nil; if fullScreenRequest == nil { player.stop() } }
        case .confirmExit:
            break // iOS apps must not terminate themselves programmatically.
        }
    }

    func closePlayer(notifyWeb: Bool = true) {
        let progress = player.progress
        fullScreenRequest = nil
        if previewRequest == nil { player.stop() }
        if notifyWeb { dispatch("streamlivex:native-player-closed", detail: ["current": progress.current, "duration": progress.duration]) }
    }

    func reportProgress() {
        let progress = player.progress
        dispatch("streamlivex:native-player-progress", detail: ["current": progress.current, "duration": progress.duration])
    }

    func reportError(_ message: String) { dispatch("streamlivex:native-player-error", detail: message) }
    func requestNext() { dispatch("streamlivex:native-player-next", detail: NSNull()) }

    func dispatch(_ name: String, detail: Any) {
        // JSONSerialization, String/Number gibi kok degerlerde Swift Error yerine
        // yakalanamayan NSException firlatir. Degeri once dizi icinde serialize edip
        // parantezleri soymak her gecerli CustomEvent.detail turunu guvenle destekler.
        let payload = [detail]
        guard JSONSerialization.isValidJSONObject(payload),
              let data = try? JSONSerialization.data(withJSONObject: payload),
              let array = String(data: data, encoding: .utf8),
              array.count >= 2 else { return }
        let json = String(array.dropFirst().dropLast())
        webView?.evaluateJavaScript("window.dispatchEvent(new CustomEvent(\(js(name)), { detail: \(json) }));")
    }

    func openDeepLink(_ url: URL) {
        guard url.scheme?.lowercased() == "streamlivex", url.host?.lowercased() == "play",
              let components = URLComponents(url: url, resolvingAgainstBaseURL: false),
              let source = components.queryItems?.first(where: { $0.name == "url" })?.value,
              let mediaURL = URL(string: source), ["http", "https"].contains(mediaURL.scheme?.lowercased()) else { return }
        let value: (String) -> String? = { key in components.queryItems?.first(where: { $0.name == key })?.value }
        let request = PlaybackRequest(id: UUID().uuidString,
                                      item: PlaybackItem(name: value("title") ?? "StreamLiveX", url: mediaURL, kind: value("kind") ?? "movie"),
                                      resumeMilliseconds: 0, preferences: PlaybackPreferences())
        fullScreenRequest = request; player.load(request, autoplay: true)
    }

    func scenePhaseChanged(_ phase: ScenePhase) {
        if phase == .background { reportProgress(); player.pauseForBackgroundIfNeeded() }
        else if phase == .active { player.resumeAfterBackgroundIfNeeded() }
    }

    private func js(_ value: String) -> String {
        let data = try? JSONSerialization.data(withJSONObject: [value])
        let array = data.flatMap { String(data: $0, encoding: .utf8) } ?? "[\"\"]"
        return String(array.dropFirst().dropLast())
    }
}
