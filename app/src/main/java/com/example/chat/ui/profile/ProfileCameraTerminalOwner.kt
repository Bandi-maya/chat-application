package com.example.chat.ui.profile

/** One outstanding external writer. Persistent leaf contains no account data or credentials.
 * Restored requests are cleanup-only. Terminal cleanup failures retain ownership for retry. */
internal class ProfileCameraTerminalOwner(
    private val save: (String?, Boolean) -> Unit,
    private val delete: (String) -> Boolean,
    restoredPath: String? = null,
    restoredTerminal: Boolean = false,
) {
    private var path: String? = restoredPath
    private var retired = restoredPath != null
    private var deliveryPending = false
    private var terminalSeen = restoredPath != null && restoredTerminal
    @Synchronized fun begin(nextPath: String) {
        check(path == null) { "A profile camera request is still awaiting its result or cleanup." }
        save(nextPath, false)
        path = nextPath; retired = false; terminalSeen = false
    }
    @Synchronized fun retire() { if (path != null) retired = true }
    @Synchronized fun hasPending() = path != null
    @Synchronized fun complete(success: Boolean): String? {
        val completed = path ?: return null
        check(!deliveryPending) { "Profile photo handoff is pending." }
        terminalSeen = true
        // Persist terminal evidence before delete/transfer; restoration can retry cleanup only.
        try { save(completed, true) } catch (error: Exception) { retired = true; throw error }
        val accepted = success && !retired
        // Any exception leaves a cleanup-only tombstone, never a transferable result.
        retired = true
        if (!accepted) check(delete(completed)) { "Profile camera cleanup failed; retry required." }
        if (accepted) { deliveryPending = true; return completed } // // durable terminal record stays until local journal acknowledges
        save(null, false)
        path = null; retired = false; terminalSeen = false; deliveryPending = false
        return null
    }
    /** Caller must first durably index this terminal path as a local output. */
    @Synchronized fun acknowledgeLocalHandoff(expectedPath: String) {
        check(terminalSeen && path == expectedPath)
        save(null, false)
        path = null; retired = false; terminalSeen = false; deliveryPending = false
    }
    /** Only a terminal callback (or synchronous launch failure) authorizes cleanup retry. */
    @Synchronized fun retryTerminalCleanup() {
        val pending = path ?: return
        if (!terminalSeen) return
        check(delete(pending)) { "Profile camera cleanup failed; retry required." }
        save(null, false)
        path = null; retired = false; terminalSeen = false; deliveryPending = false
    }
    /** Reclaim a terminal result when delivery was cancelled. Persist before cleanup. */
    @Synchronized fun reclaimTerminal(completed: String) {
        check(path == null || path == completed)
        path = completed; retired = true; terminalSeen = true
        save(completed, true)
        retryTerminalCleanup()
    }
    @Synchronized fun launchFailed() { retired = true; complete(false) }
}
