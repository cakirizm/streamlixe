import AVFoundation
import Foundation

private let directFileExtensions: Set<String> = ["mkv", "mp4", "mov", "m4v", "avi", "webm", "mpg", "mpeg", "ts", "m2ts"]
private func localDownloadExtension(for url: URL?) -> String {
    let ext = url?.pathExtension.lowercased() ?? ""
    return directFileExtensions.contains(ext) ? ext : (ext.isEmpty ? "media" : ext)
}

struct OfflineDownload: Codable, Identifiable {
    enum State: String, Codable { case downloading, paused, downloaded, failed }
    let id: String
    let title: String
    let source: URL
    let artworkURL: URL?
    let kind: String
    var localURL: URL?
    var progress: Double
    var state: State
    var error: String?

    private enum CodingKeys: String, CodingKey { case id, title, source, artworkURL, kind, localURL, progress, state, error }
    init(id: String, title: String, source: URL, artworkURL: URL? = nil, kind: String = "movie", localURL: URL? = nil, progress: Double, state: State, error: String? = nil) { self.id = id; self.title = title; self.source = source; self.artworkURL = artworkURL; self.kind = kind; self.localURL = localURL; self.progress = progress; self.state = state; self.error = error }
    init(from decoder: Decoder) throws {
        let box = try decoder.container(keyedBy: CodingKeys.self)
        id = try box.decode(String.self, forKey: .id)
        source = try box.decodeIfPresent(URL.self, forKey: .source) ?? URL(string: "about:blank")!
        title = try box.decode(String.self, forKey: .title)
        artworkURL = try box.decodeIfPresent(URL.self, forKey: .artworkURL)
        kind = try box.decodeIfPresent(String.self, forKey: .kind) ?? "movie"
        localURL = try box.decodeIfPresent(URL.self, forKey: .localURL)
        progress = try box.decode(Double.self, forKey: .progress)
        let savedState = try box.decode(State.self, forKey: .state)
        state = savedState == .downloading ? .paused : savedState
        error = try box.decodeIfPresent(String.self, forKey: .error)
    }
}

@MainActor
final class NativeDownloadManager: NSObject, ObservableObject, AVAssetDownloadDelegate, URLSessionDownloadDelegate {
    static let shared = NativeDownloadManager()
    @Published private(set) var downloads: [OfflineDownload] = []
    private var hlsTasks: [Int: String] = [:]
    private var directTasks: [Int: String] = [:]
    private let storageKey = "StreamLiveXOfflineDownloadsV1"

    private lazy var hlsSession: AVAssetDownloadURLSession = {
        let configuration = URLSessionConfiguration.background(withIdentifier: "com.streamlivex.ios.offline-hls")
        configuration.allowsCellularAccess = false
        configuration.sessionSendsLaunchEvents = true
        return AVAssetDownloadURLSession(configuration: configuration, assetDownloadDelegate: self, delegateQueue: .main)
    }()
    private lazy var directSession: URLSession = {
        let configuration = URLSessionConfiguration.background(withIdentifier: "com.streamlivex.ios.offline-files")
        configuration.allowsCellularAccess = false
        configuration.sessionSendsLaunchEvents = true
        return URLSession(configuration: configuration, delegate: self, delegateQueue: .main)
    }()

    override init() {
        super.init()
        if let data = UserDefaults.standard.data(forKey: storageKey), let saved = try? JSONDecoder().decode([OfflineDownload].self, from: data) { downloads = saved }
        hlsSession.getAllTasks { [weak self] tasks in Task { @MainActor in guard let self else { return }; tasks.forEach { if let id = $0.taskDescription { self.hlsTasks[$0.taskIdentifier] = id } } } }
        directSession.getAllTasks { [weak self] tasks in Task { @MainActor in guard let self else { return }; tasks.forEach { if let id = $0.taskDescription { self.directTasks[$0.taskIdentifier] = id } } } }
    }

    func start(id: String, title: String, source: URL, artworkURL: URL? = nil, kind: String = "movie") {
        guard kind.caseInsensitiveCompare("live") != .orderedSame else { return }
        if downloads.contains(where: { $0.id == id && $0.state == .downloaded }) { return }
        let ext = source.pathExtension.lowercased()
        replace(OfflineDownload(id: id, title: title, source: source, artworkURL: artworkURL, kind: kind, progress: 0, state: .downloading))
        if ext == "m3u8" {
            let asset = AVURLAsset(url: source)
            guard let task = hlsSession.makeAssetDownloadTask(asset: asset, assetTitle: title, assetArtworkData: nil, options: nil) else { fail(id, "HLS indirmesi başlatılamadı"); return }
            hlsTasks[task.taskIdentifier] = id; task.taskDescription = id; task.resume()
        } else {
            let task = directSession.downloadTask(with: source)
            directTasks[task.taskIdentifier] = id; task.taskDescription = id; task.resume()
        }
    }

    func pause(_ id: String) {
        hlsSession.getAllTasks { tasks in tasks.first(where: { self.hlsTasks[$0.taskIdentifier] == id })?.suspend() }
        directSession.getAllTasks { tasks in tasks.first(where: { self.directTasks[$0.taskIdentifier] == id })?.suspend() }
        mutate(id) { $0.state = .paused }
    }
    func resume(_ id: String) {
        hlsSession.getAllTasks { tasks in tasks.first(where: { self.hlsTasks[$0.taskIdentifier] == id })?.resume() }
        directSession.getAllTasks { tasks in tasks.first(where: { self.directTasks[$0.taskIdentifier] == id })?.resume() }
        mutate(id) { $0.state = .downloading; $0.error = nil }
    }
    func remove(_ id: String) {
        hlsSession.getAllTasks { tasks in tasks.first(where: { self.hlsTasks[$0.taskIdentifier] == id })?.cancel() }
        directSession.getAllTasks { tasks in tasks.first(where: { self.directTasks[$0.taskIdentifier] == id })?.cancel() }
        if let local = downloads.first(where: { $0.id == id })?.localURL { try? FileManager.default.removeItem(at: local) }
        downloads.removeAll { $0.id == id }; persist()
    }

    nonisolated func urlSession(_ session: URLSession, assetDownloadTask: AVAssetDownloadTask, didLoad timeRange: CMTimeRange, totalTimeRangesLoaded loadedTimeRanges: [NSValue], timeRangeExpectedToLoad: CMTimeRange) {
        let expected = timeRangeExpectedToLoad.duration.seconds
        let loaded = loadedTimeRanges.reduce(0) { $0 + $1.timeRangeValue.duration.seconds }
        Task { @MainActor in guard let id = hlsTasks[assetDownloadTask.taskIdentifier] else { return }; mutate(id) { $0.progress = expected > 0 ? min(1, loaded / expected) : 0 } }
    }
    nonisolated func urlSession(_ session: URLSession, assetDownloadTask: AVAssetDownloadTask, didFinishDownloadingTo location: URL) {
        Task { @MainActor in guard let id = hlsTasks[assetDownloadTask.taskIdentifier] else { return }; finish(id: id, temporaryURL: location, extension: "movpkg") }
    }
    nonisolated func urlSession(_ session: URLSession, downloadTask: URLSessionDownloadTask, didWriteData bytesWritten: Int64, totalBytesWritten: Int64, totalBytesExpectedToWrite: Int64) {
        Task { @MainActor in guard let id = directTasks[downloadTask.taskIdentifier] else { return }; mutate(id) { $0.progress = totalBytesExpectedToWrite > 0 ? min(1, Double(totalBytesWritten) / Double(totalBytesExpectedToWrite)) : 0 } }
    }
    nonisolated func urlSession(_ session: URLSession, downloadTask: URLSessionDownloadTask, didFinishDownloadingTo location: URL) {
        let ext = localDownloadExtension(for: downloadTask.originalRequest?.url)
        let staged = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString).appendingPathExtension(ext)
        do { try FileManager.default.moveItem(at: location, to: staged) }
        catch { Task { @MainActor in if let id = directTasks[downloadTask.taskIdentifier] { fail(id, "İndirilen dosya hazırlanamadı") } }; return }
        Task { @MainActor in guard let id = directTasks[downloadTask.taskIdentifier] else { try? FileManager.default.removeItem(at: staged); return }; finish(id: id, temporaryURL: staged, extension: ext) }
    }
    nonisolated func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
        guard let error else { return }
        Task { @MainActor in if let id = hlsTasks[task.taskIdentifier] ?? directTasks[task.taskIdentifier], downloads.contains(where: { $0.id == id }) { fail(id, error.localizedDescription) } }
    }

    private func finish(id: String, temporaryURL: URL, extension ext: String) {
        guard let folder = try? FileManager.default.url(for: .applicationSupportDirectory, in: .userDomainMask, appropriateFor: nil, create: true).appendingPathComponent("Downloads", isDirectory: true) else { fail(id, "İndirme klasörü oluşturulamadı"); return }
        try? FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        let destination = folder.appendingPathComponent(id).appendingPathExtension(ext)
        try? FileManager.default.removeItem(at: destination)
        do { try FileManager.default.moveItem(at: temporaryURL, to: destination); mutate(id) { $0.localURL = destination; $0.progress = 1; $0.state = .downloaded; $0.error = nil } }
        catch { fail(id, "İndirilen medya kaydedilemedi") }
    }
    private func replace(_ item: OfflineDownload) { downloads.removeAll { $0.id == item.id }; downloads.insert(item, at: 0); persist() }
    private func fail(_ id: String, _ message: String) { mutate(id) { $0.state = .failed; $0.error = message } }
    private func mutate(_ id: String, _ change: (inout OfflineDownload) -> Void) { guard let index = downloads.firstIndex(where: { $0.id == id }) else { return }; change(&downloads[index]); persist() }
    private func persist() { if let data = try? JSONEncoder().encode(downloads) { UserDefaults.standard.set(data, forKey: storageKey) } }
}
