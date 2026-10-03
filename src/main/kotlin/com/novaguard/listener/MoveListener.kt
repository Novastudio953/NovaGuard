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
        val p = e.player
        val d = plugin.data.get(p.uniqueId)
        if (d.frozen) {
            // allow looking around, block position changes
            if (e.from.x != to.x || e.from.y != to.y || e.from.z != to.z) e.isCancelled = true
            return
        }
        if (e.from.x == to.x && e.from.y == to.y && e.from.z == to.z) {
            // still run rotation-only checks
            val derp = plugin.checks.get("derp") as? com.novaguard.checks.ExtraChecks.Derp
            if (derp?.enabled == true && !d.isExempt(p)) derp.onMove(e, p, d)
            val afk = plugin.checks.get("afkmacro") as? com.novaguard.checks.MacroChecks.AfkMacro
            if (afk?.enabled == true && !d.isExempt(p)) afk.onMove(e, p, d)
            return
        }
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
        // wave-2 movement/player checks
        (plugin.checks.get("noslow") as? com.novaguard.checks.ExtraChecks.NoSlow)?.let {
            if (it.enabled) it.onMove(e, p, d)
        }
        (plugin.checks.get("sprintcheck") as? com.novaguard.checks.ExtraChecks.SprintCheck)?.let {
            if (it.enabled) it.onMove(e, p, d)
        }
        (plugin.checks.get("boatfly") as? com.novaguard.checks.ExtraChecks.BoatFly)?.let {
            if (it.enabled) it.onMove(e, p, d, maxPing)
        }
        (plugin.checks.get("derp") as? com.novaguard.checks.ExtraChecks.Derp)?.let {
            if (it.enabled) it.onMove(e, p, d)
        }
        (plugin.checks.get("baritone") as? com.novaguard.checks.ExtraChecks.Baritone)?.let {
            if (it.enabled) it.onMove(e, p, d)
        }
        (plugin.checks.get("afkmacro") as? com.novaguard.checks.MacroChecks.AfkMacro)?.let {
            if (it.enabled) it.onMove(e, p, d)
        }
    }
}
