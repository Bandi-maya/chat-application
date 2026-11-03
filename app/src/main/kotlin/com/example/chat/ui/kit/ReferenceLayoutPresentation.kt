package com.example.chat.ui.kit


/** Presentation only. Routes, menu entries and enabled destinations remain caller-owned. */
enum class ReferenceLayoutStyle { Default, Old, Ios, Other }

data class ReferenceLayoutPresentation(
    val style: ReferenceLayoutStyle = ReferenceLayoutStyle.Other,
    val animationsDisabled: Boolean = false,
) {
    val menuCornerDp: Int get() = when (style) {
        ReferenceLayoutStyle.Default -> 24
        ReferenceLayoutStyle.Old -> 4
        ReferenceLayoutStyle.Ios -> 14
        ReferenceLayoutStyle.Other -> 10
    }
    val menuEnterMs: Int get() = if (animationsDisabled) 0 else 140
    val menuExitMs: Int get() = if (animationsDisabled) 0 else 90
}

