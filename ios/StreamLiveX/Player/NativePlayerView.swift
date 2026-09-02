import SwiftUI
import UIKit

struct NativePlayerView: UIViewRepresentable {
    let controller: NativePlayerController
    let showsControls: Bool
    final class Coordinator { let controller: NativePlayerController; init(controller: NativePlayerController) { self.controller = controller } }
    func makeCoordinator() -> Coordinator { Coordinator(controller: controller) }
    func makeUIView(context: Context) -> UIView { let view = UIView(); view.backgroundColor = .black; view.isUserInteractionEnabled = showsControls; controller.attach(to: view); return view }
    func updateUIView(_ uiView: UIView, context: Context) { controller.attach(to: uiView) }
    static func dismantleUIView(_ uiView: UIView, coordinator: Coordinator) { coordinator.controller.detach(from: uiView) }
}

private enum PlayerPanel: Identifiable { case audio, subtitles; var id: Int { self == .audio ? 0 : 1 } }

struct NativePlayerScreen: View {
    @ObservedObject var model: WebPlayerModel
    @ObservedObject private var downloads = NativeDownloadManager.shared
    let request: PlaybackRequest
    @State private var controlsVisible = true
    @State private var hideTask: Task<Void, Never>?
    @State private var panel: PlayerPanel?
    @State private var subtitlePreferences: PlaybackPreferences

    init(model: WebPlayerModel, request: PlaybackRequest) {
        self.model = model
        self.request = request
        let key = "StreamLiveX.PlayerPreferences.\(request.profileID)"
        let saved = UserDefaults.standard.data(forKey: key).flatMap { try? JSONDecoder().decode(PlaybackPreferences.self, from: $0) }
        _subtitlePreferences = State(initialValue: saved ?? request.preferences)
    }

    var body: some View {
        GeometryReader { geometry in
            ZStack {
                Color.black.ignoresSafeArea()
                NativePlayerView(controller: model.player, showsControls: true).ignoresSafeArea()
                Color.clear.contentShape(Rectangle()).onTapGesture { setControls(!controlsVisible) }
                if controlsVisible { controls(landscape: geometry.size.width > geometry.size.height).transition(.opacity) }
            }
        }
        .background(Color.black).foregroundStyle(.white)
        .persistentSystemOverlays(controlsVisible ? .visible : .hidden)
        .statusBarHidden(!controlsVisible)
        .onAppear { model.player.onError = model.reportError; scheduleHide() }
        .onDisappear { hideTask?.cancel(); model.reportProgress() }
        .onChange(of: model.player.isPlaying) { _, playing in if playing { scheduleHide() } else { hideTask?.cancel(); controlsVisible = true } }
        .animation(.easeInOut(duration: 0.2), value: controlsVisible)
        .sheet(item: $panel) { selected in
            if selected == .audio { audioPanel } else { subtitlePanel }
        }
    }

    private func controls(landscape: Bool) -> some View {
        ZStack {
            LinearGradient(colors: [.black.opacity(0.78), .clear, .black.opacity(0.82)], startPoint: .top, endPoint: .bottom).ignoresSafeArea().allowsHitTesting(false)
            VStack(spacing: 0) {
                topBar
                Spacer()
                centerControls(landscape: landscape)
                Spacer()
                if request.item.isLive { liveFooter } else { vodFooter(landscape: landscape) }
            }
            .padding(.horizontal, landscape ? 28 : 16)
            .padding(.vertical, landscape ? 12 : 8)
        }
    }

    private var topBar: some View {
        HStack(spacing: 12) {
            controlButton("chevron.left", label: "Geri") { model.closePlayer() }
            VStack(alignment: .leading, spacing: 3) {
                Text(request.item.name).font(.headline).lineLimit(1)
                Text(request.item.isLive ? "CANLI" : request.item.kind == "series" ? "DİZİ BÖLÜMÜ" : "FİLM").font(.caption2.weight(.bold)).foregroundStyle(request.item.isLive ? .red : .white.opacity(0.65))
            }
            Spacer()
            if !request.item.isLive { controlButton(downloadIcon, label: "Çevrimdışı indir") { toggleDownload() } }
        }
    }

    private func centerControls(landscape: Bool) -> some View {
        HStack(spacing: landscape ? 70 : 42) {
            if !request.item.isLive { largeControl("gobackward.10", label: "10 saniye geri", size: landscape ? 36 : 31) { model.player.seek(by: -10); scheduleHide() } }
            Button { model.player.togglePlayback() } label: {
                Image(systemName: model.player.isPlaying ? "pause.fill" : "play.fill")
                    .font(.system(size: landscape ? 47 : 36, weight: .bold))
                    .frame(width: landscape ? 90 : 76, height: landscape ? 90 : 76)
                    .background(.black.opacity(0.36), in: Circle()).overlay(Circle().stroke(.white.opacity(0.18)))
            }.accessibilityLabel(model.player.isPlaying ? "Duraklat" : "Oynat")
            if !request.item.isLive { largeControl("goforward.10", label: "10 saniye ileri", size: landscape ? 36 : 31) { model.player.seek(by: 10); scheduleHide() } }
        }
    }

    private func vodFooter(landscape: Bool) -> some View {
        VStack(spacing: landscape ? 12 : 9) {
            HStack(spacing: 10) {
                Text(time(model.player.currentSeconds)).frame(width: 52, alignment: .leading)
                Slider(value: Binding(get: { model.player.currentSeconds }, set: { model.player.seek(to: $0) }), in: 0...max(model.player.durationSeconds, 1), onEditingChanged: { editing in if editing { hideTask?.cancel() } else { scheduleHide() } }).tint(.red)
                Text("−\(time(max(0, model.player.durationSeconds - model.player.currentSeconds)))").frame(width: 62, alignment: .trailing)
            }.font(.caption.monospacedDigit()).foregroundStyle(.white.opacity(0.82))
            HStack(spacing: landscape ? 24 : 12) {
                footerButton("speaker.wave.2", "Ses") { model.player.refreshTracks(); panel = .audio; hideTask?.cancel() }
                footerButton("captions.bubble", "Altyazı") { model.player.refreshTracks(); panel = .subtitles; hideTask?.cancel() }
                Menu { ForEach([0.5, 0.75, 1, 1.25, 1.5, 2], id: \.self) { speed in Button(String(format: "%g×", speed)) { model.player.setPlaybackRate(Float(speed)); subtitlePreferences.playbackRate = Float(speed); persistPreferences() } } } label: { Label(String(format: "%g×", model.player.player.rate), systemImage: "speedometer") }
                if request.item.kind == "series" {
                    footerButton("rectangle.stack", "Bölümler") { model.closePlayer() }
                    if request.item.hasPrevious { footerButton("backward.end", "Önceki") { model.requestPrevious() } }
                    if request.item.hasNext { footerButton("forward.end", "Sonraki") { model.requestNext() } }
                }
                Spacer(minLength: 0)
            }.font(.caption.weight(.semibold))
        }
        .padding(.top, 5)
    }

    private var liveFooter: some View {
        HStack { Circle().fill(.red).frame(width: 8, height: 8); Text("Canlı yayın").font(.subheadline.weight(.semibold)); Spacer(); if !model.player.audioTracks.isEmpty { footerButton("speaker.wave.2", "Ses") { panel = .audio } } }
    }

    private var audioPanel: some View {
        NavigationStack {
            List {
                Section("Kullanılabilir ses parçaları") {
                    if model.player.audioTracks.isEmpty { Text("Bu kaynakta seçilebilir ses parçası bulunamadı.").foregroundStyle(.secondary) }
                    ForEach(model.player.audioTracks) { track in Button { model.player.selectAudioTrack(track.id); panel = nil; scheduleHide() } label: { HStack { Text(track.name); Spacer(); if model.player.selectedAudioTrack == track.id { Image(systemName: "checkmark").foregroundStyle(.purple) } } }.foregroundStyle(.primary) }
                }
            }.navigationTitle("Ses").toolbar { ToolbarItem(placement: .topBarTrailing) { Button("Bitti") { panel = nil; scheduleHide() } } }
        }.presentationDetents([.medium, .large]).presentationDragIndicator(.visible).preferredColorScheme(.dark)
    }

    private var subtitlePanel: some View {
        NavigationStack {
            List {
                Section("Altyazı parçası") {
                    Button { selectSubtitleTrack(-1) } label: { selectionRow("Kapalı", selected: model.player.selectedSubtitleTrack < 0) }.foregroundStyle(.primary)
                    ForEach(model.player.subtitleTracks.filter { $0.id >= 0 }) { track in Button { selectSubtitleTrack(track.id) } label: { selectionRow(track.name, selected: model.player.selectedSubtitleTrack == track.id) }.foregroundStyle(.primary) }
                    if model.player.subtitleTracks.filter({ $0.id >= 0 }).isEmpty { Text("Gömülü veya harici altyazı bulunamadı.").foregroundStyle(.secondary) }
                }
                Section("Boyut") { Picker("Altyazı boyutu", selection: $subtitlePreferences.subtitleSize) { Text("Küçük").tag(80); Text("Orta").tag(100); Text("Büyük").tag(130); Text("Ekstra Büyük").tag(165) }.pickerStyle(.segmented) }
                Section("Yazı rengi") { colorChoices(values: [("Beyaz", "#FFFFFF"), ("Sarı", "#FFD60A"), ("Siyah", "#000000"), ("Mavi", "#64D2FF")], selection: $subtitlePreferences.subtitleColor) }
                Section("Arka plan") {
                    Picker("Arka plan", selection: $subtitlePreferences.subtitleBackground) { Text("Yok").tag("none"); Text("Siyah").tag("box"); Text("Yarı saydam").tag("shadow") }.pickerStyle(.segmented)
                    if subtitlePreferences.subtitleBackground != "none" { VStack(alignment: .leading) { Text("Opaklık %\(Int(subtitlePreferences.subtitleBackgroundOpacity * 100))"); Slider(value: $subtitlePreferences.subtitleBackgroundOpacity, in: 0.2...1, step: 0.1) } }
                }
                Section("Konum") { Stepper("Alt kenardan \(subtitlePreferences.subtitleVerticalPosition)", value: $subtitlePreferences.subtitleVerticalPosition, in: 0...20) }
                Section { Button("Ayarları Uygula") { model.player.applySubtitlePreferences(subtitlePreferences); persistPreferences(); panel = nil; scheduleHide() }.frame(maxWidth: .infinity).fontWeight(.semibold) }
            }
            .navigationTitle("Altyazı").toolbar { ToolbarItem(placement: .topBarTrailing) { Button("Bitti") { panel = nil; scheduleHide() } } }
        }.presentationDetents([.large]).presentationDragIndicator(.visible).preferredColorScheme(.dark)
    }

    private func colorChoices(values: [(String, String)], selection: Binding<String>) -> some View { HStack { ForEach(values, id: \.1) { value in Button { selection.wrappedValue = value.1 } label: { VStack { Circle().fill(Color(hex: value.1)).frame(width: 30, height: 30).overlay(Circle().stroke(selection.wrappedValue == value.1 ? .purple : .clear, lineWidth: 3)); Text(value.0).font(.caption2) } }.buttonStyle(.plain).frame(maxWidth: .infinity) } } }
    private func selectionRow(_ title: String, selected: Bool) -> some View { HStack { Text(title); Spacer(); if selected { Image(systemName: "checkmark").foregroundStyle(.purple) } } }
    private func controlButton(_ icon: String, label: String, action: @escaping () -> Void) -> some View { Button(action: action) { Image(systemName: icon).frame(width: 44, height: 44).background(.black.opacity(0.3), in: Circle()) }.accessibilityLabel(label) }
    private func largeControl(_ icon: String, label: String, size: CGFloat, action: @escaping () -> Void) -> some View { Button(action: action) { Image(systemName: icon).font(.system(size: size, weight: .medium)).frame(width: 58, height: 58) }.accessibilityLabel(label) }
    private func footerButton(_ icon: String, _ title: String, action: @escaping () -> Void) -> some View { Button(action: action) { Label(title, systemImage: icon).lineLimit(1) } }
    private func setControls(_ visible: Bool) { controlsVisible = visible; visible ? scheduleHide() : hideTask?.cancel() }
    private func scheduleHide() { hideTask?.cancel(); guard model.player.isPlaying else { return }; hideTask = Task { try? await Task.sleep(for: .seconds(4)); if !Task.isCancelled { await MainActor.run { controlsVisible = false } } } }
    private func selectSubtitleTrack(_ id: Int32) {
        model.player.selectSubtitleTrack(id)
        subtitlePreferences.subtitleMode = id < 0 ? "off" : "on"
        persistPreferences()
    }
    private func persistPreferences() {
        if let data = try? JSONEncoder().encode(subtitlePreferences) {
            UserDefaults.standard.set(data, forKey: "StreamLiveX.PlayerPreferences.\(request.profileID)")
        }
        model.updateSubtitlePreferences(subtitlePreferences)
    }
    private func time(_ seconds: Double) -> String { guard seconds.isFinite else { return "00:00" }; let value = max(0, Int(seconds)); return value >= 3600 ? String(format: "%d:%02d:%02d", value / 3600, value / 60 % 60, value % 60) : String(format: "%02d:%02d", value / 60, value % 60) }
    private var download: OfflineDownload? { downloads.downloads.first { $0.id == request.id } }
    private var downloadIcon: String { switch download?.state { case .some(.downloaded): "checkmark.circle.fill"; case .some(.downloading): "arrow.down.circle.dotted"; case .some(.paused): "pause.circle"; case .some(.failed): "exclamationmark.circle"; case nil: "arrow.down.circle" } }
    private func toggleDownload() { guard let download else { downloads.start(id: request.id, title: request.item.name, source: request.item.url, artworkURL: request.item.artwork, kind: request.item.kind); return }; switch download.state { case .downloading: downloads.pause(request.id); case .paused: downloads.resume(request.id); case .failed: downloads.start(id: request.id, title: request.item.name, source: request.item.url, artworkURL: request.item.artwork, kind: request.item.kind); case .downloaded: break }; scheduleHide() }
}

private extension Color {
    init(hex: String) { let value = Int(hex.trimmingCharacters(in: CharacterSet(charactersIn: "#")), radix: 16) ?? 0xFFFFFF; self.init(red: Double((value >> 16) & 255) / 255, green: Double((value >> 8) & 255) / 255, blue: Double(value & 255) / 255) }
}
