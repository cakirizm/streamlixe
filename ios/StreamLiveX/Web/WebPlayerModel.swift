import AVFoundation
import SwiftUI
import UIKit
import WebKit

@MainActor
final class WebPlayerModel: ObservableObject {
    @Published var uiReady = false
    @Published var fullScreenRequest: PlaybackRequest?
    @Published var previewRequest: PlaybackRequest?
    @Published var previewBounds: PreviewBounds?
    @Published var webError: String?
    @Published var downloadsPresented = false
    @Published var parentalProfile = "main"
    @Published var parentalCategories: [String] = []
    @Published var parentalPresented = false
    weak var webView: WKWebView?
    let player = NativePlayerController()

    func handle(_ command: BridgeCommand) {
        switch command {
        case .uiReady:
            uiReady = true
            webError = nil
            UserDefaults.standard.set(true, forKey: "SLXLocalWebMigrationCompleted")
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
        case .download(let request):
            NativeDownloadManager.shared.start(id: request.id,
                                               title: request.item.name,
                                               source: request.item.url,
                                               artworkURL: request.item.artwork,
                                               kind: request.item.kind)
            downloadsPresented = true
        case .showDownloads:
            downloadsPresented = true
        case .showParental(let profileID, let categories):
            parentalProfile = profileID
            parentalCategories = categories
            parentalPresented = true
        case .migrateParentalPin(let profileID, let pin):
            _ = SecurePinStore.set(pin, profileID: profileID)
        case .confirmExit:
            break // iOS apps must not terminate themselves programmatically.
        }
    }

    func retryWebUI() {
        uiReady = false
        webError = nil
        webView?.load(URLRequest(url: AppConfiguration.bundledWebAppURL))
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
    func setParentalSettings(_ enabled: Bool, restrictedGroups: [String]) { dispatch("streamlivex:native-settings", detail: ["parental": enabled, "hiddenGroups": restrictedGroups]) }
    func requestNext() { dispatch("streamlivex:native-player-next", detail: NSNull()) }
    func requestPrevious() { dispatch("streamlivex:native-player-previous", detail: NSNull()) }
    func updateSubtitlePreferences(_ preferences: PlaybackPreferences) {
        dispatch("streamlivex:native-settings", detail: ["subtitleSize": preferences.subtitleSize,
                                                          "subtitleColor": preferences.subtitleColor,
                                                          "subtitleBackground": preferences.subtitleBackground,
                                                          "subtitleBackgroundOpacity": preferences.subtitleBackgroundOpacity,
                                                          "subtitleVerticalPosition": preferences.subtitleVerticalPosition,
                                                          "playbackRate": preferences.playbackRate])
    }
    func playOffline(_ download: OfflineDownload) {
        guard let localURL = download.localURL else { return }
        downloadsPresented = false
        Task { try? await Task.sleep(for: .milliseconds(300)); let request = PlaybackRequest(id: "offline-\(download.id)", item: PlaybackItem(name: download.title, url: localURL, kind: download.kind, artwork: download.artworkURL), resumeMilliseconds: 0, preferences: PlaybackPreferences()); fullScreenRequest = request; player.load(request, autoplay: true) }
    }

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
                                      item: PlaybackItem(name: value("title") ?? "StreamLiveX", url: mediaURL, kind: value("kind") ?? "movie", artwork: nil),
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
