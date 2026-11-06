package com.example.chat
import com.example.chat.ui.calls.responsiveAudioControlColumns
import org.junit.Assert.*
import org.junit.Test
class ResponsiveAudioControlColumnsTest {
 @Test fun narrowPanelHasTwoCellsAndWideKeepsBundleGrammar() {
  assertEquals(2,responsiveAudioControlColumns(172,4))
  assertEquals(2,responsiveAudioControlColumns(172,2))
  assertEquals(4,responsiveAudioControlColumns(328,4))
  assertEquals(1,responsiveAudioControlColumns(100,4))
 }
}
