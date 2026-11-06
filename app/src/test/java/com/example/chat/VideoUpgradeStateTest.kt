package com.example.chat
import com.example.chat.data.services.VideoUpgradeState as S
import org.junit.Assert.*
import org.junit.Test
class VideoUpgradeStateTest {
 @Test fun capabilityAndConsentRequired() { val s=S("a","b");assertFalse(s.request("r",0,false));assertTrue(s.request("r",0,true));assertFalse(s.committed("r"));assertFalse(s.accept("r","wrong",1));assertTrue(s.accept("r","b",1));assertTrue(s.committed("r"));assertEquals(S.Phase.VIDEO,s.phase) }
 @Test fun declineCancelTimeoutPreserveAudio() { val s=S("a","b");s.request("r",0,true);assertFalse(s.cancel("x","b"));assertTrue(s.cancel("r","b"));assertEquals(S.Phase.AUDIO,s.phase);s.request("r2",0,true);assertFalse(s.timeout(29999));assertTrue(s.timeout(30000));assertEquals(S.Phase.AUDIO,s.phase) }
 @Test fun glareAndDuplicateAreDeterministic() { val a=S("a","b");val b=S("b","a");a.request("ra",0,true);b.request("rb",0,true);assertFalse(a.incoming("rb","b",1));assertTrue(b.incoming("ra","a",1));assertFalse(b.incoming("ra","a",2));assertTrue(b.accept("ra","b",2)) }
 @Test fun noLateOrForeignAcceptance() { val s=S("a","b");assertFalse(s.incoming("r","z",0));s.incoming("r","b",0);assertFalse(s.accept("r","a",30000));s.reset();assertFalse(s.accept("r","a",1)) }
}
