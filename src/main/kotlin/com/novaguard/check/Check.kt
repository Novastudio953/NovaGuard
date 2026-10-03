package com.novaguard.check

import com.novaguard.NovaGuard
import com.novaguard.data.PlayerData
import org.bukkit.entity.Player

enum class CheckType { MOVEMENT, COMBAT, BLOCK, PLAYER }

/**
 * Base class for every detection check. Each check is toggleable
 * and has its own violation threshold + punishment, like Vulcan.
 */
abstract class Check(
    val id: String,
    val displayName: String,
    val type: CheckType,
    protected val plugin: NovaGuard
) {
    var enabled: Boolean = true
    var maxVl: Double = 10.0
    var punishment: String = "kick {player} Unfair advantage ({check})"
    var banCommand: String = "ban {player} Cheating ({check})"
    var banVlMultiplier: Double = 3.0

    fun flag(player: Player, data: PlayerData, info: String = "") {
        if (!enabled) return
        if (data.isExempt(player)) return
        val vl = data.addVl(id, vlAmount())
        plugin.alerts.alert(player, this, vl, info)
        if (vl >= maxVl * banVlMultiplier) {
            plugin.punishments.execute(player, this, banCommand, vl)
            data.resetVl(id)
        } else if (vl >= maxVl) {
            plugin.punishments.execute(player, this, punishment, vl)
            data.setVl(id, maxVl * 0.5) // keep half so repeat offenders escalate
        }
    }

    /** How much VL one detection adds. Override for weighted checks. */
    open fun vlAmount(): Double = 1.0
}
