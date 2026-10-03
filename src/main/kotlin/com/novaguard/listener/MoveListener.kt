package com.novaguard.listener

import com.novaguard.NovaGuard
import com.novaguard.checks.MovementChecks
import com.novaguard.checks.PlayerChecks
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerMoveEvent

class MoveListener(private val plugin: NovaGuard) : Listener {

    @EventHandler(ignoreCancelled = true)
    fun onMove(e: PlayerMoveEvent) {
        val to = e.to ?: return
        if (e.from.x == to.x && e.from.y == to.y && e.from.z == to.z) return
        val p = e.player
        val d = plugin.data.get(p.uniqueId)
        if (d.isExempt(p)) return
        val maxPing = plugin.config.getInt("settings.lag-compensation-max-ping", 350)

        for (check in plugin.checks.byType(com.novaguard.check.CheckType.MOVEMENT)) {
            if (!check.enabled) continue
            when (check) {
                is MovementChecks.Fly -> check.onMove(e, p, d, maxPing)
                is MovementChecks.Speed -> check.onMove(e, p, d, maxPing)
                is MovementChecks.Timer -> check.onMove(e, p, d, maxPing)
                is MovementChecks.Jesus -> check.onMove(e, p, d, maxPing)
                is MovementChecks.Step -> check.onMove(e, p, d)
                is MovementChecks.NoFall -> check.onMove(e, p, d)
                is MovementChecks.Spider -> check.onMove(e, p, d, maxPing)
                is MovementChecks.ScaffoldMove -> check.onMove(e, p, d)
                is MovementChecks.Phase -> check.onMove(e, p, d)
                is MovementChecks.ElytraCheck -> check.onMove(e, p, d, maxPing)
            }
        }
        val bad = plugin.checks.get("badpackets") as? PlayerChecks.BadPackets
        if (bad?.enabled == true) bad.onMove(e, p, d)
        val inv = plugin.checks.get("inventory") as? PlayerChecks.InventoryMove
        if (inv?.enabled == true) inv.onMove(e, p, d)
    }
}
