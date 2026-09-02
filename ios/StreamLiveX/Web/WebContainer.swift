import SwiftUI
import UIKit
import WebKit

final class BundledWebSchemeHandler: NSObject, WKURLSchemeHandler {
    private let rootURL: URL
    private let taskLock = NSLock()
    private var networkTasks: [ObjectIdentifier: URLSessionDataTask] = [:]

    init(rootURL: URL) {
        self.rootURL = rootURL.resolvingSymlinksInPath().standardizedFileURL
        super.init()
    }

    func webView(_ webView: WKWebView, start urlSchemeTask: WKURLSchemeTask) {
        guard let requestURL = urlSchemeTask.request.url,
              requestURL.scheme == AppConfiguration.localWebScheme,
              requestURL.host == AppConfiguration.localWebHost else {
            urlSchemeTask.didFailWithError(loaderError("Invalid bundled UI request."))
            return
        }

        if requestURL.path.hasPrefix("/api/") {
            proxyAPIRequest(urlSchemeTask, requestURL: requestURL)
            return
        }

        let relativePath = requestURL.path.removingPercentEncoding?
            .trimmingCharacters(in: CharacterSet(charactersIn: "/")) ?? ""
        let requestedURL = rootURL.appendingPathComponent(relativePath.isEmpty ? "index.html" : relativePath)
            .resolvingSymlinksInPath().standardizedFileURL
        let rootPath = rootURL.path.hasSuffix("/") ? rootURL.path : rootURL.path + "/"
        var isDirectory: ObjCBool = false
        guard requestedURL.path.hasPrefix(rootPath), requestedURL.isFileURL,
              FileManager.default.fileExists(atPath: requestedURL.path, isDirectory: &isDirectory), !isDirectory.boolValue else {
            urlSchemeTask.didFailWithError(loaderError("Bundled UI path is outside the application resources."))
            return
        }

        do {
            let data = try Data(contentsOf: requestedURL, options: .mappedIfSafe)
            let response = URLResponse(
                url: requestURL,
                mimeType: Self.mimeType(for: requestedURL.pathExtension),
                expectedContentLength: data.count,
                textEncodingName: Self.isTextExtension(requestedURL.pathExtension) ? "utf-8" : nil
            )
            urlSchemeTask.didReceive(response)
            urlSchemeTask.didReceive(data)
            urlSchemeTask.didFinish()
        } catch {
            urlSchemeTask.didFailWithError(loaderError("Bundled UI resource could not be loaded: \(relativePath)"))
        }
    }

    func webView(_ webView: WKWebView, stop urlSchemeTask: WKURLSchemeTask) {
        let identifier = ObjectIdentifier(urlSchemeTask as AnyObject)
        taskLock.lock()
        let task = networkTasks.removeValue(forKey: identifier)
        taskLock.unlock()
        task?.cancel()
    }

    private func proxyAPIRequest(_ schemeTask: WKURLSchemeTask, requestURL: URL) {
        guard var components = URLComponents(url: AppConfiguration.serviceURL, resolvingAgainstBaseURL: false) else {
            schemeTask.didFailWithError(loaderError("Service URL could not be created."))
            return
        }
        components.path = requestURL.path
        components.percentEncodedQuery = URLComponents(url: requestURL, resolvingAgainstBaseURL: false)?.percentEncodedQuery
        guard let upstreamURL = components.url else {
            schemeTask.didFailWithError(loaderError("API URL could not be created."))
            return
        }

        var upstreamRequest = URLRequest(url: upstreamURL)
        upstreamRequest.httpMethod = schemeTask.request.httpMethod
        let blockedHeaders = Set(["host", "origin", "referer", "content-length", "x-streamlivex-body"])
        schemeTask.request.allHTTPHeaderFields?.forEach { name, value in
            if !blockedHeaders.contains(name.lowercased()) { upstreamRequest.setValue(value, forHTTPHeaderField: name) }
        }
        if let encodedBody = schemeTask.request.value(forHTTPHeaderField: "X-StreamLiveX-Body") {
            upstreamRequest.httpBody = Data(base64Encoded: encodedBody)
        } else {
            upstreamRequest.httpBody = schemeTask.request.httpBody
        }

        let identifier = ObjectIdentifier(schemeTask as AnyObject)
        let task = URLSession.shared.dataTask(with: upstreamRequest) { [weak self] data, response, error in
            guard let self else { return }
            self.taskLock.lock()
            let isActive = self.networkTasks.removeValue(forKey: identifier) != nil
            self.taskLock.unlock()
            guard isActive else { return }
            DispatchQueue.main.async {
                if let error { schemeTask.didFailWithError(error); return }
                guard let upstream = response as? HTTPURLResponse else {
                    schemeTask.didFailWithError(self.loaderError("The API returned an invalid response.")); return
                }
                var headers = upstream.allHeaderFields.reduce(into: [String: String]()) { result, pair in
                    if let name = pair.key as? String, let value = pair.value as? String { result[name] = value }
                }
                headers.removeValue(forKey: "Content-Length")
                headers.removeValue(forKey: "Content-Encoding")
                guard let proxyResponse = HTTPURLResponse(url: requestURL, statusCode: upstream.statusCode, httpVersion: "HTTP/1.1", headerFields: headers) else {
                    schemeTask.didFailWithError(self.loaderError("The API response could not be forwarded.")); return
                }
                schemeTask.didReceive(proxyResponse)
                if let data, !data.isEmpty { schemeTask.didReceive(data) }
                schemeTask.didFinish()
            }
        }
        taskLock.lock()
        networkTasks[identifier] = task
        taskLock.unlock()
        task.resume()
    }

    private func loaderError(_ message: String) -> NSError {
        NSError(domain: "StreamLiveX.BundledWeb", code: 1, userInfo: [NSLocalizedDescriptionKey: message])
    }

    private static func isTextExtension(_ value: String) -> Bool {
        ["html", "css", "js", "mjs", "json", "svg", "txt", "xml"].contains(value.lowercased())
    }

    private static func mimeType(for extensionName: String) -> String {
        switch extensionName.lowercased() {
        case "html": return "text/html"
        case "css": return "text/css"
        case "js", "mjs": return "application/javascript"
        case "json": return "application/json"
        case "svg": return "image/svg+xml"
        case "png": return "image/png"
        case "jpg", "jpeg": return "image/jpeg"
        case "gif": return "image/gif"
        case "webp": return "image/webp"
        case "avif": return "image/avif"
        case "ico": return "image/x-icon"
        case "woff": return "font/woff"
        case "woff2": return "font/woff2"
        case "ttf": return "font/ttf"
        case "otf": return "font/otf"
        case "wasm": return "application/wasm"
        case "webmanifest": return "application/manifest+json"
        default: return "application/octet-stream"
        }
    }
}

struct WebContainer: View {
    @ObservedObject var model: WebPlayerModel

    var body: some View {
        ZStack(alignment: .topLeading) {
            BrowserView(model: model)
                .ignoresSafeArea(edges: .bottom)
            if !model.uiReady && model.webError == nil {
                ZStack {
                    Color.black
                    ProgressView("StreamLiveX hazırlanıyor…").tint(.white).foregroundStyle(.white)
                }
                .ignoresSafeArea()
            }
            if let request = model.previewRequest, let bounds = model.previewBounds, bounds.visible {
                NativePlayerView(controller: model.player, showsControls: false)
                    .frame(width: bounds.width, height: bounds.height)
                    .offset(x: bounds.left, y: bounds.top)
                    .clipped()
                    .accessibilityLabel(request.item.name)
            }
            if let message = model.webError {
                VStack(spacing: 18) {
                    ContentUnavailableView("Uygulama açılamadı", systemImage: "exclamationmark.triangle", description: Text(message))
                    Button("Tekrar Dene") { model.retryWebUI() }.buttonStyle(.borderedProminent)
                }
                .foregroundStyle(.white)
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
        let bundledHandler = BundledWebSchemeHandler(rootURL: AppConfiguration.bundledWebRootURL)
        config.setURLSchemeHandler(bundledHandler, forURLScheme: AppConfiguration.localWebScheme)
        let view = WKWebView(frame: .zero, configuration: config)
        view.customUserAgent = "StreamLiveXiOS/1.0"
        view.navigationDelegate = context.coordinator
        view.scrollView.contentInsetAdjustmentBehavior = .never
        model.webView = view
        if context.coordinator.requiresLegacyMigration {
            context.coordinator.isLoadingLegacyMigration = true
            view.load(URLRequest(url: AppConfiguration.legacyWebAppURL))
        } else {
            view.load(URLRequest(url: AppConfiguration.bundledWebAppURL))
        }
        return view
    }
    func updateUIView(_ uiView: WKWebView, context: Context) {}
    static func dismantleUIView(_ uiView: WKWebView, coordinator: NativeBridge) {
        uiView.configuration.userContentController.removeScriptMessageHandler(forName: "streamlivex")
    }
}

extension NativeBridge: WKNavigationDelegate {
    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        if isLoadingLegacyMigration, webView.url?.host == AppConfiguration.serviceURL.host {
            exportLegacyStorage(from: webView)
            return
        }
        guard webView.url?.scheme == AppConfiguration.localWebScheme else { return }
        model?.webError = nil
        DispatchQueue.main.asyncAfter(deadline: .now() + 15) { [weak self, weak webView] in
            guard let self, let webView else { return }
            webView.evaluateJavaScript("document.getElementById('root')?.childElementCount ?? 0") { result, error in
                let childCount = (result as? NSNumber)?.intValue ?? 0
                if error == nil, childCount > 0 {
                    if self.model?.uiReady == false { self.model?.handle(.uiReady) }
                } else if self.model?.uiReady == false {
                    self.model?.webError = "Uygulama arayüzü başlatılamadı. Lütfen bu TestFlight sürümünü güncelleyin."
                }
            }
        }
    }
    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) { handleNavigationFailure(in: webView, error: error) }
    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) { handleNavigationFailure(in: webView, error: error) }
    func webView(_ webView: WKWebView, decidePolicyFor action: WKNavigationAction, decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
        guard let url = action.request.url else { decisionHandler(.cancel); return }
        if url.scheme == AppConfiguration.localWebScheme,
           url.host == AppConfiguration.localWebHost { decisionHandler(.allow); return }
        if isLoadingLegacyMigration, action.targetFrame?.isMainFrame != false,
           url.scheme?.lowercased() == "https", url.host == AppConfiguration.serviceURL.host { decisionHandler(.allow); return }
        if action.targetFrame?.isMainFrame == false,
           url.scheme?.lowercased() == "https", url.host == AppConfiguration.serviceURL.host { decisionHandler(.allow); return }
        if ["http", "https", "mailto", "tel"].contains(url.scheme?.lowercased()) { UIApplication.shared.open(url) }
        decisionHandler(.cancel)
    }

    private func handleNavigationFailure(in webView: WKWebView, error: Error) {
        if isLoadingLegacyMigration { loadBundledUI(in: webView); return }
        model?.webError = error.localizedDescription
    }

    private func exportLegacyStorage(from webView: WKWebView) {
        guard !didStartLegacyExport else { return }
        didStartLegacyExport = true
        Task { @MainActor [weak self, weak webView] in
            guard let webView else { return }
            let result = try? await webView.callAsyncJavaScript(Self.legacyExportScript, arguments: [:], in: nil, contentWorld: .page)
            if let json = result as? String, let data = json.data(using: .utf8) {
                let encoded = data.base64EncodedString()
                let source = "window.__SLX_LEGACY_EXPORT_BASE64__ = '\(encoded)';"
                webView.configuration.userContentController.addUserScript(WKUserScript(source: source, injectionTime: .atDocumentStart, forMainFrameOnly: true))
            }
            self?.loadBundledUI(in: webView)
        }
    }

    private func loadBundledUI(in webView: WKWebView) {
        isLoadingLegacyMigration = false
        webView.load(URLRequest(url: AppConfiguration.bundledWebAppURL))
    }

    private static let legacyExportScript = #"""
        const snapshot = { localStorage: {}, sessionStorage: {}, library: [] };
        for (let i = 0; i < localStorage.length; i++) { const key = localStorage.key(i); if (key) snapshot.localStorage[key] = localStorage.getItem(key); }
        for (let i = 0; i < sessionStorage.length; i++) { const key = sessionStorage.key(i); if (key) snapshot.sessionStorage[key] = sessionStorage.getItem(key); }
        try {
          const db = await new Promise((resolve, reject) => { const request = indexedDB.open('streamlivex-v2', 1); request.onsuccess = () => resolve(request.result); request.onerror = () => reject(request.error); });
          if (db.objectStoreNames.contains('library')) {
            snapshot.library = await new Promise((resolve, reject) => { const rows = []; const request = db.transaction('library').objectStore('library').openCursor(); request.onsuccess = () => { const cursor = request.result; if (!cursor) { resolve(rows); return; } rows.push({ key: cursor.key, value: cursor.value }); cursor.continue(); }; request.onerror = () => reject(request.error); });
          }
          db.close();
        } catch (_) {}
        return JSON.stringify(snapshot);
        """#
}
