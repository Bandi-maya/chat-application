package com.example.chat.ui.camera

import org.junit.Assert.*
import org.junit.Test

class VideoRecordingPolicyTest {
    @Test fun missingMicrophoneProducesSilentVideo() { assertFalse(shouldRecordAudio(true, false)) }
    @Test fun audioRequiresBothIntentAndGrant() {
        assertTrue(shouldRecordAudio(true, true)); assertFalse(shouldRecordAudio(false, true)); assertFalse(shouldRecordAudio(false, false))
    }
    @Test fun onlyValidFinalizedVideoCanBeReviewed() {
        assertTrue(canReviewVideo(false, 1024, 1000, true))
        assertFalse(canReviewVideo(true, 1024, 1000, true))
        assertFalse(canReviewVideo(false, 0, 1000, true))
        assertFalse(canReviewVideo(false, 1024, null, true))
        assertFalse(canReviewVideo(false, 1024, 0, true))
        assertFalse(canReviewVideo(false, 1024, 1000, false))
    }
}
