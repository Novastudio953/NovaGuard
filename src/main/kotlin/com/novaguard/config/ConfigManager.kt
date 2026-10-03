package com.novaguard.config

import com.novaguard.NovaGuard
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

/**
 * Multi-file configuration: config.yml (settings), checks.yml,
 * messages.yml, xray.yml. Each ships as a real default resource.
 */
class ConfigManager(private val plugin: NovaGuard) {

    lateinit var checks: FileConfiguration
        private set
    lateinit var messages: FileConfiguration
        private set
    lateinit var xray: FileConfiguration
        private set

    fun load() {
        checks = loadFile("checks.yml")
        messages = loadFile("messages.yml")
        xray = loadFile("xray.yml")
    }

    private fun loadFile(name: String): FileConfiguration {
        val file = File(plugin.dataFolder, name)
        if (!file.exists()) plugin.saveResource(name, false)
        return YamlConfiguration.loadConfiguration(file)
    }

    /** Premium message lookup with {prefix} + variable replacement. */
    fun msg(key: String, vararg vars: Pair<String, String>): String {
        var s = messages.getString(key, key) ?: key
        val prefix = messages.getString("prefix", "") ?: ""
        s = s.replace("{prefix}", prefix)
        for ((k, v) in vars) s = s.replace("{$k}", v)
        return s.replace('&', '§')
    }

    fun saveChecks() {
        checks.save(File(plugin.dataFolder, "checks.yml"))
    }
}
