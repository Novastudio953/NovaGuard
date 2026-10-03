package com.novaguard.command

import com.novaguard.NovaGuard
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

/** /sus — opens the suspects GUI with every flagged player. */
class SusCommand(private val plugin: NovaGuard) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val p = sender as? Player ?: run {
            sender.sendMessage(plugin.configs.msg("player-only")); return true
        }
        if (!sender.hasPermission("novaguard.sus")) {
            sender.sendMessage(plugin.configs.msg("no-permission"))
            return true
        }
        plugin.staff.openSuspects(p)
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> =
        emptyList()
}
