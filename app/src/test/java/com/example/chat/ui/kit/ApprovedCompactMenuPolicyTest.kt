package com.example.chat.ui.kit

import androidx.compose.ui.unit.*
import org.junit.Assert.*
import org.junit.Test

class ApprovedCompactMenuPolicyTest {
    @Test fun explicitCompactMenuStaysBelowAndReportsAvailableBodyHeight() {
        var available = -1
        val provider = ChatyMenuPositionProvider(8, preferBelow = true, onAvailableHeight = { available = it })
        val result = provider.calculatePosition(IntRect(350, 650, 390, 700), IntSize(400, 800), LayoutDirection.Ltr, IntSize(200, 250))
        assertEquals(192, result.x)
        assertEquals(708, result.y)
        assertEquals(84, available)
    }
    @Test fun approvedCompactEnvelopeIsSeparateFromLegacyGeometry() {
        val metrics = com.example.chat.ui.navigation.ReferenceLayoutMetrics
        assertEquals(160, metrics.compactHeaderHeightDp)
        assertEquals(metrics.compactHeaderHeightDp, metrics.compactIdentityHeightDp + metrics.actionBarHeightDp)
        assertEquals(26, metrics.compactIdentityTextSizeSp)
        assertEquals(250, metrics.expandedHeaderHeightDp)
        assertEquals(30, metrics.identityTextSizeSp)
    }
}
