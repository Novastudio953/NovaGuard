package com.novaguard.staff

import com.novaguard.NovaGuard
import com.novaguard.check.Check
import com.novaguard.check.CheckType
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack

/**
 * In-game config GUI: toggle checks live. /novaguard config
 */
class ConfigGui(private val plugin: NovaGuard) {

    private val title = "§8[§b§lNova§3§lGuard§8] §fCheck Config"
    /** slot -> check id for the currently open GUI */
    private val slotMap = mutableMapOf<Int, String>()

    fun isOurGui(viewTitle: String): Boolean = viewTitle == title

    fun open(staff: Player) {
        val inv = Bukkit.createInventory(null, 54, title)
        border(inv)
        slotMap.clear()
        val checks = plugin.checks.all.sortedWith(
            compareBy({ it.type.ordinal }, { it.id }))
        var slot = 10
        for (check in checks) {
            if (slot >= 44) break
            if (slot % 9 == 8) slot++ // skip border column
            inv.setItem(slot, checkItem(check))
            slotMap[slot] = check.id
            slot++
        }
        staff.openInventory(inv)
    }

    private fun checkItem(check: Check): ItemStack {
        val mat = when (check.type) {
            CheckType.MOVEMENT -> Material.FEATHER
            CheckType.COMBAT -> Material.DIAMOND_SWORD
            CheckType.BLOCK -> Material.GRASS_BLOCK
            CheckType.PLAYER -> Material.PLAYER_HEAD
        }
        val item = ItemStack(mat)
        val meta = item.itemMeta
        val stateColor = if (check.enabled) NamedTextColor.GREEN else NamedTextColor.RED
        val stateText = if (check.enabled) "ENABLED" else "DISABLED"
        meta.displayName(Component.text(check.displayName, stateColor, TextDecoration.BOLD))
        meta.lore(listOf(
            Component.text("ID  ", NamedTextColor.GRAY)
                .append(Component.text(check.id, NamedTextColor.DARK_GRAY)),
            Component.text("Type  ", NamedTextColor.GRAY)
                .append(Component.text(check.type.name, NamedTextColor.GOLD)),
            Component.text("Max VL  ", NamedTextColor.GRAY)
                .append(Component.text("%.1f".format(check.maxVl), NamedTextColor.YELLOW)),
            Component.text("State  ", NamedTextColor.GRAY)
                .append(Component.text(stateText, stateColor, TextDecoration.BOLD)),
            Component.text("Click to toggle", NamedTextColor.DARK_GRAY, TextDecoration.ITALIC)
        ))
        item.itemMeta = meta
        return item
    }

    /** Returns true if the click was ours. */
    fun onClick(staff: Player, slot: Int, inv: Inventory): Boolean {
        val checkId = slotMap[slot] ?: return true
        val check = plugin.checks.get(checkId) ?: return true
        check.enabled = !check.enabled
        plugin.configs.checks.set("checks.${check.id}.enabled", check.enabled)
        plugin.configs.saveChecks()
        inv.setItem(slot, checkItem(check))
        staff.sendMessage(plugin.configs.msg("check-toggled",
            "check" to check.id,
            "state" to plugin.configs.msg(if (check.enabled) "state-enabled" else "state-disabled")))
        return true
    }

    private fun border(inv: Inventory) {
        val pane = ItemStack(Material.BLACK_STAINED_GLASS_PANE)
        val meta = pane.itemMeta
        meta.displayName(Component.text(" "))
        pane.itemMeta = meta
        for (i in 0 until 9) inv.setItem(i, pane)
        for (i in inv.size - 9 until inv.size) inv.setItem(i, pane)
        for (i in listOf(9, 17, 18, 26, 27, 35, 36, 44)) inv.setItem(i, pane)
    }
}
