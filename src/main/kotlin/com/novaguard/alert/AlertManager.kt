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

    fun load() {
        enabled = plugin.config.getBoolean("settings.alerts-enabled", true)
    }

    fun alert(player: Player, check: Check, vl: Double, info: String) {
        if (!enabled) return
        val prefix = color(plugin.configs.msg("alert-format",
            "player" to player.name,
            "check" to check.displayName,
            "vl" to "%.1f".format(vl),
            "info" to info))
        val hover = Component.text()
            .append(Component.text("NovaGuard", NamedTextColor.AQUA, TextDecoration.BOLD)
            .append(Component.text(" Detection", NamedTextColor.DARK_AQUA, TextDecoration.BOLD))).append(Component.newline())
            .append(Component.text("──────────────────", NamedTextColor.DARK_GRAY)).append(Component.newline())
            .append(Component.text("Player  ", NamedTextColor.GRAY))
            .append(Component.text(player.name, NamedTextColor.WHITE)).append(Component.newline())
            .append(Component.text("Check   ", NamedTextColor.GRAY))
            .append(Component.text(check.displayName, NamedTextColor.RED)).append(Component.newline())
            .append(Component.text("Type     ", NamedTextColor.GRAY))
            .append(Component.text(check.type.name, NamedTextColor.GOLD)).append(Component.newline())
            .append(Component.text("Level    ", NamedTextColor.GRAY))
            .append(Component.text("%.1f".format(vl) + " VL", NamedTextColor.YELLOW)).append(Component.newline())
            .append(Component.text("Ping     ", NamedTextColor.GRAY))
            .append(Component.text("${safePing(player)} ms", NamedTextColor.YELLOW))
            .append(Component.newline())
            .append(Component.text("──────────────────", NamedTextColor.DARK_GRAY)).append(Component.newline())
            .append(Component.text("Click to teleport to player", NamedTextColor.GREEN, TextDecoration.ITALIC))
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
        // § color code support (messages.yml already converts & -> §)
        val codes = mapOf(
            "§0" to NamedTextColor.BLACK, "§1" to NamedTextColor.DARK_BLUE,
            "§2" to NamedTextColor.DARK_GREEN, "§3" to NamedTextColor.DARK_AQUA,
            "§4" to NamedTextColor.DARK_RED, "§5" to NamedTextColor.DARK_PURPLE,
            "§6" to NamedTextColor.GOLD, "§7" to NamedTextColor.GRAY,
            "§8" to NamedTextColor.DARK_GRAY, "§9" to NamedTextColor.BLUE,
            "§a" to NamedTextColor.GREEN, "§b" to NamedTextColor.AQUA,
            "§c" to NamedTextColor.RED, "§d" to NamedTextColor.LIGHT_PURPLE,
            "§e" to NamedTextColor.YELLOW, "§f" to NamedTextColor.WHITE,
            "§l" to TextDecoration.BOLD
        )
        var comp = Component.empty()
        var current: Any = NamedTextColor.WHITE
        var bold = false
        var buf = StringBuilder()
        var i = 0
        fun flush() {
            if (buf.isNotEmpty()) {
                var c = Component.text(buf.toString(), current as NamedTextColor)
                if (bold) c = c.decorate(TextDecoration.BOLD)
                comp = comp.append(c)
                buf = StringBuilder()
            }
        }
        while (i < s.length) {
            if (s[i] == '§' && i + 1 < s.length) {
                val code = s.substring(i, i + 2).lowercase()
                if (code == "§r") {
                    flush(); current = NamedTextColor.WHITE; bold = false; i += 2; continue
                }
                if (codes.containsKey(code)) {
                    flush()
                    val v = codes[code]!!
                    if (v is TextDecoration) bold = true else { current = v; }
                    i += 2
                    continue
                }
            }
            buf.append(s[i]); i++
        }
        flush()
        return comp
    }
}
