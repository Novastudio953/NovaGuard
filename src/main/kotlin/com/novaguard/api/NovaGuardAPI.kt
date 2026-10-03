package com.novaguard.api

import com.novaguard.NovaGuard
import com.novaguard.check.Check
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.UUID

/**
 * Public developer API. Other plugins can query NovaGuard state
 * without touching internals:
 *
 *     val vl = NovaGuardAPI.getViolationLevel(player, "killaura-rate")
 */
object NovaGuardAPI {

    private var plugin: NovaGuard? = null

    /** Called by NovaGuard on enable. Do not call this yourself. */
    fun init(plugin: NovaGuard) {
        this.plugin = plugin
    }

    fun getPlugin(): NovaGuard =
        plugin ?: throw IllegalStateException("NovaGuard is not enabled")

    fun isEnabled(): Boolean = plugin?.isEnabled == true

    fun getCheck(id: String): Check? = getPlugin().checks.get(id)

    fun getAllChecks(): Collection<Check> = getPlugin().checks.all

    fun getViolationLevel(player: Player, checkId: String): Double =
        getPlugin().data.get(player.uniqueId).getVl(checkId)

    fun getTotalViolationLevel(player: Player): Double =
        getPlugin().data.get(player.uniqueId).vlSnapshot().values.sum()

    fun resetViolations(player: Player) =
        getPlugin().data.get(player.uniqueId).resetAllVls()

    fun isExempt(player: Player): Boolean =
        getPlugin().data.get(player.uniqueId).isExempt(player)

    fun isBedrock(player: Player): Boolean =
        getPlugin().data.get(player.uniqueId).checkBedrock()

    fun isFrozen(uuid: UUID): Boolean =
        getPlugin().data.get(uuid).frozen

    /** Convenience: grab the API straight from Bukkit's plugin manager. */
    @JvmStatic
    fun getInstance(): NovaGuardAPI = NovaGuardAPI

    @JvmStatic
    fun isAvailable(): Boolean =
        Bukkit.getPluginManager().getPlugin("NovaGuard")?.isEnabled == true
}
