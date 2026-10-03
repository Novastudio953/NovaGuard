package com.novaguard.checks

import com.novaguard.NovaGuard
import com.novaguard.check.Check
import com.novaguard.check.CheckType
import com.novaguard.data.PlayerData
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.potion.PotionEffectType

object MovementChecks {

    fun registerAll(plugin: NovaGuard, register: (Check) -> Unit) {
        register(Fly(plugin)); register(Speed(plugin)); register(Timer(plugin))
        register(Jesus(plugin)); register(Step(plugin)); register(NoFall(plugin))
        register(Spider(plugin)); register(ScaffoldMove(plugin)); register(Phase(plugin))
        register(ElytraCheck(plugin))
    }

    private fun speedEffectLevel(p: Player): Int =
        p.getPotionEffect(PotionEffectType.SPEED)?.amplifier?.plus(1) ?: 0

    private fun onClimbable(p: Player): Boolean {
        val b = p.location.block.type
        val below = p.location.clone().add(0.0, -0.5, 0.0).block.type
        return b == Material.LADDER || b == Material.VINE || b == Material.TWISTING_VINES ||
                b == Material.WEEPING_VINES || b == Material.SCAFFOLDING ||
                below == Material.LADDER || below == Material.SCAFFOLDING
    }

    private fun inLiquid(p: Player): Boolean {
        val t = p.location.block.type
        return t == Material.WATER || t == Material.LAVA
    }

    private fun nearGround(p: Player): Boolean {
        val loc = p.location.clone()
        for (dy in 0..2) {
            val t = loc.clone().add(0.0, -dy * 0.5 - 0.1, 0.0).block.type
            if (t.isSolid && t.isOccluding) return true
        }
        return false
    }

    /** Fly (A): sustained horizontal air movement without losing altitude. */
    class Fly(plugin: NovaGuard) : Check("fly", "Fly", CheckType.MOVEMENT, plugin) {
        fun onMove(e: PlayerMoveEvent, p: Player, d: PlayerData, maxPing: Int) {
            if (d.isLagging(p, maxPing)) return
            val from = e.from; val to = e.to
            val dy = to.y - from.y
            val dx = to.x - from.x; val dz = to.z - from.z
            val horizontal = dx * dx + dz * dz
            if (!p.isOnGround && !p.isGliding && !p.isSwimming && !inLiquid(p) && !onClimbable(p)
                && !p.isFlying && horizontal > 0.001) {
                if (dy >= -0.05) {
                    if (d.addInt("fly_air", 1) > 30) {
                        flag(p, d, "air=${d.getInt("fly_air")} dy=${"%.3f".format(dy)}")
                        d.setInt("fly_air", 20)
                    }
                } else d.setInt("fly_air", 0)
            } else d.setInt("fly_air", 0)
        }
    }

    /** Speed (A): horizontal velocity beyond legit limits. */
    class Speed(plugin: NovaGuard) : Check("speed", "Speed", CheckType.MOVEMENT, plugin) {
        fun onMove(e: PlayerMoveEvent, p: Player, d: PlayerData, maxPing: Int) {
            if (d.isLagging(p, maxPing)) return
            val from = e.from; val to = e.to
            val dist = Math.hypot(to.x - from.x, to.z - from.z)
            if (dist < 0.01 || p.isGliding || p.isFlying || inLiquid(p)) return
            var limit = if (p.isSprinting) 0.36 else 0.30
            limit *= 1.0 + 0.2 * speedEffectLevel(p)
            if (p.isOnGround) limit *= 1.15 // bunny-hop allowance
            if (from.block.type == Material.ICE || from.block.type == Material.PACKED_ICE ||
                from.block.type == Material.BLUE_ICE) limit *= 1.8
            if (dist > limit) {
                if (d.addInt("speed_vl_ticks", 1) > 6) {
                    flag(p, d, "dist=${"%.3f".format(dist)} limit=${"%.3f".format(limit)}")
                    d.setInt("speed_vl_ticks", 0)
                }
            } else d.setInt("speed_vl_ticks", 0)
        }
    }

    /** Timer (A): more movement packets than 20/sec allows. */
    class Timer(plugin: NovaGuard) : Check("timer", "Timer", CheckType.MOVEMENT, plugin) {
        fun onMove(e: PlayerMoveEvent, p: Player, d: PlayerData, maxPing: Int) {
            if (d.isLagging(p, maxPing)) return
            val now = System.currentTimeMillis()
            val window = d.getLong("timer_window")
            if (now - window > 1000) {
                val count = d.getInt("timer_count")
                if (count > 32 && window != 0L) flag(p, d, "packets=$count/s")
                d.setLong("timer_window", now)
                d.setInt("timer_count", 1)
            } else d.addInt("timer_count", 1)
        }
    }

    /** Jesus (A): walking on liquid surfaces. */
    class Jesus(plugin: NovaGuard) : Check("jesus", "Jesus", CheckType.MOVEMENT, plugin) {
        fun onMove(e: PlayerMoveEvent, p: Player, d: PlayerData, maxPing: Int) {
            if (d.isLagging(p, maxPing)) return
            val feet = p.location.block.type
            val below = p.location.clone().add(0.0, -0.4, 0.0).block.type
            val onLiquid = feet == Material.WATER || below == Material.WATER
            if (onLiquid && !p.isSwimming && !p.isFlying && !p.isGliding) {
                val dy = Math.abs(e.to.y - e.from.y)
                val dist = Math.hypot(e.to.x - e.from.x, e.to.z - e.from.z)
                if (dy < 0.05 && dist > 0.05) {
                    if (d.addInt("jesus_ticks", 1) > 12) {
                        flag(p, d, "")
                        d.setInt("jesus_ticks", 6)
                    }
                } else d.setInt("jesus_ticks", 0)
            } else d.setInt("jesus_ticks", 0)
        }
    }

    /** Step (A): climbing more than 0.6 blocks in one move. */
    class Step(plugin: NovaGuard) : Check("step", "Step", CheckType.MOVEMENT, plugin) {
        fun onMove(e: PlayerMoveEvent, p: Player, d: PlayerData) {
            val dy = e.to.y - e.from.y
            if (dy > 0.65 && p.isOnGround && !onClimbable(p) && !p.isFlying) {
                flag(p, d, "dy=${"%.2f".format(dy)}")
            }
        }
    }

    /** NoFall (A): surviving falls that should hurt. */
    class NoFall(plugin: NovaGuard) : Check("nofall", "NoFall", CheckType.MOVEMENT, plugin) {
        fun onMove(e: PlayerMoveEvent, p: Player, d: PlayerData) {
            if (p.fallDistance > 4.0) d.setInt("nofall_armed", 1)
            if (d.getInt("nofall_armed") == 1 && p.isOnGround && p.fallDistance == 0f) {
                d.setInt("nofall_armed", 0)
                // landed from a big fall; damage event clears this flag via listener
                if (d.getLong("nofall_dmg") < System.currentTimeMillis() - 1500 && !inLiquid(p)
                    && !onClimbable(p) && !p.isGliding) {
                    flag(p, d, "survived ${"%.1f".format(p.fallDistance)}+ block fall")
                }
            }
            if (p.isOnGround) d.setInt("nofall_armed", 0)
        }
        fun onDamage(d: PlayerData) { d.setLong("nofall_dmg", System.currentTimeMillis()) }
    }

    /** Spider (A): climbing vertical walls without ladders. */
    class Spider(plugin: NovaGuard) : Check("spider", "Spider", CheckType.MOVEMENT, plugin) {
        fun onMove(e: PlayerMoveEvent, p: Player, d: PlayerData, maxPing: Int) {
            if (d.isLagging(p, maxPing)) return
            val dy = e.to.y - e.from.y
            if (dy > 0.15 && !onClimbable(p) && !p.isFlying && !inLiquid(p) && !p.isGliding) {
                // must be against a wall
                val loc = p.location
                var wall = false
                for ((ox, oz) in listOf(0.4 to 0.0, -0.4 to 0.0, 0.0 to 0.4, 0.0 to -0.4)) {
                    if (loc.clone().add(ox, 0.5, oz).block.type.isOccluding) { wall = true; break }
                }
                if (wall) {
                    if (d.addInt("spider_ticks", 1) > 10) {
                        flag(p, d, "dy=${"%.2f".format(dy)}")
                        d.setInt("spider_ticks", 5)
                    }
                } else d.setInt("spider_ticks", 0)
            } else d.setInt("spider_ticks", 0)
        }
    }

    /** Scaffold movement component: placing blocks while moving fast. */
    class ScaffoldMove(plugin: NovaGuard) : Check("scaffold", "Scaffold", CheckType.MOVEMENT, plugin) {
        fun onPlace(p: Player, d: PlayerData) {
            val now = System.currentTimeMillis()
            val last = d.getLong("scaffold_last_place")
            if (now - last < 120) {
                if (d.addInt("scaffold_fast", 1) > 14) {
                    // handled with pitch check in listener
                }
            } else d.setInt("scaffold_fast", 0)
            d.setLong("scaffold_last_place", now)
        }
        fun onMove(e: PlayerMoveEvent, p: Player, d: PlayerData) {
            val dist = Math.hypot(e.to.x - e.from.x, e.to.z - e.from.z)
            val placing = System.currentTimeMillis() - d.getLong("scaffold_last_place") < 500
            if (placing && dist > 0.15 && p.location.pitch > 45) {
                if (d.addInt("scaffold_move", 1) > 12) {
                    flag(p, d, "bridging while sprinting")
                    d.setInt("scaffold_move", 0)
                }
            } else if (!placing) d.setInt("scaffold_move", 0)
        }
    }

    /** Phase (A): ending a move inside a solid block. */
    class Phase(plugin: NovaGuard) : Check("phase", "Phase", CheckType.MOVEMENT, plugin) {
        fun onMove(e: PlayerMoveEvent, p: Player, d: PlayerData) {
            val to = e.to
            val feet = to.block.type
            val head = to.clone().add(0.0, 1.0, 0.0).block.type
            if ((feet.isOccluding || head.isOccluding) && !p.isFlying) {
                if (d.addInt("phase_ticks", 1) > 4) {
                    flag(p, d, "inside ${feet.name}")
                    d.setInt("phase_ticks", 0)
                }
            } else d.setInt("phase_ticks", 0)
        }
    }

    /** Elytra (A): impossible elytra speeds. */
    class ElytraCheck(plugin: NovaGuard) : Check("elytra", "ElytraFly", CheckType.MOVEMENT, plugin) {
        fun onMove(e: PlayerMoveEvent, p: Player, d: PlayerData, maxPing: Int) {
            if (!p.isGliding || d.isLagging(p, maxPing)) return
            val dist = Math.hypot(e.to.x - e.from.x, e.to.z - e.from.z)
            val dy = e.to.y - e.from.y
            if (dist > 3.2 || dy > 1.2) {
                if (d.addInt("elytra_ticks", 1) > 8) {
                    flag(p, d, "dist=${"%.2f".format(dist)} dy=${"%.2f".format(dy)}")
                    d.setInt("elytra_ticks", 0)
                }
            } else d.setInt("elytra_ticks", 0)
        }
    }
}
