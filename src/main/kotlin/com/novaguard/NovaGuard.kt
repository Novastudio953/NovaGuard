package com.novaguard

import com.novaguard.alert.AlertManager
import com.novaguard.check.CheckManager
import com.novaguard.checks.ExtraChecks
import com.novaguard.command.GuardCommand
import com.novaguard.command.ReportCommand
import com.novaguard.config.ConfigManager
import com.novaguard.data.DataManager
import com.novaguard.listener.BlockListener
import com.novaguard.listener.CombatListener
import com.novaguard.listener.ExtraListener
import com.novaguard.listener.MoveListener
import com.novaguard.listener.PlayerListener
import com.novaguard.punish.PunishmentManager
import com.novaguard.staff.StaffTools
import com.novaguard.xray.XrayManager
import org.bukkit.plugin.java.JavaPlugin

class NovaGuard : JavaPlugin() {

    lateinit var data: DataManager
        private set
    lateinit var xray: XrayManager
        private set
    lateinit var checks: CheckManager
        private set
    lateinit var alerts: AlertManager
        private set
    lateinit var punishments: PunishmentManager
        private set
    lateinit var staff: StaffTools
        private set
    lateinit var configs: ConfigManager
        private set

    /** Bedrock (Geyser) leniency state, loaded from config. */
    var bedrockLeniency: Boolean = true
    var bedrockExempt: Set<String> = emptySet()

    override fun onEnable() {
        saveDefaultConfig()
        com.novaguard.support.BedrockSupport.init(this)
        configs = ConfigManager(this)
        configs.load()
        data = DataManager()
        xray = XrayManager(this)          // before CheckManager (registers xray check)
        checks = CheckManager(this)
        alerts = AlertManager(this)
        punishments = PunishmentManager(this)
        staff = StaffTools(this)
        checks.load()
        alerts.load()
        punishments.load()
        xray.start()

        server.pluginManager.registerEvents(MoveListener(this), this)
        server.pluginManager.registerEvents(CombatListener(this), this)
        server.pluginManager.registerEvents(BlockListener(this), this)
        server.pluginManager.registerEvents(PlayerListener(this), this)
        server.pluginManager.registerEvents(ExtraListener(this), this)

        val cmd = GuardCommand(this)
        getCommand("novaguard")?.setExecutor(cmd)
        getCommand("novaguard")?.tabCompleter = cmd
        val report = ReportCommand(this)
        getCommand("report")?.setExecutor(report)
        getCommand("report")?.tabCompleter = report

        // VL decay task
        server.scheduler.runTaskTimerAsynchronously(this, Runnable {
            data.decayVls(config.getDouble("settings.vl-decay-per-minute", 1.0))
        }, 1200L, 1200L)

        // illegal item scanner
        val scanSecs = config.getLong("settings.illegal-scan-seconds", 60)
        server.scheduler.runTaskTimerAsynchronously(this, Runnable {
            val check = checks.get("illegals") as? ExtraChecks.IllegalItems ?: return@Runnable
            if (!check.enabled) return@Runnable
            for (p in server.onlinePlayers) {
                val d = data.get(p.uniqueId)
                if (d.isExempt(p)) continue
                server.scheduler.runTask(this, Runnable { check.scan(p, d) })
            }
        }, scanSecs * 20, scanSecs * 20)

        logger.info("NovaGuard v${pluginMeta.version} enabled with ${checks.all.size} checks.")
    }

    override fun onDisable() {
        data.clear()
        logger.info("NovaGuard disabled.")
    }

    fun reloadAll() {
        reloadConfig()
        configs.load()
        checks.load()
        alerts.load()
        punishments.load()
    }
}
