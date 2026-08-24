/// Centralized, single-source-of-truth persistence keys for Chaty.
///
/// Every string used to store or transport preference data lives here so that
/// renames are impossible to do casually (which would silently orphan a user's
/// saved data). The schema version below drives the migration runner in
/// [PreferencesMigrator]; bump it whenever the on-disk/remote shape changes and
/// add a corresponding upgrade step.
class PreferenceKeys {
  const PreferenceKeys._();

  /// Current schema version of the serialized preference blob. Persisted under
  /// [schemaVersion] and compared on load to decide which migrations to run.
  static const int currentSchemaVersion = 2;

  // ---------------------------------------------------------------------------
  // Storage-level keys (SharedPreferences entries).
  // ---------------------------------------------------------------------------

  /// Base key for the serialized preference blob. When a user is authenticated
  /// the effective key is namespaced per user (see [scopedPreferences]); the
  /// bare key is only used for the signed-out / device-global bucket and for
  /// reading the pre-namespacing legacy blob during migration.
  static const String preferencesBase = 'chaty_preferences_v1';

  /// Device-global theme/display state. Not account sensitive, and loaded
  /// before the first frame (before auth is known) to avoid a theme flash.
  static const String themeState = 'chaty_theme_v1';

  /// Device-global UI template configuration state.
  static const String templateState = 'chaty_template_v1';

  /// Id of the last authenticated user, used to safely adopt a legacy global
  /// blob into its rightful owner's namespace exactly once.
  static const String storedUserId = 'chaty_stored_user_id';

  /// Computes the per-user namespaced preferences key.
  static String scopedPreferences(String userId) => '$preferencesBase.$userId';

  // ---------------------------------------------------------------------------
  // Top-level fields inside the serialized preference blob.
  // ---------------------------------------------------------------------------

  static const String schemaVersion = 'schemaVersion';
  static const String privacy = 'privacy';
  static const String security = 'security';
  static const String home = 'home';
  static const String conversation = 'conversation';
  static const String notification = 'notification';
  static const String automation = 'automation';
  static const String effects = 'effects';
  static const String gbFeatures = 'gbFeatures';
  static const String favorites = 'favorites';

  /// Every top-level group key, used by corruption recovery to know which
  /// fields are structurally expected to be maps.
  static const List<String> groupKeys = <String>[
    privacy,
    security,
    home,
    conversation,
    notification,
    automation,
    effects,
  ];

  // ---------------------------------------------------------------------------
  // Legacy security secret fields. These plaintext credential fields used to be
  // serialized into the synced blob. They are now purged on migration and the
  // real credentials live only in platform secure storage (LocalLockService).
  // ---------------------------------------------------------------------------

  static const List<String> legacySecuritySecretFields = <String>[
    'pinCode',
    'password',
    'patternCode',
    'recoveryQuestion',
    'recoveryAnswer',
  ];
}
