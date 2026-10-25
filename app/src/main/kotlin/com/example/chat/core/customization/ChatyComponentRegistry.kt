package com.example.chat.core.customization

/** Port of chaty_component_registry.dart: single source of truth for all component variants.
 *  Catalog below is generated 1:1 from the 51 Dart register() calls (order, ids, names, descriptions, costs). */
class ChatyComponentRegistry private constructor() {
    private val specs = LinkedHashMap<ComponentId, LinkedHashMap<String, ComponentSpec>>()
    private val defaultVariants = HashMap<ComponentId, String>()

    init { registerDefaultCatalog() }

    fun register(spec: ComponentSpec, isDefault: Boolean = false) {
        specs.getOrPut(spec.componentId) { LinkedHashMap() }[spec.variantId] = spec
        if (isDefault || !defaultVariants.containsKey(spec.componentId)) defaultVariants[spec.componentId] = spec.variantId
    }

    fun getSpec(componentId: ComponentId, variantId: String): ComponentSpec? = specs[componentId]?.get(variantId)
    fun getDefaultVariantId(componentId: ComponentId): String = defaultVariants[componentId] ?: ""
    fun getVariantsFor(componentId: ComponentId): List<ComponentSpec> = specs[componentId]?.values?.toList() ?: emptyList()

    fun nearestCompatible(componentId: ComponentId, requestedVariantId: String, context: VariantContext): ComponentSpec {
        val spec = getSpec(componentId, requestedVariantId)
        if (spec != null && spec.supports(context)) return spec
        for (candidate in getVariantsFor(componentId)) if (candidate.supports(context)) return candidate
        getSpec(componentId, getDefaultVariantId(componentId))?.let { return it }
        return ComponentSpec(componentId = componentId, variantId = requestedVariantId, displayName = requestedVariantId)
    }

    private fun reg(
        id: ComponentId, variantId: String, displayName: String, description: String,
        cost: PerformanceCost, motion: String, isDefault: Boolean = false,
    ) = register(
        ComponentSpec(id, variantId, displayName, description, ComponentCapabilities.universal, cost, motion),
        isDefault,
    )

    private fun registerDefaultCatalog() {
        reg(ComponentId.navigation, "classic_label_bar", "Classic Label Bar", "Familiar icon + persistent label row with native platform spacing.", PerformanceCost.low, "standard", isDefault = true)
        reg(ComponentId.navigation, "floating_dock", "Floating Dock", "Elevated compact dock with subtle elevation and border.", PerformanceCost.low, "standard")
        reg(ComponentId.navigation, "capsule_rail", "Capsule Rail", "Active destination in an animated pill capsule.", PerformanceCost.low, "standard")
        reg(ComponentId.navigation, "split_segments", "Split Segments", "Productivity segmented layout with distinct active background.", PerformanceCost.low, "standard")
        reg(ComponentId.navigation, "glass_dock", "Glass Dock", "Frosted translucent backdrop surface with bounded blur.", PerformanceCost.medium, "standard")
        reg(ComponentId.navigation, "compact_icon_dock", "Compact Icon Dock", "Icon-first layout with accessible tooltip on long-press.", PerformanceCost.low, "standard")
        reg(ComponentId.navigation, "orbit", "Orbit", "Subtle radial active indicator around the selected icon.", PerformanceCost.low, "standard")
        reg(ComponentId.navigation, "elevated_pill", "Elevated Pill", "Single continuous pill container with sliding active indicator.", PerformanceCost.low, "standard")
        reg(ComponentId.navigation, "line_marker", "Line Marker", "Minimalist editorial underline / side line indicator.", PerformanceCost.low, "standard")
        reg(ComponentId.navigation, "adaptive_rail", "Adaptive Rail", "Automatically shifts between compact bottom dock and side rail on wide screens.", PerformanceCost.low, "standard")
        reg(ComponentId.header, "standard_identity", "Standard Identity", "Leading back/avatar, title, subtitle, and contextual actions.", PerformanceCost.low, "standard", isDefault = true)
        reg(ComponentId.header, "large_context", "Large Context", "Prominent identity cluster that gracefully collapses on scroll.", PerformanceCost.low, "standard")
        reg(ComponentId.header, "compact", "Compact Header", "Dense toolbar maximizing message reading area.", PerformanceCost.low, "standard")
        reg(ComponentId.header, "search_first", "Search First", "Inline search field integrated directly into the header.", PerformanceCost.low, "standard")
        reg(ComponentId.header, "glass_strip", "Glass Strip", "Translucent floating header strip with bounded blur.", PerformanceCost.medium, "standard")
        reg(ComponentId.header, "profile_led", "Profile Led", "Prominent avatar header with status beneath.", PerformanceCost.low, "standard")
        reg(ComponentId.header, "selection_header", "Selection Header", "Multi-select header showing count, pin, forward, delete, star.", PerformanceCost.low, "standard")
        reg(ComponentId.header, "call_context", "Call Context", "Call state banner with live elapsed timer and return-to-call action.", PerformanceCost.low, "standard")
        reg(ComponentId.composer, "chaty_capsule", "Chaty Capsule", "Balanced attachment, emoji, text field, mic/send capsule.", PerformanceCost.low, "standard", isDefault = true)
        reg(ComponentId.composer, "telegram_dense", "Telegram Dense", "High density action spacing with attachment shortcut first.", PerformanceCost.low, "standard")
        reg(ComponentId.composer, "imessage_expressive", "iMessage Expressive", "Expandable app rail with smooth action drawer.", PerformanceCost.low, "standard")
        reg(ComponentId.composer, "messenger_action_row", "Messenger Action Row", "Prominent bottom action row around a rounded text field.", PerformanceCost.low, "standard")
        reg(ComponentId.composer, "signal_quiet", "Signal Quiet", "Minimal privacy-focused input with essential controls.", PerformanceCost.low, "standard")
        reg(ComponentId.composer, "instagram_media_first", "Instagram Media First", "Quick camera, gallery, voice and sticker actions.", PerformanceCost.low, "standard")
        reg(ComponentId.composer, "lens_first", "Lens First", "Prominent camera and AR effect shortcut.", PerformanceCost.low, "standard")
        reg(ComponentId.composer, "command_composer", "Command Composer", "Slash commands, tasks, and markdown shortcuts.", PerformanceCost.low, "standard")
        reg(ComponentId.bubble, "stock", "Classic WhatsApp", "", PerformanceCost.low, "standard", isDefault = true)
        reg(ComponentId.bubble, "ios_like", "iOS Smooth", "", PerformanceCost.low, "standard")
        reg(ComponentId.bubble, "modern", "Modern Rounded", "", PerformanceCost.low, "standard")
        reg(ComponentId.bubble, "minimal", "Minimal Hairline", "", PerformanceCost.low, "standard")
        reg(ComponentId.bubble, "angular_tail", "Angular Tail", "", PerformanceCost.low, "standard")
        reg(ComponentId.bubble, "threaded", "Threaded Spine", "", PerformanceCost.low, "standard")
        reg(ComponentId.bubble, "fold", "Fold Asymmetric", "", PerformanceCost.low, "standard")
        reg(ComponentId.bubble, "soft_block", "Soft Block", "", PerformanceCost.low, "standard")
        reg(ComponentId.bubble, "editorial", "Editorial Text First", "", PerformanceCost.low, "standard")
        reg(ComponentId.bubble, "pixel_grid", "Pixel Grid", "", PerformanceCost.low, "standard")
        reg(ComponentId.tick, "rc_ios_11", "RC iOS 11", "", PerformanceCost.low, "standard", isDefault = true)
        reg(ComponentId.tick, "green_tick", "Green Tick Classic", "", PerformanceCost.low, "standard")
        reg(ComponentId.tick, "bw_ticks", "B.W Ticks Minimal", "", PerformanceCost.low, "standard")
        reg(ComponentId.tick, "circle_print", "Circle Print", "", PerformanceCost.low, "standard")
        reg(ComponentId.tick, "split_arc", "Split Arc", "", PerformanceCost.low, "standard")
        reg(ComponentId.tick, "dot_pair", "Dot Pair", "", PerformanceCost.low, "standard")
        reg(ComponentId.tick, "diamond", "Diamond Status", "", PerformanceCost.low, "standard")
        reg(ComponentId.tick, "pulse_ring", "Pulse Ring", "", PerformanceCost.low, "standard")
        reg(ComponentId.tick, "bar", "Linear Bar", "", PerformanceCost.low, "standard")
        reg(ComponentId.tick, "glyph", "Micro Glyph", "", PerformanceCost.low, "standard")
        reg(ComponentId.modal, "center_dialog", "Center Dialog (10px Radius)", "", PerformanceCost.low, "standard", isDefault = true)
        reg(ComponentId.modal, "sheet", "Adaptive Bottom Sheet", "", PerformanceCost.low, "standard")
        reg(ComponentId.modal, "side_sheet", "Side Sheet (Expanded Layouts)", "", PerformanceCost.low, "standard")
        reg(ComponentId.modal, "action_sheet", "Action Sheet", "", PerformanceCost.low, "standard")
        reg(ComponentId.modal, "context_menu", "Anchored Context Menu", "", PerformanceCost.low, "standard")
    }

    companion object { val instance: ChatyComponentRegistry by lazy { ChatyComponentRegistry() } }
}
