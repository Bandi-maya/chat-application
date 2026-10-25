package com.example.chat.ui.settings.appicon

/** Pure activity-alias switch plan for the Dart MethodChannel
 *  'chaty/launcher_icon' contract (lib/ui/core/controllers/
 *  app_icon_controller.dart). Kept free of android.* types so the full
 *  transition table is JVM-testable; AndroidLauncherIconBridge applies it
 *  through PackageManager.
 *
 *  Identity-preserving model:
 *  - .MainActivity keeps its LAUNCHER intent-filter and represents the
 *    currently installed icon. It maps to the Dart default variant 'bird'
 *    (app_icon_controller.dart: `_launcherIcon = LauncherIconVariant.bird`).
 *  - The 7 variants each get an activity-alias component
 *    (<pkg>.launcher.<Variant>IconAlias), all android:enabled="false" in the
 *    manifest. Switching enables exactly one alias and disables the rest;
 *    selecting 'bird' disables all aliases and re-enables .MainActivity.
 *  - No applicationId/package/signing change; switches use
 *    PackageManager.DONT_KILL_APP. */
object LauncherIconAliasPlan {

    /** Dart LauncherIconVariant names in declaration order. */
    val ALIASES: List<String> = listOf(
        "bird", "warm", "outline", "obsidian", "glass", "signal", "fold",
    )

    /** The variant the installed .MainActivity launcher entry represents. */
    const val DEFAULT_ALIAS = "bird"

    const val MAIN_ACTIVITY = "MainActivity"

    fun isValidAlias(alias: String): Boolean = alias in ALIASES

    fun aliasComponent(packageName: String, alias: String): String =
        packageName + ".launcher." + alias.replaceFirstChar { it.uppercase() } + "IconAlias"

    fun mainActivityComponent(packageName: String): String =
        "$packageName.$MAIN_ACTIVITY"

    /** Every component this plan manages: aliases + .MainActivity. */
    fun managedComponents(packageName: String): List<String> =
        ALIASES.map { aliasComponent(packageName, it) } + mainActivityComponent(packageName)

    /** component -> desired enabled state for a setLauncherIcon(alias) call.
     *  Exactly one entry is enabled. */
    fun transitions(packageName: String, requested: String): Map<String, Boolean> {
        require(isValidAlias(requested)) { "Unknown launcher icon alias: $requested" }
        val states = LinkedHashMap<String, Boolean>()
        for (alias in ALIASES) {
            // The .MainActivity entry IS the default (bird) icon, so the bird
            // alias is never enabled: selecting 'bird' means MainActivity
            // enabled + every alias disabled.
            states[aliasComponent(packageName, alias)] =
                (alias == requested && requested != DEFAULT_ALIAS)
        }
        states[mainActivityComponent(packageName)] = (requested == DEFAULT_ALIAS)
        return states
    }

    /** Reads the active alias from component enabled states: the enabled
     *  alias, or [DEFAULT_ALIAS] when none is enabled (the .MainActivity
     *  entry is the active one). */
    fun currentAlias(packageName: String, enabledComponents: Set<String>): String {
        for (alias in ALIASES) {
            if (aliasComponent(packageName, alias) in enabledComponents) return alias
        }
        return DEFAULT_ALIAS
    }
}
