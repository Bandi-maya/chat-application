enum AuthBootstrapDestination {
  loading,
  authenticated,
  unauthenticated,
}

/// Prevents a persisted Supabase session from selecting the authenticated
/// shell before ChatyBackendService has restored/hydrated its session state.
AuthBootstrapDestination resolveAuthBootstrapDestination({
  required bool isInitialized,
  required bool isAuthenticated,
}) {
  if (!isInitialized) return AuthBootstrapDestination.loading;
  return isAuthenticated
      ? AuthBootstrapDestination.authenticated
      : AuthBootstrapDestination.unauthenticated;
}
