package com.novaguard.listener

import com.novaguard.NovaGuard
import com.novaguard.checks.ExtraChecks
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityResurrectEvent
import org.bukkit.event.entity.EntityShootBowEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerEditBookEvent
import org.bukkit.event.player.PlayerInteractEvent

class ExtraListener(private val plugin: NovaGuard) : Listener {

    private inline fun <reified T : com.novaguard.check.Check> check(id: String): T? {
        val c = plugin.checks.get(id) as? T
        return if (c?.enabled == true) c else null
    }

    @EventHandler(ignoreCancelled = true)
    fun onInteract(e: PlayerInteractEvent) {
        val p = e.player
        val d = plugin.data.get(p.uniqueId)
        if (d.isExempt(p)) return
        val item = e.item

        // crystal place detection
        if (item?.type == Material.END_CRYSTAL && e.clickedBlock != null) {
            val t = e.clickedBlock!!.type
            if (t == Material.OBSIDIAN || t == Material.BEDROCK) {
                check<ExtraChecks.CrystalAura>("crystalaura")?.onPlace(p, d)
            }
        }
        // bow draw tracking
        if (item?.type == Material.BOW || item?.type == Material.CROSSBOW) {
            check<ExtraChecks.NoSlow>("noslow")?.onBowDraw(d)
        }
        // ghost hand
        check<ExtraChecks.GhostHand>("ghosthand")?.onInteract(p, d, e)
    }

    @EventHandler
    fun onShoot(e: EntityShootBowEvent) {
        val p = e.entity as? Player ?: return
        val d = plugin.data.get(p.uniqueId)
        if (d.isExempt(p)) return
        check<ExtraChecks.FastBow>("fastbow")?.onShoot(p, d, e)
        (plugin.checks.get("noslow") as? ExtraChecks.NoSlow)?.let {
            if (it.enabled) it.onBowShoot(d)
        }
    }

    @EventHandler
    fun onResurrect(e: EntityResurrectEvent) {
        val p = e.entity as? Player ?: return
        val d = plugin.data.get(p.uniqueId)
        if (d.isExempt(p)) return
        check<ExtraChecks.AutoTotem>("autototem")?.onPop(p, d, e)
    }

    @EventHandler
    fun onInventoryClick(e: InventoryClickEvent) {
        val p = e.whoClicked as? Player ?: return
        // staff GUI handling first
        val title = e.view.title
        if (title.startsWith("§8[§b§lNova§3§lGuard§8]")) {
            e.isCancelled = true
            if (title.endsWith("Player Reports")) {
                val slot = e.rawSlot
                if (e.isRightClick) {
                    plugin.staff.dismissReport(slot)
                    p.sendMessage(plugin.configs.msg("report-dismissed"))
                    p.closeInventory()
                } else {
                    val target = plugin.staff.reportTargetAtSlot(slot)
                    if (target != null) {
                        p.closeInventory()
                        p.performCommand("tp $target")
                    }
                }
            } else if (title.endsWith("Suspects")) {
                val slot = e.rawSlot
                val uuid = plugin.staff.suspectAtSlot(p.uniqueId, slot) ?: return
                val target = org.bukkit.Bukkit.getPlayer(uuid) ?: return
                p.closeInventory()
                if (e.isRightClick) {
                    plugin.staff.openHistory(p, target)
                } else {
                    p.performCommand("tp ${target.name}")
                }
            }
            return
        }
        val d = plugin.data.get(p.uniqueId)
        if (d.isExempt(p)) return
        check<ExtraChecks.ChestStealer>("cheststealer")?.onTake(p, d, e)
        check<ExtraChecks.InventoryClicker>("inventoryclicker")?.onClick(p, d)
        // totem equip tracking: totem moved to offhand (slot 40)
        if (e.currentItem?.type == Material.TOTEM_OF_UNDYING && e.slot == 40) {
            (plugin.checks.get("autototem") as? ExtraChecks.AutoTotem)?.let {
                if (it.enabled) it.onTotemEquip(d)
            }
        }
    }

    @EventHandler
    fun onEditBook(e: PlayerEditBookEvent) {
        val p = e.player
        val d = plugin.data.get(p.uniqueId)
        if (d.isExempt(p)) return
        check<ExtraChecks.BookBan>("bookban")?.onEdit(p, d, e)
    }
}
