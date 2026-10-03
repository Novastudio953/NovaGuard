package com.novaguard.api

import com.novaguard.check.Check
import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/**
 * Fired every time NovaGuard flags a player, before VL is applied.
 * Cancelling prevents the violation (no VL, no alert, no punishment).
 */
class NovaGuardFlagEvent(
    val player: Player,
    val check: Check,
    val violationLevel: Double,
    val info: String
) : Event(), Cancellable {

    private var cancelled = false

    override fun isCancelled(): Boolean = cancelled
    override fun setCancelled(cancel: Boolean) { cancelled = cancel }
    override fun getHandlers(): HandlerList = handlerList

    companion object {
        @JvmStatic
        val handlerList = HandlerList()
    }
}
