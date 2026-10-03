package com.novaguard.listener

import com.novaguard.NovaGuard
import com.novaguard.checks.BlockChecks
import com.novaguard.checks.PlayerChecks
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerItemConsumeEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerTeleportEvent

class PlayerListener(private val plugin: NovaGuard) : Listener {

    init {
        // ping-spoof sampler, every 3 seconds
        plugin.server.scheduler.runTaskTimerAsynchronously(plugin, Runnable {
            val check = plugin.checks.get("pingspoof") as? PlayerChecks.PingSpoof ?: return@Runnable
            if (!check.enabled) return@Runnable
            for (p in plugin.server.onlinePlayers) {
                check.onTick(p, plugin.data.get(p.uniqueId))
            }
        }, 60L, 60L)
    }

    @EventHandler
    fun onPreLogin(e: org.bukkit.event.player.AsyncPlayerPreLoginEvent) {
        if (!plugin.config.getBoolean("settings.vpn-block.enabled", false)) return
        val ip = e.address?.hostAddress ?: return
        if (plugin.config.getStringList("settings.vpn-block.exempt-ips").contains(ip)) return
        if (com.novaguard.vpn.VpnBlocker.isBlocked(ip)) {
            @Suppress("DEPRECATION")
            e.disallow(
                org.bukkit.event.player.AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                plugin.configs.msg("vpn-kick")
            )
            plugin.logger.info("[NovaGuard] Blocked VPN/proxy connection from $ip (${e.name})")
        }
    }

    @EventHandler
    fun onJoin(e: PlayerJoinEvent) {
        val d = plugin.data.get(e.player.uniqueId) // ensure entry
        // warm the Bedrock cache early so first checks don't pay lookup cost
        plugin.server.scheduler.runTaskLaterAsynchronously(plugin, Runnable {
            d.checkBedrock()
        }, 40L)
    }

    @EventHandler
    fun onQuit(e: PlayerQuitEvent) {
        plugin.punishments.clearCooldowns(e.player.uniqueId)
        plugin.data.remove(e.player.uniqueId)
    }

    @EventHandler(ignoreCancelled = true)
    fun onTeleport(e: PlayerTeleportEvent) {
        plugin.data.get(e.player.uniqueId).lastTeleport = System.currentTimeMillis()
    }

    @EventHandler
    fun onInventoryOpen(e: InventoryOpenEvent) {
        (e.player as? org.bukkit.entity.Player)?.let {
            plugin.data.get(it.uniqueId).inventoryOpen = true
        }
    }

    @EventHandler
    fun onInventoryClose(e: InventoryCloseEvent) {
        (e.player as? org.bukkit.entity.Player)?.let {
            val d = plugin.data.get(it.uniqueId)
            d.inventoryOpen = false
            d.setInt("inv_c", 0)
        }
    }

    @EventHandler
    fun onInteract(e: PlayerInteractEvent) {
        val item = e.item ?: return
        if (!item.type.isEdible) return
        val p = e.player
        (plugin.checks.get("fasteat") as? BlockChecks.FastEat)?.let {
            if (it.enabled) it.onEatStart(p, plugin.data.get(p.uniqueId))
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onConsume(e: PlayerItemConsumeEvent) {
        val p = e.player
        val d = plugin.data.get(p.uniqueId)
        if (d.isExempt(p)) return
        (plugin.checks.get("fasteat") as? BlockChecks.FastEat)?.let {
            if (it.enabled) it.onEat(p, d, e)
        }
    }
}
