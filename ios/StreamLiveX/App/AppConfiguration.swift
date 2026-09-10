import Foundation

enum AppConfiguration {
    static let serviceURL = URL(string: "https://streamlivex.com")!
    static let localWebScheme = "streamlivex-local"
    static let localWebHost = "app"
    static let bundledWebAppURL = URL(string: "streamlivex-local://app/index.html")!
    static let legacyWebAppURL = URL(string: "https://streamlivex.com/app")!

    static let bundledWebRootURL: URL = {
        guard let url = Bundle.main.url(forResource: "Web", withExtension: nil) else {
            fatalError("Bundled iOS web UI is missing. Run npm run build:ios-web before building the IPA.")
        }
        return url
    }()
}
