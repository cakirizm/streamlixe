import SwiftUI

struct NativeDownloadsScreen: View {
    @Environment(\.dismiss) private var dismiss
    @ObservedObject private var manager = NativeDownloadManager.shared
    let onPlay: (OfflineDownload) -> Void

    var body: some View {
        NavigationStack {
            Group {
                if manager.downloads.isEmpty {
                    ContentUnavailableView("İndirme yok", systemImage: "arrow.down.circle", description: Text("Film veya bölüm ayrıntısındaki indirme düğmesine dokunabilirsin."))
                } else {
                    List(manager.downloads) { item in
                        HStack(alignment: .top, spacing: 13) {
                            AsyncImage(url: item.artworkURL) { image in image.resizable().scaledToFill() } placeholder: { ZStack { Color.purple.opacity(0.16); Image(systemName: item.kind == "series" ? "tv" : "film") } }
                                .frame(width: 68, height: 102).clipShape(RoundedRectangle(cornerRadius: 10))
                            VStack(alignment: .leading, spacing: 9) {
                            HStack { Text(item.title).font(.headline).lineLimit(2); Spacer(); Text(label(item.state)).font(.caption.weight(.semibold)).foregroundStyle(tint(item.state)) }
                            Text(item.kind == "series" ? "Dizi bölümü" : "Film").font(.caption).foregroundStyle(.secondary)
                            ProgressView(value: item.progress).tint(.purple)
                            HStack {
                                Text("%\(Int(item.progress * 100)) · \(size(item))").font(.caption.monospacedDigit()).foregroundStyle(.secondary)
                                Spacer()
                                if item.state == .downloaded { Button("Oynat") { onPlay(item) }.buttonStyle(.borderedProminent).tint(.purple) }
                                if item.state == .downloading { Button("Duraklat") { manager.pause(item.id) } }
                                if item.state == .paused { Button("Sürdür") { manager.resume(item.id) } }
                                if item.state == .failed { Button("Yeniden dene") { manager.start(id: item.id, title: item.title, source: item.source, artworkURL: item.artworkURL, kind: item.kind) } }
                                Button("Sil", role: .destructive) { manager.remove(item.id) }
                            }.font(.subheadline)
                            if let error = item.error { Text(error).font(.caption).foregroundStyle(.red) }
                            }
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
    private func size(_ item: OfflineDownload) -> String {
        guard let url = item.localURL else { return "Boyut hesaplanıyor" }
        if let values = try? url.resourceValues(forKeys: [.fileSizeKey]), let directSize = values.fileSize, directSize > 0 { return ByteCountFormatter.string(fromByteCount: Int64(directSize), countStyle: .file) }
        guard let enumerator = FileManager.default.enumerator(at: url, includingPropertiesForKeys: [.fileSizeKey]) else { return "Boyut hesaplanıyor" }
        let bytes = enumerator.compactMap { ($0 as? URL).flatMap { try? $0.resourceValues(forKeys: [.fileSizeKey]).fileSize } }.reduce(0, +)
        return bytes > 0 ? ByteCountFormatter.string(fromByteCount: Int64(bytes), countStyle: .file) : "Boyut hesaplanıyor"
    }
}
