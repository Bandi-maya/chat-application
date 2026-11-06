package com.example.chat
import com.example.chat.domain.models.UniversalPreferences
import org.junit.Assert.*
import org.junit.Test
class HugeiconsDefaultPreferenceTest {
 @Test fun freshAndMissingUseFreeWhileExplicitPackAndOverridesRemain() {
  assertEquals("Hugeicons Free",UniversalPreferences().iconTheme)
  assertEquals("Hugeicons Free",UniversalPreferences.fromMap(emptyMap()).iconTheme)
  for(pack in listOf("Chaty Orbit","Chaty Sigil","Pikaicons Stroke","Hugeicons Free"))
   assertEquals(pack,UniversalPreferences.fromMap(mapOf("iconTheme" to pack)).iconTheme)
  val p=UniversalPreferences(iconTheme="Chaty Orbit",iconVariants=mapOf("signal" to "Technical Blueprint"))
  assertEquals(p,UniversalPreferences.fromMap(p.toMap()))
 }
}
