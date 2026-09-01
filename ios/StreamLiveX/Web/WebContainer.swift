import SwiftUI
import WebKit

struct WebContainer: View {
    @ObservedObject var model: WebPlayerModel

    var body: some View {
        ZStack(alignment: .topLeading) {
            BrowserView(model: model)
                .ignoresSafeArea(edges: .bottom)
            if let request = model.previewRequest, let bounds = model.previewBounds, bounds.visible {
                NativePlayerView(controller: model.player, showsControls: false)
                    .frame(width: bounds.width, height: bounds.height)
                    .offset(x: bounds.left, y: bounds.top)
                    .clipped()
                    .accessibilityLabel(request.item.name)
            }
            if let message = model.webError {
                ContentUnavailableView("Bağlantı kurulamadı", systemImage: "wifi.exclamationmark", description: Text(message))
                    .frame(maxWidth: .infinity, maxHeight: .infinity).background(.black)
            }
        }
        .background(Color.black)
        .fullScreenCover(item: $model.fullScreenRequest, onDismiss: { model.closePlayer() }) { request in
            NativePlayerScreen(model: model, request: request)
        }
        .sheet(isPresented: $model.downloadsPresented) { NativeDownloadsScreen(onPlay: model.playOffline) }
        .sheet(isPresented: $model.parentalPresented) { NativeParentalScreen(profileID: model.parentalProfile, categories: model.parentalCategories, onSettingsChange: model.setParentalSettings) }
    }
}


private struct BrowserView: UIViewRepresentable {
    @ObservedObject var model: WebPlayerModel

    func makeCoordinator() -> NativeBridge { NativeBridge(model: model) }
    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        config.websiteDataStore = .default()
        config.allowsInlineMediaPlayback = true
        config.mediaTypesRequiringUserActionForPlayback = []
        config.userContentController.addUserScript(NativeBridge.bootstrap)
        config.userContentController.add(context.coordinator, name: "streamlivex")
        let view = WKWebView(frame: .zero, configuration: config)
        view.customUserAgent = "StreamLiveXiOS/1.0"
        view.navigationDelegate = context.coordinator
        view.scrollView.contentInsetAdjustmentBehavior = .never
        model.webView = view
        view.load(URLRequest(url: AppConfiguration.webAppURL, cachePolicy: .useProtocolCachePolicy, timeoutInterval: 20))
        return view
    }
    func updateUIView(_ uiView: WKWebView, context: Context) {}
    static func dismantleUIView(_ uiView: WKWebView, coordinator: NativeBridge) {
        uiView.configuration.userContentController.removeScriptMessageHandler(forName: "streamlivex")
    }
}

extension NativeBridge: WKNavigationDelegate {
    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) { model?.webError = nil }
    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) { model?.webError = error.localizedDescription }
    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) { model?.webError = error.localizedDescription }
    func webView(_ webView: WKWebView, decidePolicyFor action: WKNavigationAction, decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
        guard let url = action.request.url else { decisionHandler(.cancel); return }
        if action.targetFrame?.isMainFrame != false,
           url.host == AppConfiguration.webAppURL.host || url == AppConfiguration.webAppURL { decisionHandler(.allow); return }
        if ["http", "https", "mailto", "tel"].contains(url.scheme?.lowercased()) { UIApplication.shared.open(url) }
        decisionHandler(.cancel)
    }
}
