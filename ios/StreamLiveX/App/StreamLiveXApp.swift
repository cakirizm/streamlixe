import SwiftUI

@main
struct StreamLiveXApp: App {
    @Environment(\.scenePhase) private var scenePhase
    @StateObject private var model = WebPlayerModel()

    var body: some Scene {
        WindowGroup {
            WebContainer(model: model)
                .ignoresSafeArea(.keyboard)
                .onOpenURL { model.openDeepLink($0) }
        }
        .onChange(of: scenePhase) { _, phase in model.scenePhaseChanged(phase) }
    }
}
