package com.novaguard.xray

import com.novaguard.NovaGuard
import com.novaguard.check.Check
import com.novaguard.check.CheckType
import com.novaguard.data.PlayerData
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.event.block.BlockBreakEvent
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * Anti-Xray that works even against texture-pack X-ray:
 *  - Honeypot ores: fake valuables hidden in solid rock. Texture packs
 *    show them as real, but only X-rayers dig straight to them.
 *  - Unexposed-ore tracking: legit miners expose ores before mining them.
 *  - Ore-rate alerts: inhuman diamonds/hour.
 */
class XrayCheck(plugin: NovaGuard) : Check("xray", "Xray", CheckType.PLAYER, plugin)

class XrayManager(private val plugin: NovaGuard) {

    val check = XrayCheck(plugin)
    private val honeypots = ConcurrentHashMap<String, Long>()
    private val valuable = setOf(
        Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE,
        Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE,
        Material.ANCIENT_DEBRIS, Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE,
        Material.NETHERITE_BLOCK
    )

    fun registerHook(register: (Check) -> Unit) = register(check)

    fun start() {
        val interval = plugin.configs.xray.getLong("xray.honeypot-interval-minutes", 10) * 60 * 20
        plugin.server.scheduler.runTaskTimer(plugin, Runnable { plantHoneypots() }, 200L, interval)
    }

    private fun key(b: Block) = "${b.world.name}:${b.x}:${b.y}:${b.z}"

    private fun plantHoneypots() {
        if (!plugin.configs.xray.getBoolean("xray.honeypots-enabled", true)) return
        val perCycle = plugin.configs.xray.getInt("xray.honeypots-per-cycle", 12)
        val maxTotal = plugin.configs.xray.getInt("xray.max-honeypots", 200)
        if (honeypots.size >= maxTotal) return
        val fakes = listOf(Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE,
            Material.EMERALD_ORE, Material.ANCIENT_DEBRIS)
        var planted = 0
        for (world in plugin.server.worlds) {
            if (planted >= perCycle) break
            val chunks = world.loadedChunks.toList()
            if (chunks.isEmpty()) continue
            repeat(4) {
                if (planted >= perCycle) return@repeat
                val chunk = chunks.random()
                val x = chunk.x * 16 + Random.nextInt(16)
                val z = chunk.z * 16 + Random.nextInt(16)
                val y = Random.nextInt(5, 45)
                val block = world.getBlockAt(x, y, z)
                val t = block.type
                if (t != Material.STONE && t != Material.DEEPSLATE && t != Material.NETHERRACK) return@repeat
                // must be fully encased in solid rock (no legit way to see it)
                var encased = true
                for (dx in -1..1) for (dy in -1..1) for (dz in -1..1) {
                    if (dx == 0 && dy == 0 && dz == 0) continue
                    val n = world.getBlockAt(x + dx, y + dy, z + dz).type
                    if (!n.isOccluding) { encased = false; break }
                }
                if (!encased) return@repeat
                // no players nearby (avoid instant legit hits)
                val near = world.players.any { it.location.distanceSquared(block.location) < 256 }
                if (near) return@repeat
                block.type = fakes.random()
                honeypots[key(block)] = System.currentTimeMillis()
                planted++
            }
        }
        if (planted > 0) plugin.logger.info("[NovaGuard] Planted $planted X-ray honeypots.")
    }

    fun onBreak(p: Player, d: PlayerData, e: BlockBreakEvent) {
        if (!check.enabled || d.isExempt(p)) return
        val block = e.block
        val k = key(block)

        // 1) Honeypot hit: only an X-rayer digs straight to encased fakes
        if (honeypots.containsKey(k)) {
            honeypots.remove(k)
            e.isDropItems = false
            block.type = Material.STONE
            d.addVl(check.id, 2.0)
            val vl = d.getVl(check.id)
            plugin.alerts.alert(p, check, vl, "mined honeypot ore")
            d.addHistory(check.id, 2.0, "mined honeypot ore")
            return
        }

        if (block.type !in valuable) return

        // 2) Unexposed-ore tracking
        var exposed = false
        for (dx in -1..1) for (dy in -1..1) for (dz in -1..1) {
            if (dx == 0 && dy == 0 && dz == 0) continue
            if (!block.world.getBlockAt(block.x + dx, block.y + dy, block.z + dz).type.isOccluding) {
                exposed = true; break
            }
        }
        val total = d.addInt("xray_total", 1)
        if (!exposed) d.addInt("xray_unexposed", 1)
        val minSample = plugin.configs.xray.getInt("xray.unexposed-min-sample", 20)
        if (total >= minSample) {
            val ratio = d.getInt("xray_unexposed").toDouble() / total
            if (ratio > plugin.configs.xray.getDouble("xray.unexposed-ratio-threshold", 0.55)) {
                check.flag(p, d, "unexposed ratio ${"%.0f".format(ratio * 100)}% ($total ores)")
                d.setInt("xray_total", 0); d.setInt("xray_unexposed", 0)
            }
        }

        // 3) Diamonds/hour rate
        if (block.type == Material.DIAMOND_ORE || block.type == Material.DEEPSLATE_DIAMOND_ORE) {
            val now = System.currentTimeMillis()
            if (now - d.getLong("xray_dia_window") > 3600_000) {
                d.setLong("xray_dia_window", now); d.setInt("xray_dia", 1)
            } else if (d.addInt("xray_dia", 1) > plugin.configs.xray.getInt("xray.max-diamonds-per-hour", 32)) {
                check.flag(p, d, "${d.getInt("xray_dia")} diamonds/hour")
                d.setInt("xray_dia", 0)
            }
        }
    }
}
