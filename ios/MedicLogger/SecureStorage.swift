import Foundation
import CryptoKit
import Security

final class SecureStorage {
    let url: URL
    private let key: SymmetricKey
    private static let header = Data("MLIOS001".utf8)
    init(url: URL, key: SymmetricKey) { self.url = url; self.key = key }
    convenience init(scope: String = "main") throws {
        let folder = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("MedicLogger", isDirectory: true)
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        var excluded = folder; var values = URLResourceValues(); values.isExcludedFromBackup = true; try excluded.setResourceValues(values)
        self.init(url: folder.appendingPathComponent(scope + ".enc"), key: try Self.key(scope: scope))
    }
    private static func key(scope: String) throws -> SymmetricKey {
        let service = "fr.mediclogger.storage." + scope
        let query: [String:Any] = [kSecClass as String:kSecClassGenericPassword,kSecAttrService as String:service,kSecAttrAccount as String:"aes-256",kSecAttrSynchronizable as String:false]
        var read = query; read[kSecReturnData as String] = true; read[kSecMatchLimit as String] = kSecMatchLimitOne
        var result: CFTypeRef?; let status = SecItemCopyMatching(read as CFDictionary, &result)
        if status == errSecSuccess, let bytes = result as? Data, bytes.count == 32 { return SymmetricKey(data: bytes) }
        guard status == errSecItemNotFound else { throw AppFailure("Encrypted storage could not be opened. Existing data was kept.") }
        var bytes = Data(count: 32); let randomStatus = bytes.withUnsafeMutableBytes { SecRandomCopyBytes(kSecRandomDefault, 32, $0.baseAddress!) }
        guard randomStatus == errSecSuccess else { throw AppFailure("Could not create an encryption key.") }
        var insert = query; insert[kSecValueData as String] = bytes; insert[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        guard SecItemAdd(insert as CFDictionary, nil) == errSecSuccess else { throw AppFailure("Could not save the encryption key.") }
        return SymmetricKey(data: bytes)
    }
    func read() throws -> AppData {
        let encoded = try Data(contentsOf: url)
        guard encoded.starts(with: Self.header) else { throw AppFailure("Encrypted storage could not be opened. Existing data was kept.") }
        let sealed = try AES.GCM.SealedBox(combined: encoded.dropFirst(Self.header.count))
        let plain = try AES.GCM.open(sealed, using: key, authenticating: Self.header)
        let decoder = JSONDecoder(); decoder.dateDecodingStrategy = .millisecondsSince1970
        return try decoder.decode(AppData.self, from: plain)
    }
    func write(_ data: AppData) throws {
        let encoder = JSONEncoder(); encoder.dateEncodingStrategy = .millisecondsSince1970
        let sealed = try AES.GCM.seal(encoder.encode(data), using: key, authenticating: Self.header)
        guard let combined = sealed.combined else { throw AppFailure("Could not encrypt app data.") }
        try (Self.header + combined).write(to: url, options: [.atomic,.completeFileProtectionUntilFirstUserAuthentication])
        var file = url; var values = URLResourceValues(); values.isExcludedFromBackup = true; try file.setResourceValues(values)
    }
}
