import SwiftUI

struct NativeParentalScreen: View {
    enum Mode { case setup, verifyDisable, changeCurrent, changeNew }
    @Environment(\.dismiss) private var dismiss
    let profileID: String
    let categories: [String]
    let onSettingsChange: (Bool, [String]) -> Void
    @State private var enabled = true
    @State private var mode: Mode = .setup
    @State private var pin = ""
    @State private var message = "4 haneli bir PIN belirle"
    @State private var hasPIN = false
    @State private var restrictedGroups: Set<String> = []

    var body: some View {
        NavigationStack {
            ScrollView { VStack(spacing: 24) {
                Image(systemName: "lock.shield.fill").font(.system(size: 46)).foregroundStyle(.purple).padding(.top, 20)
                VStack(spacing: 7) { Text("Ebeveyn Kontrolü").font(.title2.bold()); Text(message).font(.subheadline).foregroundStyle(.secondary).multilineTextAlignment(.center) }
                HStack(spacing: 14) { ForEach(0..<4, id: \.self) { index in Circle().fill(index < pin.count ? Color.purple : Color.secondary.opacity(0.25)).frame(width: 14, height: 14) } }
                IOSPinPad(value: $pin) { submit() }
                if hasPIN && mode == .changeCurrent { Button("PIN’i değiştir") { pin = ""; message = "Mevcut PIN’i gir" } }
                Toggle("Yetişkin kategorilerini gizle", isOn: Binding(get: { enabled }, set: toggleFiltering)).padding(16).background(.thinMaterial, in: RoundedRectangle(cornerRadius: 16))
                if !categories.isEmpty {
                    VStack(alignment: .leading, spacing: 0) {
                        Text("Kategori Kısıtlamaları").font(.headline).padding(.horizontal, 4).padding(.bottom, 8)
                        ForEach(categories, id: \.self) { category in
                            Toggle(category, isOn: Binding(get: { restrictedGroups.contains(category) }, set: { restricted in
                                if restricted { restrictedGroups.insert(category) } else { restrictedGroups.remove(category) }
                                onSettingsChange(enabled, Array(restrictedGroups).sorted())
                            })).padding(.horizontal, 14).frame(minHeight: 48)
                            if category != categories.last { Divider().padding(.leading, 14) }
                        }
                    }.background(.thinMaterial, in: RoundedRectangle(cornerRadius: 16))
                }
                Text("Sağlayıcı kategorileri her zaman güvenilir yaş bilgisi taşımaz. Filtre, yetişkin olarak tanımlanabilen kategori adlarını ve seçtiğin gizli grupları kapsar.").font(.caption).foregroundStyle(.secondary)
            }}
            .padding(.horizontal, 24)
            .navigationTitle("Profil Güvenliği")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .topBarTrailing) { Button("Bitti") { dismiss() } } }
            .onAppear { hasPIN = keychainEntryExists(); restrictedGroups = Set(categories.filter(isAdultCategory)); mode = hasPIN ? .changeCurrent : .setup; message = hasPIN ? "PIN’i değiştirmek için mevcut PIN’i gir" : "4 haneli bir PIN belirle" }
        }.preferredColorScheme(.dark)
    }

    private func keychainEntryExists() -> Bool { SecurePinStore.exists(profileID) }
    private func submit() {
        guard pin.count == 4 else { return }
        switch mode {
        case .setup, .changeNew:
            if SecurePinStore.set(pin, profileID: profileID) { hasPIN = true; mode = .changeCurrent; message = "PIN güvenli biçimde kaydedildi"; enabled = true; onSettingsChange(true, Array(restrictedGroups).sorted()) } else { message = "PIN kaydedilemedi" }
        case .changeCurrent:
            if SecurePinStore.verify(pin, profileID: profileID) { mode = .changeNew; message = "Yeni PIN’i gir" } else { message = "PIN yanlış. Tekrar dene" }
        case .verifyDisable:
            if SecurePinStore.verify(pin, profileID: profileID) { enabled = false; onSettingsChange(false, Array(restrictedGroups).sorted()); mode = .changeCurrent; message = "Ebeveyn filtresi kapatıldı" } else { message = "PIN yanlış. Filtre açık kaldı" }
        }
        pin = ""
    }

    private func toggleFiltering(_ next: Bool) { if next { enabled = true; onSettingsChange(true, Array(restrictedGroups).sorted()) } else if hasPIN { mode = .verifyDisable; pin = ""; message = "Filtreyi kapatmak için PIN’i gir" } else { enabled = false; onSettingsChange(false, Array(restrictedGroups).sorted()) } }
    private func isAdultCategory(_ value: String) -> Bool { let text = value.folding(options: [.diacriticInsensitive, .caseInsensitive], locale: Locale(identifier: "tr_TR")); return ["yetiskin", "adult", "xxx", "erotik", "18+"].contains { text.contains($0) } }
}

private struct IOSPinPad: View {
    @Binding var value: String
    let submit: () -> Void
    private let keys = ["1","2","3","4","5","6","7","8","9","","0","⌫"]
    var body: some View {
        LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 18), count: 3), spacing: 14) {
            ForEach(Array(keys.enumerated()), id: \.offset) { _, key in
                if key.isEmpty { Color.clear.frame(height: 58) }
                else { Button { if key == "⌫" { if !value.isEmpty { value.removeLast() } } else if value.count < 4 { value.append(key); if value.count == 4 { submit() } } } label: { Text(key).font(.title2.weight(.semibold)).frame(maxWidth: .infinity, minHeight: 58).background(.thinMaterial, in: Circle()) }.buttonStyle(.plain) }
            }
        }.frame(maxWidth: 310)
    }
}
