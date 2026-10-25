package com.example.chat.ui.kit

/** Pure geometry port of lib/ui/core/menu/context_surface_engine.dart (ContextSurfaceResolver). Units are dp. */
data class SurfaceRect(val left: Float, val top: Float, val width: Float, val height: Float) {
    val right: Float get() = left + width
    val bottom: Float get() = top + height
    val centerX: Float get() = left + width / 2f
}

data class SurfaceInsets(val left: Float = 0f, val top: Float = 0f, val right: Float = 0f, val bottom: Float = 0f)
data class SurfaceSize(val width: Float, val height: Float)

enum class ContextSurfacePlacement { aboveLeft, aboveRight, belowLeft, belowRight, sideLeft, sideRight }

enum class ContextAnchorType { overflowButton, messageIncoming, messageOutgoing, chatRow, avatar, navigationItem, generic }

/** Dart Alignment as fractions: x,y in -1..1 (topRight = (1,-1)). */
data class SurfaceOrigin(val x: Float, val y: Float) {
    companion object {
        val topLeft = SurfaceOrigin(-1f, -1f)
        val topRight = SurfaceOrigin(1f, -1f)
        val bottomLeft = SurfaceOrigin(-1f, 1f)
        val bottomRight = SurfaceOrigin(1f, 1f)
    }
}

data class ContextSurfaceRequest(
    val anchorRect: SurfaceRect,
    val preferredSize: SurfaceSize,
    val safeInsets: SurfaceInsets,
    val screenSize: SurfaceSize,
    val keyboardInsets: SurfaceInsets = SurfaceInsets(),
    val anchorType: ContextAnchorType = ContextAnchorType.generic,
    val contentSized: Boolean = true,
)

data class ContextSurfaceResolution(
    val targetRect: SurfaceRect,
    val maxHeight: Float,
    val placement: ContextSurfacePlacement,
    val transformOrigin: SurfaceOrigin,
)

object ContextSurfaceResolver {
    private fun Float.clampTo(lo: Float, hi: Float) = if (this < lo) lo else if (this > hi) hi else this

    fun resolve(request: ContextSurfaceRequest): ContextSurfaceResolution {
        val screen = request.screenSize
        val padding = request.safeInsets
        val keyboard = request.keyboardInsets
        val anchor = request.anchorRect
        val pref = request.preferredSize

        val effectiveBottom = screen.height - padding.bottom - keyboard.bottom - 8f
        val effectiveTop = padding.top + 8f
        val effectiveLeft = padding.left + 8f
        val effectiveRight = screen.width - padding.right - 8f

        val spaceBelow = (effectiveBottom - anchor.bottom).clampTo(0f, screen.height)
        val spaceAbove = (anchor.top - effectiveTop).clampTo(0f, screen.height)

        val showBelow: Boolean
        val availableHeight: Float
        if (spaceBelow >= pref.height) { showBelow = true; availableHeight = spaceBelow }
        else if (spaceAbove >= pref.height) { showBelow = false; availableHeight = spaceAbove }
        else if (spaceAbove > spaceBelow) { showBelow = false; availableHeight = spaceAbove }
        else { showBelow = true; availableHeight = spaceBelow }

        val targetHeight = pref.height.clampTo(0f, availableHeight)

        val isRightSide = when (request.anchorType) {
            ContextAnchorType.messageOutgoing -> true
            ContextAnchorType.messageIncoming -> false
            ContextAnchorType.overflowButton -> true
            else -> anchor.centerX > (screen.width / 2f)
        }

        val placement = if (showBelow) {
            if (isRightSide) ContextSurfacePlacement.belowRight else ContextSurfacePlacement.belowLeft
        } else {
            if (isRightSide) ContextSurfacePlacement.aboveRight else ContextSurfacePlacement.aboveLeft
        }

        val targetWidth = pref.width.clampTo(150f, screen.width - 24f)

        var left = if (isRightSide) anchor.right - targetWidth else anchor.left
        if (left + targetWidth > effectiveRight) left = effectiveRight - targetWidth
        if (left < effectiveLeft) left = effectiveLeft

        var top: Float
        val origin: SurfaceOrigin
        if (showBelow) {
            top = anchor.bottom + 6f
            if (top + targetHeight > effectiveBottom) top = (effectiveBottom - targetHeight).clampTo(effectiveTop, effectiveBottom)
            origin = if (isRightSide) SurfaceOrigin.topRight else SurfaceOrigin.topLeft
        } else {
            top = anchor.top - targetHeight - 6f
            if (top < effectiveTop) top = effectiveTop
            origin = if (isRightSide) SurfaceOrigin.bottomRight else SurfaceOrigin.bottomLeft
        }
        return ContextSurfaceResolution(SurfaceRect(left, top, targetWidth, targetHeight), availableHeight, placement, origin)
    }
}
