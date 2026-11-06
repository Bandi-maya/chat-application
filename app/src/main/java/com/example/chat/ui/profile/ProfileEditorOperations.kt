package com.example.chat.ui.profile

import com.example.chat.data.api.ApiAccountScope
import com.example.chat.domain.models.UserProfile

/** Production facade adapter is in AppRoot; synthetic hosts never replace ServiceLocator state. */
interface ProfileEditorOperations {
    fun snapshot(): ApiAccountScope
    fun user(): UserProfile?
    fun addListener(listener: (ApiAccountScope) -> Unit)
    fun removeListener(listener: (ApiAccountScope) -> Unit)
    suspend fun upload(expected: ApiAccountScope, bytes: ByteArray): String
    suspend fun save(expected: ApiAccountScope, name: String, username: String, about: String, avatarUrl: String?)
}

