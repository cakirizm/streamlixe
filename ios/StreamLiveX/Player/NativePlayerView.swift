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
    let request: PlaybackRequest
    var body: some View {
        ZStack(alignment: .top) {
            Color.black.ignoresSafeArea()
            NativePlayerView(controller: model.player, showsControls: true).ignoresSafeArea()
            HStack {
                Button { model.closePlayer() } label: { Image(systemName: "chevron.down").padding(12).background(.ultraThinMaterial, in: Circle()) }
                Text(request.item.name).font(.headline).lineLimit(1)
                Spacer()
                Button { model.player.togglePlayback() } label: {
                    Image(systemName: model.player.isPlaying ? "pause.fill" : "play.fill")
                        .padding(12).background(.ultraThinMaterial, in: Circle())
                }
                if request.item.hasNext { Button("Sonraki Bölüm") { model.requestNext() }.buttonStyle(.borderedProminent) }
            }.padding().foregroundStyle(.white)
        }
        .onAppear { model.player.onError = model.reportError }
        .onDisappear { model.reportProgress() }
    }
}
