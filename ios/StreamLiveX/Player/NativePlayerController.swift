import AVFoundation
import UIKit

@MainActor
final class NativePlayerController: ObservableObject {
    let player = AVPlayer()
    private var currentID: String?
    private var shouldResume = false
    private var statusObservation: NSKeyValueObservation?
    private var preparationTimeout: Task<Void, Never>?
    var onError: ((String) -> Void)?

    var progress: (current: Double, duration: Double) {
        let current = player.currentTime().seconds
        let duration = player.currentItem?.duration.seconds ?? 0
        return (current.isFinite ? current : 0, duration.isFinite ? duration : 0)
    }

    init() {
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .moviePlayback)
        try? AVAudioSession.sharedInstance().setActive(true)
        NotificationCenter.default.addObserver(forName: AVAudioSession.interruptionNotification, object: nil, queue: .main) { [weak self] note in
            Task { @MainActor in self?.handleInterruption(note) }
        }
    }

    func load(_ request: PlaybackRequest, autoplay: Bool) {
        if currentID == request.id { if autoplay { player.play() }; return }
        currentID = request.id
        prepare(request, autoplay: autoplay, candidateIndex: 0)
    }

    private func prepare(_ request: PlaybackRequest, autoplay: Bool, candidateIndex: Int) {
        let candidates = playbackCandidates(for: request.item.url)
        guard candidates.indices.contains(candidateIndex) else {
            onError?("Yayın iPhone tarafından desteklenen bir biçimde sunulmuyor.")
            return
        }
        let asset = AVURLAsset(
            url: candidates[candidateIndex],
            options: [AVURLAssetHTTPUserAgentKey: "VLC/3.0 StreamLiveX-iOS/1.0"]
        )
        let item = AVPlayerItem(asset: asset)
        item.preferredForwardBufferDuration = request.item.isLive ? 4 : 12
        item.canUseNetworkResourcesForLiveStreamingWhilePaused = false
        statusObservation = nil
        preparationTimeout?.cancel()
        player.replaceCurrentItem(with: item)
        player.automaticallyWaitsToMinimizeStalling = true
        statusObservation = item.observe(\.status, options: [.new]) { [weak self] item, _ in
            Task { @MainActor in
                if case .failed = item.status {
                    self?.tryNextCandidate(
                        request,
                        failedIndex: candidateIndex,
                        message: item.error?.localizedDescription ?? "Yayın açılamadı"
                    )
                }
                if case .readyToPlay = item.status {
                    self?.preparationTimeout?.cancel()
                    self?.applyMediaPreferences(request.preferences, to: item)
                }
            }
        }
        preparationTimeout = Task { [weak self, weak item] in
            try? await Task.sleep(for: .seconds(15))
            guard !Task.isCancelled,
                  let self,
                  let item,
                  self.currentID == request.id,
                  self.player.currentItem === item,
                  item.status != .readyToPlay else { return }
            self.tryNextCandidate(request, failedIndex: candidateIndex, message: "Sunucu 15 saniye içinde yanıt vermedi")
        }
        if request.resumeMilliseconds > 0 && !request.item.isLive {
            player.seek(to: CMTime(seconds: request.resumeMilliseconds / 1000, preferredTimescale: 600), toleranceBefore: .zero, toleranceAfter: .zero)
        }
        player.rate = autoplay ? request.preferences.playbackRate : 0
        if autoplay { player.playImmediately(atRate: request.preferences.playbackRate) }
        UIApplication.shared.isIdleTimerDisabled = true
    }

    func stop() { player.pause(); player.replaceCurrentItem(with: nil); currentID = nil; statusObservation = nil; preparationTimeout?.cancel(); UIApplication.shared.isIdleTimerDisabled = false }
    func pauseForBackgroundIfNeeded() { shouldResume = player.rate > 0; player.pause() }
    func resumeAfterBackgroundIfNeeded() { if shouldResume { player.play() }; shouldResume = false }
    private func handleInterruption(_ note: Notification) {
        guard let raw = note.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt,
              let type = AVAudioSession.InterruptionType(rawValue: raw) else { return }
        if type == .began { shouldResume = player.rate > 0; player.pause() }
        else if shouldResume { try? AVAudioSession.sharedInstance().setActive(true); player.play(); shouldResume = false }
    }

    private func tryNextCandidate(_ request: PlaybackRequest, failedIndex: Int, message: String) {
        guard currentID == request.id else { return }
        preparationTimeout?.cancel()
        let nextIndex = failedIndex + 1
        if playbackCandidates(for: request.item.url).indices.contains(nextIndex) {
            Task {
                try? await Task.sleep(for: .milliseconds(350))
                if self.currentID == request.id {
                    self.prepare(request, autoplay: true, candidateIndex: nextIndex)
                }
            }
        } else {
            onError?("Yayın açılamadı: \(message)")
        }
    }

    private func playbackCandidates(for source: URL) -> [URL] {
        guard source.pathExtension.caseInsensitiveCompare("ts") == .orderedSame,
              var components = URLComponents(url: source, resolvingAgainstBaseURL: false) else {
            return [source]
        }
        components.path = String(components.path.dropLast(2)) + "m3u8"
        guard let hlsURL = components.url, hlsURL != source else { return [source] }
        return [hlsURL, source]
    }

    private func applyMediaPreferences(_ preferences: PlaybackPreferences, to item: AVPlayerItem) {
        let asset = item.asset
        if preferences.audioLanguage != "auto", let group = asset.mediaSelectionGroup(forMediaCharacteristic: .audible) {
            let option = group.options.first { $0.extendedLanguageTag?.hasPrefix(preferences.audioLanguage) == true }
            if let option { item.select(option, in: group) }
        }
        if let group = asset.mediaSelectionGroup(forMediaCharacteristic: .legible) {
            if preferences.subtitleMode == "off" { item.select(nil, in: group) }
            else if let option = group.options.first(where: { $0.extendedLanguageTag?.hasPrefix(preferences.subtitleLanguage) == true }) {
                item.select(option, in: group)
            }
        }
    }
}
