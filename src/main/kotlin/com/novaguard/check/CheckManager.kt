package com.novaguard.check

import com.novaguard.NovaGuard
import com.novaguard.checks.AdvancedChecks
import com.novaguard.checks.BlockChecks
import com.novaguard.checks.CombatChecks
import com.novaguard.checks.ExtraChecks
import com.novaguard.checks.MacroChecks
import com.novaguard.checks.MovementChecks
import com.novaguard.checks.PlayerChecks

class CheckManager(private val plugin: NovaGuard) {

    private val checks = mutableMapOf<String, Check>()
    val all: Collection<Check> get() = checks.values

    init {
        MovementChecks.registerAll(plugin, ::register)
        CombatChecks.registerAll(plugin, ::register)
        BlockChecks.registerAll(plugin, ::register)
        PlayerChecks.registerAll(plugin, ::register)
        ExtraChecks.registerAll(plugin, ::register)
        MacroChecks.registerAll(plugin, ::register)
        AdvancedChecks.registerAll(plugin, ::register)
        plugin.xray.registerHook(::register)
    }

    private fun register(check: Check) {
        checks[check.id.lowercase()] = check
    }

    fun get(id: String): Check? = checks[id.lowercase()]

    fun byType(type: CheckType): List<Check> = checks.values.filter { it.type == type }

    /** Load per-check toggles from checks.yml, Vulcan-style. */
    fun load() {
        val cfg = plugin.configs.checks
        for (check in checks.values) {
            val path = "checks.${check.id}"
            check.enabled = cfg.getBoolean("$path.enabled", true)
            check.maxVl = cfg.getDouble("$path.max-vl", 10.0)
            check.punishment = cfg.getString("$path.punishment", check.punishment)!!
            check.banCommand = cfg.getString("$path.ban-command", check.banCommand)!!
            check.banVlMultiplier = cfg.getDouble("$path.ban-vl-multiplier", 3.0)
        }
        plugin.bedrockLeniency = plugin.config.getBoolean("bedrock.enabled", true)
        plugin.bedrockExempt = plugin.config.getStringList("bedrock.exempt-checks")
            .map { it.lowercase() }.toSet()
        plugin.graceEnabled = plugin.config.getBoolean("settings.grace-period.enabled", true)
        plugin.graceMinutes = plugin.config.getLong("settings.grace-period.minutes", 60)
        plugin.graceMultiplier = plugin.config.getDouble("settings.grace-period.vl-multiplier", 2.0)
    }
}
