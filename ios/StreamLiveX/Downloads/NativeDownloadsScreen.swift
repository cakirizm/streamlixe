import SwiftUI

struct NativeDownloadsScreen: View {
    @Environment(\.dismiss) private var dismiss
    @ObservedObject private var manager = NativeDownloadManager.shared
    let onPlay: (OfflineDownload) -> Void

    var body: some View {
        NavigationStack {
            Group {
                if manager.downloads.isEmpty {
                    ContentUnavailableView("İndirme yok", systemImage: "arrow.down.circle", description: Text("HLS film veya bölüm oynatırken indirme düğmesine dokunabilirsin."))
                } else {
                    List(manager.downloads) { item in
                        VStack(alignment: .leading, spacing: 9) {
                            HStack { Text(item.title).font(.headline).lineLimit(2); Spacer(); Text(label(item.state)).font(.caption.weight(.semibold)).foregroundStyle(tint(item.state)) }
                            ProgressView(value: item.progress).tint(.purple)
                            HStack {
                                Text("%\(Int(item.progress * 100))").font(.caption.monospacedDigit()).foregroundStyle(.secondary)
                                Spacer()
                                if item.state == .downloaded { Button("Oynat") { onPlay(item) }.buttonStyle(.borderedProminent).tint(.purple) }
                                if item.state == .downloading { Button("Duraklat") { manager.pause(item.id) } }
                                if item.state == .paused { Button("Sürdür") { manager.resume(item.id) } }
                                if item.state == .failed { Button("Yeniden dene") { manager.start(id: item.id, title: item.title, source: item.source) } }
                                Button("Sil", role: .destructive) { manager.remove(item.id) }
                            }.font(.subheadline)
                            if let error = item.error { Text(error).font(.caption).foregroundStyle(.red) }
                        }.padding(.vertical, 5)
                    }
                }
            }
            .navigationTitle("İndirmeler")
            .toolbar { ToolbarItem(placement: .topBarTrailing) { Button("Bitti") { dismiss() } } }
        }.preferredColorScheme(.dark)
    }

    private func label(_ state: OfflineDownload.State) -> String { switch state { case .downloading: "İndiriliyor"; case .paused: "Duraklatıldı"; case .downloaded: "İndirildi"; case .failed: "Başarısız" } }
    private func tint(_ state: OfflineDownload.State) -> Color { switch state { case .downloading: .purple; case .paused: .orange; case .downloaded: .green; case .failed: .red } }
}
