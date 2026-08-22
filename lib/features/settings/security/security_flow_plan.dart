import '../../../data/services/local_lock_service.dart';

/// What the user is trying to do with the App Lock credential.
enum SecurityIntent { enableLock, changeMethod, changeCredential, disableLock }

/// Which lock method a flow step refers to. These are the user-facing types
/// offered by the method selector.
enum LockMethodType { pin, pattern, password, biometric, deviceCredential }

/// Ordered steps for a security flow. The UI executes them front-to-back and
/// aborts on the first failure — nothing is persisted until every step passes.
enum SecurityFlowStep {
  /// Confirm the CURRENT credential before changing or removing protection
  /// (industry "confirm credentials before modify" pattern).
  verifyCurrent,

  /// Show the method-type selector ("what type do you want?").
  chooseMethod,

  /// Set + confirm the NEW credential (enter twice, pattern drawn twice,
  /// strength validated by [LocalLockService.setCredential]).
  setupNew,

  /// Run one real biometric/device authentication to prove the chosen OS
  /// path works BEFORE enabling it as the lock.
  preflightOsAuth,

  /// Persist the resulting preference changes.
  apply,
}

class SecurityFlowPlan {
  const SecurityFlowPlan._();

  /// True when the given method stores a local secret that can be verified.
  static bool hasVerifiableSecret(LockMethodType method) =>
      switch (method) {
        LockMethodType.pin ||
        LockMethodType.pattern ||
        LockMethodType.password => true,
        LockMethodType.biometric ||
        LockMethodType.deviceCredential => false,
      };

  static String methodStorageKey(LockMethodType method) => switch (method) {
    LockMethodType.pin => 'PIN',
    LockMethodType.pattern => 'Pattern',
    LockMethodType.password => 'Password',
    _ => '',
  };

  /// Compute the exact ordered steps for an intent.
  ///
  /// Rules (mirroring platform credential flows):
  /// - Changing ANYTHING while a verifiable secret exists ⇒ verifyCurrent
  ///   first. A lock must never be weakened, re-typed, or removed without
  ///   proving knowledge of the current secret.
  /// - Enabling always offers the method selector ("which type do you
  ///   want?") and then collects a fresh credential.
  /// - Biometric / Device Credential get a real preflight authentication so
  ///   an unavailable sensor or missing device lock can never become the
  ///   only way in.
  static List<SecurityFlowStep> plan({
    required SecurityIntent intent,
    required LockMethodType currentMethod,
    required bool currentSecretConfigured,
    LockMethodType? targetMethod,
  }) {
    final target = targetMethod ?? currentMethod;
    switch (intent) {
      case SecurityIntent.enableLock:
        return <SecurityFlowStep>[
          if (hasVerifiableSecret(currentMethod) && currentSecretConfigured)
            SecurityFlowStep.verifyCurrent,
          SecurityFlowStep.chooseMethod,
          if (hasVerifiableSecret(target)) SecurityFlowStep.setupNew,
          if (!hasVerifiableSecret(target)) SecurityFlowStep.preflightOsAuth,
          SecurityFlowStep.apply,
        ];
      case SecurityIntent.changeMethod:
        return <SecurityFlowStep>[
          if (hasVerifiableSecret(currentMethod) && currentSecretConfigured)
            SecurityFlowStep.verifyCurrent,
          if (hasVerifiableSecret(target)) SecurityFlowStep.setupNew,
          if (!hasVerifiableSecret(target)) SecurityFlowStep.preflightOsAuth,
          SecurityFlowStep.apply,
        ];
      case SecurityIntent.changeCredential:
        return <SecurityFlowStep>[
          if (currentSecretConfigured) SecurityFlowStep.verifyCurrent,
          SecurityFlowStep.setupNew,
          SecurityFlowStep.apply,
        ];
      case SecurityIntent.disableLock:
        return <SecurityFlowStep>[
          // Removing protection always requires proving knowledge of the
          // current secret, or a live OS auth when the method is OS-based.
          hasVerifiableSecret(currentMethod)
              ? (currentSecretConfigured
                    ? SecurityFlowStep.verifyCurrent
                    : SecurityFlowStep.apply)
              : SecurityFlowStep.preflightOsAuth,
          SecurityFlowStep.apply,
        ];
    }
  }
}
