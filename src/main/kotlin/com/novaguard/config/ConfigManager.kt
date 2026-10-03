package com.novaguard.config

import com.novaguard.NovaGuard
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

/**
 * Multi-file configuration: config.yml (settings), checks.yml,
 * messages.yml, xray.yml. Each ships as a real default resource.
 *
 * Multi-language: settings.language selects languages/<lang>.yml
 * (en/es/tl ship with the jar). Missing keys fall back to messages.yml.
 */
class ConfigManager(private val plugin: NovaGuard) {

    lateinit var checks: FileConfiguration
        private set
    lateinit var messages: FileConfiguration
        private set
    lateinit var xray: FileConfiguration
        private set
    private var lang: FileConfiguration? = null
    var language: String = "en"
        private set

    fun load() {
        checks = loadFile("checks.yml")
        messages = loadFile("messages.yml")
        xray = loadFile("xray.yml")
        loadLanguage()
    }

    private fun loadLanguage() {
        language = plugin.config.getString("settings.language", "en")!!
            .lowercase().take(8)
        lang = null
        if (language != "en") {
            val file = File(plugin.dataFolder, "languages/$language.yml")
            if (!file.exists()) {
                // ship defaults for bundled languages
                try {
                    plugin.saveResource("languages/$language.yml", false)
                } catch (_: Exception) { }
            }
            if (file.exists()) lang = YamlConfiguration.loadConfiguration(file)
            if (lang == null) {
                plugin.logger.warning("NovaGuard: language '$language' not found, falling back to English.")
                language = "en"
            }
        }
    }

    /** All bundled language codes (en = default, built into messages.yml). */
    fun availableLanguages(): List<String> = listOf(
        "en", "es", "tl", "fr", "de", "pt", "ru", "zh", "ja", "ko",
        "ar", "hi", "id", "it", "nl", "pl", "tr", "uk", "vi", "th", "ms"
    )

    /** Switch language at runtime and persist to config.yml. */
    fun setLanguage(code: String): Boolean {
        val c = code.lowercase()
        if (c !in availableLanguages()) return false
        plugin.config.set("settings.language", c)
        plugin.saveConfig()
        loadLanguage()
        return true
    }

    private fun loadFile(name: String): FileConfiguration {
        val file = File(plugin.dataFolder, name)
        if (!file.exists()) plugin.saveResource(name, false)
        return YamlConfiguration.loadConfiguration(file)
    }

    /** Premium message lookup with {prefix} + variable replacement. */
    fun msg(key: String, vararg vars: Pair<String, String>): String {
        var s = lang?.getString(key) ?: messages.getString(key, key) ?: key
        val prefix = lang?.getString("prefix") ?: messages.getString("prefix", "") ?: ""
        s = s.replace("{prefix}", prefix)
        for ((k, v) in vars) s = s.replace("{$k}", v)
        return s.replace('&', '§')
    }

    fun saveChecks() {
        checks.save(File(plugin.dataFolder, "checks.yml"))
    }
}
