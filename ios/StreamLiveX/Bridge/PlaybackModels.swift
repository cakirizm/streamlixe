import CoreGraphics
import Foundation

struct SubtitleSource: Codable, Hashable {
    let src: URL
    let label: String
    let language: String
}

struct PlaybackPreferences: Codable {
    var audioLanguage = "auto"
    var subtitleMode = "auto"
    var subtitleLanguage = "tr"
    var subtitleDelay: Double = 0
    var playbackRate: Float = 1
    var subtitleSize: Int = 100
    var subtitleColor = "#FFFFFF"
    var subtitleBackground = "shadow"
    var subtitleBackgroundOpacity: Double = 0.6
    var subtitleVerticalPosition: Int = 8
}

struct PlaybackItem: Codable {
    let name: String
    let url: URL
    let kind: String
    var artwork: URL?
    var subtitles: [SubtitleSource] = []
    var hasNext = false
    var hasPrevious = false
    var isLive: Bool { kind.caseInsensitiveCompare("live") == .orderedSame }
}

struct PlaybackRequest: Identifiable {
    let id: String
    let item: PlaybackItem
    let resumeMilliseconds: Double
    var preferences: PlaybackPreferences
    var profileID = "main"
}

struct PreviewBounds: Codable {
    let left: CGFloat
    let top: CGFloat
    let width: CGFloat
    let height: CGFloat
    let visible: Bool
}

enum BridgeCommand {
    case uiReady
    case play(PlaybackRequest)
    case preview(PlaybackRequest, PreviewBounds)
    case previewLayout(String, PreviewBounds)
    case promotePreview(String)
    case close(String?)
    case closePreview(String?)
    case download(PlaybackRequest)
    case showDownloads
    case showParental(String, [String])
    case migrateParentalPin(String, String)
    case confirmExit
}

enum BridgeParser {
    static func parse(_ body: Any) -> BridgeCommand? {
        let root: [String: Any]
        if let dictionary = body as? [String: Any] { root = dictionary }
        else if let string = body as? String,
                let data = string.data(using: .utf8),
                let dictionary = try? JSONSerialization.jsonObject(with: data) as? [String: Any] { root = dictionary }
        else { return nil }

        let type = (root["type"] as? String)?.lowercased() ?? ""
        let session = (root["sessionId"] as? String)?.nilIfBlank
        switch type {
        case "ui-ready": return .uiReady
        case "play": return request(root).map(BridgeCommand.play)
        case "preview":
            guard let request = request(root), let bounds = bounds(root) else { return nil }
            return .preview(request, bounds)
        case "preview-layout":
            guard let session, let bounds = bounds(root) else { return nil }
            return .previewLayout(session, bounds)
        case "promote-preview": return session.map(BridgeCommand.promotePreview)
        case "close": return .close(session)
        case "close-preview": return .closePreview(session)
        case "download": return request(root).map(BridgeCommand.download)
        case "show-downloads": return .showDownloads
        case "show-parental": return .showParental((root["profileId"] as? String)?.nilIfBlank ?? "main", (root["categories"] as? [String] ?? []).filter { !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty })
        case "migrate-parental-pin":
            guard let profile = (root["profileId"] as? String)?.nilIfBlank, let pin = root["pin"] as? String else { return nil }
            return .migrateParentalPin(profile, pin)
        case "confirm-exit": return .confirmExit
        default: return nil
        }
    }

    private static func request(_ root: [String: Any]) -> PlaybackRequest? {
        guard let rawItem = root["item"] as? [String: Any],
              let rawURL = rawItem["url"] as? String,
              let url = secureMediaURL(rawURL) else { return nil }
        let subtitleRows = rawItem["subtitles"] as? [[String: Any]] ?? []
        let subtitles = subtitleRows.compactMap { row -> SubtitleSource? in
            guard let source = row["src"] as? String, let url = secureMediaURL(source) else { return nil }
            return SubtitleSource(src: url,
                                  label: (row["label"] as? String)?.nilIfBlank ?? "Subtitle",
                                  language: (row["language"] as? String)?.nilIfBlank ?? "und")
        }
        let prefs = root["preferences"] as? [String: Any] ?? [:]
        return PlaybackRequest(
            id: (root["sessionId"] as? String)?.nilIfBlank ?? UUID().uuidString,
            item: PlaybackItem(name: (rawItem["name"] as? String)?.nilIfBlank ?? "StreamLiveX",
                               url: url,
                               kind: (rawItem["kind"] as? String)?.nilIfBlank ?? "movie",
                               artwork: (rawItem["artwork"] as? String).flatMap(secureMediaURL),
                               subtitles: subtitles,
                               hasNext: rawItem["hasNext"] as? Bool ?? false,
                               hasPrevious: rawItem["hasPrevious"] as? Bool ?? false),
            resumeMilliseconds: max(0, (root["resumeTime"] as? NSNumber)?.doubleValue ?? 0),
            preferences: PlaybackPreferences(audioLanguage: (prefs["audioLanguage"] as? String)?.nilIfBlank ?? "auto",
                                             subtitleMode: (prefs["subtitleMode"] as? String)?.nilIfBlank ?? "auto",
                                             subtitleLanguage: (prefs["subtitleLanguage"] as? String)?.nilIfBlank ?? "tr",
                                             subtitleDelay: min(10, max(-10, (prefs["subtitleDelay"] as? NSNumber)?.doubleValue ?? 0)),
                                             playbackRate: min(2, max(0.5, (prefs["playbackRate"] as? NSNumber)?.floatValue ?? 1)),
                                             subtitleSize: min(180, max(70, (prefs["subtitleSize"] as? NSNumber)?.intValue ?? 100)),
                                             subtitleColor: (prefs["subtitleColor"] as? String)?.nilIfBlank ?? "#FFFFFF",
                                             subtitleBackground: (prefs["subtitleBackground"] as? String)?.nilIfBlank ?? "shadow",
                                             subtitleBackgroundOpacity: min(1, max(0, (prefs["subtitleBackgroundOpacity"] as? NSNumber)?.doubleValue ?? 0.6)),
                                             subtitleVerticalPosition: min(20, max(0, (prefs["subtitleVerticalPosition"] as? NSNumber)?.intValue ?? 8))),
            profileID: (root["profileId"] as? String)?.nilIfBlank ?? "main"
        )
    }

    private static func bounds(_ root: [String: Any]) -> PreviewBounds? {
        guard let value = root["bounds"] as? [String: Any],
              let width = (value["width"] as? NSNumber)?.doubleValue,
              let height = (value["height"] as? NSNumber)?.doubleValue,
              width > 0, height > 0 else { return nil }
        return PreviewBounds(left: (value["left"] as? NSNumber)?.doubleValue ?? 0,
                             top: (value["top"] as? NSNumber)?.doubleValue ?? 0,
                             width: width, height: height,
                             visible: value["visible"] as? Bool ?? true)
    }

    private static func secureMediaURL(_ value: String) -> URL? {
        guard let url = URL(string: value.trimmingCharacters(in: .whitespacesAndNewlines)),
              ["http", "https"].contains(url.scheme?.lowercased()) else { return nil }
        return url
    }
}

private extension String {
    var nilIfBlank: String? { trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : self }
}
