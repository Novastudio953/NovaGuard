package com.novaguard.data

import org.bukkit.GameMode
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-player tracking state. Checks store their counters here
 * so Check instances stay stateless.
 */
class PlayerData(val uuid: UUID) {

    private val vls = ConcurrentHashMap<String, Double>()
    private val ints = ConcurrentHashMap<String, Int>()
    private val longs = ConcurrentHashMap<String, Long>()
    private val doubles = ConcurrentHashMap<String, Double>()

    @Volatile var lastTeleport: Long = 0L
    @Volatile var inventoryOpen: Boolean = false
    @Volatile var alertsEnabled: Boolean = true

    fun addVl(checkId: String, amount: Double): Double {
        val vl = (vls[checkId] ?: 0.0) + amount
        vls[checkId] = vl
        return vl
    }

    fun getVl(checkId: String): Double = vls[checkId] ?: 0.0
    fun setVl(checkId: String, vl: Double) { vls[checkId] = vl }
    fun resetVl(checkId: String) { vls.remove(checkId) }
    fun resetAllVls() { vls.clear() }
    fun vlSnapshot(): Map<String, Double> = HashMap(vls)

    fun decay(amount: Double) {
        val it = vls.entries.iterator()
        while (it.hasNext()) {
            val e = it.next()
            val left = e.value - amount
            if (left <= 0) it.remove() else e.setValue(left)
        }
    }

    fun getInt(key: String): Int = ints[key] ?: 0
    fun setInt(key: String, v: Int) { ints[key] = v }
    fun addInt(key: String, d: Int): Int {
        val v = getInt(key) + d
        ints[key] = v
        return v
    }

    fun getLong(key: String): Long = longs[key] ?: 0L
    fun setLong(key: String, v: Long) { longs[key] = v }

    fun getDouble(key: String): Double = doubles[key] ?: 0.0
    fun setDouble(key: String, v: Double) { doubles[key] = v }

    fun clearTransient() {
        ints.clear()
        longs.clear()
        doubles.clear()
    }

    /**
     * Global exemptions: creative/spectator, flight permission, vehicles,
     * recent teleports, dead players, bypass permission, high ping lag.
     */
    fun isExempt(player: Player): Boolean {
        if (player.hasPermission("novaguard.bypass")) return true
        val gm = player.gameMode
        if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) return true
        if (player.allowFlight && player.isFlying) return true
        if (player.isInsideVehicle) return true
        if (player.isDead) return true
        if (System.currentTimeMillis() - lastTeleport < 3000) return true
        return false
    }

    /** Lag compensation: skip movement checks for very laggy players. */
    fun isLagging(player: Player, maxPing: Int): Boolean {
        return try { player.ping > maxPing } catch (_: Exception) { false }
    }
}
