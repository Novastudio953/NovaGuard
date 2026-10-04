package com.novaguard.command

import com.novaguard.NovaGuard
import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter

/**
 * Paper 26.x removed YAML command declarations for paper plugins and
 * JavaPlugin#getCommand throws during startup — that crash killed every
 * 1.8.0 enable. Commands are registered through the COMMANDS lifecycle
 * event instead; the existing executors (and their tab completion)
 * are reused untouched.
 */
class BrigadierBridge(
    private val plugin: NovaGuard,
    private val guard: GuardCommand,
    private val report: ReportCommand,
    private val sus: SusCommand
) {

    fun register() {
        plugin.lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            val registrar = event.registrar()
            registrar.register(
                "novaguard", "NovaGuard admin command", listOf("ng", "guard"),
                bridge(guard, "novaguard", "novaguard.admin"))
            registrar.register(
                "report", "Report a suspected cheater to staff", emptyList(),
                bridge(report, "report", ""))
            registrar.register(
                "sus", "Open the suspects GUI", listOf("suspects"),
                bridge(sus, "sus", "novaguard.sus"))
        }
    }

    private fun bridge(exec: CommandExecutor, label: String, permission: String) =
        object : BasicCommand {
            // executors never touch the Command object; a stub satisfies the signature
            private val dummy = object : Command(label) {
                override fun execute(sender: CommandSender, cmdLabel: String, args: Array<String>): Boolean = false
            }

            override fun execute(stack: CommandSourceStack, args: Array<String>) {
                exec.onCommand(stack.sender, dummy, label, args)
            }

            override fun suggest(stack: CommandSourceStack, args: Array<String>): Collection<String> {
                val completer = exec as? TabCompleter ?: return emptyList()
                return completer.onTabComplete(stack.sender, dummy, label, args) ?: emptyList()
            }

            override fun permission(): String = permission
        }
}
