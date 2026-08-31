import AVFoundation
import UIKit

@MainActor
final class NativePlayerController: ObservableObject {
    let player = AVPlayer()
    private var currentID: String?
    private var retryCount = 0
    private var shouldResume = false
    private var statusObservation: NSKeyValueObservation?
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
        retryCount = 0
        prepare(request, autoplay: autoplay)
    }

    private func prepare(_ request: PlaybackRequest, autoplay: Bool) {
        let asset = AVURLAsset(url: request.item.url)
        let item = AVPlayerItem(asset: asset)
        item.preferredForwardBufferDuration = request.item.isLive ? 4 : 12
        item.canUseNetworkResourcesForLiveStreamingWhilePaused = false
        player.replaceCurrentItem(with: item)
        player.automaticallyWaitsToMinimizeStalling = true
        statusObservation = item.observe(\.status, options: [.new]) { [weak self] item, _ in
            Task { @MainActor in
                if case .failed = item.status { self?.retryOrReport(request, message: item.error?.localizedDescription ?? "Yayın açılamadı") }
                if case .readyToPlay = item.status { self?.applyMediaPreferences(request.preferences, to: item) }
            }
        }
        if request.resumeMilliseconds > 0 && !request.item.isLive {
            player.seek(to: CMTime(seconds: request.resumeMilliseconds / 1000, preferredTimescale: 600), toleranceBefore: .zero, toleranceAfter: .zero)
        }
        player.rate = autoplay ? request.preferences.playbackRate : 0
        if autoplay { player.playImmediately(atRate: request.preferences.playbackRate) }
        UIApplication.shared.isIdleTimerDisabled = true
    }

    func stop() { player.pause(); player.replaceCurrentItem(with: nil); currentID = nil; retryCount = 0; UIApplication.shared.isIdleTimerDisabled = false }
    func pauseForBackgroundIfNeeded() { shouldResume = player.rate > 0; player.pause() }
    func resumeAfterBackgroundIfNeeded() { if shouldResume { player.play() }; shouldResume = false }
    private func handleInterruption(_ note: Notification) {
        guard let raw = note.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt,
              let type = AVAudioSession.InterruptionType(rawValue: raw) else { return }
        if type == .began { shouldResume = player.rate > 0; player.pause() }
        else if shouldResume { try? AVAudioSession.sharedInstance().setActive(true); player.play(); shouldResume = false }
    }

    private func retryOrReport(_ request: PlaybackRequest, message: String) {
        guard currentID == request.id else { return }
        if retryCount < 1 {
            retryCount += 1
            Task { try? await Task.sleep(for: .seconds(1)); if self.currentID == request.id { self.prepare(request, autoplay: true) } }
        } else { onError?(message) }
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
