package com.example.chat.core.auth

/** Exact port of _LoginScreenState._friendlyError. */
fun friendlyLoginError(error: Throwable): String {
    val value = (error.message ?: error.toString()).replaceFirst("Exception: ", "")
    val lower = value.lowercase()
    if (lower.contains("connection refused") ||
        lower.contains("socketexception") ||
        lower.contains("failed host lookup") ||
        lower.contains("clientexception")
    ) {
        return "Unable to connect to server. Please ensure the backend is running."
    }
    if (lower.contains("tenant not found") ||
        lower.contains("tenant_not_found") ||
        lower.contains("x-tenant-id")
    ) {
        return "Workspace / Organization not found. Please check your Workspace ID or register a new organization."
    }
    if (lower.contains("invalid") || value.contains("Incorrect") || value.contains("401")) {
        return "Incorrect email/username or password."
    }
    if (value.contains("Email not confirmed")) {
        return "Confirm your email first, then sign in."
    }
    return value
}

/** Exact port of _RegisterScreenState._friendlyError. */
fun friendlyRegisterError(error: Throwable): String {
    val value = (error.message ?: error.toString()).replaceFirst("Exception: ", "")
    val lower = value.lowercase()
    if (lower.contains("connection refused") ||
        lower.contains("socketexception") ||
        lower.contains("failed host lookup") ||
        lower.contains("clientexception")
    ) {
        return "Unable to connect to server. Please ensure the backend is running."
    }
    if (lower.contains("self-registration is disabled") ||
        lower.contains("registration is disabled") ||
        lower.contains("disabled for organization")
    ) {
        return value
    }
    if (lower.contains("tenant not found") ||
        lower.contains("tenant_not_found") ||
        lower.contains("x-tenant-id")
    ) {
        return "Workspace / Organization not found. Please check your Workspace ID or register a new organization."
    }
    if (value.contains("already exists") || value.contains("already registered")) {
        return "An account already exists for this email or username. Sign in instead."
    }
    if (value.contains("already taken")) {
        return "That username was just taken. Choose another username."
    }
    if (value.contains("rate limit")) {
        return "Too many attempts. Wait a moment and try again."
    }
    return value
}
