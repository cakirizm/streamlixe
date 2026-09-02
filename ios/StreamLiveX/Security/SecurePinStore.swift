import CryptoKit
import Foundation
import Security

enum SecurePinStore {
    private static let service = "com.streamlivex.ios.parental-pin"

    static func set(_ pin: String, profileID: String) -> Bool {
        guard pin.range(of: #"^\d{4}$"#, options: .regularExpression) != nil else { return false }
        let salt = Data((0..<16).map { _ in UInt8.random(in: 0...255) })
        let digest = hash(pin, salt: salt)
        let payload = salt + digest
        delete(profileID)
        return SecItemAdd([kSecClass: kSecClassGenericPassword, kSecAttrService: service, kSecAttrAccount: profileID, kSecValueData: payload, kSecAttrAccessible: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly] as CFDictionary, nil) == errSecSuccess
    }

    static func verify(_ pin: String, profileID: String) -> Bool {
        var result: CFTypeRef?
        let status = SecItemCopyMatching([kSecClass: kSecClassGenericPassword, kSecAttrService: service, kSecAttrAccount: profileID, kSecReturnData: true, kSecMatchLimit: kSecMatchLimitOne] as CFDictionary, &result)
        guard status == errSecSuccess, let payload = result as? Data, payload.count == 48 else { return false }
        return Data(hash(pin, salt: payload.prefix(16))) == payload.suffix(32)
    }

    static func exists(_ profileID: String) -> Bool {
        SecItemCopyMatching([kSecClass: kSecClassGenericPassword, kSecAttrService: service, kSecAttrAccount: profileID, kSecReturnData: false, kSecMatchLimit: kSecMatchLimitOne] as CFDictionary, nil) == errSecSuccess
    }

    static func delete(_ profileID: String) { SecItemDelete([kSecClass: kSecClassGenericPassword, kSecAttrService: service, kSecAttrAccount: profileID] as CFDictionary) }
    private static func hash(_ pin: String, salt: Data) -> Data { Data(SHA256.hash(data: salt + Data(pin.utf8))) }
}
