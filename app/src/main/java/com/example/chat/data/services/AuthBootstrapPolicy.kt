package com.example.chat.data.services

/** Exact port of lib/data/services/auth_bootstrap_policy.dart. */

enum class AuthBootstrapDestination { loading, authenticated, unauthenticated }

/** Prevents a persisted Supabase session from selecting the authenticated
 *  shell before the backend service has restored/hydrated its session
 *  state. */
fun resolveAuthBootstrapDestination(
    isInitialized: Boolean,
    isAuthenticated: Boolean,
): AuthBootstrapDestination {
    if (!isInitialized) return AuthBootstrapDestination.loading
    return if (isAuthenticated) {
        AuthBootstrapDestination.authenticated
    } else {
        AuthBootstrapDestination.unauthenticated
    }
}
