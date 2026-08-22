import 'package:flutter_test/flutter_test.dart';
import 'package:chat/features/settings/security/security_flow_plan.dart';

void main() {
  group('SecurityFlowPlan', () {
    test('first-time enable: choose → setup → apply (no verify)', () {
      expect(
        SecurityFlowPlan.plan(
          intent: SecurityIntent.enableLock,
          currentMethod: LockMethodType.pin,
          currentSecretConfigured: false,
        ),
        const [
          SecurityFlowStep.chooseMethod,
          SecurityFlowStep.setupNew,
          SecurityFlowStep.apply,
        ],
      );
    });

    test('enable when an old credential exists verifies it FIRST', () {
      // The lock must never be re-typed or replaced without proving
      // knowledge of the currently configured secret.
      expect(
        SecurityFlowPlan.plan(
          intent: SecurityIntent.enableLock,
          currentMethod: LockMethodType.pattern,
          currentSecretConfigured: true,
        ),
        const [
          SecurityFlowStep.verifyCurrent,
          SecurityFlowStep.chooseMethod,
          SecurityFlowStep.setupNew,
          SecurityFlowStep.apply,
        ],
      );
    });

    test('changing method verifies current, then collects the new secret', () {
      expect(
        SecurityFlowPlan.plan(
          intent: SecurityIntent.changeMethod,
          currentMethod: LockMethodType.pin,
          currentSecretConfigured: true,
          targetMethod: LockMethodType.password,
        ),
        const [
          SecurityFlowStep.verifyCurrent,
          SecurityFlowStep.setupNew,
          SecurityFlowStep.apply,
        ],
      );
    });

    test('OS-based targets get a real preflight instead of a setup form', () {
      expect(
        SecurityFlowPlan.plan(
          intent: SecurityIntent.changeMethod,
          currentMethod: LockMethodType.pin,
          currentSecretConfigured: true,
          targetMethod: LockMethodType.biometric,
        ),
        const [
          SecurityFlowStep.verifyCurrent,
          SecurityFlowStep.preflightOsAuth,
          SecurityFlowStep.apply,
        ],
      );
    });

    test('disabling always requires proof of the current secret', () {
      expect(
        SecurityFlowPlan.plan(
          intent: SecurityIntent.disableLock,
          currentMethod: LockMethodType.pattern,
          currentSecretConfigured: true,
        ),
        const [SecurityFlowStep.verifyCurrent, SecurityFlowStep.apply],
      );
    });

    test('disabling an OS-based lock runs a live OS auth, not nothing', () {
      expect(
        SecurityFlowPlan.plan(
          intent: SecurityIntent.disableLock,
          currentMethod: LockMethodType.biometric,
          currentSecretConfigured: false,
        ),
        const [SecurityFlowStep.preflightOsAuth, SecurityFlowStep.apply],
      );
    });

    test('changing the credential re-verifies then collects a fresh one', () {
      expect(
        SecurityFlowPlan.plan(
          intent: SecurityIntent.changeCredential,
          currentMethod: LockMethodType.pin,
          currentSecretConfigured: true,
        ),
        const [
          SecurityFlowStep.verifyCurrent,
          SecurityFlowStep.setupNew,
          SecurityFlowStep.apply,
        ],
      );
    });

    test('OS methods expose no verifiable local secret', () {
      expect(
        SecurityFlowPlan.hasVerifiableSecret(LockMethodType.biometric),
        isFalse,
      );
      expect(
        SecurityFlowPlan.hasVerifiableSecret(LockMethodType.deviceCredential),
        isFalse,
      );
      expect(SecurityFlowPlan.hasVerifiableSecret(LockMethodType.pin), isTrue);
    });
  });
}
