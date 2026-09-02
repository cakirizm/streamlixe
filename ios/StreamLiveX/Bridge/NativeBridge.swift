import WebKit
import UIKit

final class NativeBridge: NSObject, WKScriptMessageHandler {
    weak var model: WebPlayerModel?
    var isLoadingLegacyMigration = false
    var didStartLegacyExport = false
    var requiresLegacyMigration: Bool { !UserDefaults.standard.bool(forKey: "SLXLocalWebMigrationCompleted") }

    init(model: WebPlayerModel) { self.model = model }

    @objc func handleBackSwipe(_ gesture: UIScreenEdgePanGestureRecognizer) {
        guard gesture.state == .ended,
              gesture.translation(in: gesture.view).x > 72,
              gesture.velocity(in: gesture.view).x > 120 else { return }
        model?.webView?.evaluateJavaScript("window.dispatchEvent(new Event('streamlivex:hardware-back'))")
    }

    func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
        guard message.name == "streamlivex", let command = BridgeParser.parse(message.body) else { return }
        Task { @MainActor in model?.handle(command) }
    }

    static let bootstrap = WKUserScript(source: #"""
        function markStreamLiveXiOS() {
          if (document.documentElement) document.documentElement.classList.add('streamlivex-ios');
        }
        markStreamLiveXiOS();
        document.addEventListener('DOMContentLoaded', markStreamLiveXiOS, { once: true });
        window.chrome = window.chrome || {};
        window.chrome.webview = window.chrome.webview || {
          postMessage: function(message) {
            window.webkit.messageHandlers.streamlivex.postMessage(message);
          }
        };
        window.dispatchEvent(new Event('streamlivex:native-bridge-ready'));
        Object.keys(localStorage).filter(function (key) { return /^slx-.+-settings$/.test(key); }).forEach(function (key) {
          try {
            var value = JSON.parse(localStorage.getItem(key) || '{}');
            if (/^\d{4}$/.test(value.pin || '')) {
              var profileId = key.slice(4, -9);
              window.chrome.webview.postMessage({ type: 'migrate-parental-pin', profileId: profileId, pin: value.pin });
              value.pin = '';
              value.pinProtected = true;
              localStorage.setItem(key, JSON.stringify(value));
            }
          } catch (_) {}
        });
        function installIOSActions() {
          var target = document.querySelector('.advanced-settings .settings-sections');
          if (!target || target.querySelector('.ios-native-downloads-button')) return;
          var button = document.createElement('button');
          button.type = 'button';
          button.className = 'settings-danger ios-native-downloads-button';
          button.innerHTML = '<span aria-hidden="true">↓</span><span><b>İndirmeler</b><small>Çevrimdışı HLS film ve bölümlerini yönet</small></span>';
          button.addEventListener('click', function () { window.chrome.webview.postMessage({ type: 'show-downloads' }); });
          target.prepend(button);
          var parental = document.createElement('button');
          parental.type = 'button';
          parental.className = 'settings-danger ios-native-downloads-button ios-native-parental-button';
          parental.innerHTML = '<span aria-hidden="true">⌾</span><span><b>Güvenli Ebeveyn Kontrolü</b><small>PIN’i iOS Keychain ile yönet</small></span>';
          parental.addEventListener('click', function () { window.chrome.webview.postMessage({ type: 'show-parental', profileId: localStorage.getItem('slx-active-profile') || 'main' }); });
          target.prepend(parental);
        }
        new MutationObserver(installIOSActions).observe(document.documentElement, { childList: true, subtree: true });
        document.addEventListener('DOMContentLoaded', installIOSActions);
        """#, injectionTime: .atDocumentStart, forMainFrameOnly: true)
}
