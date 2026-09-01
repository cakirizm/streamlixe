import Foundation

enum AppConfiguration {
    static let serviceURL = URL(string: "https://streamlivex.com")!

    static let bundledWebAppURL: URL = {
        guard let url = Bundle.main.url(forResource: "index", withExtension: "html", subdirectory: "Web") else {
            fatalError("Bundled iOS web UI is missing. Run npm run build:ios-web before building the IPA.")
        }
        return url
    }()
}
