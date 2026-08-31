import WebKit

final class NativeBridge: NSObject, WKScriptMessageHandler {
    weak var model: WebPlayerModel?

    init(model: WebPlayerModel) { self.model = model }

    func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
        guard message.name == "streamlivex", let command = BridgeParser.parse(message.body) else { return }
        Task { @MainActor in model?.handle(command) }
    }

    static let bootstrap = WKUserScript(source: """
        window.chrome = window.chrome || {};
        window.chrome.webview = window.chrome.webview || {
          postMessage: function(message) {
            window.webkit.messageHandlers.streamlivex.postMessage(message);
          }
        };
        window.dispatchEvent(new Event('streamlivex:native-bridge-ready'));
        """, injectionTime: .atDocumentStart, forMainFrameOnly: true)
}
