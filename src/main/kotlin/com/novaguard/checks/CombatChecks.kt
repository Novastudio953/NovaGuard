package com.novaguard.checks

import com.novaguard.NovaGuard
import com.novaguard.check.Check
import com.novaguard.check.CheckType
import com.novaguard.data.PlayerData
import org.bukkit.entity.Player
import kotlin.math.sqrt

object CombatChecks {

    fun registerAll(plugin: NovaGuard, register: (Check) -> Unit) {
        register(KillAuraRate(plugin)); register(KillAuraAngle(plugin))
        register(KillAuraCooldown(plugin)); register(Reach(plugin))
        register(AutoClicker(plugin)); register(Criticals(plugin))
        register(Velocity(plugin))
    }

    private fun prune(list: MutableList<Long>, windowMs: Long): MutableList<Long> {
        val now = System.currentTimeMillis()
        list.removeIf { now - it > windowMs }
        return list
    }

    /** KillAura (A): inhuman attack rate. */
    class KillAuraRate(plugin: NovaGuard) : Check("killaura-rate", "KillAura (Rate)", CheckType.COMBAT, plugin) {
        fun onAttack(p: Player, d: PlayerData) {
            val key = "ka_rate"
            val now = System.currentTimeMillis()
            val last = d.getLong("${key}_t")
            if (now - last < 55) { // faster than humanly possible
                if (d.addInt("${key}_c", 1) > 8) {
                    flag(p, d, "interval=${now - last}ms")
                    d.setInt("${key}_c", 0)
                }
            } else d.setInt("${key}_c", 0)
            d.setLong("${key}_t", now)
        }
    }

    /** KillAura (B): hitting entities outside the look cone. */
    class KillAuraAngle(plugin: NovaGuard) : Check("killaura-angle", "KillAura (Angle)", CheckType.COMBAT, plugin) {
        fun onAttack(p: Player, target: org.bukkit.entity.Entity, d: PlayerData) {
            val eye = p.eyeLocation
            val dir = eye.direction.normalize()
            val toTarget = target.location.clone().add(0.0, target.height / 2, 0.0)
                .subtract(eye).toVector().normalize()
            val dot = dir.dot(toTarget).coerceIn(-1.0, 1.0)
            val angle = Math.toDegrees(Math.acos(dot))
            if (angle > 70) {
                if (d.addInt("ka_angle_c", 1) > 4) {
                    flag(p, d, "angle=${"%.1f".format(angle)}")
                    d.setInt("ka_angle_c", 0)
                }
            } else d.setInt("ka_angle_c", 0)
        }
    }

    /** KillAura (C): attacking without waiting for cooldown. */
    class KillAuraCooldown(plugin: NovaGuard) : Check("killaura-cooldown", "KillAura (Cooldown)", CheckType.COMBAT, plugin) {
        fun onAttack(p: Player, d: PlayerData) {
            try {
                if (p.attackCooldown < 0.6) {
                    if (d.addInt("ka_cd_c", 1) > 10) {
                        flag(p, d, "cooldown=${"%.2f".format(p.attackCooldown)}")
                        d.setInt("ka_cd_c", 5)
                    }
                } else d.setInt("ka_cd_c", 0)
            } catch (_: Exception) { }
        }
    }

    /** Reach (A): hitting beyond 3 blocks. */
    class Reach(plugin: NovaGuard) : Check("reach", "Reach", CheckType.COMBAT, plugin) {
        fun onAttack(p: Player, target: org.bukkit.entity.Entity, d: PlayerData) {
            val dist = p.eyeLocation.distance(target.location.clone().add(0.0, target.height / 2, 0.0))
            if (dist > 3.4) {
                if (d.addInt("reach_c", 1) > 5) {
                    flag(p, d, "dist=${"%.2f".format(dist)}")
                    d.setInt("reach_c", 2)
                }
            } else d.setInt("reach_c", 0)
        }
    }

    /** AutoClicker (A): unnaturally consistent click timing. */
    class AutoClicker(plugin: NovaGuard) : Check("autoclicker", "AutoClicker", CheckType.COMBAT, plugin) {
        fun onAttack(p: Player, d: PlayerData) {
            val now = System.currentTimeMillis()
            val last = d.getLong("ac_last")
            if (last != 0L) {
                val interval = now - last
                if (interval in 20..1000) {
                    val count = d.getInt("ac_n")
                    val mean = d.getDouble("ac_mean")
                    val m2 = d.getDouble("ac_m2")
                    val n = count + 1
                    val delta = interval - mean
                    val newMean = mean + delta / n
                    val newM2 = m2 + delta * (interval - newMean)
                    d.setInt("ac_n", n); d.setDouble("ac_mean", newMean); d.setDouble("ac_m2", newM2)
                    if (n >= 25) {
                        val variance = newM2 / n
                        val stddev = sqrt(variance)
                        val cps = 1000.0 / newMean
                        if (stddev < 12 && cps > 7) {
                            flag(p, d, "cps=${"%.1f".format(cps)} sd=${"%.1f".format(stddev)}")
                            d.setInt("ac_n", 0); d.setDouble("ac_mean", 0.0); d.setDouble("ac_m2", 0.0)
                        } else if (n >= 60) {
                            d.setInt("ac_n", 0); d.setDouble("ac_mean", 0.0); d.setDouble("ac_m2", 0.0)
                        }
                    }
                }
            }
            d.setLong("ac_last", now)
        }
    }

    /** Criticals (A): every hit landing as a critical. */
    class Criticals(plugin: NovaGuard) : Check("criticals", "Criticals", CheckType.COMBAT, plugin) {
        fun onAttack(p: Player, d: PlayerData) {
            val crit = p.fallDistance > 0 && !p.isOnGround && !p.isInWater &&
                    !p.isClimbing && !p.isGliding && !p.isSprinting
            if (crit) {
                if (d.addInt("crit_c", 1) > 12) {
                    flag(p, d, "12 consecutive crits")
                    d.setInt("crit_c", 6)
                }
            } else d.setInt("crit_c", 0)
        }
    }

    /** Velocity (A): taking no knockback. */
    class Velocity(plugin: NovaGuard) : Check("velocity", "Velocity", CheckType.COMBAT, plugin) {
        fun onHurt(p: Player, d: PlayerData) {
            // sample velocity shortly after the hit lands
            val before = p.velocity.length()
            plugin.server.scheduler.runTaskLater(plugin, Runnable {
                if (!p.isOnline) return@Runnable
                val after = p.velocity.length()
                // legit knockback spikes velocity; cheaters barely move
                if (after < 0.12 && before < 0.12) {
                    if (d.addInt("vel_c", 1) > 5) {
                        flag(p, d, "no knockback")
                        d.setInt("vel_c", 2)
                    }
                } else d.setInt("vel_c", 0)
            }, 2L)
        }
    }
}
