package com.novaguard.listener

import com.novaguard.NovaGuard
import com.novaguard.checks.BlockChecks
import com.novaguard.checks.MovementChecks
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent

class BlockListener(private val plugin: NovaGuard) : Listener {

    @EventHandler(ignoreCancelled = true)
    fun onPlace(e: BlockPlaceEvent) {
        val p = e.player
        val d = plugin.data.get(p.uniqueId)
        if (d.isExempt(p)) return
        (plugin.checks.get("fastplace") as? BlockChecks.FastPlace)?.let {
            if (it.enabled) it.onPlace(p, d, e)
        }
        (plugin.checks.get("scaffold") as? MovementChecks.ScaffoldMove)?.let {
            if (it.enabled) it.onPlace(p, d)
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onBreak(e: BlockBreakEvent) {
        val p = e.player
        val d = plugin.data.get(p.uniqueId)
        if (d.isExempt(p)) return
        (plugin.checks.get("fastbreak") as? BlockChecks.FastBreak)?.let {
            if (it.enabled) it.onBreak(p, d, e)
        }
        (plugin.checks.get("nuker") as? BlockChecks.Nuker)?.let {
            if (it.enabled) it.onBreak(p, d)
        }
    }
}
