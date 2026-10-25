package com.example.chat.core.customization

/** Port of customization_migration.dart. */
object CustomizationMigration {
    fun migrate(rawJson: Map<String, Any?>): CustomizationSnapshot {
        val version = (rawJson["schemaVersion"] as? Int) ?: 1
        val current = LinkedHashMap(rawJson)
        if (version < 1) {
            current["schemaVersion"] = 1
            current.putIfAbsent("globalThemeId", "chaty_emerald")
            current.putIfAbsent("navigationVariant", "classic_label_bar")
            current.putIfAbsent("headerVariant", "standard_identity")
            current.putIfAbsent("composerVariant", "chaty_capsule")
            current.putIfAbsent("bubbleVariant", "stock")
            current.putIfAbsent("tickVariant", "rc_ios_11")
        }
        return CustomizationSnapshot.fromJson(current)
    }
}
