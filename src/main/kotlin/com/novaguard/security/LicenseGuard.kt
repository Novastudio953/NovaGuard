package com.novaguard.security

import com.novaguard.NovaGuard
import java.io.File
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.jar.JarFile

/**
 * Nova Studio license guard. The official jar is signed with Nova Studio's
 * private key at build time; the public key is embedded here.
 *
 * On startup (and every 10 minutes after) the plugin verifies:
 *  1. The plugin author is still "Nova Studio".
 *  2. The jar's RSA signature matches its contents.
 *
 * If someone renames the author or rebuilds/repacks the jar without the
 * private key, verification fails and the plugin shuts itself down.
 */
object LicenseGuard {

    const val AUTHOR = "Nova Studio"
    private const val SIG_ENTRY = "META-INF/NOVAGUARD.SF"

    // Nova Studio public key (the private key never leaves the studio)
    private const val PUBLIC_KEY_B64 =
        "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAvtc4/mt5Uu6ZSHnSZsy3" +
        "H7l+tMdbrDGdGDRo0KbeoMNHz+EJisCwYiueYDtMhH/RMqoqCzafa7Rm+xLjUsOJ" +
        "ReYlgeOHHJ592LO3feEESEeHZ0DEexmnntPOhTzwswd2X2AYiXNIFvYduk2TTFPK" +
        "VTBFJOS0GOSAP3WOg6LO0DkOsMwQRXwrKuANJHcvdx9y+elYJgUsjfsmP2Y98DM" +
        "iC+ujEReXuSUIh3V1FPaDU0mpOL0CBkPiBsoEh0JE7jDQc4WvAA1mUl6NY99lV0" +
        "1ooZ4ltqYDsedE3Hs/5vOJclITA63vw2WvLvkrB9oTdc0dE8eXjFQT/tCTmdcBe" +
        "9nyuQIDAQAB"

    fun verify(plugin: NovaGuard): Boolean {
        // 1) author must be Nova Studio
        if (plugin.pluginMeta.authors.none { it.equals(AUTHOR, ignoreCase = true) }) {
            plugin.logger.severe("[LicenseGuard] Author mismatch — expected '$AUTHOR'.")
            return false
        }
        // 2) jar signature must be valid
        return try {
            val jarFile = File(plugin.javaClass.protectionDomain.codeSource.location.toURI())
            if (!jarFile.isFile) return true // dev/classes run: author check only
            verifyJar(jarFile) { plugin.logger.severe(it) }
        } catch (e: Exception) {
            plugin.logger.severe("[LicenseGuard] Verification error: ${e.message}")
            false
        }
    }

    /** Verifies a jar file's Nova Studio signature. Testable without a server. */
    fun verifyJar(jarFile: File, log: (String) -> Unit = {}): Boolean {
        return try {
            val jar = JarFile(jarFile)
            try {
                val sigEntry = jar.getJarEntry(SIG_ENTRY) ?: run {
                    log("[LicenseGuard] Missing signature file.")
                    return false
                }
                val sigBytes = Base64.getDecoder().decode(
                    jar.getInputStream(sigEntry).readBytes().toString(Charsets.UTF_8).trim()
                )
                val manifest = buildManifest(jar)
                val sig = Signature.getInstance("SHA256withRSA")
                sig.initVerify(publicKey())
                sig.update(manifest.toByteArray(Charsets.UTF_8))
                val ok = sig.verify(sigBytes)
                if (!ok) log("[LicenseGuard] Signature mismatch — jar was modified or rebuilt.")
                ok
            } finally {
                jar.close()
            }
        } catch (e: Exception) {
            log("[LicenseGuard] Verification error: ${e.message}")
            false
        }
    }

    /**
     * Manifest format (must match the build signer exactly):
     * for every jar entry except the signature file, sorted by name:
     *   "<name>:<hex(sha256(name_utf8 + entry_bytes)>\n"
     */
    fun buildManifest(jar: JarFile): String {
        val names = jar.entries().asSequence()
            .map { it.name }
            .filter { it != SIG_ENTRY }
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

    private fun publicKey(): java.security.PublicKey {
        val bytes = Base64.getDecoder().decode(PUBLIC_KEY_B64)
        return KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(bytes))
    }
}
