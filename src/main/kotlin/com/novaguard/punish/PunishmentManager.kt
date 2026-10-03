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
    }

    fun execute(player: Player, check: Check, template: String, vl: Double, isBan: Boolean = false) {
        val key = "${player.uniqueId}:${check.id}"
        val now = System.currentTimeMillis()
        if (now - (cooldowns[key] ?: 0L) < cooldownSeconds * 1000) return
        cooldowns[key] = now

        // ban-wave mode: defer bans into the wave list instead of banning now
        if (isBan && plugin.config.getBoolean("settings.ban-wave-mode", false)) {
            plugin.staff.waveList.add(player.uniqueId)
            player.kickPlayer(plugin.configs.msg("wave-kick"))
            plugin.logger.info("[PUNISH] ${player.name} added to ban wave (${check.displayName})")
            com.novaguard.staff.DiscordHook.send(plugin,
                plugin.configs.msg("wave-add-discord", "player" to player.name,
                    "check" to check.displayName, "vl" to "%.1f".format(vl)))
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
        com.novaguard.staff.DiscordHook.send(plugin,
            plugin.configs.msg("punishment-discord", "player" to player.name, "command" to command))

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
