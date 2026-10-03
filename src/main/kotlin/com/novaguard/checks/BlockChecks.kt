package com.novaguard.checks

import com.novaguard.NovaGuard
import com.novaguard.check.Check
import com.novaguard.check.CheckType
import com.novaguard.data.PlayerData
import org.bukkit.entity.Player
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.player.PlayerItemConsumeEvent

object BlockChecks {

    fun registerAll(plugin: NovaGuard, register: (Check) -> Unit) {
        register(FastPlace(plugin)); register(FastBreak(plugin))
        register(Nuker(plugin)); register(FastEat(plugin))
    }

    /** FastPlace (A): placing blocks faster than possible. */
    class FastPlace(plugin: NovaGuard) : Check("fastplace", "FastPlace", CheckType.BLOCK, plugin) {
        fun onPlace(p: Player, d: PlayerData, e: BlockPlaceEvent) {
            val now = System.currentTimeMillis()
            val window = d.getLong("fp_window")
            if (now - window > 1000) {
                val count = d.getInt("fp_count")
                if (count > 8 && window != 0L) flag(p, d, "$count/s")
                d.setLong("fp_window", now); d.setInt("fp_count", 1)
            } else d.addInt("fp_count", 1)
        }
    }

    /** FastBreak (A): breaking blocks faster than possible. */
    class FastBreak(plugin: NovaGuard) : Check("fastbreak", "FastBreak", CheckType.BLOCK, plugin) {
        fun onBreak(p: Player, d: PlayerData, e: BlockBreakEvent) {
            val now = System.currentTimeMillis()
            val window = d.getLong("fb_window")
            if (now - window > 1000) {
                val count = d.getInt("fb_count")
                if (count > 10 && window != 0L) flag(p, d, "$count/s")
                d.setLong("fb_window", now); d.setInt("fb_count", 1)
            } else d.addInt("fb_count", 1)
        }
    }

    /** Nuker (A): breaking multiple blocks in a single tick. */
    class Nuker(plugin: NovaGuard) : Check("nuker", "Nuker", CheckType.BLOCK, plugin) {
        fun onBreak(p: Player, d: PlayerData) {
            val tick = p.world.gameTime
            val lastTick = d.getLong("nuker_tick")
            if (tick == lastTick) {
                if (d.addInt("nuker_c", 1) >= 3) {
                    flag(p, d, "${d.getInt("nuker_c")} blocks in one tick")
                }
            } else {
                d.setLong("nuker_tick", tick)
                d.setInt("nuker_c", 1)
            }
        }
    }

    /** FastEat (A): consuming food faster than the eat animation. */
    class FastEat(plugin: NovaGuard) : Check("fasteat", "FastEat", CheckType.BLOCK, plugin) {
        fun onEatStart(p: Player, d: PlayerData) {
            d.setLong("fe_start", System.currentTimeMillis())
        }
        fun onEat(p: Player, d: PlayerData, e: PlayerItemConsumeEvent) {
            val start = d.getLong("fe_start")
            if (start == 0L) return
            val took = System.currentTimeMillis() - start
            if (took < 1200) {
                if (d.addInt("fe_c", 1) > 2) {
                    flag(p, d, "ate in ${took}ms")
                    d.setInt("fe_c", 0)
                }
            } else d.setInt("fe_c", 0)
            d.setLong("fe_start", 0L)
        }
    }
}
