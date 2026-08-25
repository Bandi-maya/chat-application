# Migration Guard Rails in Chaty Application

This document summarizes the migration guard rails identified in the Chaty codebase as part of the HIVE work order review. These guard rails protect against data corruption, incompatible state, and security issues during app upgrades and version transitions.

## 1. Local Cache Versioning (LocalSnapshotCacheService)

**File**: `lib/data/services/local_snapshot_cache_service.dart`

**Mechanism**:
- Maintains a version constant (`_version = 1`)
- Checks version when reading snapshots: `if (map['v'] != _version) return null;`
- On version mismatch, returns null (cache miss) and triggers rebuild from server

**Protection**:
- Prevents use of cached data that's incompatible with current app version
- Ensures UI renders correctly after app upgrades that change cache format
- Forces refresh from server when local cache schema evolves

**Flow**:
1. App attempts to read encrypted snapshot from local storage
2. Codec reads outer envelope and checks version field
3. If version mismatch → returns null → treated as cache miss
4. App falls back to fetching fresh data from Supabase backend
5. New snapshot written with current version

## 2. Forward-Compatible Data Codecs (SnapshotCodec)

**File**: `lib/data/services/snapshot_codec.dart`

**Mechanism**:
- Written in chatMessageToJson/conversationToJson functions:
  - `'v': _version,` (embeds current version in payload)
- Read-side validation with safe fallbacks for unknown enum values
- Comments: "Unknown enum values fall back to safe defaults so a snapshot written by a newer build never crashes an older one."

**Protection**:
- Enables forward compatibility: newer app versions can write data readable by older versions
- Prevents crashes when downgrading apps or running mixed-version installations
- Maintains data accessibility across version boundaries

**Examples**:
- ConversationType: Unknown values default to `.direct`
- EncryptionStatus: Unknown values default to `.verificationNeeded`
- MessageType: Unknown values default to `.text`
- DeliveryState: Unknown values default to `.delivered`

## 3. Preferences Schema Migration (PreferencesMigrator)

**Files**:
- `lib/ui/core/persistence/preference_keys.dart` (defines `currentSchemaVersion = 2`)
- `lib/ui/core/persistence/preferences_migrator.dart` (implements migration logic)
- `lib/ui/core/controllers/preferences_controller.dart` (uses migrator)

**Mechanism**:
- On app load, resolves local preference blob
- Calls `PreferencesMigrator.migrate(raw)` to upgrade to current schema
- Specific v1→v2 migration: strips plaintext security fields (`pinCode`, `password`, etc.)
- Migrated data resaved to local storage and optionally pushed to backend

**Protection**:
- Securely handles migration of user preference data between versions
- Specifically addresses security vulnerability: removes plaintext credentials from synced blob
- Ensures credentials only exist in platform-secure storage (LocalLockService)
- Prevents credential leakage during version upgrades

**v1→v2 Migration Details**:
- Reads legacy security fields from preference blob
- Moves them to platform-secure storage via LocalLockService
- Removes legacy fields from blob so they can never be resynced to backend
- Preserves all non-security preference data

## 4. Encrypted Payload Validation (MLS E2EE Service)

**File**: `lib/data/services/mls_e2ee_service.dart`

**Mechanism**:
- In `decryptPayload()` function, validates MLS envelope structure:
  ```dart
  if (envelope['version'] != 1 ||
      envelope['conversation_id']?.toString() != conversationId ||
      envelope['payload'] is! Map) {
    throw const MlsE2eeException(
      'Encrypted payload failed Chaty context validation.',
    );
  }
  ```
- Similar validation in `encryptPayload()` for outbound messages

**Protection**:
- Prevents processing of malformed or tampered encrypted MLS messages
- Ensures payloads match expected conversation and version
- Throws specific exceptions (`MlsE2eeException`) for validation failures
- Maintains integrity of end-to-end encrypted communication

**Validation Scope**:
- Envelope version must be exactly 1
- Conversation ID must match target conversation
- Payload must be a Map (not null or other type)
- Failures treated as security-relevant, not retryable errors

## Summary of Protection Areas

| Guard Rail | Protects Against | Key Benefit |
|------------|------------------|-------------|
| Local Cache Versioning | Incompatible cached data after app upgrade | Correct UI rendering post-update |
| Forward-Compatible Codecs | Crashes when downgrading/mixed versions | Data accessibility across versions |
| Preferences Migration | Credential leakage, preference corruption | Secure handling of user data upgrades |
| Encrypted Payload Validation | Malformed/ tampered MLS messages | E2EE message integrity and security |

These guard rails collectively ensure that Chaty can:
- Safely upgrade between versions without data loss or corruption
- Maintain backward and forward compatibility where appropriate
- Handle security-sensitive data correctly during migrations
- Reject invalid data rather than processing it incorrectly
- Provide a smooth user experience across version transitions

The implementation follows defense-in-depth principles, with multiple layers of validation and migration handling appropriate to the data type and sensitivity level.