package com.novaguard.command

import com.novaguard.NovaGuard
import com.novaguard.check.CheckType
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter

class GuardCommand(private val plugin: NovaGuard) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!sender.hasPermission("novaguard.admin")) {
            sender.sendMessage("§cNo permission.")
            return true
        }
        if (args.isEmpty()) {
            sender.sendMessage("§8[§cNovaGuard§8] §7/novaguard <reload|list|toggle|vl|alerts>")
            return true
        }
        when (args[0].lowercase()) {
            "reload" -> {
                plugin.reloadAll()
                sender.sendMessage("§aNovaGuard config reloaded.")
            }
            "list" -> {
                sender.sendMessage("§8[§cNovaGuard§8] §7Checks:")
                for (type in CheckType.values()) {
                    val line = plugin.checks.byType(type).joinToString("§8, ") {
                        (if (it.enabled) "§a" else "§c") + it.id
                    }
                    sender.sendMessage("§7${type.name}: $line")
                }
            }
            "toggle" -> {
                if (args.size < 2) { sender.sendMessage("§cUsage: /novaguard toggle <check>"); return true }
                val check = plugin.checks.get(args[1])
                if (check == null) { sender.sendMessage("§cUnknown check: ${args[1]}"); return true }
                check.enabled = !check.enabled
                plugin.config.set("checks.${check.id}.enabled", check.enabled)
                plugin.saveConfig()
                sender.sendMessage("§7Check §f${check.id} §7is now " + if (check.enabled) "§aENABLED" else "§cDISABLED")
            }
            "vl" -> {
                if (args.size < 2) { sender.sendMessage("§cUsage: /novaguard vl <player>"); return true }
                val target = Bukkit.getPlayer(args[1])
                if (target == null) { sender.sendMessage("§cPlayer not online."); return true }
                val snap = plugin.data.get(target.uniqueId).vlSnapshot()
                if (snap.isEmpty()) sender.sendMessage("§7${target.name} has no violations.")
                else {
                    sender.sendMessage("§8[§cNovaGuard§8] §7VLs for §f${target.name}§7:")
                    snap.entries.sortedByDescending { it.value }.forEach { (id, vl) ->
                        sender.sendMessage("§7- §f$id§8: §e${"%.1f".format(vl)}")
                    }
                }
            }
            "vlreset" -> {
                if (args.size < 2) { sender.sendMessage("§cUsage: /novaguard vlreset <player>"); return true }
                val target = Bukkit.getPlayer(args[1])
                if (target == null) { sender.sendMessage("§cPlayer not online."); return true }
                plugin.data.get(target.uniqueId).resetAllVls()
                sender.sendMessage("§aCleared violations for ${target.name}.")
            }
            "alerts" -> {
                val p = sender as? org.bukkit.entity.Player ?: run {
                    sender.sendMessage("§cPlayers only."); return true
                }
                val d = plugin.data.get(p.uniqueId)
                d.alertsEnabled = !d.alertsEnabled
                sender.sendMessage("§7Alerts " + if (d.alertsEnabled) "§aON" else "§cOFF")
            }
            else -> sender.sendMessage("§cUnknown subcommand.")
        }
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        if (!sender.hasPermission("novaguard.admin")) return emptyList()
        if (args.size == 1) return listOf("reload", "list", "toggle", "vl", "vlreset", "alerts")
            .filter { it.startsWith(args[0].lowercase()) }
        if (args.size == 2 && args[0].equals("toggle", true))
            return plugin.checks.all.map { it.id }.filter { it.startsWith(args[1].lowercase()) }
        return emptyList()
    }
}
