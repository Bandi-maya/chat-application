import 'package:flutter_test/flutter_test.dart';
import 'package:chat/data/services/auth_bootstrap_policy.dart';

void main() {
  group('Auth bootstrap destination', () {
    test('waits for backend hydration even when Supabase has a session', () {
      expect(
        resolveAuthBootstrapDestination(
          isInitialized: false,
          isAuthenticated: false,
        ),
        AuthBootstrapDestination.loading,
      );
      expect(
        resolveAuthBootstrapDestination(
          isInitialized: false,
          isAuthenticated: true,
        ),
        AuthBootstrapDestination.loading,
      );
    });

    test('routes to the authenticated shell only after initialization', () {
      expect(
        resolveAuthBootstrapDestination(
          isInitialized: true,
          isAuthenticated: true,
        ),
        AuthBootstrapDestination.authenticated,
      );
    });

    test('routes to Welcome only after initialization confirms no session', () {
      expect(
        resolveAuthBootstrapDestination(
          isInitialized: true,
          isAuthenticated: false,
        ),
        AuthBootstrapDestination.unauthenticated,
      );
    });
  });
}
