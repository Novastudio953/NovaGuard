package com.novaguard.staff

import com.novaguard.NovaGuard
import com.novaguard.check.Check
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Verbose mode: staff watch one player's live detections.
 * /novaguard verbose <player> toggles it.
 */
class VerboseManager(private val plugin: NovaGuard) {

    /** staff uuid -> target uuid */
    private val watchers = ConcurrentHashMap<UUID, UUID>()

    var enabled: Boolean = true

    fun load() {
        enabled = plugin.config.getBoolean("settings.verbose.enabled", true)
    }

    /** Toggle verbose on a target. Returns true if now watching. */
    fun toggle(staff: Player, target: Player): Boolean {
        val key = staff.uniqueId
        return if (watchers[key] == target.uniqueId) {
            watchers.remove(key)
            false
        } else {
            watchers[key] = target.uniqueId
            true
        }
    }

    fun stopWatching(staff: UUID) = watchers.remove(staff)

    fun isWatching(staff: Player, target: Player): Boolean =
        watchers[staff.uniqueId] == target.uniqueId

    /** Called from AlertManager on every flag. */
    fun onFlag(player: Player, check: Check, vl: Double, info: String) {
        if (!enabled) return
        val msg = plugin.configs.msg("verbose-line",
            "player" to player.name,
            "check" to check.displayName,
            "vl" to "%.1f".format(vl),
            "info" to info.ifBlank { "-" },
            "ping" to safePing(player).toString())
        for ((staffId, targetId) in watchers) {
            if (targetId != player.uniqueId) continue
            val staff = plugin.server.getPlayer(staffId) ?: continue
            if (!staff.hasPermission("novaguard.admin")) continue
            staff.sendMessage(msg)
        }
    }

    private fun safePing(p: Player): Int = try { p.ping } catch (_: Exception) { -1 }
}
