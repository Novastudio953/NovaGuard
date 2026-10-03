package com.novaguard.checks

import com.novaguard.NovaGuard
import com.novaguard.check.Check
import com.novaguard.check.CheckType
import com.novaguard.data.PlayerData
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerMoveEvent

object PlayerChecks {

    fun registerAll(plugin: NovaGuard, register: (Check) -> Unit) {
        register(BadPackets(plugin)); register(InventoryMove(plugin)); register(PingSpoof(plugin))
    }

    /** BadPackets (A): impossible client data. */
    class BadPackets(plugin: NovaGuard) : Check("badpackets", "BadPackets", CheckType.PLAYER, plugin) {
        fun onMove(e: PlayerMoveEvent, p: Player, d: PlayerData) {
            val pitch = e.to.pitch
            if (pitch > 90.5f || pitch < -90.5f) {
                flag(p, d, "pitch=$pitch")
            }
            val yaw = e.to.yaw
            if (yaw.isNaN() || pitch.isNaN()) {
                flag(p, d, "NaN rotation")
            }
        }
    }

    /** Inventory (A): sprinting/moving while inventory is open. */
    class InventoryMove(plugin: NovaGuard) : Check("inventory", "InventoryMove", CheckType.PLAYER, plugin) {
        fun onMove(e: PlayerMoveEvent, p: Player, d: PlayerData) {
            if (!d.inventoryOpen) return
            val dist = Math.hypot(e.to.x - e.from.x, e.to.z - e.from.z)
            if (dist > 0.2 || p.isSprinting) {
                if (d.addInt("inv_c", 1) > 6) {
                    flag(p, d, "moving with inventory open")
                    d.setInt("inv_c", 0)
                }
            } else d.setInt("inv_c", 0)
        }
    }

    /** PingSpoof (A): impossible or manipulated ping values. */
    class PingSpoof(plugin: NovaGuard) : Check("pingspoof", "PingSpoof", CheckType.PLAYER, plugin) {
        fun onTick(p: Player, d: PlayerData) {
            val ping = try { p.ping } catch (_: Exception) { return }
            if (ping < 0) {
                flag(p, d, "ping=$ping")
                return
            }
            // perfectly frozen ping over a long window is a spoof tell
            val last = d.getInt("ps_last")
            if (ping == last && ping < 30) {
                if (d.addInt("ps_c", 1) > 600) { // 30s of identical low ping
                    flag(p, d, "frozen ping=${ping}ms")
                    d.setInt("ps_c", 0)
                }
            } else d.setInt("ps_c", 0)
            d.setInt("ps_last", ping)
        }
    }
}
