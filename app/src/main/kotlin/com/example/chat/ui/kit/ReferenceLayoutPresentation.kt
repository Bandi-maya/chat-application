package com.example.chat.ui.kit


/** Presentation only. Routes, menu entries and enabled destinations remain caller-owned. */
enum class ReferenceLayoutStyle { Default, Old, Ios, Other }

data class ReferenceLayoutPresentation(
    val style: ReferenceLayoutStyle = ReferenceLayoutStyle.Other,
    val animationsDisabled: Boolean = false,
) {
    /** User asked for the popup/dialog border radius kept at 10 (their wording "10px";
     *  implemented as 10dp pending unit confirmation - GB reference menus measure ~10dp).
     *  Constant across reference layouts so menus stop changing shape on layout switch. */
    val menuCornerDp: Int get() = 10
    val menuEnterMs: Int get() = if (animationsDisabled) 0 else 140
    val menuExitMs: Int get() = if (animationsDisabled) 0 else 90
}
