package com.novaguard.punish

import com.novaguard.NovaGuard
import com.novaguard.check.Check
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class PunishmentManager(private val plugin: NovaGuard) {

    private val cooldowns = ConcurrentHashMap<String, Long>()
    var cooldownSeconds: Long = 30
    var broadcastPunishments: Boolean = true

    fun load() {
        cooldownSeconds = plugin.config.getLong("settings.punishment-cooldown-seconds", 30)
        broadcastPunishments = plugin.config.getBoolean("settings.broadcast-punishments", true)
        plugin.lagShieldEnabled = plugin.config.getBoolean("settings.lag-shield.enabled", true)
        plugin.lagShieldThreshold = plugin.config.getDouble("settings.lag-shield.tps-threshold", 18.0)
    }

    fun execute(player: Player, check: Check, template: String, vl: Double, isBan: Boolean = false) {
        val key = "${player.uniqueId}:${check.id}"
        val now = System.currentTimeMillis()
        if (now - (cooldowns[key] ?: 0L) < cooldownSeconds * 1000) return
        cooldowns[key] = now

        // lag shield: never punish while the server itself is lagging
        if (plugin.lagShieldEnabled) {
            val tps = try { Bukkit.getTPS()[0] } catch (_: Exception) { 20.0 }
            if (tps < plugin.lagShieldThreshold) {
                plugin.logger.info("[LAG-SHIELD] Punishment for ${player.name} (${check.displayName}) skipped — TPS ${"%.1f".format(tps)}")
                return
            }
        }

        // ban-wave mode: defer bans into the wave list instead of banning now
        if (isBan && plugin.config.getBoolean("settings.ban-wave-mode", false)) {
            plugin.staff.waveList.add(player.uniqueId)
            player.kickPlayer(plugin.configs.msg("wave-kick"))
            plugin.logger.info("[PUNISH] ${player.name} added to ban wave (${check.displayName})")
            val L = plugin.configs
            com.novaguard.staff.DiscordHook.sendEmbed(plugin,
                L.msg("discord-embed-title-queue"), 0xF5A623, listOf(
                    Triple(L.msg("discord-embed-player"), player.name, true),
                    Triple(L.msg("discord-embed-check"), check.displayName, true),
                    Triple(L.msg("discord-embed-vl"), "%.1f".format(vl), true)
                ))
            return
        }

        val command = template
            .replace("{player}", player.name)
            .replace("{uuid}", player.uniqueId.toString())
            .replace("{check}", check.displayName)
            .replace("{vl}", "%.1f".format(vl))

        plugin.logger.info("[PUNISH] ${player.name}: $command")
        Bukkit.getScheduler().runTask(plugin, Runnable {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)
        })
        val L = plugin.configs
        val color = if (isBan) 0xE74C3C else 0xF5A623
        com.novaguard.staff.DiscordHook.sendEmbed(plugin,
            L.msg("discord-embed-title-punish"), color, listOf(
                Triple(L.msg("discord-embed-player"), player.name, true),
                Triple(L.msg("discord-embed-check"), check.displayName, true),
                Triple(L.msg("discord-embed-vl"), "%.1f".format(vl), true),
                Triple(L.msg("discord-embed-command"), "`$command`", false)
            ))

        if (broadcastPunishments) {
            val msg = plugin.configs.msg("punishment-broadcast",
                "player" to player.name, "check" to check.displayName)
            Bukkit.broadcastMessage(msg)
        }
    }

    fun clearCooldowns(uuid: UUID) {
        cooldowns.keys.removeIf { it.startsWith(uuid.toString()) }
    }
}
