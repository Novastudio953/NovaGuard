package com.novaguard.checks

import com.novaguard.NovaGuard
import com.novaguard.check.Check
import com.novaguard.check.CheckType
import com.novaguard.data.PlayerData
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityRegainHealthEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.event.player.PlayerMoveEvent
import kotlin.math.abs
import kotlin.math.atan2

/**
 * Wave-3 checks (1.7.0): advanced movement, combat-aid and automation detections.
 */
object AdvancedChecks {

    fun registerAll(plugin: NovaGuard, register: (Check) -> Unit) {
        register(Strafe(plugin)); register(GroundSpoof(plugin))
        register(AutoArmor(plugin)); register(AutoTool(plugin))
        register(Regen(plugin)); register(Aimbot(plugin))
    }

    private fun hasGroundBelow(p: Player, depth: Double): Boolean {
        val loc = p.location.clone()
        var dy = 0.0
        while (dy <= depth) {
            val t = loc.clone().add(0.0, -dy - 0.05, 0.0).block.type
            if (t.isSolid && t.isOccluding) return true
            dy += 0.3
        }
        return false
    }

    /** Strafe (A): impossible mid-air direction changes. Air control is limited. */
    class Strafe(plugin: NovaGuard) : Check("strafe", "Strafe", CheckType.MOVEMENT, plugin) {
        fun onMove(e: PlayerMoveEvent, p: Player, d: PlayerData, maxPing: Int) {
            if (d.isLagging(p, maxPing)) return
            val dx = e.to.x - e.from.x
            val dz = e.to.z - e.from.z
            val dist = Math.hypot(dx, dz)
            val lastDx = d.getDouble("strafe_dx")
            val lastDz = d.getDouble("strafe_dz")
            val lastDist = Math.hypot(lastDx, lastDz)
            d.setDouble("strafe_dx", dx)
            d.setDouble("strafe_dz", dz)
            // only meaningful while airborne and moving with intent
            if (p.isOnGround || p.isFlying || p.isGliding || p.isSwimming) return
            if (dist < 0.12 || lastDist < 0.12) return
            if (d.isExempt(p)) return
            val angle = abs(atan2(dx, dz) - atan2(lastDx, lastDz))
            val norm = if (angle > Math.PI) 2 * Math.PI - angle else angle
            if (norm > 1.75) { // > ~100 degrees in one tick: impossible
                if (d.addInt("strafe_turns", 1) > 2) {
                    flag(p, d, "air-turn=${"%.0f".format(Math.toDegrees(norm))}°")
                    d.setInt("strafe_turns", 0)
                }
            } else d.setInt("strafe_turns", 0)
        }
    }

    /** GroundSpoof (A): client claims on-ground while floating above the void. */
    class GroundSpoof(plugin: NovaGuard) : Check("groundspoof", "GroundSpoof", CheckType.MOVEMENT, plugin) {
        fun onMove(e: PlayerMoveEvent, p: Player, d: PlayerData, maxPing: Int) {
            if (d.isLagging(p, maxPing)) return
            val dy = e.to.y - e.from.y
            // jumping upward is legit; only flag "grounded" hover
            if (!p.isOnGround || dy > 0.1 || p.isFlying || p.isGliding || p.isSwimming) {
                d.setInt("gspoof_ticks", 0); return
            }
            val feet = p.location.block.type
            if (feet == Material.WATER || feet == Material.LAVA || feet == Material.LADDER ||
                feet == Material.SCAFFOLDING || p.fallDistance > 0.6) {
                d.setInt("gspoof_ticks", 0); return
            }
            if (!hasGroundBelow(p, 0.6)) {
                if (d.addInt("gspoof_ticks", 1) > 14) {
                    flag(p, d, "claimed ground, air below")
                    d.setInt("gspoof_ticks", 7)
                }
            } else d.setInt("gspoof_ticks", 0)
        }
    }

    /** AutoArmor (A): equipping multiple armor pieces faster than humanly possible. */
    class AutoArmor(plugin: NovaGuard) : Check("autoarmor", "AutoArmor", CheckType.PLAYER, plugin) {
        private val armorMats = setOf(
            Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS,
            Material.CHAINMAIL_HELMET, Material.CHAINMAIL_CHESTPLATE, Material.CHAINMAIL_LEGGINGS, Material.CHAINMAIL_BOOTS,
            Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS,
            Material.GOLDEN_HELMET, Material.GOLDEN_CHESTPLATE, Material.GOLDEN_LEGGINGS, Material.GOLDEN_BOOTS,
            Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS,
            Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE, Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS
        )

        fun onClick(e: InventoryClickEvent, p: Player, d: PlayerData) {
            val item = e.currentItem ?: return
            if (item.type !in armorMats) return
            // shift-click or armor-slot placement equips armor
            val equips = e.isShiftClick || e.slotType == org.bukkit.event.inventory.InventoryType.SlotType.ARMOR
            if (!equips) return
            val now = System.currentTimeMillis()
            val last = d.getLong("aarmor_last")
            if (now - last < 260) {
                if (d.addInt("aarmor_fast", 1) >= 2) {
                    flag(p, d, "3+ pieces in <260ms each")
                    d.setInt("aarmor_fast", 0)
                }
            } else d.setInt("aarmor_fast", 0)
            d.setLong("aarmor_last", now)
        }
    }

    /** AutoTool (A): swapping to the perfect tool the instant a block is hit. */
    class AutoTool(plugin: NovaGuard) : Check("autotool", "AutoTool", CheckType.PLAYER, plugin) {
        fun onBlockHit(d: PlayerData) {
            d.setLong("atool_hit", System.currentTimeMillis())
        }

        fun onHeldChange(e: PlayerItemHeldEvent, p: Player, d: PlayerData) {
            if (e.newSlot == e.previousSlot) return
            val sinceHit = System.currentTimeMillis() - d.getLong("atool_hit")
            if (sinceHit in 1..180) {
                if (d.addInt("atool_swaps", 1) >= 4) {
                    flag(p, d, "4 perfect swaps <180ms after hit")
                    d.setInt("atool_swaps", 0)
                }
                // decay the counter if swaps stop
                if (sinceHit > 2000) d.setInt("atool_swaps", 0)
            } else if (sinceHit > 2000) d.setInt("atool_swaps", 0)
        }
    }

    /** Regen (A): healing faster than vanilla mechanics allow. */
    class Regen(plugin: NovaGuard) : Check("regen", "Regen", CheckType.COMBAT, plugin) {
        fun onRegain(e: EntityRegainHealthEvent, p: Player, d: PlayerData) {
            if (e.regainReason == EntityRegainHealthEvent.RegainReason.MAGIC) return // potions handled below
            val now = System.currentTimeMillis()
            if (now - d.getLong("regen_window") > 5000) {
                d.setLong("regen_window", now)
                d.setDouble("regen_amount", 0.0)
            }
            // ignore while under regeneration effect or right after golden apple
            val hasRegenFx = p.hasPotionEffect(org.bukkit.potion.PotionEffectType.REGENERATION)
            val ateGapple = now - d.getLong("regen_gapple") < 60000
            if (hasRegenFx || ateGapple) return
            d.setDouble("regen_amount", d.getDouble("regen_amount") + e.amount)
            // vanilla saturation regen: ~1.25 HP per 5s. Anything far above is impossible.
            if (d.getDouble("regen_amount") > 5.0) {
                flag(p, d, "${"%.1f".format(d.getDouble("regen_amount"))} HP in 5s (no effect)")
                d.setDouble("regen_amount", 0.0)
                d.setLong("regen_window", now)
            }
        }

        fun onConsume(d: PlayerData, mat: Material) {
            if (mat == Material.GOLDEN_APPLE || mat == Material.ENCHANTED_GOLDEN_APPLE)
                d.setLong("regen_gapple", System.currentTimeMillis())
        }
    }

    /** Aimbot (A): hitting entities the player isn't looking at + inhuman aim snaps. */
    class Aimbot(plugin: NovaGuard) : Check("aimbot", "Aimbot", CheckType.COMBAT, plugin) {
        fun onMove(e: PlayerMoveEvent, p: Player, d: PlayerData) {
            val to = e.to ?: return
            d.setDouble("aim_yaw", to.yaw.toDouble())
            d.setDouble("aim_pitch", to.pitch.toDouble())
            d.setLong("aim_time", System.currentTimeMillis())
        }

        fun onAttack(p: Player, target: org.bukkit.entity.Entity, d: PlayerData, maxPing: Int) {
            if (d.isLagging(p, maxPing)) return
            val eye = p.eyeLocation
            val t = target.location.clone().add(0.0, 1.0, 0.0)
            val dx = t.x - eye.x; val dz = t.z - eye.z
            val dist = Math.hypot(dx, dz)
            if (dist < 1.0 || dist > 6.0) return
            // required yaw to face the target
            val reqYaw = Math.toDegrees(atan2(-dx, dz))
            val yawDiff = abs(((eye.yaw - reqYaw + 540) % 360) - 180)
            // inhuman snap: huge rotation right before the hit
            val sinceRot = System.currentTimeMillis() - d.getLong("aim_time")
            val lastYaw = d.getDouble("aim_yaw")
            val snap = abs(((eye.yaw - lastYaw + 540) % 360) - 180)
            if (sinceRot < 120 && snap > 80) {
                flag(p, d, "snap ${"%.0f".format(snap)}° in ${sinceRot}ms")
                return
            }
            // hitting without looking
            if (yawDiff > 55) {
                if (d.addInt("aim_offangle", 1) >= 3) {
                    flag(p, d, "hit at ${"%.0f".format(yawDiff)}° off-aim")
                    d.setInt("aim_offangle", 0)
                }
            } else d.setInt("aim_offangle", 0)
        }
    }
}
