package com.novaguard.staff

import com.novaguard.NovaGuard
import org.bukkit.Location
import org.bukkit.entity.Player
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Evidence recorder: keeps a rolling buffer of every player's recent
 * movement. Staff can replay a suspect's last seconds with
 * /novaguard replay <player> — they get teleported along the path.
 */
class EvidenceRecorder(private val plugin: NovaGuard) {

    private data class Sample(val loc: Location, val time: Long)

    private val buffers = ConcurrentHashMap<UUID, ArrayDeque<Sample>>()
    private val replays = ConcurrentHashMap<UUID, Int>() // staff uuid -> task id

    var enabled: Boolean = true
    var recordSeconds: Int = 20

    fun load() {
        enabled = plugin.config.getBoolean("settings.evidence.enabled", true)
        recordSeconds = plugin.config.getInt("settings.evidence.record-seconds", 20)
    }

    /** Called from MoveListener, throttled to ~10Hz per player. */
    fun record(player: Player) {
        if (!enabled) return
        val now = System.currentTimeMillis()
        val buf = buffers.computeIfAbsent(player.uniqueId) { ArrayDeque() }
        val last = buf.peekLast()
        if (last != null && now - last.time < 100) return
        buf.addLast(Sample(player.location.clone(), now))
        val maxSamples = (recordSeconds * 10).coerceAtLeast(50)
        while (buf.size > maxSamples) buf.removeFirst()
    }

    fun clear(uuid: UUID) {
        buffers.remove(uuid)
    }

    fun sampleCount(uuid: UUID): Int = buffers[uuid]?.size ?: 0

    fun secondsRecorded(uuid: UUID): Double {
        val buf = buffers[uuid] ?: return 0.0
        if (buf.size < 2) return 0.0
        return (buf.peekLast()!!.time - buf.peekFirst()!!.time) / 1000.0
    }

    /** Replay the target's recorded movement for the staff member. */
    fun replay(staff: Player, target: Player): Boolean {
        val buf = buffers[target.uniqueId] ?: return false
        if (buf.size < 10) return false
        stopReplay(staff.uniqueId)
        val path = buf.map { it.loc.clone() }
        staff.sendMessage(plugin.configs.msg("replay-start",
            "player" to target.name,
            "seconds" to "%.1f".format(secondsRecorded(target.uniqueId))))
        var index = 0
        val taskId = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            if (!staff.isOnline || !target.isOnline || index >= path.size) {
                stopReplay(staff.uniqueId)
                if (staff.isOnline) staff.sendMessage(plugin.configs.msg("replay-end", "player" to target.name))
                return@Runnable
            }
            staff.teleport(path[index])
            index++
        }, 0L, 2L).taskId
        replays[staff.uniqueId] = taskId
        return true
    }

    fun stopReplay(staff: UUID) {
        replays.remove(staff)?.let { plugin.server.scheduler.cancelTask(it) }
    }

    fun isReplaying(staff: UUID): Boolean = replays.containsKey(staff)
}
