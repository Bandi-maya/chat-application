package com.example.chat.core.customization

import org.json.JSONObject

/** Port of customization_snapshot.dart. Deliberately a plain class (NOT a data class): the Dart class defines
 *  no ==, so equality is identity and CustomizationController.isDirty / CustomizationHistory.record's
 *  `previous == next` guard behave as reference comparisons. */
class CustomizationSnapshot(
    val schemaVersion: Int = currentSchemaVersion,
    val globalThemeId: String = "chaty_emerald",
    val navigationVariant: String = "classic_label_bar",
    val headerVariant: String = "standard_identity",
    val composerVariant: String = "chaty_capsule",
    val bubbleVariant: String = "stock",
    val tickVariant: String = "rc_ios_11",
    val chatListVariant: String = "spacious_content_first",
    val reactionVariant: String = "standard_rail",
    val popupVariant: String = "sheet",
    val buttonVariant: String = "standard_filled",
    val textFieldVariant: String = "standard_outlined",
    val avatarVariant: String = "circle",
    val wallpaperId: String = "default_solid",
    val motionProfile: String = "standard",
    val densityProfile: String = "comfortable",
    val screenOverrides: Map<String, Map<String, String>> = emptyMap(),
    val conversationOverrides: Map<String, Map<String, String>> = emptyMap(),
    val metadata: Map<String, Any?> = emptyMap(),
) {
    fun copyWith(
        schemaVersion: Int? = null, globalThemeId: String? = null, navigationVariant: String? = null,
        headerVariant: String? = null, composerVariant: String? = null, bubbleVariant: String? = null,
        tickVariant: String? = null, chatListVariant: String? = null, reactionVariant: String? = null,
        popupVariant: String? = null, buttonVariant: String? = null, textFieldVariant: String? = null,
        avatarVariant: String? = null, wallpaperId: String? = null, motionProfile: String? = null,
        densityProfile: String? = null, screenOverrides: Map<String, Map<String, String>>? = null,
        conversationOverrides: Map<String, Map<String, String>>? = null, metadata: Map<String, Any?>? = null,
    ) = CustomizationSnapshot(
        schemaVersion ?: this.schemaVersion, globalThemeId ?: this.globalThemeId,
        navigationVariant ?: this.navigationVariant, headerVariant ?: this.headerVariant,
        composerVariant ?: this.composerVariant, bubbleVariant ?: this.bubbleVariant,
        tickVariant ?: this.tickVariant, chatListVariant ?: this.chatListVariant,
        reactionVariant ?: this.reactionVariant, popupVariant ?: this.popupVariant,
        buttonVariant ?: this.buttonVariant, textFieldVariant ?: this.textFieldVariant,
        avatarVariant ?: this.avatarVariant, wallpaperId ?: this.wallpaperId,
        motionProfile ?: this.motionProfile, densityProfile ?: this.densityProfile,
        screenOverrides ?: this.screenOverrides, conversationOverrides ?: this.conversationOverrides,
        metadata ?: this.metadata,
    )

    fun toJson(): Map<String, Any?> = linkedMapOf(
        "schemaVersion" to schemaVersion, "globalThemeId" to globalThemeId,
        "navigationVariant" to navigationVariant, "headerVariant" to headerVariant,
        "composerVariant" to composerVariant, "bubbleVariant" to bubbleVariant, "tickVariant" to tickVariant,
        "chatListVariant" to chatListVariant, "reactionVariant" to reactionVariant,
        "popupVariant" to popupVariant, "buttonVariant" to buttonVariant,
        "textFieldVariant" to textFieldVariant, "avatarVariant" to avatarVariant, "wallpaperId" to wallpaperId,
        "motionProfile" to motionProfile, "densityProfile" to densityProfile,
        "screenOverrides" to screenOverrides, "conversationOverrides" to conversationOverrides,
        "metadata" to metadata,
    )

    fun encode(): String = JSONObject(toJson()).toString()

    companion object {
        const val currentSchemaVersion = 1
        val defaultSnapshot = CustomizationSnapshot()

        private fun parseNestedMap(raw: Any?): Map<String, Map<String, String>> {
            if (raw !is Map<*, *>) return emptyMap()
            val result = LinkedHashMap<String, Map<String, String>>()
            for ((k, v) in raw) {
                if (v is Map<*, *>) result[k.toString()] = v.entries.associate { it.key.toString() to it.value.toString() }
            }
            return result
        }

        /** Dart `json[k] as String? ?? d`: a non-String value would throw in Dart; decode() swallows that to null. */
        private fun str(json: Map<String, Any?>, key: String, d: String): String {
            val v = json[key] ?: return d
            return v as? String ?: throw ClassCastException(key)
        }

        fun fromJson(json: Map<String, Any?>): CustomizationSnapshot {
            val sv = json["schemaVersion"]
            val md = json["metadata"]
            @Suppress("UNCHECKED_CAST")
            return CustomizationSnapshot(
                schemaVersion = if (sv == null) currentSchemaVersion else (sv as? Int ?: throw ClassCastException("schemaVersion")),
                globalThemeId = str(json, "globalThemeId", "chaty_emerald"),
                navigationVariant = str(json, "navigationVariant", "classic_label_bar"),
                headerVariant = str(json, "headerVariant", "standard_identity"),
                composerVariant = str(json, "composerVariant", "chaty_capsule"),
                bubbleVariant = str(json, "bubbleVariant", "stock"),
                tickVariant = str(json, "tickVariant", "rc_ios_11"),
                chatListVariant = str(json, "chatListVariant", "spacious_content_first"),
                reactionVariant = str(json, "reactionVariant", "standard_rail"),
                popupVariant = str(json, "popupVariant", "sheet"),
                buttonVariant = str(json, "buttonVariant", "standard_filled"),
                textFieldVariant = str(json, "textFieldVariant", "standard_outlined"),
                avatarVariant = str(json, "avatarVariant", "circle"),
                wallpaperId = str(json, "wallpaperId", "default_solid"),
                motionProfile = str(json, "motionProfile", "standard"),
                densityProfile = str(json, "densityProfile", "comfortable"),
                screenOverrides = parseNestedMap(json["screenOverrides"]),
                conversationOverrides = parseNestedMap(json["conversationOverrides"]),
                metadata = if (md is Map<*, *>) md as Map<String, Any?> else emptyMap(),
            )
        }

        /** Null for null/empty/malformed input (Dart swallows every parse error). */
        fun decode(raw: String?): CustomizationSnapshot? {
            if (raw.isNullOrEmpty()) return null
            return try {
                fromJson(unwrap(JSONObject(raw)) as Map<String, Any?>)
            } catch (_: Exception) {
                null
            }
        }

        @Suppress("UNCHECKED_CAST")
        internal fun unwrap(v: Any?): Any? = when (v) {
            JSONObject.NULL -> null
            is JSONObject -> v.keys().asSequence().associateWith { unwrap(v.get(it)) }
            is org.json.JSONArray -> (0 until v.length()).map { unwrap(v.get(it)) }
            else -> v
        }
    }
}
