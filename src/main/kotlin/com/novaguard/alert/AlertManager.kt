package com.novaguard.alert

import com.novaguard.NovaGuard
import com.novaguard.check.Check
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.entity.Player

class AlertManager(private val plugin: NovaGuard) {

    var enabled: Boolean = true
    var format: String = "&8[&cNovaGuard&8] &f{player} &7failed &c{check} &8(VL: {vl})"

    fun load() {
        enabled = plugin.config.getBoolean("settings.alerts-enabled", true)
        format = plugin.config.getString("settings.alert-format", format)!!
    }

    fun alert(player: Player, check: Check, vl: Double, info: String) {
        if (!enabled) return
        val prefix = color(format
            .replace("{player}", player.name)
            .replace("{check}", check.displayName)
            .replace("{vl}", "%.1f".format(vl))
            .replace("{info}", info))
        val hover = Component.text()
            .append(Component.text("Check: ", NamedTextColor.GRAY))
            .append(Component.text(check.displayName, NamedTextColor.RED)).append(Component.newline())
            .append(Component.text("Player: ", NamedTextColor.GRAY))
            .append(Component.text(player.name, NamedTextColor.WHITE)).append(Component.newline())
            .append(Component.text("VL: ", NamedTextColor.GRAY))
            .append(Component.text("%.1f".format(vl), NamedTextColor.YELLOW)).append(Component.newline())
            .append(Component.text("Ping: ", NamedTextColor.GRAY))
            .append(Component.text("${safePing(player)}ms", NamedTextColor.YELLOW)).append(Component.newline())
            .append(Component.text("Click to teleport", NamedTextColor.GREEN, TextDecoration.ITALIC))
            .build()
        val message = Component.text()
            .append(prefix)
            .clickEvent(ClickEvent.runCommand("/tp ${player.name}"))
            .hoverEvent(HoverEvent.showText(hover))
            .build()
        for (viewer in Bukkit.getOnlinePlayers()) {
            if (!viewer.hasPermission("novaguard.alerts")) continue
            val data = plugin.data.get(viewer.uniqueId)
            if (!data.alertsEnabled) continue
            viewer.sendMessage(message)
        }
        if (plugin.config.getBoolean("settings.log-to-console", true)) {
            plugin.logger.info("[ALERT] ${player.name} failed ${check.displayName} (VL ${"%.1f".format(vl)}) $info")
        }
    }

    private fun safePing(p: Player): Int = try { p.ping } catch (_: Exception) { -1 }

    private fun color(s: String): Component {
        // simple & color code support
        var out = s
        val codes = mapOf(
            "&0" to NamedTextColor.BLACK, "&1" to NamedTextColor.DARK_BLUE,
            "&2" to NamedTextColor.DARK_GREEN, "&3" to NamedTextColor.DARK_AQUA,
            "&4" to NamedTextColor.DARK_RED, "&5" to NamedTextColor.DARK_PURPLE,
            "&6" to NamedTextColor.GOLD, "&7" to NamedTextColor.GRAY,
            "&8" to NamedTextColor.DARK_GRAY, "&9" to NamedTextColor.BLUE,
            "&a" to NamedTextColor.GREEN, "&b" to NamedTextColor.AQUA,
            "&c" to NamedTextColor.RED, "&d" to NamedTextColor.LIGHT_PURPLE,
            "&e" to NamedTextColor.YELLOW, "&f" to NamedTextColor.WHITE
        )
        var comp = Component.empty()
        var current = NamedTextColor.WHITE
        var buf = StringBuilder()
        var i = 0
        fun flush() {
            if (buf.isNotEmpty()) { comp = comp.append(Component.text(buf.toString(), current)); buf = StringBuilder() }
        }
        while (i < out.length) {
            if (out[i] == '&' && i + 1 < out.length) {
                val code = out.substring(i, i + 2).lowercase()
                if (codes.containsKey(code)) { flush(); current = codes[code]!!; i += 2; continue }
            }
            buf.append(out[i]); i++
        }
        flush()
        return comp
    }
}
