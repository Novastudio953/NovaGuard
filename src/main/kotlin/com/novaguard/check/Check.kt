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
        if (plugin.bedrockLeniency && data.checkBedrock() && plugin.bedrockExempt.contains(id)) return
        val vl = data.addVl(id, vlAmount())
        // developer API: allow other plugins to observe/cancel the flag
        val event = com.novaguard.api.NovaGuardFlagEvent(player, this, vl, info)
        try {
            org.bukkit.Bukkit.getPluginManager().callEvent(event)
        } catch (_: Exception) { }
        if (event.isCancelled) {
            data.setVl(id, vl - vlAmount()) // roll back the VL we just added
            return
        }
        val inGrace = plugin.graceEnabled && System.currentTimeMillis() < data.graceUntil
        data.addHistory(id, vl, if (inGrace) "$info [grace]" else info)
        plugin.alerts.alert(player, this, vl, if (inGrace) "$info [grace]" else info)
        // grace period: brand-new players need 2x VL before punishment
        val effectiveMax = if (inGrace) maxVl * plugin.graceMultiplier else maxVl
        if (vl >= effectiveMax * banVlMultiplier) {
            plugin.punishments.execute(player, this, banCommand, vl, true)
            data.resetVl(id)
        } else if (vl >= effectiveMax) {
            plugin.punishments.execute(player, this, punishment, vl, false)
            data.setVl(id, effectiveMax * 0.5) // keep half so repeat offenders escalate
        }
    }

    /** How much VL one detection adds. Override for weighted checks. */
    open fun vlAmount(): Double = 1.0
}
