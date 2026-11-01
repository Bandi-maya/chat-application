package com.example.chat

/**
 * Pure permission/privacy gate for Dynamic Island notification ingestion.
 * Kept separate so the security decision is exercised by JVM unit tests.
 */
internal object DynamicIslandPrivacyPolicy {
    fun shouldCapture(
        enabled: Boolean,
        overlayPermissionGranted: Boolean,
        privateScreen: Boolean,
    ): Boolean = enabled && overlayPermissionGranted && !privateScreen
}
