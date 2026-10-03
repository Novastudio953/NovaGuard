package com.novaguard.listener

import com.novaguard.NovaGuard
import com.novaguard.checks.CombatChecks
import com.novaguard.checks.MovementChecks
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent

class CombatListener(private val plugin: NovaGuard) : Listener {

    private fun attacker(e: EntityDamageByEntityEvent): Player? {
        val d = e.damager
        return when (d) {
            is Player -> d
            is Projectile -> d.shooter as? Player
            else -> null
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onDamage(e: EntityDamageByEntityEvent) {
        val p = attacker(e) ?: return
        val d = plugin.data.get(p.uniqueId)
        if (d.isExempt(p)) return
        val target = e.entity
        if (target is Player && plugin.data.get(target.uniqueId).isExempt(target)) return

        (plugin.checks.get("killaura-rate") as? CombatChecks.KillAuraRate)?.let {
            if (it.enabled) it.onAttack(p, d)
        }
        (plugin.checks.get("killaura-angle") as? CombatChecks.KillAuraAngle)?.let {
            if (it.enabled) it.onAttack(p, target, d)
        }
        (plugin.checks.get("killaura-cooldown") as? CombatChecks.KillAuraCooldown)?.let {
            if (it.enabled) it.onAttack(p, d)
        }
        (plugin.checks.get("reach") as? CombatChecks.Reach)?.let {
            if (it.enabled) it.onAttack(p, target, d)
        }
        (plugin.checks.get("autoclicker") as? CombatChecks.AutoClicker)?.let {
            if (it.enabled) it.onAttack(p, d)
        }
        (plugin.checks.get("criticals") as? CombatChecks.Criticals)?.let {
            if (it.enabled) it.onAttack(p, d)
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onHurt(e: EntityDamageEvent) {
        val p = e.entity as? Player ?: return
        if (e.cause == EntityDamageEvent.DamageCause.FALL) {
            // feed NoFall: player actually took fall damage = legit
            (plugin.checks.get("nofall") as? MovementChecks.NoFall)
                ?.onDamage(plugin.data.get(p.uniqueId))
            return
        }
        if (e is EntityDamageByEntityEvent) {
            val d = plugin.data.get(p.uniqueId)
            if (d.isExempt(p)) return
            (plugin.checks.get("velocity") as? CombatChecks.Velocity)?.let {
                if (it.enabled) it.onHurt(p, d)
            }
        }
    }
}
