package com.novaguard.command

import com.novaguard.NovaGuard
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

/** /report <player> <reason...> — any player can report a suspected cheater. */
class ReportCommand(private val plugin: NovaGuard) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val p = sender as? Player ?: run {
            sender.sendMessage(plugin.configs.msg("player-only")); return true
        }
        if (args.size < 2) {
            p.sendMessage(plugin.configs.msg("report-usage"))
            return true
        }
        val target = args[0]
        if (Bukkit.getPlayer(target) == null) {
            p.sendMessage(plugin.configs.msg("player-not-online"))
            return true
        }
        if (target.equals(p.name, true)) {
            p.sendMessage(plugin.configs.msg("report-self"))
            return true
        }
        plugin.staff.fileReport(p, target, args.drop(1).joinToString(" "))
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        if (args.size == 1) return Bukkit.getOnlinePlayers().map { it.name }
            .filter { it.startsWith(args[0], true) }
        return emptyList()
    }
}
