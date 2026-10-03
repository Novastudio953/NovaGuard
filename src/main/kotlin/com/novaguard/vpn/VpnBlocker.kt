package com.novaguard.vpn

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * VPN/proxy/hosting blocker. Checks the connecting IP against the free
 * ip-api.com database (proxy + hosting flags), cached for 6 hours.
 * Fails OPEN: if the API is down, nobody gets blocked.
 */
object VpnBlocker {

    private val client: HttpClient by lazy { HttpClient.newHttpClient() }
    private val cache = ConcurrentHashMap<String, Pair<Boolean, Long>>()
    private const val CACHE_MS = 6 * 3600_000L

    fun isBlocked(ip: String): Boolean {
        if (isLocal(ip)) return false
        val now = System.currentTimeMillis()
        cache[ip]?.let { (blocked, at) -> if (now - at < CACHE_MS) return blocked }
        val blocked = lookup(ip)
        cache[ip] = blocked to now
        return blocked
    }

    private fun isLocal(ip: String): Boolean =
        ip == "127.0.0.1" || ip == "0:0:0:0:0:0:0:1" || ip == "::1" ||
                ip.startsWith("192.168.") || ip.startsWith("10.") ||
                ip.startsWith("172.16.") || ip.startsWith("172.17.") ||
                ip.startsWith("172.18.") || ip.startsWith("172.19.") ||
                ip.startsWith("172.2") || ip.startsWith("172.30.") || ip.startsWith("172.31.")

    private fun lookup(ip: String): Boolean {
        return try {
            val req = HttpRequest.newBuilder(
                URI.create("http://ip-api.com/json/$ip?fields=status,proxy,hosting"))
                .timeout(Duration.ofSeconds(4))
                .header("User-Agent", "NovaGuard")
                .GET().build()
            val body = client.send(req, HttpResponse.BodyHandlers.ofString())
                .body().replace(" ", "")
            body.contains("\"proxy\":true") || body.contains("\"hosting\":true")
        } catch (_: Exception) {
            false
        }
    }
}
