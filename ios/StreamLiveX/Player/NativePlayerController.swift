import AVFoundation
import MobileVLCKit
import UIKit

struct NativeMediaTrack: Identifiable, Hashable { let id: Int32; let name: String }

@MainActor
final class NativePlayerController: NSObject, ObservableObject, VLCMediaPlayerDelegate {
    let player = VLCMediaPlayer()
    @Published private(set) var isPlaying = false
    @Published private(set) var currentSeconds: Double = 0
    @Published private(set) var durationSeconds: Double = 0
    @Published private(set) var audioTracks: [NativeMediaTrack] = []
    @Published private(set) var subtitleTracks: [NativeMediaTrack] = []
    @Published private(set) var selectedAudioTrack: Int32 = -1
    @Published private(set) var selectedSubtitleTrack: Int32 = -1
    private var currentID: String?
    private var currentRequest: PlaybackRequest?
    private var candidateIndex = 0
    private var autoplay = true
    private var shouldResume = false
    private var preparationTimeout: Task<Void, Never>?
    private var pendingResumeMilliseconds: Double = 0
    private var pendingAudioTrack: Int32?
    private var pendingSubtitleTrack: Int32?
    private var pauseAfterPreferenceReload = false
    private var externalSubtitleAttached = false
    var onError: ((String) -> Void)?

    var progress: (current: Double, duration: Double) {
        (currentSeconds, durationSeconds)
    }

    override init() {
        super.init()
        player.delegate = self
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .moviePlayback)
        try? AVAudioSession.sharedInstance().setActive(true)
        NotificationCenter.default.addObserver(forName: AVAudioSession.interruptionNotification, object: nil, queue: .main) { [weak self] note in
            Task { @MainActor in self?.handleInterruption(note) }
        }
    }

    func load(_ request: PlaybackRequest, autoplay: Bool) {
        if currentID == request.id {
            if autoplay { player.play() }
            return
        }
        currentID = request.id
        currentRequest = request
        candidateIndex = 0
        self.autoplay = autoplay
        pendingResumeMilliseconds = request.item.isLive ? 0 : request.resumeMilliseconds
        externalSubtitleAttached = false
        prepare(request, candidateIndex: candidateIndex)
    }

    private func prepare(_ request: PlaybackRequest, candidateIndex: Int) {
        let candidates = playbackCandidates(for: request.item.url)
        guard candidates.indices.contains(candidateIndex) else {
            onError?("Yayın açılamadı. Sağlayıcı bağlantıyı reddetti veya kaynak çevrimdışı.")
            return
        }

        preparationTimeout?.cancel()
        player.stop()

        let media = VLCMedia(url: candidates[candidateIndex])
        media.addOption(":http-user-agent=VLC/3.0 StreamLiveX-iOS/1.0")
        media.addOption(":http-reconnect")
        media.addOption(request.item.isLive ? ":network-caching=1500" : ":network-caching=3000")
        media.addOption(":freetype-fontsize=\(subtitleFontSize(request.preferences.subtitleSize))")
        media.addOption(":freetype-color=\(subtitleColorValue(request.preferences.subtitleColor))")
        let subtitleBackgroundOpacity = request.preferences.subtitleBackground == "none" ? 0 : request.preferences.subtitleBackground == "box" ? 255 : Int(request.preferences.subtitleBackgroundOpacity * 255)
        media.addOption(":freetype-background-opacity=\(subtitleBackgroundOpacity)")
        media.addOption(":freetype-background-color=0")
        media.addOption(":sub-margin=\(request.preferences.subtitleVerticalPosition * 4)")
        if !request.item.isLive, pendingResumeMilliseconds > 0 {
            media.addOption(":start-time=\(pendingResumeMilliseconds / 1000)")
        }
        player.media = media
        player.rate = request.preferences.playbackRate

        preparationTimeout = Task { [weak self] in
            try? await Task.sleep(for: .seconds(15))
            guard !Task.isCancelled, let self,
                  self.currentID == request.id,
                  self.player.state != .playing else { return }
            self.tryNextCandidate(message: "Sunucu 15 saniye içinde yanıt vermedi")
        }

        if autoplay { player.play() }
        UIApplication.shared.isIdleTimerDisabled = true
    }

    func attach(to view: UIView) {
        if player.drawable as? UIView !== view { player.drawable = view }
    }

    func detach(from view: UIView) {
        if player.drawable as? UIView === view { player.drawable = nil }
    }

    func togglePlayback() {
        player.isPlaying ? player.pause() : player.play()
    }

    func seek(by seconds: Double) {
        guard durationSeconds > 0 else { return }
        seek(to: currentSeconds + seconds)
    }

    func seek(to seconds: Double) {
        guard durationSeconds > 0 else { return }
        let target = min(max(0, seconds), durationSeconds)
        player.time = VLCTime(int: Int32(min(target * 1000, Double(Int32.max))))
        updateProgress()
    }

    func selectAudioTrack(_ id: Int32) { player.currentAudioTrackIndex = id; selectedAudioTrack = id }
    func selectSubtitleTrack(_ id: Int32) { player.currentVideoSubTitleIndex = id; selectedSubtitleTrack = id }
    func setPlaybackRate(_ rate: Float) { player.rate = min(2, max(0.5, rate)); if var request = currentRequest { request.preferences.playbackRate = player.rate; currentRequest = request } }
    func applySubtitlePreferences(_ preferences: PlaybackPreferences) {
        guard var request = currentRequest else { return }
        let resume = currentSeconds * 1000
        pendingAudioTrack = player.currentAudioTrackIndex
        pendingSubtitleTrack = player.currentVideoSubTitleIndex
        pauseAfterPreferenceReload = !player.isPlaying
        request.preferences = preferences
        currentRequest = request
        pendingResumeMilliseconds = resume
        externalSubtitleAttached = false
        prepare(request, candidateIndex: candidateIndex)
    }

    func stop() {
        preparationTimeout?.cancel()
        player.stop()
        player.media = nil
        currentID = nil
        currentRequest = nil
        currentSeconds = 0
        durationSeconds = 0
        isPlaying = false
        UIApplication.shared.isIdleTimerDisabled = false
    }

    func pauseForBackgroundIfNeeded() {
        shouldResume = player.isPlaying
        player.pause()
    }

    func resumeAfterBackgroundIfNeeded() {
        if shouldResume { player.play() }
        shouldResume = false
    }

    private func handleInterruption(_ note: Notification) {
        guard let raw = note.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt,
              let type = AVAudioSession.InterruptionType(rawValue: raw) else { return }
        if type == .began { shouldResume = player.isPlaying; player.pause() }
        else if shouldResume { try? AVAudioSession.sharedInstance().setActive(true); player.play(); shouldResume = false }
    }

    nonisolated func mediaPlayerStateChanged(_ notification: Notification) {
        Task { @MainActor [weak self] in self?.handlePlayerState() }
    }

    nonisolated func mediaPlayerTimeChanged(_ notification: Notification) {
        Task { @MainActor [weak self] in self?.updateProgress() }
    }

    private func handlePlayerState() {
        isPlaying = player.state == .playing
        switch player.state {
        case .playing:
            preparationTimeout?.cancel()
            if pendingResumeMilliseconds > 0 {
                player.time = VLCTime(int: Int32(min(pendingResumeMilliseconds, Double(Int32.max))))
                pendingResumeMilliseconds = 0
            }
            updateProgress()
            attachPreferredExternalSubtitleIfNeeded()
            if let request = currentRequest {
                player.currentVideoSubTitleDelay = Int(request.preferences.subtitleDelay * 1_000_000)
            }
            refreshTracks()
            restoreTracksAfterPreferenceReload()
        case .error:
            tryNextCandidate(message: "VLC medya akışını başlatamadı")
        default:
            break
        }
    }

    private func updateProgress() {
        currentSeconds = max(0, Double(player.time.intValue) / 1000)
        durationSeconds = max(0, Double(player.media?.length.intValue ?? 0) / 1000)
    }

    func refreshTracks() {
        let audioNames = player.audioTrackNames ?? []
        let audioIndexes = player.audioTrackIndexes ?? []
        audioTracks = zip(audioNames, audioIndexes).compactMap { name, index in
            guard let number = index as? NSNumber else { return nil }
            return NativeMediaTrack(id: number.int32Value, name: String(describing: name))
        }
        let subtitleNames = player.videoSubTitlesNames ?? []
        let subtitleIndexes = player.videoSubTitlesIndexes ?? []
        subtitleTracks = zip(subtitleNames, subtitleIndexes).compactMap { name, index in
            guard let number = index as? NSNumber else { return nil }
            return NativeMediaTrack(id: number.int32Value, name: String(describing: name))
        }
        selectedAudioTrack = player.currentAudioTrackIndex
        selectedSubtitleTrack = player.currentVideoSubTitleIndex
    }

    private func attachPreferredExternalSubtitleIfNeeded() {
        guard !externalSubtitleAttached, let request = currentRequest,
              request.preferences.subtitleMode != "off", !request.item.subtitles.isEmpty else { return }
        let preferred = request.item.subtitles.first(where: { request.preferences.subtitleLanguage == "auto" || $0.language.lowercased().hasPrefix(request.preferences.subtitleLanguage.lowercased()) }) ?? request.item.subtitles[0]
        externalSubtitleAttached = player.addPlaybackSlave(preferred.src, type: .subtitle, enforce: true) == 0
        player.currentVideoSubTitleDelay = Int(request.preferences.subtitleDelay * 1_000_000)
    }

    private func subtitleColorValue(_ value: String) -> Int {
        Int(value.trimmingCharacters(in: CharacterSet(charactersIn: "#")), radix: 16) ?? 0xFFFFFF
    }

    private func subtitleFontSize(_ preference: Int) -> Int {
        switch preference {
        case ..<90: return 16
        case ..<115: return 20
        case ..<150: return 27
        default: return 34
        }
    }

    private func restoreTracksAfterPreferenceReload() {
        guard pendingAudioTrack != nil || pendingSubtitleTrack != nil || pauseAfterPreferenceReload else { return }
        let audio = pendingAudioTrack
        let subtitle = pendingSubtitleTrack
        let shouldPause = pauseAfterPreferenceReload
        pendingAudioTrack = nil
        pendingSubtitleTrack = nil
        pauseAfterPreferenceReload = false
        Task { [weak self] in
            try? await Task.sleep(for: .milliseconds(180))
            guard let self else { return }
            self.refreshTracks()
            if let audio, self.audioTracks.contains(where: { $0.id == audio }) { self.selectAudioTrack(audio) }
            if let subtitle, subtitle < 0 || self.subtitleTracks.contains(where: { $0.id == subtitle }) { self.selectSubtitleTrack(subtitle) }
            if shouldPause { self.player.pause() }
        }
    }

    private func tryNextCandidate(message: String) {
        guard let request = currentRequest, currentID == request.id else { return }
        preparationTimeout?.cancel()
        let nextIndex = candidateIndex + 1
        if playbackCandidates(for: request.item.url).indices.contains(nextIndex) {
            candidateIndex = nextIndex
            Task {
                try? await Task.sleep(for: .milliseconds(350))
                if self.currentID == request.id {
                    self.prepare(request, candidateIndex: nextIndex)
                }
            }
        } else {
            onError?("Yayın açılamadı: \(message)")
        }
    }

    private func playbackCandidates(for source: URL) -> [URL] {
        let candidates = [source]
        guard source.pathExtension.caseInsensitiveCompare("ts") == .orderedSame,
              var components = URLComponents(url: source, resolvingAgainstBaseURL: false) else {
            return candidates
        }
        components.path = String(components.path.dropLast(2)) + "m3u8"
        guard let hlsURL = components.url, hlsURL != source else { return candidates }
        return candidates + [hlsURL]
    }
}
