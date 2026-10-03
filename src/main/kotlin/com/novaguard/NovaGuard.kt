package com.novaguard

import com.novaguard.alert.AlertManager
import com.novaguard.check.CheckManager
import com.novaguard.checks.ExtraChecks
import com.novaguard.command.GuardCommand
import com.novaguard.command.ReportCommand
import com.novaguard.command.SusCommand
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
        val sus = SusCommand(this)
        getCommand("sus")?.setExecutor(sus)
        getCommand("sus")?.tabCompleter = sus

        // client brand detection (minecraft:brand channel)
        server.messenger.registerIncomingPluginChannel(this, "minecraft:brand",
            org.bukkit.plugin.messaging.PluginMessageListener { _, player, message ->
                try {
                    val brand = readBrand(message).take(64)
                    val d = data.get(player.uniqueId)
                    if (d.isExempt(player)) return@PluginMessageListener
                    val check = checks.get("clientbrand") as? com.novaguard.checks.MacroChecks.ClientBrand
                    if (check?.enabled == true) {
                        server.scheduler.runTask(this, Runnable {
                            if (player.isOnline) check.onBrand(player, d, brand.ifBlank { "unknown" })
                        })
                    } else d.clientBrand = brand.ifBlank { "unknown" }
                } catch (_: Exception) { }
            })

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
        try {
            server.messenger.unregisterIncomingPluginChannel(this, "minecraft:brand")
        } catch (_: Exception) { }
        data.clear()
        logger.info("NovaGuard disabled.")
    }

    /** Reads a VarInt-prefixed UTF string (the minecraft:brand payload format). */
    private fun readBrand(bytes: ByteArray): String {
        var numRead = 0
        var result = 0
        var idx = 0
        do {
            if (idx >= bytes.size) return "unknown"
            val read = bytes[idx++].toInt()
            result = result or ((read and 0b01111111) shl (7 * numRead))
            numRead++
            if (numRead > 5) return "unknown"
        } while ((read and 0b10000000) != 0)
        if (idx + result > bytes.size) return "unknown"
        return bytes.copyOfRange(idx, idx + result).toString(Charsets.UTF_8)
    }

    fun reloadAll() {
        reloadConfig()
        configs.load()
        checks.load()
        alerts.load()
        punishments.load()
    }
}
