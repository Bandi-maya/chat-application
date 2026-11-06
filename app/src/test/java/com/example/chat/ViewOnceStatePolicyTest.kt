package com.example.chat

import com.example.chat.core.messages.ViewOnceStatePolicy
import com.example.chat.core.messages.ViewOnceStatePolicy.Kind
import com.example.chat.core.messages.ViewOnceStatePolicy.Surface
import org.junit.Assert.assertEquals
import org.junit.Test

class ViewOnceStatePolicyTest {

    @Test fun unopenedIsLocked() {
        assertEquals(Surface.LOCKED, ViewOnceStatePolicy.surface(Kind.MEDIA, opened = false, retainOpenedMedia = false, playbackCompleted = false))
        assertEquals(Surface.LOCKED, ViewOnceStatePolicy.surface(Kind.VOICE, opened = false, retainOpenedMedia = false, playbackCompleted = false))
    }

    @Test fun openedMediaExpiresUnlessRetained() {
        assertEquals(Surface.EXPIRED, ViewOnceStatePolicy.surface(Kind.MEDIA, opened = true, retainOpenedMedia = false, playbackCompleted = false))
        assertEquals(Surface.CONTENT, ViewOnceStatePolicy.surface(Kind.MEDIA, opened = true, retainOpenedMedia = true, playbackCompleted = false))
    }

    @Test fun openedVoiceStaysPlayableUntilPlaybackCompletes() {
        assertEquals(Surface.CONTENT, ViewOnceStatePolicy.surface(Kind.VOICE, opened = true, retainOpenedMedia = false, playbackCompleted = false))
        assertEquals(Surface.EXPIRED, ViewOnceStatePolicy.surface(Kind.VOICE, opened = true, retainOpenedMedia = false, playbackCompleted = true))
    }
}
