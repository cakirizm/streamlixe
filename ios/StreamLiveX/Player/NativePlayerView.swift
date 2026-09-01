import SwiftUI
import UIKit

struct NativePlayerView: UIViewRepresentable {
    let controller: NativePlayerController
    let showsControls: Bool

    final class Coordinator {
        let controller: NativePlayerController
        init(controller: NativePlayerController) { self.controller = controller }
    }

    func makeCoordinator() -> Coordinator { Coordinator(controller: controller) }

    func makeUIView(context: Context) -> UIView {
        let view = UIView()
        view.backgroundColor = .black
        view.isUserInteractionEnabled = showsControls
        controller.attach(to: view)
        return view
    }

    func updateUIView(_ uiView: UIView, context: Context) {
        controller.attach(to: uiView)
    }

    static func dismantleUIView(_ uiView: UIView, coordinator: Coordinator) {
        // Preview -> tam ekran gecisinde yeni drawable once baglanabilir. Controller,
        // yalnizca halen bu gorunume bagliysa eski gorunumu ayirir.
        coordinator.controller.detach(from: uiView)
    }
}

struct NativePlayerScreen: View {
    @ObservedObject var model: WebPlayerModel
    @ObservedObject private var downloads = NativeDownloadManager.shared
    let request: PlaybackRequest
    @State private var controlsVisible = true
    @State private var hideTask: Task<Void, Never>?
    @State private var audioSheet = false
    @State private var subtitleSheet = false

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            NativePlayerView(controller: model.player, showsControls: true).ignoresSafeArea()
            Color.clear.contentShape(Rectangle()).onTapGesture { setControls(!controlsVisible) }
            if controlsVisible {
                VStack(spacing: 0) {
                    HStack(spacing: 12) {
                        Button { model.closePlayer() } label: { Image(systemName: "chevron.down").frame(width: 44, height: 44).background(.ultraThinMaterial, in: Circle()) }
                        VStack(alignment: .leading, spacing: 2) {
                            Text(request.item.name).font(.headline).lineLimit(1)
                            Text(request.item.isLive ? "CANLI" : request.item.kind.uppercased()).font(.caption2.weight(.bold)).foregroundStyle(request.item.isLive ? .red : .secondary)
                        }
                        Spacer()
                        if !request.item.isLive && request.item.url.pathExtension.lowercased() == "m3u8" {
                            Button { toggleDownload() } label: {
                                Image(systemName: downloadIcon).frame(width: 44, height: 44).background(.ultraThinMaterial, in: Circle())
                            }.accessibilityLabel("Çevrimdışı indir")
                        }
                        if request.item.hasNext { Button("Sonraki") { model.requestNext() }.buttonStyle(.borderedProminent).tint(.purple) }
                    }
                    .padding(.horizontal, 16).padding(.top, 10)
                    Spacer()
                    HStack(spacing: 34) {
                        if !request.item.isLive { Button { model.player.seek(by: -10); scheduleHide() } label: { Image(systemName: "gobackward.10").font(.title) } }
                        Button { model.player.togglePlayback(); scheduleHide() } label: {
                            Image(systemName: model.player.isPlaying ? "pause.fill" : "play.fill")
                                .font(.system(size: 27, weight: .bold)).frame(width: 66, height: 66).background(.ultraThinMaterial, in: Circle())
                        }
                        if !request.item.isLive { Button { model.player.seek(by: 10); scheduleHide() } label: { Image(systemName: "goforward.10").font(.title) } }
                    }
                    Spacer()
                    if request.item.isLive {
                        HStack { Circle().fill(.red).frame(width: 8, height: 8); Text("Canlı yayın").font(.subheadline.weight(.semibold)); Spacer() }
                            .padding(16).background(.ultraThinMaterial)
                    } else {
                        VStack(spacing: 8) {
                            HStack(spacing: 12) {
                                if !model.player.audioTracks.isEmpty { Button { audioSheet = true; hideTask?.cancel() } label: { Label("Ses", systemImage: "speaker.wave.2") }.buttonStyle(.bordered) }
                                if !model.player.subtitleTracks.isEmpty { Button { subtitleSheet = true; hideTask?.cancel() } label: { Label("Altyazı", systemImage: "captions.bubble") }.buttonStyle(.bordered) }
                                Spacer()
                            }
                            Slider(value: Binding(get: { model.player.currentSeconds }, set: { model.player.seek(to: $0) }), in: 0...max(model.player.durationSeconds, 1)).tint(.purple)
                            HStack { Text(time(model.player.currentSeconds)); Spacer(); Text(time(model.player.durationSeconds)) }.font(.caption.monospacedDigit()).foregroundStyle(.secondary)
                        }
                        .padding(.horizontal, 18).padding(.vertical, 12).background(.ultraThinMaterial)
                    }
                }
                .foregroundStyle(.white).transition(.opacity)
            }
        }
        .persistentSystemOverlays(controlsVisible ? .visible : .hidden)
        .onAppear { model.player.onError = model.reportError; scheduleHide() }
        .onDisappear { hideTask?.cancel(); model.reportProgress() }
        .animation(.easeInOut(duration: 0.2), value: controlsVisible)
        .confirmationDialog("Ses Parçası", isPresented: $audioSheet) { ForEach(model.player.audioTracks) { track in Button(track.name) { model.player.selectAudioTrack(track.id); scheduleHide() } } }
        .confirmationDialog("Altyazı", isPresented: $subtitleSheet) { Button("Kapalı") { model.player.selectSubtitleTrack(-1); scheduleHide() }; ForEach(model.player.subtitleTracks.filter { $0.id >= 0 }) { track in Button(track.name) { model.player.selectSubtitleTrack(track.id); scheduleHide() } } }
    }

    private func setControls(_ visible: Bool) { controlsVisible = visible; if visible { scheduleHide() } else { hideTask?.cancel() } }
    private func scheduleHide() { hideTask?.cancel(); hideTask = Task { try? await Task.sleep(for: .seconds(4)); if !Task.isCancelled { await MainActor.run { controlsVisible = false } } } }
    private func time(_ seconds: Double) -> String { guard seconds.isFinite else { return "00:00" }; let value = max(0, Int(seconds)); return value >= 3600 ? String(format: "%d:%02d:%02d", value / 3600, value / 60 % 60, value % 60) : String(format: "%02d:%02d", value / 60, value % 60) }
    private var download: OfflineDownload? { downloads.downloads.first { $0.id == request.id } }
    private var downloadIcon: String { switch download?.state { case .some(.downloaded): "checkmark.circle.fill"; case .some(.downloading): "arrow.down.circle.dotted"; case .some(.paused): "pause.circle"; case .some(.failed): "exclamationmark.circle"; case nil: "arrow.down.circle" } }
    private func toggleDownload() {
        guard let download else { downloads.start(id: request.id, title: request.item.name, source: request.item.url); return }
        switch download.state { case .downloading: downloads.pause(request.id); case .paused: downloads.resume(request.id); case .failed: downloads.start(id: request.id, title: request.item.name, source: request.item.url); case .downloaded: break }
        scheduleHide()
    }
}
