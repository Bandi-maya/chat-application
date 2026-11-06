package com.example.chat.ui.profile

import com.example.chat.data.api.ApiAccountScope
import com.example.chat.data.api.ApiScopeChangedException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Explicit Save only. The local draft is never sent as a URL, and retry reuses completed upload. */
internal class ProfileEditorSaveSession(
    private val expected: ApiAccountScope,
    private val operations: ProfileEditorOperations,
    private val compress: (ByteArray) -> ByteArray,
) {
    private var draft: ProfileAvatarDraft? = null
    private var uploaded: ProfileAvatarDraft? = null
    private var url: String? = null
    private fun checkCurrent() { if (operations.snapshot() !== expected) throw ApiScopeChangedException() }
    fun replace(next: ProfileAvatarDraft) { checkCurrent(); draft = next; uploaded = null; url = null }
    suspend fun save(name: String, username: String, about: String, initialAvatar: String?) {
        checkCurrent()
        val local = draft
        var resolved = if (uploaded === local) url else null
        if (local != null && resolved == null) {
            val bytes = withContext(Dispatchers.IO) {
                compress(local.readBytes()).also { check(it.isNotEmpty() && it.size <= 2 * 1024 * 1024) { "Processed profile photo is too large." } }
            }
            checkCurrent()
            resolved = operations.upload(expected, bytes)
            checkCurrent()
            check(resolved.isNotBlank()) { "Profile photo upload returned no URL." }
            uploaded = local; url = resolved
        }
        try { operations.save(expected, name, username, about, resolved ?: initialAvatar); checkCurrent() }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (changed: ApiScopeChangedException) { throw changed }
        catch (error: Exception) {
            if (local != null && resolved != null) throw IllegalStateException(
                "Photo was uploaded, but profile details were not saved. Retry Save; Discard will not undo the uploaded photo.", error)
            throw error
        }
    }
}
