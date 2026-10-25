import com.example.chat.ui.settings.registry.*
fun main() {
    check(SettingsRegistry.validateInvariants())
    check(SettingsRegistry.allSettings.size == 39) { SettingsRegistry.allSettings.size }
    check(SettingsRegistry.clusters.size == 18)
    check(SettingsRegistry.allSettings.first().id == "account_display_name")
    check(SettingsCategory.values().size == 13)
    println("OK " + SettingsRegistry.allSettings.map { it.defaultValue }.count { it == true })
}
