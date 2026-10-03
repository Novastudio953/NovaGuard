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

    fun execute(player: Player, check: Check, template: String, vl: Double) {
        val key = "${player.uniqueId}:${check.id}"
        val now = System.currentTimeMillis()
        if (now - (cooldowns[key] ?: 0L) < cooldownSeconds * 1000) return
        cooldowns[key] = now

        val command = template
            .replace("{player}", player.name)
            .replace("{uuid}", player.uniqueId.toString())
            .replace("{check}", check.displayName)
            .replace("{vl}", "%.1f".format(vl))

        plugin.logger.info("[PUNISH] ${player.name}: $command")
        Bukkit.getScheduler().runTask(plugin, Runnable {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)
        })

        if (broadcastPunishments) {
            val msg = plugin.config.getString("settings.punishment-broadcast",
                "&8[&cNovaGuard&8] &f{player} &7was punished for &c{check}")!!
                .replace("{player}", player.name)
                .replace("{check}", check.displayName)
            // strip color codes simply
            Bukkit.broadcastMessage(msg.replace(Regex("&[0-9a-fk-or]"), ""))
        }
    }

    fun clearCooldowns(uuid: UUID) {
        cooldowns.keys.removeIf { it.startsWith(uuid.toString()) }
    }
}
