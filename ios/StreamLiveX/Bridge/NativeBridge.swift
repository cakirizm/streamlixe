import WebKit
import UIKit

final class NativeBridge: NSObject, WKScriptMessageHandler, UIGestureRecognizerDelegate {
    weak var model: WebPlayerModel?
    var isLoadingLegacyMigration = false
    var didStartLegacyExport = false
    var requiresLegacyMigration: Bool { !UserDefaults.standard.bool(forKey: "SLXLocalWebMigrationCompleted") }

    init(model: WebPlayerModel) { self.model = model }

    @objc func handleBackSwipe(_ gesture: UIScreenEdgePanGestureRecognizer) {
        guard gesture.state == .ended,
              gesture.translation(in: gesture.view).x > 28 else { return }
        model?.webView?.evaluateJavaScript("window.dispatchEvent(new Event('streamlivex:hardware-back'))")
    }

    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer,
                           shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool { true }

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
          if (target && !target.querySelector('.ios-native-downloads-button')) {
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
          var actions = document.querySelector('.details-page .detail-actions');
          if (actions && !actions.querySelector('.ios-detail-download')) {
            var detailDownload = document.createElement('button');
            detailDownload.type = 'button';
            detailDownload.className = 'detail-save ios-detail-download';
            var isSeries = ((document.querySelector('.detail-main article > span') || {}).textContent || '').indexOf('DİZİ') === 0;
            detailDownload.innerHTML = '<span aria-hidden="true">↓</span> ' + (isSeries ? 'Bölüm İndir' : 'İndir');
            detailDownload.addEventListener('click', function () {
              if (isSeries) { var episodes = document.getElementById('episodes'); if (episodes) episodes.scrollIntoView({ behavior: 'smooth' }); return; }
              var play = actions.querySelector('.detail-play');
              if (!play || play.disabled) return;
              sessionStorage.setItem('slx-ios-download-next', '1');
              play.click();
            });
            actions.append(detailDownload);
          }
          document.querySelectorAll('.episode-cards article').forEach(function (article) {
            var controls = article.querySelector(':scope > div');
            if (!controls || controls.querySelector('.ios-episode-download')) return;
            var episodeDownload = document.createElement('button');
            episodeDownload.type = 'button';
            episodeDownload.className = 'ios-episode-download';
            episodeDownload.innerHTML = '<span aria-hidden="true">↓</span> İndir';
            episodeDownload.addEventListener('click', function () {
              var play = controls.querySelector(':scope > button:not(.ios-episode-download)');
              if (!play) return;
              sessionStorage.setItem('slx-ios-download-next', '1');
              play.click();
            });
            controls.append(episodeDownload);
          });
        }
        new MutationObserver(installIOSActions).observe(document.documentElement, { childList: true, subtree: true });
        document.addEventListener('DOMContentLoaded', installIOSActions);
        """#, injectionTime: .atDocumentStart, forMainFrameOnly: true)
}
