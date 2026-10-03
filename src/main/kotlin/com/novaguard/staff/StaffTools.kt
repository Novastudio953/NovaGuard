package com.novaguard.staff

import com.novaguard.NovaGuard
import com.novaguard.data.ViolationRecord
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID

data class Report(val reporter: String, val target: String, val reason: String, val time: Long)

/** Staff tooling: reports, freeze, ban waves, history GUIs, Discord webhooks. */
class StaffTools(private val plugin: NovaGuard) {

    val reports = mutableListOf<Report>()
    val waveList = mutableSetOf<UUID>()

    // ---------- /report ----------
    fun fileReport(reporter: Player, targetName: String, reason: String) {
        reports.add(Report(reporter.name, targetName, reason, System.currentTimeMillis()))
        if (reports.size > 100) reports.removeAt(0)
        val msg = "§8[§cNovaGuard§8] §f${reporter.name} §7reported §f$targetName§8: §7$reason"
        for (s in Bukkit.getOnlinePlayers()) {
            if (s.hasPermission("novaguard.admin")) s.sendMessage(msg)
        }
        reporter.sendMessage("§aReport filed. Staff have been notified.")
    }

    // ---------- freeze ----------
    fun toggleFreeze(staff: Player, target: Player): Boolean {
        val d = plugin.data.get(target.uniqueId)
        d.frozen = !d.frozen
        if (d.frozen) {
            target.sendMessage("§cYou have been frozen by staff. Do not move or log out.")
            staff.sendMessage("§7Froze §f${target.name}§7.")
        } else {
            target.sendMessage("§aYou have been unfrozen.")
            staff.sendMessage("§7Unfroze §f${target.name}§7.")
        }
        return d.frozen
    }

    // ---------- ban wave ----------
    fun banWave(sender: org.bukkit.command.CommandSender): Int {
        var count = 0
        for (uuid in waveList.toList()) {
            val p = Bukkit.getPlayer(uuid)
            val name = p?.name ?: Bukkit.getOfflinePlayer(uuid).name ?: uuid.toString()
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ban $name Cheating (NovaGuard ban wave)")
            p?.kickPlayer("§cBanned by NovaGuard ban wave.")
            count++
        }
        waveList.clear()
        sender.sendMessage("§8[§cNovaGuard§8] §7Ban wave executed: §c$count §7players banned.")
        DiscordHook.send(plugin, "🌊 **Ban wave executed:** $count players banned.")
        return count
    }

    // ---------- history GUI ----------
    fun openHistory(staff: Player, target: Player) {
        val history: List<ViolationRecord> = plugin.data.get(target.uniqueId).historySnapshot()
        val inv = Bukkit.createInventory(null, 54, "§8NG History: ${target.name}")
        val sorted = history.sortedByDescending { it.time }.take(45)
        for ((i, rec) in sorted.withIndex()) {
            val item = ItemStack(Material.PAPER)
            val meta = item.itemMeta
            meta.displayName(Component.text(rec.checkId, NamedTextColor.RED))
            val age = (System.currentTimeMillis() - rec.time) / 1000
            meta.lore(listOf(
                Component.text("VL: ${"%.1f".format(rec.vl)}", NamedTextColor.YELLOW),
                Component.text("${age}s ago", NamedTextColor.GRAY),
                Component.text(rec.info.take(40), NamedTextColor.DARK_GRAY)
            ))
            item.itemMeta = meta
            inv.setItem(i, item)
        }
        // summary
        val total = ItemStack(Material.BOOK)
        val tm = total.itemMeta
        tm.displayName(Component.text("Total VL", NamedTextColor.GOLD))
        val totalVl = plugin.checks.all.sumOf { plugin.data.get(target.uniqueId).getVl(it.id) }
        tm.lore(listOf(Component.text("%.1f".format(totalVl), NamedTextColor.YELLOW)))
        total.itemMeta = tm
        inv.setItem(53, total)
        staff.openInventory(inv)
    }

    // ---------- reports GUI ----------
    fun openReports(staff: Player) {
        val inv = Bukkit.createInventory(null, 54, "§8NG Reports")
        for ((i, rep) in reports.take(45).withIndex()) {
            val item = ItemStack(Material.SKELETON_SKULL)
            val meta = item.itemMeta
            meta.displayName(Component.text(rep.target, NamedTextColor.RED))
            meta.lore(listOf(
                Component.text("By: ${rep.reporter}", NamedTextColor.GRAY),
                Component.text(rep.reason.take(40), NamedTextColor.WHITE),
                Component.text("Click to teleport", NamedTextColor.GREEN)
            ))
            item.itemMeta = meta
            // stash target name for click handling
            inv.setItem(i, item)
        }
        staff.openInventory(inv)
    }

    fun reportTargetAtSlot(slot: Int): String? = reports.getOrNull(slot)?.target
    fun dismissReport(slot: Int) { if (slot < reports.size) reports.removeAt(slot) }
}

/** Fire-and-forget Discord webhook posts. */
object DiscordHook {
    private val client: HttpClient by lazy { HttpClient.newHttpClient() }

    fun send(plugin: NovaGuard, content: String) {
        val url = plugin.config.getString("settings.discord-webhook", "") ?: ""
        if (url.isBlank() || url == "none") return
        try {
            val safe = content.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").take(1800)
            val body = "{\"content\":\"$safe\"}"
            val req = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(5))
                .build()
            client.sendAsync(req, HttpResponse.BodyHandlers.discarding())
        } catch (_: Exception) { }
    }
}
