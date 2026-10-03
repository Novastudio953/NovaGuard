package com.novaguard.support

import com.novaguard.NovaGuard
import java.util.UUID

/**
 * Geyser/Floodgate support: Bedrock players move differently through the
 * Geyser translation layer, so movement checks false-flag them constantly.
 * We detect Floodgate players (reflection = no hard dependency) and let
 * the config exempt them from the unreliable checks.
 */
object BedrockSupport {

    private var floodgatePresent = false
    private var apiInstance: Any? = null
    private var isFloodgatePlayerMethod: java.lang.reflect.Method? = null

    fun init(plugin: NovaGuard) {
        try {
            val apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi")
            apiInstance = apiClass.getMethod("getInstance").invoke(null)
            isFloodgatePlayerMethod = apiInstance!!.javaClass.getMethod("isFloodgatePlayer", UUID::class.java)
            floodgatePresent = true
            plugin.logger.info("[NovaGuard] Floodgate detected — Bedrock leniency active.")
        } catch (_: Exception) {
            plugin.logger.info("[NovaGuard] Floodgate not found — Bedrock leniency disabled.")
        }
    }

    fun isBedrockPlayer(uuid: UUID): Boolean {
        if (!floodgatePresent) return false
        return try {
            isFloodgatePlayerMethod!!.invoke(apiInstance, uuid) as Boolean
        } catch (_: Exception) { false }
    }
}
