package com.novaguard.update

import com.novaguard.NovaGuard
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Update checker: compares the running version against the latest
 * GitHub release and notifies staff on join.
 */
class UpdateChecker(private val plugin: NovaGuard) : Listener {

    var enabled: Boolean = true
    @Volatile var latestVersion: String? = null
        private set

    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(8))
        .build()

    fun load() {
        enabled = plugin.config.getBoolean("settings.update-checker.enabled", true)
    }

    fun start() {
        if (!enabled) return
        plugin.server.scheduler.runTaskTimerAsynchronously(plugin, Runnable {
            check()
        }, 100L, 6 * 60 * 60 * 20L) // on boot + every 6h
    }

    private fun check() {
        try {
            val req = HttpRequest.newBuilder(
                URI.create("https://api.github.com/repos/Novastudio953/NovaGuard/releases/latest"))
                .header("User-Agent", "NovaGuard-UpdateChecker")
                .header("Accept", "application/vnd.github+json")
                .timeout(Duration.ofSeconds(10))
                .GET().build()
            val res = client.send(req, HttpResponse.BodyHandlers.ofString())
            if (res.statusCode() != 200) return
            val tag = Regex("\"tag_name\"\\s*:\\s*\"([^\"]+)\"")
                .find(res.body())?.groupValues?.get(1) ?: return
            val latest = tag.trimStart('v', 'V')
            latestVersion = latest
            if (isNewer(latest, currentVersion())) {
                plugin.logger.info("A new NovaGuard version is available: v$latest (you run v${currentVersion()}). " +
                        "Download: https://github.com/Novastudio953/NovaGuard/releases")
            }
        } catch (_: Exception) { /* fail silently, offline-safe */ }
    }

    @EventHandler
    fun onJoin(e: PlayerJoinEvent) {
        if (!enabled) return
        val latest = latestVersion ?: return
        if (!isNewer(latest, currentVersion())) return
        val p = e.player
        if (!p.hasPermission("novaguard.admin")) return
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            if (p.isOnline) p.sendMessage(plugin.configs.msg("update-available",
                "latest" to latest, "current" to currentVersion()))
        }, 60L)
    }

    fun isUpdateAvailable(): Boolean {
        val latest = latestVersion ?: return false
        return isNewer(latest, currentVersion())
    }

    private fun currentVersion(): String = plugin.pluginMeta.version.trimStart('v', 'V')

    private fun isNewer(latest: String, current: String): Boolean {
        val l = latest.split(".").map { it.toIntOrNull() ?: 0 }
        val c = current.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(l.size, c.size)) {
            val lv = l.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (lv != cv) return lv > cv
        }
        return false
    }
}
