package com.novaguard.checks

import com.novaguard.NovaGuard
import com.novaguard.check.Check
import com.novaguard.check.CheckType
import com.novaguard.data.PlayerData
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerMoveEvent
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Anti-macro wave: perfect-interval macros, click signatures,
 * AFK-evasion micro-movements, inventory bots, chat macros,
 * and hacked-client brand detection.
 */
object MacroChecks {

    fun registerAll(plugin: NovaGuard, register: (Check) -> Unit) {
        register(Macro(plugin)); register(ClickSignature(plugin))
        register(AfkMacro(plugin)); register(InventoryBot(plugin))
        register(ChatMacro(plugin)); register(ClientBrand(plugin))
    }

    /** Macro (A): actions repeating at mathematically perfect intervals. */
    class Macro(plugin: NovaGuard) : Check("macro", "Macro", CheckType.PLAYER, plugin) {
        fun onAction(p: Player, d: PlayerData) {
            val now = System.currentTimeMillis()
            val last = d.getLong("macro_last")
            if (last != 0L) {
                val interval = now - last
                if (interval in 30..5000) {
                    val n = d.getInt("macro_n")
                    val mn = if (n == 0) interval else min(d.getLong("macro_min"), interval)
                    val mx = if (n == 0) interval else max(d.getLong("macro_max"), interval)
                    d.setLong("macro_min", mn); d.setLong("macro_max", mx)
                    if (d.addInt("macro_n", 1) >= 30) {
                        if (mx - mn <= 3) flag(p, d, "perfect ${interval}ms interval")
                        d.setInt("macro_n", 0)
                    }
                } else d.setInt("macro_n", 0)
            }
            d.setLong("macro_last", now)
        }
    }

    /** ClickSignature (A): known auto-clicker timing signatures (exact 50/100/150ms). */
    class ClickSignature(plugin: NovaGuard) : Check("clicksignature", "ClickSignature", CheckType.PLAYER, plugin) {
        fun onAction(p: Player, d: PlayerData) {
            val now = System.currentTimeMillis()
            val last = d.getLong("csig_last")
            if (last != 0L) {
                val interval = now - last
                if (interval in 20..2000) {
                    val rounded = Math.round(interval / 50.0) * 50
                    if (interval >= 40 && abs(interval - rounded) <= 2) {
                        if (d.addInt("csig_n", 1) >= 40) {
                            flag(p, d, "signature ${rounded}ms clicks")
                            d.setInt("csig_n", 0)
                        }
                    } else d.setInt("csig_n", 0)
                }
            }
            d.setLong("csig_last", now)
        }
    }

    /** AFKMacro (A): periodic micro-movements used to dodge AFK kickers. */
    class AfkMacro(plugin: NovaGuard) : Check("afkmacro", "AFKMacro", CheckType.PLAYER, plugin) {
        fun onMove(e: PlayerMoveEvent, p: Player, d: PlayerData) {
            val dx = e.to.x - e.from.x; val dz = e.to.z - e.from.z
            val moved = dx * dx + dz * dz
            var dyaw = abs(e.to.yaw - d.getDouble("afk_yaw")) % 360
            if (dyaw > 180) dyaw = 360 - dyaw
            val hasLast = d.getLong("afk_set") != 0L
            if (hasLast && moved < 0.0025 && dyaw in 0.1..8.0) {
                val now = System.currentTimeMillis()
                val last = d.getLong("afk_last")
                if (last != 0L) {
                    val interval = now - last
                    if (interval in 200..30000) {
                        val n = d.getInt("afk_n")
                        val mn = if (n == 0) interval else min(d.getLong("afk_min"), interval)
                        val mx = if (n == 0) interval else max(d.getLong("afk_max"), interval)
                        d.setLong("afk_min", mn); d.setLong("afk_max", mx)
                        if (d.addInt("afk_n", 1) >= 15 && mx - mn <= 150) {
                            flag(p, d, "periodic micro-movement")
                            d.setInt("afk_n", 0)
                        }
                    }
                }
                d.setLong("afk_last", now)
            } else if (moved >= 0.0025) {
                d.setInt("afk_n", 0)
            }
            d.setDouble("afk_yaw", e.to.yaw.toDouble())
            d.setLong("afk_set", 1L)
        }
    }

    /** InventoryBot (A): identical click loops repeated in inventories. */
    class InventoryBot(plugin: NovaGuard) : Check("invbot", "InventoryBot", CheckType.PLAYER, plugin) {
        fun onClick(p: Player, d: PlayerData, slot: Int) {
            val s = slot.coerceIn(0, 63)
            val idx = d.getInt("invb_i")
            val pat = (d.getLong("invb_pat") shl 6) or s.toLong()
            d.setLong("invb_pat", pat)
            if (idx >= 7) {
                val window = pat and 0xFFFFFFFFFFFFL
                val prev = d.getLong("invb_prev")
                if (prev != 0L && window == prev) {
                    if (d.addInt("invb_rep", 1) >= 2) {
                        flag(p, d, "repeated click loop")
                        d.setInt("invb_rep", 0); d.setLong("invb_prev", 0L)
                    }
                } else {
                    d.setLong("invb_prev", window); d.setInt("invb_rep", 0)
                }
                d.setLong("invb_pat", 0L); d.setInt("invb_i", 0)
            } else d.setInt("invb_i", idx + 1)
        }
    }

    /** ChatMacro (A): identical messages/commands at fixed intervals. */
    class ChatMacro(plugin: NovaGuard) : Check("chatmacro", "ChatMacro", CheckType.PLAYER, plugin) {
        fun onChat(p: Player, d: PlayerData, message: String) {
            if (message.length < 4) return
            val now = System.currentTimeMillis()
            val h = message.hashCode().toLong()
            if (h == d.getLong("chatm_hash")) {
                val interval = now - d.getLong("chatm_last")
                if (interval in 1000..600000) {
                    val n = d.getInt("chatm_n")
                    val mn = if (n == 0) interval else min(d.getLong("chatm_min"), interval)
                    val mx = if (n == 0) interval else max(d.getLong("chatm_max"), interval)
                    d.setLong("chatm_min", mn); d.setLong("chatm_max", mx)
                    if (d.addInt("chatm_n", 1) >= 4 && mx - mn <= 3000) {
                        val count = d.getInt("chatm_n")
                        // chat events are async — flag on the main thread
                        plugin.server.scheduler.runTask(plugin, Runnable {
                            if (p.isOnline) flag(p, d, "repeated message x$count")
                        })
                        d.setInt("chatm_n", 0)
                    }
                }
            } else d.setInt("chatm_n", 0)
            d.setLong("chatm_hash", h)
            d.setLong("chatm_last", now)
        }
    }

    /** ClientBrand (A): flags known hacked-client brands on the brand channel. */
    class ClientBrand(plugin: NovaGuard) : Check("clientbrand", "ClientBrand", CheckType.PLAYER, plugin) {
        fun onBrand(p: Player, d: PlayerData, brand: String) {
            d.clientBrand = brand
            val blocked = plugin.configs.checks.getStringList("checks.clientbrand.blocked")
            val lower = brand.lowercase()
            for (b in blocked) {
                if (b.isNotBlank() && lower.contains(b.lowercase())) {
                    flag(p, d, "brand=$brand")
                    return
                }
            }
        }
    }
}
