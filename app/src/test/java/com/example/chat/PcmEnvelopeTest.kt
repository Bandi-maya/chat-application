package com.example.chat
import com.example.chat.core.audio.PcmEnvelope
import org.junit.Assert.*
import org.junit.Test
class PcmEnvelopeTest {
 @Test fun silenceNotInvented() { assertTrue(PcmEnvelope(durationUs=100).values().all { it == 0f }) }
 @Test fun binsReflectSoundAtActualTime() { val e=PcmEnvelope(4,100);e.accept(60,-0.75f);assertEquals(listOf(0f,0f,0.75f,0f),e.values()) }
 @Test fun peaksKeepMaximumNotLastSample() { val e=PcmEnvelope(1,100);e.accept(20,0.8f);e.accept(30,0.1f);assertEquals(0.8f,e.values().single()) }
 @Test fun invalidSamplesIgnored() { val e=PcmEnvelope(2,100);e.accept(-1,1f);e.accept(101,1f);e.accept(0,Float.NaN);assertEquals(listOf(0f,0f),e.values()) }
 @Test fun endSampleUsesLastBin() { val e=PcmEnvelope(2,100);e.accept(100,1f);assertEquals(listOf(0f,1f),e.values()) }
 @Test fun excessiveAmplitudeClamped() { val e=PcmEnvelope(1,100);e.accept(1,20f);assertEquals(1f,e.values().single()) }
 @Test fun invalidDurationRejected() { assertThrows(IllegalArgumentException::class.java) { PcmEnvelope(durationUs=0) } }
}
