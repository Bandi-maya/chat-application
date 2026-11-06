package com.example.chat.ui.camera

import com.example.chat.data.api.ApiBackendService
import com.example.chat.data.api.ApiAccountScope

/** Construct at the camera button event, not in the child composition. No auth details are logged. */
class ApiVideoAuthPort(private val api: ApiBackendService, private val entryScope: ApiAccountScope = api.authSnapshot()) : VideoAuthPort {
    private val listeners = java.util.IdentityHashMap<(Any) -> Unit, (ApiAccountScope) -> Unit>()
    override fun snapshot(): Any = entryScope
    override fun isSignedIn(snapshot: Any): Boolean = snapshot === entryScope && api.withAuthScope(entryScope) {}
    override fun withScope(expected: Any, block: () -> Unit): Boolean = (expected as? ApiAccountScope)?.let { api.withAuthScope(it,block) } ?: false
    override fun addListener(listener: (Any) -> Unit) {
        val typed: (ApiAccountScope) -> Unit = { listener(it) }
        synchronized(listeners) { listeners[listener] = typed }
        api.addAuthScopeListener(typed)
    }
    override fun removeListener(listener: (Any) -> Unit) {
        val typed = synchronized(listeners) { listeners.remove(listener) }
        typed?.let(api::removeAuthScopeListener)
    }
}
