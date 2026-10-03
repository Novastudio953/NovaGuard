package com.novaguard.staff

import com.novaguard.NovaGuard
import com.novaguard.data.ViolationRecord
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID
import org.bukkit.inventory.meta.SkullMeta

data class Report(val reporter: String, val target: String, val reason: String, val time: Long)

/** Staff tooling: reports, freeze, ban waves, history GUIs, Discord webhooks. */
class StaffTools(private val plugin: NovaGuard) {

    val reports = mutableListOf<Report>()
    val waveList = mutableSetOf<UUID>()

    private fun msg(key: String, vararg vars: Pair<String, String>) =
        plugin.configs.msg(key, *vars)

    // ---------- /report ----------
    fun fileReport(reporter: Player, targetName: String, reason: String) {
        reports.add(Report(reporter.name, targetName, reason, System.currentTimeMillis()))
        if (reports.size > 100) reports.removeAt(0)
        val notify = msg("report-notify", "reporter" to reporter.name,
            "target" to targetName, "reason" to reason)
        for (s in Bukkit.getOnlinePlayers()) {
            if (s.hasPermission("novaguard.admin")) s.sendMessage(notify)
        }
        reporter.sendMessage(msg("report-filed"))
    }

    // ---------- freeze ----------
    fun toggleFreeze(staff: Player, target: Player): Boolean {
        val d = plugin.data.get(target.uniqueId)
        d.frozen = !d.frozen
        if (d.frozen) {
            target.sendMessage(msg("freeze-target-frozen"))
            staff.sendMessage(msg("freeze-staff-frozen", "player" to target.name))
        } else {
            target.sendMessage(msg("freeze-target-unfrozen"))
            staff.sendMessage(msg("freeze-staff-unfrozen", "player" to target.name))
        }
        return d.frozen
    }

    // ---------- ban wave ----------
    fun banWave(sender: CommandSender): Int {
        var count = 0
        for (uuid in waveList.toList()) {
            val p = Bukkit.getPlayer(uuid)
            val name = p?.name ?: Bukkit.getOfflinePlayer(uuid).name ?: uuid.toString()
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ban $name Cheating (NovaGuard ban wave)")
            p?.kickPlayer(msg("wave-kick"))
            count++
        }
        waveList.clear()
        sender.sendMessage(msg("wave-executed", "count" to count.toString()))
        DiscordHook.send(plugin, msg("wave-discord", "count" to count.toString())
            .replace("§", "").replace(Regex("&[0-9a-fk-or]"), ""))
        return count
    }

    // ---------- GUI helpers ----------
    private fun border(inv: org.bukkit.inventory.Inventory) {
        val pane = ItemStack(Material.BLACK_STAINED_GLASS_PANE)
        val meta = pane.itemMeta
        meta.displayName(Component.text(" "))
        pane.itemMeta = meta
        for (i in 0 until 9) inv.setItem(i, pane)
        for (i in inv.size - 9 until inv.size) inv.setItem(i, pane)
    }

    private fun title(text: String) = "§8[§b§lNova§3§lGuard§8] §f$text"

    // ---------- history GUI ----------
    fun openHistory(staff: Player, target: Player) {
        val history: List<ViolationRecord> = plugin.data.get(target.uniqueId).historySnapshot()
        val inv = Bukkit.createInventory(null, 54, title("Violation History"))
        border(inv)
        val sorted = history.sortedByDescending { it.time }.take(36)
        for ((i, rec) in sorted.withIndex()) {
            val item = ItemStack(Material.PAPER)
            val meta = item.itemMeta
            meta.displayName(Component.text(rec.checkId, NamedTextColor.RED, TextDecoration.BOLD))
            val age = (System.currentTimeMillis() - rec.time) / 1000
            val ago = when {
                age < 60 -> "${age}s ago"
                age < 3600 -> "${age / 60}m ago"
                else -> "${age / 3600}h ago"
            }
            meta.lore(listOf(
                Component.text("Severity  ", NamedTextColor.GRAY)
                    .append(Component.text("%.1f VL".format(rec.vl), NamedTextColor.YELLOW)),
                Component.text("When  ", NamedTextColor.GRAY)
                    .append(Component.text(ago, NamedTextColor.GRAY)),
                Component.text(rec.info.take(42), NamedTextColor.DARK_GRAY)
            ))
            item.itemMeta = meta
            inv.setItem(9 + i, item)
        }
        // summary diamond
        val total = ItemStack(Material.DIAMOND)
        val tm = total.itemMeta
        tm.displayName(Component.text(target.name, NamedTextColor.AQUA, TextDecoration.BOLD))
        val totalVl = plugin.checks.all.sumOf { plugin.data.get(target.uniqueId).getVl(it.id) }
        val active = plugin.checks.all.count { plugin.data.get(target.uniqueId).getVl(it.id) > 0 }
        tm.lore(listOf(
            Component.text("Total VL  ", NamedTextColor.GRAY)
                .append(Component.text("%.1f".format(totalVl), NamedTextColor.YELLOW)),
            Component.text("Flagged checks  ", NamedTextColor.GRAY)
                .append(Component.text("$active", NamedTextColor.YELLOW)),
            Component.text("Records  ", NamedTextColor.GRAY)
                .append(Component.text("${history.size}", NamedTextColor.YELLOW))
        ))
        total.itemMeta = tm
        inv.setItem(49, total)
        staff.openInventory(inv)
    }

    // ---------- reports GUI ----------
    fun openReports(staff: Player) {
        val inv = Bukkit.createInventory(null, 54, title("Player Reports"))
        border(inv)
        for ((i, rep) in reports.take(36).withIndex()) {
            val item = ItemStack(Material.PLAYER_HEAD)
            val meta = item.itemMeta as org.bukkit.inventory.meta.SkullMeta
            meta.displayName(Component.text(rep.target, NamedTextColor.RED, TextDecoration.BOLD))
            val age = (System.currentTimeMillis() - rep.time) / 1000
            meta.lore(listOf(
                Component.text("Reported by  ", NamedTextColor.GRAY)
                    .append(Component.text(rep.reporter, NamedTextColor.WHITE)),
                Component.text("Reason  ", NamedTextColor.GRAY)
                    .append(Component.text(rep.reason.take(32), NamedTextColor.WHITE)),
                Component.text("${age}s ago", NamedTextColor.DARK_GRAY),
                Component.empty(),
                Component.text("▸ Left-click to teleport", NamedTextColor.GREEN),
                Component.text("▸ Right-click to dismiss", NamedTextColor.RED)
            ))
            item.itemMeta = meta
            inv.setItem(9 + i, item)
        }
        staff.openInventory(inv)
    }

    fun reportTargetAtSlot(slot: Int): String? {
        val idx = slot - 9
        return reports.getOrNull(idx)?.target
    }

    fun dismissReport(slot: Int) {
        val idx = slot - 9
        if (idx in reports.indices) reports.removeAt(idx)
    }

    // ---------- suspects GUI ----------
    private val suspectCache = mutableMapOf<UUID, List<UUID>>()

    fun openSuspects(staff: Player) {
        val threshold = plugin.config.getDouble("settings.sus-vl-threshold", 5.0)
        val suspects = Bukkit.getOnlinePlayers()
            .map { p -> p.uniqueId to plugin.checks.all.sumOf { c -> plugin.data.get(p.uniqueId).getVl(c.id) } }
            .filter { it.second >= threshold }
            .sortedByDescending { it.second }
        suspectCache[staff.uniqueId] = suspects.map { it.first }
        val inv = Bukkit.createInventory(null, 54, title("Suspects"))
        border(inv)
        for ((i, pair) in suspects.take(36).withIndex()) {
            val uuid = pair.first
            val total = pair.second
            val p = Bukkit.getPlayer(uuid) ?: continue
            val d = plugin.data.get(uuid)
            val item = ItemStack(Material.PLAYER_HEAD)
            val meta = item.itemMeta as SkullMeta
            meta.owningPlayer = p
            meta.displayName(Component.text(p.name, NamedTextColor.RED, TextDecoration.BOLD))
            val top = plugin.checks.all
                .map { c -> c.displayName to d.getVl(c.id) }
                .filter { it.second > 0 }
                .sortedByDescending { it.second }
                .take(3)
            val lore = mutableListOf<Component>()
            lore.add(Component.text("Threat  ", NamedTextColor.GRAY)
                .append(Component.text("%.1f VL".format(total), NamedTextColor.YELLOW)))
            val ping = try { p.ping } catch (_: Exception) { -1 }
            lore.add(Component.text("Ping  ", NamedTextColor.GRAY)
                .append(Component.text("$ping ms", NamedTextColor.YELLOW)))
            lore.add(Component.text("Client  ", NamedTextColor.GRAY)
                .append(Component.text(if (d.checkBedrock()) "Bedrock" else "Java", NamedTextColor.AQUA)))
            lore.add(Component.empty())
            lore.add(Component.text("Top detections:", NamedTextColor.GRAY))
            for ((name, vl) in top) {
                lore.add(Component.text("▸ $name  ", NamedTextColor.DARK_GRAY)
                    .append(Component.text("%.1f".format(vl), NamedTextColor.RED)))
            }
            lore.add(Component.empty())
            lore.add(Component.text("▸ Left-click to teleport", NamedTextColor.GREEN))
            lore.add(Component.text("▸ Right-click for history", NamedTextColor.GOLD))
            meta.lore(lore)
            item.itemMeta = meta
            inv.setItem(9 + i, item)
        }
        staff.openInventory(inv)
    }

    fun suspectAtSlot(staff: UUID, slot: Int): UUID? {
        val idx = slot - 9
        return suspectCache[staff]?.getOrNull(idx)
    }
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
