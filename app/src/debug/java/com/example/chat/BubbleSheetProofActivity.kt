package com.example.chat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.theme.BubbleStyleId
import com.example.chat.core.theme.ThemePresets
import com.example.chat.ui.kit.ChatyBubbleContainer
import com.example.chat.ui.theme.ChatyTheme

/**
 * Debug-only contact sheet host: PER_PAGE bubble styles per page, each drawn for "me" and "them" with the
 * real ChatyBubbleContainer / drawBubble. No ServiceLocator, session or network. Extras: dark, page, text.
 * text=false draws silhouettes only (used for the pairwise distinctness assertion); text=true adds a sample
 * message (used for the readability screenshots). Each cell is 178x72dp: a 150x44dp bubble with a 14dp margin
 * so tails and accents drawn outside the bubble bounds are inside the captured cell.
 */
class BubbleSheetProofActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dark = intent.getBooleanExtra(EXTRA_DARK, false)
        val page = intent.getIntExtra(EXTRA_PAGE, 0)
        val withText = intent.getBooleanExtra(EXTRA_TEXT, false)
        // Readability sheets include the real dome shoulder inset plus a text line.
        // Silhouette captures and pairwise comparison dimensions remain unchanged.
        val bubbleHeight = if (withText) 60.dp else 44.dp
        val cellHeight = if (withText) 88.dp else 72.dp
        val t = ThemePresets.getSystemDefaultTheme(platformIsLight = !dark)
        val ids = BubbleStyleId.values().drop(page * PER_PAGE).take(PER_PAGE)
        setContent {
            ChatyTheme(t) {
                Column(Modifier.fillMaxSize().background(Color(t.backgroundColor)).statusBarsPadding()) {
                    ids.forEach { id ->
                        Text(id.displayName, Modifier.padding(start = 14.dp, top = 4.dp), color = Color(t.secondaryTextColor), fontSize = 11.sp)
                        Row {
                            listOf(true, false).forEach { me ->
                                Box(
                                    Modifier.size(178.dp, cellHeight).semantics { contentDescription = "bubble-${id.name}-${if (me) "me" else "them"}" },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    ChatyBubbleContainer(
                                        styleId = id, isMe = me,
                                        fillColor = Color(if (me) t.accentColor else t.cardColor),
                                        accentColor = Color(t.accentColor),
                                        modifier = Modifier.size(150.dp, bubbleHeight),
                                    ) {
                                        if (withText) Text("Hello there", color = Color(if (me) t.onAccentColor else t.primaryTextColor), fontSize = 14.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_DARK = "dark"
        const val EXTRA_PAGE = "page"
        const val EXTRA_TEXT = "text"
        const val PER_PAGE = 7
        fun pageCount() = (BubbleStyleId.values().size + PER_PAGE - 1) / PER_PAGE
    }
}
