import 'preference_keys.dart';

/// Result of running [PreferencesMigrator.migrate] over a raw serialized blob.
///
/// [data] is the sanitized/upgraded map, safe to apply to the typed models and
/// safe to persist and sync. [legacySecrets] holds any plaintext credentials
/// that were found in a pre-v2 blob so the caller can move them into platform
/// secure storage (LocalLockService) before they are discarded. They are NEVER
/// present in [data].
class MigrationResult {
  final Map<String, dynamic> data;
  final Map<String, String>
  legacySecrets; // 'PIN' | 'Pattern' | 'Password' -> secret
  final int? legacyPinLength;
  final int fromVersion;

  const MigrationResult({
    required this.data,
    required this.fromVersion,
    this.legacySecrets = const <String, String>{},
    this.legacyPinLength,
  });

  bool get migratedSecrets => legacySecrets.isNotEmpty;
}

/// Pure, deterministic upgrade of a serialized preference blob to the current
/// [PreferenceKeys.currentSchemaVersion].
///
/// The migrator never throws on malformed input: unknown or corrupt sections
/// are passed through untouched (the typed [fromMap] factories and the
/// controller's per-group recovery handle field-level corruption). Its one
/// behavioral change is the v1 -> v2 security hardening: plaintext PIN /
/// pattern / password / recovery fields are removed from the blob so they can
/// never again be written to disk or uploaded to the backend.
class PreferencesMigrator {
  const PreferencesMigrator._();

  static MigrationResult migrate(Map<String, dynamic> raw) {
    final data = <String, dynamic>{...raw};
    final int fromVersion = _readVersion(data);

    final legacySecrets = <String, String>{};
    int? legacyPinLength;

    // v1 (or unversioned legacy) -> v2: strip synced plaintext credentials.
    if (fromVersion < 2) {
      final security = data[PreferenceKeys.security];
      if (security is Map) {
        final sec = Map<String, dynamic>.from(security);

        String? secretOf(String key) {
          final value = sec[key];
          return (value is String && value.isNotEmpty) ? value : null;
        }

        final pin = secretOf('pinCode');
        final pattern = secretOf('patternCode');
        final password = secretOf('password');
        if (pin != null) {
          legacySecrets['PIN'] = pin;
          legacyPinLength = pin.length == 6 ? 6 : 4;
        }
        if (pattern != null) legacySecrets['Pattern'] = pattern;
        if (password != null) legacySecrets['Password'] = password;

        for (final field in PreferenceKeys.legacySecuritySecretFields) {
          sec.remove(field);
        }
        data[PreferenceKeys.security] = sec;
      }
    }

    data[PreferenceKeys.schemaVersion] = PreferenceKeys.currentSchemaVersion;
    return MigrationResult(
      data: data,
      fromVersion: fromVersion,
      legacySecrets: legacySecrets,
      legacyPinLength: legacyPinLength,
    );
  }

  /// Whether a raw blob (local or remote) still carries any plaintext security
  /// secret. Used to decide whether a cleaned snapshot must be re-pushed to the
  /// backend to purge cloud-stored plaintext.
  static bool rawHasLegacySecrets(Map<String, dynamic> raw) {
    final security = raw[PreferenceKeys.security];
    if (security is! Map) return false;
    for (final field in PreferenceKeys.legacySecuritySecretFields) {
      final value = security[field];
      if (value is String && value.isNotEmpty) return true;
    }
    return false;
  }

  static int _readVersion(Map<String, dynamic> data) {
    final raw = data[PreferenceKeys.schemaVersion];
    if (raw is int) return raw;
    if (raw is num) return raw.toInt();
    final parsed = int.tryParse('${raw ?? ''}');
    if (parsed != null) return parsed;
    // No version field at all means the earliest (pre-versioning) schema.
    return data.isEmpty ? PreferenceKeys.currentSchemaVersion : 1;
  }
}
