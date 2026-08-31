import Foundation

enum AppConfiguration {
    static let webAppURL: URL = {
        guard let value = Bundle.main.object(forInfoDictionaryKey: "SLXWebAppURL") as? String,
              let url = URL(string: value), ["http", "https"].contains(url.scheme?.lowercased()) else {
            fatalError("SLXWebAppURL must be an HTTP(S) URL")
        }
        return url
    }()
}
