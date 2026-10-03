package com.novaguard.internal

import com.novaguard.NovaGuard
import java.io.File
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.jar.JarFile

/**
 * Internal consistency validator. Ensures the runtime jar matches the
 * build the studio shipped: same author metadata, same sealed contents.
 */
object CoreValidator {

    // sealed payload marker (built from codes so it isn't plainly listed)
    private fun marker(): String = intArrayOf(
        77, 69, 84, 65, 45, 73, 78, 70, 47, 78, 79, 86, 65, 71, 85, 65, 82, 68, 46, 83, 70
    ).map { it.toChar() }.joinToString("")

    // expected author tag (built from codes so it isn't plainly listed)
    private fun tag(): String = intArrayOf(
        78, 111, 118, 97, 32, 83, 116, 117, 100, 105, 111
    ).map { it.toChar() }.joinToString("")

    // sealed public component
    private const val K0 =
        "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAvtc4/mt5Uu6ZSHnSZsy3" +
        "H7l+tMdbrDGdGDRo0KbeoMNHz+EJisCwYiueYDtMhH/RMqoqCzafa7Rm+xLjUsOJ" +
        "ReYlgeOHHJ592LO3feEESEeHZ0DEexmnntPOhTzwswd2X2AYiXNIFvYduk2TTFPK" +
        "VTBFJOS0GOSAP3WOg6LO0DkOsMwQRXwrKuANJHcvdx9y+elYJgUsjfsmP2Y98DM" +
        "iC+ujEReXuSUIh3V1FPaDU0mpOL0CBkPiBsoEh0JE7jDQc4WvAA1mUl6NY99lV0" +
        "1ooZ4ltqYDsedE3Hs/5vOJclITA63vw2WvLvkrB9oTdc0dE8eXjFQT/tCTmdcBe" +
        "9nyuQIDAQAB"

    fun check(plugin: NovaGuard): Boolean {
        if (plugin.pluginMeta.authors.none { it.equals(tag(), ignoreCase = true) }) {
            plugin.logger.severe("[NovaGuard] Internal consistency check failed (tag).")
            return false
        }
        return try {
            val jarFile = File(plugin.javaClass.protectionDomain.codeSource.location.toURI())
            if (!jarFile.isFile) return true
            checkJar(jarFile) { plugin.logger.severe(it) }
        } catch (e: Exception) {
            plugin.logger.severe("[NovaGuard] Internal consistency check failed: ${e.message}")
            false
        }
    }

    fun checkJar(jarFile: File, log: (String) -> Unit = {}): Boolean {
        return try {
            val m = marker()
            val jar = JarFile(jarFile)
            try {
                val sigEntry = jar.getJarEntry(m) ?: run {
                    log("[NovaGuard] Internal consistency check failed (seal).")
                    return false
                }
                val sigBytes = Base64.getDecoder().decode(
                    jar.getInputStream(sigEntry).readBytes().toString(Charsets.UTF_8).trim()
                )
                val index = buildIndex(jar, m)
                val sig = Signature.getInstance("SHA256withRSA")
                sig.initVerify(key())
                sig.update(index.toByteArray(Charsets.UTF_8))
                val ok = sig.verify(sigBytes)
                if (!ok) log("[NovaGuard] Internal consistency check failed (integrity).")
                ok
            } finally {
                jar.close()
            }
        } catch (e: Exception) {
            log("[NovaGuard] Internal consistency check failed: ${e.message}")
            false
        }
    }

    fun buildIndex(jar: JarFile, marker: String = marker()): String {
        val names = jar.entries().asSequence()
            .map { it.name }
            .filter { it != marker }
            .sorted()
            .toList()
        val md = MessageDigest.getInstance("SHA-256")
        val sb = StringBuilder()
        for (name in names) {
            val bytes = jar.getInputStream(jar.getJarEntry(name)).readBytes()
            md.reset()
            md.update(name.toByteArray(Charsets.UTF_8))
            md.update(bytes)
            sb.append(name).append(':')
                .append(md.digest().joinToString("") { "%02x".format(it) })
                .append('\n')
        }
        return sb.toString()
    }

    private fun key(): java.security.PublicKey {
        val bytes = Base64.getDecoder().decode(K0)
        return KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(bytes))
    }
}
