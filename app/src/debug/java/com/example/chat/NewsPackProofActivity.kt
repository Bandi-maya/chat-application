package com.example.chat
import android.os.Bundle
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.chat.core.stickers.NewsStickerPack
import com.example.chat.core.theme.ThemePresets
import com.example.chat.ui.chats.detail.ClusteredStickerPicker
import com.example.chat.ui.theme.ChatyTheme
class NewsPackProofActivity : ComponentActivity() {
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  val page = intent.getIntExtra("page", 0).coerceIn(0, 3)
  if (page == 0) {
   NewsStickerPack.pack.stickers.forEach { s ->
    val d = ImageDecoder.decodeDrawable(ImageDecoder.createSource(assets, s.assetPath))
    check(d is AnimatedImageDrawable && d.intrinsicWidth == 128 && d.intrinsicHeight == 128)
    (d as AnimatedImageDrawable).stop()
   }
   Log.i("NewsPackProof", "DECODE_100_OK")
  }
  val t = ThemePresets.getSystemDefaultTheme(platformIsLight = true)
  val pack = NewsStickerPack.pack.copy(stickers = NewsStickerPack.pack.stickers.drop(page * 25).take(25))
  setContent { ChatyTheme(t) { Box(Modifier.fillMaxSize().background(Color(t.backgroundColor)).statusBarsPadding()) {
   ClusteredStickerPicker(listOf(pack), t, onConfirmSend = null, unavailableReason = "Debug display fixture. Sending disabled.")
  } } }
 }
}
