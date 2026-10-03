package com.novaguard.checks

import com.novaguard.NovaGuard
import com.novaguard.check.Check
import com.novaguard.check.CheckType
import com.novaguard.data.PlayerData
import org.bukkit.Material
import org.bukkit.entity.Boat
import org.bukkit.entity.Player
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityResurrectEvent
import org.bukkit.event.entity.EntityShootBowEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerEditBookEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.inventory.meta.BookMeta

/**
 * Wave 2 checks: CrystalAura, AutoTotem, FastBow, NoSlow, Sprint,
 * BoatFly, ChestStealer, InventoryClicker, GhostHand, Derp, Baritone,
 * BookBan, IllegalItems.
 */
object ExtraChecks {

    fun registerAll(plugin: NovaGuard, register: (Check) -> Unit) {
        register(CrystalAura(plugin)); register(AutoTotem(plugin)); register(FastBow(plugin))
        register(NoSlow(plugin)); register(SprintCheck(plugin)); register(BoatFly(plugin))
        register(ChestStealer(plugin)); register(InventoryClicker(plugin))
        register(GhostHand(plugin)); register(Derp(plugin)); register(Baritone(plugin))
        register(BookBan(plugin)); register(IllegalItems(plugin))
    }

    /** CrystalAura (A): placing end crystals inhumanly fast. */
    class CrystalAura(plugin: NovaGuard) : Check("crystalaura", "CrystalAura", CheckType.COMBAT, plugin) {
        fun onPlace(p: Player, d: PlayerData) {
            val now = System.currentTimeMillis()
            if (now - d.getLong("ca_window") > 1000) {
                d.setLong("ca_window", now); d.setInt("ca_c", 1)
            } else if (d.addInt("ca_c", 1) > 4) {
                flag(p, d, "${d.getInt("ca_c")} crystals/s")
                d.setInt("ca_c", 0)
            }
        }
    }

    /** AutoTotem (A): totem equipped suspiciously fast before fatal damage. */
    class AutoTotem(plugin: NovaGuard) : Check("autototem", "AutoTotem", CheckType.COMBAT, plugin) {
        fun onTotemEquip(d: PlayerData) { d.setLong("at_equip", System.currentTimeMillis()) }
        fun onPop(p: Player, d: PlayerData, e: EntityResurrectEvent) {
            val since = System.currentTimeMillis() - d.getLong("at_equip")
            if (since in 1..500) {
                if (d.addInt("at_c", 1) > 2) {
                    flag(p, d, "totem equipped ${since}ms before pop")
                    d.setInt("at_c", 0)
                }
            } else d.setInt("at_c", 0)
        }
    }

    /** FastBow (A): shooting arrows faster than the draw allows. */
    class FastBow(plugin: NovaGuard) : Check("fastbow", "FastBow", CheckType.COMBAT, plugin) {
        fun onShoot(p: Player, d: PlayerData, e: EntityShootBowEvent) {
            val now = System.currentTimeMillis()
            val last = d.getLong("fbow_last")
            if (last != 0L && now - last < 400) {
                if (d.addInt("fbow_c", 1) > 5) {
                    flag(p, d, "interval=${now - last}ms")
                    d.setInt("fbow_c", 0)
                }
            } else d.setInt("fbow_c", 0)
            d.setLong("fbow_last", now)
        }
    }

    /** NoSlow (A): full movement speed while eating or drawing a bow. */
    class NoSlow(plugin: NovaGuard) : Check("noslow", "NoSlow", CheckType.MOVEMENT, plugin) {
        fun onBowDraw(d: PlayerData) { d.setLong("ns_bow", System.currentTimeMillis()) }
        fun onBowShoot(d: PlayerData) { d.setLong("ns_bow", 0L) }
        fun onMove(e: PlayerMoveEvent, p: Player, d: PlayerData) {
            val now = System.currentTimeMillis()
            val eating = now - d.getLong("fe_start") in 1..30000 && d.getLong("fe_start") != 0L
            val drawing = now - d.getLong("ns_bow") in 1..6000
            if (eating || drawing) {
                val dist = Math.hypot(e.to.x - e.from.x, e.to.z - e.from.z)
                if (dist > 0.22) {
                    if (d.addInt("ns_c", 1) > 10) {
                        flag(p, d, if (eating) "moving while eating" else "moving while drawing bow")
                        d.setInt("ns_c", 5)
                    }
                } else d.setInt("ns_c", 0)
            } else d.setInt("ns_c", 0)
        }
    }

    /** Sprint (A): sprinting while sneaking, or sprinting backwards. */
    class SprintCheck(plugin: NovaGuard) : Check("sprintcheck", "Sprint", CheckType.MOVEMENT, plugin) {
        fun onMove(e: PlayerMoveEvent, p: Player, d: PlayerData) {
            if (!p.isSprinting) { d.setInt("sp_c", 0); return }
            if (p.isSneaking) {
                if (d.addInt("sp_c", 1) > 8) { flag(p, d, "sprinting while sneaking"); d.setInt("sp_c", 0) }
                return
            }
            val dx = e.to.x - e.from.x; val dz = e.to.z - e.from.z
            if (dx * dx + dz * dz < 0.01) return
            val moveYaw = Math.toDegrees(Math.atan2(-dx, dz))
            var diff = Math.abs(moveYaw - e.to.yaw) % 360
            if (diff > 180) diff = 360 - diff
            if (diff > 110) { // moving backwards while sprinting
                if (d.addInt("sp_c", 1) > 12) { flag(p, d, "backwards sprint"); d.setInt("sp_c", 6) }
            } else d.setInt("sp_c", 0)
        }
    }

    /** BoatFly (A): vertical flight while riding a boat. */
    class BoatFly(plugin: NovaGuard) : Check("boatfly", "BoatFly", CheckType.MOVEMENT, plugin) {
        fun onMove(e: PlayerMoveEvent, p: Player, d: PlayerData, maxPing: Int) {
            val boat = p.vehicle as? Boat ?: run { d.setInt("boat_c", 0); return }
            if (d.isLagging(p, maxPing)) return
            val dy = e.to.y - e.from.y
            val belowWater = boat.location.clone().add(0.0, -0.5, 0.0).block.type == Material.WATER
            if (dy > 0.25 && !belowWater) {
                if (d.addInt("boat_c", 1) > 8) { flag(p, d, "dy=${"%.2f".format(dy)}"); d.setInt("boat_c", 4) }
            } else d.setInt("boat_c", 0)
        }
    }

    /** ChestStealer (A): emptying containers inhumanly fast. */
    class ChestStealer(plugin: NovaGuard) : Check("cheststealer", "ChestStealer", CheckType.PLAYER, plugin) {
        fun onTake(p: Player, d: PlayerData, e: InventoryClickEvent) {
            val top = e.view.topInventory
            val isContainer = top.holder is org.bukkit.block.Container ||
                    top.type == org.bukkit.event.inventory.InventoryType.CHEST ||
                    top.type == org.bukkit.event.inventory.InventoryType.ENDER_CHEST ||
                    top.type == org.bukkit.event.inventory.InventoryType.SHULKER_BOX
            if (!isContainer || e.clickedInventory != top) return
            if (e.currentItem == null || e.currentItem!!.type.isAir) return
            val now = System.currentTimeMillis()
            if (now - d.getLong("cs_window") > 1000) {
                d.setLong("cs_window", now); d.setInt("cs_c", 1)
            } else if (d.addInt("cs_c", 1) > 10) {
                flag(p, d, "${d.getInt("cs_c")} items/s from container")
                d.setInt("cs_c", 0)
            }
        }
    }

    /** InventoryClicker (A): automated inventory clicking. */
    class InventoryClicker(plugin: NovaGuard) : Check("inventoryclicker", "InventoryClicker", CheckType.PLAYER, plugin) {
        fun onClick(p: Player, d: PlayerData) {
            val now = System.currentTimeMillis()
            if (now - d.getLong("ic_window") > 1000) {
                val count = d.getInt("ic_c")
                if (count > 14 && d.getLong("ic_window") != 0L) {
                    if (d.addInt("ic_sustain", 1) > 2) {
                        flag(p, d, "$count clicks/s")
                        d.setInt("ic_sustain", 0)
                    }
                } else d.setInt("ic_sustain", 0)
                d.setLong("ic_window", now); d.setInt("ic_c", 1)
            } else d.addInt("ic_c", 1)
        }
    }

    /** GhostHand (A): interacting with blocks through walls. */
    class GhostHand(plugin: NovaGuard) : Check("ghosthand", "GhostHand", CheckType.PLAYER, plugin) {
        fun onInteract(p: Player, d: PlayerData, e: PlayerInteractEvent) {
            if (e.action != Action.RIGHT_CLICK_BLOCK) return
            val clicked = e.clickedBlock ?: return
            if (clicked.location.distanceSquared(p.eyeLocation) > 36) return
            val ray = p.rayTraceBlocks(6.0) ?: return
            val hit = ray.hitBlock
            if (hit != null && hit.location != clicked.location &&
                hit.location.blockX == clicked.location.blockX &&
                hit.location.blockY == clicked.location.blockY &&
                hit.location.blockZ == clicked.location.blockZ) return // same block, fine
            if (hit == null || hit.location.distanceSquared(clicked.location) > 0.5) {
                if (d.addInt("gh_c", 1) > 3) { flag(p, d, "interact through wall"); d.setInt("gh_c", 0) }
            } else d.setInt("gh_c", 0)
        }
    }

    /** Derp (A): spinbot-style head snapping. */
    class Derp(plugin: NovaGuard) : Check("derp", "Derp", CheckType.PLAYER, plugin) {
        fun onMove(e: PlayerMoveEvent, p: Player, d: PlayerData) {
            val lastYaw = d.getDouble("derp_yaw")
            val hasLast = d.getLong("derp_set") != 0L
            var dyaw = Math.abs(e.to.yaw - lastYaw) % 360
            if (dyaw > 180) dyaw = 360 - dyaw
            if (hasLast && dyaw > 120) {
                if (d.addInt("derp_c", 1) > 5) { flag(p, d, "head snap ${"%.0f".format(dyaw)}°"); d.setInt("derp_c", 0) }
            } else d.setInt("derp_c", 0)
            d.setDouble("derp_yaw", e.to.yaw.toDouble())
            d.setLong("derp_set", 1L)
        }
    }

    /** Baritone (A): bot-like perfectly straight movement (alert-weighted). */
    class Baritone(plugin: NovaGuard) : Check("baritone", "Baritone", CheckType.PLAYER, plugin) {
        fun onMove(e: PlayerMoveEvent, p: Player, d: PlayerData) {
            val dx = e.to.x - e.from.x; val dz = e.to.z - e.from.z
            if (dx * dx + dz * dz < 0.02) { d.setInt("bar_c", 0); return }
            val lastYaw = d.getDouble("bar_yaw")
            val hasLast = d.getLong("bar_set") != 0L
            var dyaw = Math.abs(e.to.yaw - lastYaw) % 360
            if (dyaw > 180) dyaw = 360 - dyaw
            if (hasLast && dyaw < 0.4) {
                if (d.addInt("bar_c", 1) > 600) { // 30s of pixel-perfect straight line
                    flag(p, d, "bot-like pathing")
                    d.setInt("bar_c", 300)
                }
            } else d.setInt("bar_c", 0)
            d.setDouble("bar_yaw", e.to.yaw.toDouble())
            d.setLong("bar_set", 1L)
        }
    }

    /** BookBan (A): crash-exploit books are cancelled and flagged. */
    class BookBan(plugin: NovaGuard) : Check("bookban", "BookBan", CheckType.PLAYER, plugin) {
        fun onEdit(p: Player, d: PlayerData, e: PlayerEditBookEvent) {
            val meta = e.newBookMeta as? BookMeta ?: return
            var bad = false
            for (page in meta.pages) {
                if (page.length > 2000) { bad = true; break }
            }
            if (meta.pageCount > 100) bad = true
            if (bad) {
                e.isCancelled = true
                flag(p, d, "oversized book (${meta.pageCount} pages)")
            }
        }
    }

    /** IllegalItems (A): strips impossible enchantments and stacked unstackables. */
    class IllegalItems(plugin: NovaGuard) : Check("illegals", "IllegalItems", CheckType.PLAYER, plugin) {
        fun scan(p: Player, d: PlayerData): Int {
            var removed = 0
            val inv = p.inventory
            for (i in 0 until inv.size) {
                val item = inv.getItem(i) ?: continue
                if (item.type.isAir) continue
                var illegal = false
                for ((ench, lvl) in item.enchantments) {
                    if (lvl > ench.maxLevel || lvl <= 0) { illegal = true; break }
                }
                if (!illegal && item.amount > item.maxStackSize) illegal = true
                if (illegal) {
                    inv.setItem(i, null)
                    removed++
                }
            }
            if (removed > 0) flag(p, d, "removed $removed illegal items")
            return removed
        }
    }
}
