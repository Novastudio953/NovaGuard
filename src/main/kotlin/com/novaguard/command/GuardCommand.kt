package com.novaguard.command

import com.novaguard.NovaGuard
import com.novaguard.check.CheckType
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class GuardCommand(private val plugin: NovaGuard) : CommandExecutor, TabCompleter {

    private fun msg(key: String, vararg vars: Pair<String, String>) =
        plugin.configs.msg(key, *vars)

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!sender.hasPermission("novaguard.admin")) {
            sender.sendMessage(msg("no-permission"))
            return true
        }
        if (args.isEmpty()) {
            sender.sendMessage(msg("usage", "usage" to "/novaguard <reload|list|toggle|vl|vlreset|alerts|freeze|wave|wavelist|reports|history>"))
            return true
        }
        when (args[0].lowercase()) {
            "reload" -> {
                plugin.reloadAll()
                sender.sendMessage(msg("reloaded"))
            }
            "list" -> {
                sender.sendMessage("§8[§3🛡 §fNovaGuard§8] §7Registered checks:")
                for (type in CheckType.values()) {
                    val line = plugin.checks.byType(type).joinToString("§8, ") {
                        (if (it.enabled) "§a" else "§c") + it.id
                    }
                    sender.sendMessage("§8▸ §7${type.name}: $line")
                }
            }
            "toggle" -> {
                if (args.size < 2) { sender.sendMessage(msg("usage", "usage" to "/novaguard toggle <check>")); return true }
                val check = plugin.checks.get(args[1])
                if (check == null) { sender.sendMessage(msg("unknown-check", "check" to args[1])); return true }
                check.enabled = !check.enabled
                plugin.configs.checks.set("checks.${check.id}.enabled", check.enabled)
                plugin.configs.saveChecks()
                val state = msg(if (check.enabled) "state-enabled" else "state-disabled")
                sender.sendMessage(msg("check-toggled", "check" to check.id, "state" to state))
            }
            "vl" -> {
                if (args.size < 2) { sender.sendMessage(msg("usage", "usage" to "/novaguard vl <player>")); return true }
                val target = Bukkit.getPlayer(args[1])
                if (target == null) { sender.sendMessage(msg("player-not-online")); return true }
                val snap = plugin.data.get(target.uniqueId).vlSnapshot()
                if (snap.isEmpty()) sender.sendMessage(msg("vl-empty", "player" to target.name))
                else {
                    sender.sendMessage(msg("vl-header", "player" to target.name))
                    snap.entries.sortedByDescending { it.value }.forEach { (id, vl) ->
                        sender.sendMessage(msg("vl-line", "check" to id, "vl" to "%.1f".format(vl)))
                    }
                }
            }
            "vlreset" -> {
                if (args.size < 2) { sender.sendMessage(msg("usage", "usage" to "/novaguard vlreset <player>")); return true }
                val target = Bukkit.getPlayer(args[1])
                if (target == null) { sender.sendMessage(msg("player-not-online")); return true }
                plugin.data.get(target.uniqueId).resetAllVls()
                sender.sendMessage(msg("vl-cleared", "player" to target.name))
            }
            "alerts" -> {
                val p = sender as? Player ?: run {
                    sender.sendMessage(msg("player-only")); return true
                }
                val d = plugin.data.get(p.uniqueId)
                d.alertsEnabled = !d.alertsEnabled
                sender.sendMessage(msg(if (d.alertsEnabled) "alerts-on" else "alerts-off"))
            }
            "freeze" -> {
                if (args.size < 2) { sender.sendMessage(msg("usage", "usage" to "/novaguard freeze <player>")); return true }
                val target = Bukkit.getPlayer(args[1])
                if (target == null) { sender.sendMessage(msg("player-not-online")); return true }
                val staff = sender as? Player ?: run {
                    sender.sendMessage(msg("player-only")); return true
                }
                plugin.staff.toggleFreeze(staff, target)
            }
            "wave" -> {
                plugin.staff.banWave(sender)
            }
            "wavelist" -> {
                if (plugin.staff.waveList.isEmpty()) { sender.sendMessage(msg("wave-empty")); return true }
                sender.sendMessage(msg("wave-header"))
                plugin.staff.waveList.forEach { uuid ->
                    sender.sendMessage("§8▸ §f${Bukkit.getOfflinePlayer(uuid).name ?: uuid}")
                }
            }
            "reports" -> {
                val p = sender as? Player ?: run {
                    sender.sendMessage(msg("player-only")); return true
                }
                plugin.staff.openReports(p)
            }
            "history" -> {
                if (args.size < 2) { sender.sendMessage(msg("usage", "usage" to "/novaguard history <player>")); return true }
                val p = sender as? Player ?: run {
                    sender.sendMessage(msg("player-only")); return true
                }
                val target = Bukkit.getPlayer(args[1])
                if (target == null) { sender.sendMessage(msg("player-not-online")); return true }
                plugin.staff.openHistory(p, target)
            }
            else -> sender.sendMessage(msg("usage", "usage" to "/novaguard <reload|list|toggle|vl|vlreset|alerts|freeze|wave|wavelist|reports|history>"))
        }
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        if (!sender.hasPermission("novaguard.admin")) return emptyList()
        if (args.size == 1) return listOf("reload", "list", "toggle", "vl", "vlreset",
            "alerts", "freeze", "wave", "wavelist", "reports", "history")
            .filter { it.startsWith(args[0].lowercase()) }
        if (args.size == 2 && args[0].equals("toggle", true))
            return plugin.checks.all.map { it.id }.filter { it.startsWith(args[1].lowercase()) }
        return emptyList()
    }
}
