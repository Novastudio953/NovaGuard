package com.novaguard

import com.novaguard.alert.AlertManager
import com.novaguard.check.CheckManager
import com.novaguard.command.GuardCommand
import com.novaguard.data.DataManager
import com.novaguard.listener.BlockListener
import com.novaguard.listener.CombatListener
import com.novaguard.listener.MoveListener
import com.novaguard.listener.PlayerListener
import com.novaguard.punish.PunishmentManager
import org.bukkit.plugin.java.JavaPlugin

class NovaGuard : JavaPlugin() {

    lateinit var data: DataManager
        private set
    lateinit var checks: CheckManager
        private set
    lateinit var alerts: AlertManager
        private set
    lateinit var punishments: PunishmentManager
        private set

    override fun onEnable() {
        saveDefaultConfig()
        data = DataManager()
        checks = CheckManager(this)
        alerts = AlertManager(this)
        punishments = PunishmentManager(this)
        checks.load()

        server.pluginManager.registerEvents(MoveListener(this), this)
        server.pluginManager.registerEvents(CombatListener(this), this)
        server.pluginManager.registerEvents(BlockListener(this), this)
        server.pluginManager.registerEvents(PlayerListener(this), this)

        val cmd = GuardCommand(this)
        getCommand("novaguard")?.setExecutor(cmd)
        getCommand("novaguard")?.tabCompleter = cmd

        // VL decay task
        server.scheduler.runTaskTimerAsynchronously(this, Runnable {
            data.decayVls(config.getDouble("settings.vl-decay-per-minute", 1.0))
        }, 1200L, 1200L)

        logger.info("NovaGuard v${description.version} enabled with ${checks.all.size} checks.")
    }

    override fun onDisable() {
        data.clear()
        logger.info("NovaGuard disabled.")
    }

    fun reloadAll() {
        reloadConfig()
        checks.load()
        alerts.load()
        punishments.load()
    }
}
