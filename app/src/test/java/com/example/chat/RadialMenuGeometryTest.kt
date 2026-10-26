package com.example.chat

import com.example.chat.ui.navigation.RadialMenuGeometry
import kotlin.math.hypot
import org.junit.Assert.*
import org.junit.Test

class RadialMenuGeometryTest {
    @Test fun `all icons share one radius around persistent trigger`() {
        val layout = RadialMenuGeometry.layout(360f, 720f, 5)
        assertEquals(36f, layout.anchor.x, 0.001f)
        assertEquals(36f, layout.anchor.y, 0.001f)
        assertEquals(5, layout.centers.size)
        layout.centers.forEach { point ->
            assertEquals(layout.radius, hypot(point.x - layout.anchor.x, point.y - layout.anchor.y), 0.001f)
        }
    }

    @Test fun `targets fit safe local bounds and never intersect`() {
        for (width in listOf(240f, 320f, 360f, 412f, 800f)) {
            for (height in listOf(240f, 360f, 720f)) {
                for (count in 1..12) {
                    val layout = RadialMenuGeometry.layout(width, height, count)
                    layout.centers.forEach { point ->
                        assertTrue(point.x - 28f >= 0f)
                        assertTrue(point.y - 28f >= 0f)
                        assertTrue(point.x + 28f <= width)
                        assertTrue(point.y + 28f <= height)
                        assertTrue(hypot(point.x - layout.anchor.x, point.y - layout.anchor.y) >= 64f)
                    }
                    layout.centers.zipWithNext().forEach { (a, b) ->
                        assertTrue(hypot(a.x - b.x, a.y - b.y) >= 63.999f)
                    }
                }
            }
        }
    }

    @Test fun `anchor is independent of menu state and count`() {
        for (count in 0..15) {
            assertEquals(RadialMenuGeometry.layout(360f, 720f, 0).anchor,
                RadialMenuGeometry.layout(360f, 720f, count).anchor)
        }
    }

    @Test fun `capacity paginates rather than reducing touch size`() {
        val layout = RadialMenuGeometry.layout(360f, 720f, 12)
        assertEquals(5, layout.pageSize)
        assertEquals(5, layout.centers.size)
        val indices = (0 until 12 step layout.pageSize).flatMap { start ->
            (start until minOf(start + layout.pageSize, 12)).toList()
        }
        assertEquals((0 until 12).toList(), indices)
    }

    @Test fun `empty and tiny windows remain finite`() {
        for (size in listOf(0f, 30f, 100f)) {
            val layout = RadialMenuGeometry.layout(size, size, 0)
            assertTrue(layout.anchor.x.isFinite())
            assertTrue(layout.radius.isFinite())
            assertTrue(layout.centers.isEmpty())
        }
    }

    @Test fun `next-page target is separate from arc on supported fan windows`() {
        for (width in listOf(232f, 240f, 320f, 360f, 800f)) {
            for (height in listOf(232f, 240f, 360f, 720f)) {
                val layout = RadialMenuGeometry.layout(width, height, 12)
                if (layout.radius < 160f) continue
                val nextX = layout.anchor.x + layout.radius * 0.35f
                val nextY = layout.anchor.y + layout.radius * 0.35f
                assertTrue(hypot(nextX - layout.anchor.x, nextY - layout.anchor.y) >= 56f)
                layout.centers.forEach { point ->
                    assertTrue(hypot(point.x - nextX, point.y - nextY) >= 56f)
                }
            }
        }
    }
}
