import AVFoundation
import Foundation

struct OfflineDownload: Codable, Identifiable {
    enum State: String, Codable { case downloading, paused, downloaded, failed }
    let id: String
    let title: String
    let source: URL
    var localURL: URL?
    var progress: Double
    var state: State
    var error: String?

    private enum CodingKeys: String, CodingKey { case id, title, localURL, progress, state, error }
    init(id: String, title: String, source: URL, localURL: URL? = nil, progress: Double, state: State, error: String? = nil) { self.id = id; self.title = title; self.source = source; self.localURL = localURL; self.progress = progress; self.state = state; self.error = error }
    init(from decoder: Decoder) throws { let box = try decoder.container(keyedBy: CodingKeys.self); id = try box.decode(String.self, forKey: .id); title = try box.decode(String.self, forKey: .title); source = URL(string: "about:blank")!; localURL = try box.decodeIfPresent(URL.self, forKey: .localURL); progress = try box.decode(Double.self, forKey: .progress); let savedState = try box.decode(State.self, forKey: .state); state = savedState == .downloading ? .paused : savedState; error = try box.decodeIfPresent(String.self, forKey: .error) }
    func encode(to encoder: Encoder) throws { var box = encoder.container(keyedBy: CodingKeys.self); try box.encode(id, forKey: .id); try box.encode(title, forKey: .title); try box.encodeIfPresent(localURL, forKey: .localURL); try box.encode(progress, forKey: .progress); try box.encode(state, forKey: .state); try box.encodeIfPresent(error, forKey: .error) }
}

@MainActor
final class NativeDownloadManager: NSObject, ObservableObject, AVAssetDownloadDelegate {
    static let shared = NativeDownloadManager()
    @Published private(set) var downloads: [OfflineDownload] = []
    private var tasks: [Int: String] = [:]
    private lazy var session: AVAssetDownloadURLSession = {
        let configuration = URLSessionConfiguration.background(withIdentifier: "com.streamlivex.ios.offline-hls")
        configuration.allowsCellularAccess = false
        configuration.sessionSendsLaunchEvents = true
        return AVAssetDownloadURLSession(configuration: configuration, assetDownloadDelegate: self, delegateQueue: .main)
    }()
    private let storageKey = "StreamLiveXOfflineDownloadsV1"

    override init() {
        super.init()
        if let data = UserDefaults.standard.data(forKey: storageKey), let saved = try? JSONDecoder().decode([OfflineDownload].self, from: data) { downloads = saved }
        session.getAllTasks { [weak self] activeTasks in Task { @MainActor in guard let self else { return }; activeTasks.forEach { task in if let id = task.taskDescription { self.tasks[task.taskIdentifier] = id } } } }
    }

    func start(id: String, title: String, source: URL) {
        guard source.pathExtension.lowercased() == "m3u8" else { return }
        if downloads.contains(where: { $0.id == id && $0.state == .downloaded }) { return }
        downloads.removeAll { $0.id == id }
        downloads.insert(OfflineDownload(id: id, title: title, source: source, progress: 0, state: .downloading), at: 0)
        let asset = AVURLAsset(url: source)
        guard let task = session.makeAssetDownloadTask(asset: asset, assetTitle: title, assetArtworkData: nil, options: nil) else {
            fail(id, "İndirme başlatılamadı")
            return
        }
        tasks[task.taskIdentifier] = id
        task.taskDescription = id
        persist(); task.resume()
    }

    func pause(_ id: String) { session.getAllTasks { tasks in tasks.first(where: { self.tasks[$0.taskIdentifier] == id })?.suspend() }; mutate(id) { $0.state = .paused } }
    func resume(_ id: String) { session.getAllTasks { tasks in tasks.first(where: { self.tasks[$0.taskIdentifier] == id })?.resume() }; mutate(id) { $0.state = .downloading } }
    func remove(_ id: String) {
        session.getAllTasks { tasks in tasks.first(where: { self.tasks[$0.taskIdentifier] == id })?.cancel() }
        if let local = downloads.first(where: { $0.id == id })?.localURL { try? FileManager.default.removeItem(at: local) }
        downloads.removeAll { $0.id == id }; persist()
    }

    nonisolated func urlSession(_ session: URLSession, assetDownloadTask: AVAssetDownloadTask, didLoad timeRange: CMTimeRange, totalTimeRangesLoaded loadedTimeRanges: [NSValue], timeRangeExpectedToLoad: CMTimeRange) {
        let expected = timeRangeExpectedToLoad.duration.seconds
        let loaded = loadedTimeRanges.reduce(0) { $0 + $1.timeRangeValue.duration.seconds }
        Task { @MainActor in guard let id = tasks[assetDownloadTask.taskIdentifier] else { return }; mutate(id) { $0.progress = expected > 0 ? min(1, loaded / expected) : 0 } }
    }

    nonisolated func urlSession(_ session: URLSession, assetDownloadTask: AVAssetDownloadTask, didFinishDownloadingTo location: URL) {
        Task { @MainActor in
            guard let id = tasks[assetDownloadTask.taskIdentifier] else { return }
            let folder = try? FileManager.default.url(for: .applicationSupportDirectory, in: .userDomainMask, appropriateFor: nil, create: true).appendingPathComponent("Downloads", isDirectory: true)
            guard let folder else { fail(id, "İndirme klasörü oluşturulamadı"); return }
            try? FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
            let destination = folder.appendingPathComponent(id).appendingPathExtension("movpkg")
            try? FileManager.default.removeItem(at: destination)
            do { try FileManager.default.moveItem(at: location, to: destination); mutate(id) { $0.localURL = destination; $0.progress = 1; $0.state = .downloaded; $0.error = nil } }
            catch { fail(id, "İndirilen medya kaydedilemedi") }
        }
    }

    nonisolated func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
        guard let error else { return }
        Task { @MainActor in if let id = tasks[task.taskIdentifier] { fail(id, error.localizedDescription) } }
    }

    private func fail(_ id: String, _ message: String) { mutate(id) { $0.state = .failed; $0.error = message } }
    private func mutate(_ id: String, _ change: (inout OfflineDownload) -> Void) { guard let index = downloads.firstIndex(where: { $0.id == id }) else { return }; change(&downloads[index]); persist() }
    private func persist() { if let data = try? JSONEncoder().encode(downloads) { UserDefaults.standard.set(data, forKey: storageKey) } }
}
