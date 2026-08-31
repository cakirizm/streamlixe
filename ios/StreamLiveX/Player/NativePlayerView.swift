import AVKit
import SwiftUI

struct NativePlayerView: UIViewControllerRepresentable {
    let controller: NativePlayerController
    let showsControls: Bool
    func makeUIViewController(context: Context) -> AVPlayerViewController {
        let view = AVPlayerViewController()
        view.player = controller.player
        view.showsPlaybackControls = showsControls
        view.allowsPictureInPicturePlayback = false
        return view
    }
    func updateUIViewController(_ uiViewController: AVPlayerViewController, context: Context) {
        uiViewController.player = controller.player
        uiViewController.showsPlaybackControls = showsControls
    }
}

struct NativePlayerScreen: View {
    @ObservedObject var model: WebPlayerModel
    let request: PlaybackRequest
    var body: some View {
        ZStack(alignment: .top) {
            Color.black.ignoresSafeArea()
            NativePlayerView(controller: model.player, showsControls: true).ignoresSafeArea()
            HStack {
                Button { model.closePlayer() } label: { Image(systemName: "chevron.down").padding(12).background(.ultraThinMaterial, in: Circle()) }
                Text(request.item.name).font(.headline).lineLimit(1)
                Spacer()
                if request.item.hasNext { Button("Sonraki Bölüm") { model.requestNext() }.buttonStyle(.borderedProminent) }
            }.padding().foregroundStyle(.white)
        }
        .onAppear { model.player.onError = model.reportError }
        .onDisappear { model.reportProgress() }
    }
}
