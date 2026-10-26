package com.questorylabs.tvc.agent.adb

import android.content.Context
import android.os.Build
import android.sun.security.x509.AlgorithmId
import android.sun.security.x509.CertificateAlgorithmId
import android.sun.security.x509.CertificateExtensions
import android.sun.security.x509.CertificateIssuerName
import android.sun.security.x509.CertificateSerialNumber
import android.sun.security.x509.CertificateSubjectName
import android.sun.security.x509.CertificateValidity
import android.sun.security.x509.CertificateVersion
import android.sun.security.x509.CertificateX509Key
import android.sun.security.x509.KeyIdentifier
import android.sun.security.x509.PrivateKeyUsageExtension
import android.sun.security.x509.SubjectKeyIdentifierExtension
import android.sun.security.x509.X500Name
import android.sun.security.x509.X509CertImpl
import android.sun.security.x509.X509CertInfo
import android.util.Log
import com.questorylabs.tvc.agent.AgentState
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

class AdbManager(private val context: Context) : AbsAdbConnectionManager() {
    private val keyFile = File(context.filesDir, "adb.key")
    private val certFile = File(context.filesDir, "adb.crt")
    private val prefs = context.getSharedPreferences("adb", Context.MODE_PRIVATE)
    private val identity: Pair<PrivateKey, Certificate> by lazy {
        loadIdentity() ?: generateIdentity().also { (key, cert) ->
            keyFile.writeBytes(key.encoded)
            certFile.writeBytes(cert.encoded)
        }
    }
    private val mutex = Mutex()

    init {
        setApi(Build.VERSION.SDK_INT)
        setHostAddress("127.0.0.1")
        setTimeout(10, TimeUnit.SECONDS)
    }

    override fun getPrivateKey(): PrivateKey = identity.first
    override fun getCertificate(): Certificate = identity.second
    override fun getDeviceName(): String = "TV Companion Agent"

    val paired: Boolean get() = prefs.getBoolean(PREF_PAIRED, false)

    suspend fun pairWith(port: Int, code: String): Result<Unit> = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching {
                pair("127.0.0.1", port, code)
                prefs.edit().putBoolean(PREF_PAIRED, true).apply()
                AgentState.adbMessage.value = "Paired. Now connect."
            }.onFailure {
                Log.w(TAG, "pairing failed", it)
                AgentState.adbMessage.value = "Pairing failed: ${it.message}"
            }
        }
    }

    suspend fun connectAuto(timeoutMs: Long = 10_000): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (isConnected) return@withLock true
            val ok = runCatching { autoConnect(context, timeoutMs) }
                .onFailure { AgentState.adbMessage.value = "Connect failed: ${it.message}" }
                .getOrDefault(false) || isConnected
            AgentState.adbConnected.value = ok
            if (ok) AgentState.adbMessage.value = "ADB connected"
            ok
        }
    }

    suspend fun connectPort(port: Int): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (isConnected) return@withLock true
            val ok = runCatching { connect("127.0.0.1", port) }
                .onFailure { AgentState.adbMessage.value = "Connect failed: ${it.message}" }
                .getOrDefault(false) || isConnected
            AgentState.adbConnected.value = ok
            if (ok) AgentState.adbMessage.value = "ADB connected"
            ok
        }
    }

    suspend fun connectIfPaired() {
        if (paired) connectAuto()
    }

    suspend fun shell(cmd: String, timeoutMs: Long = 15_000): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!isConnected) {
                AgentState.adbConnected.value = false
                throw IllegalStateException("ADB not connected")
            }
            val timedOut = AtomicBoolean(false)
            try {
                val stream = openStream("shell:$cmd")
                coroutineScope {
                    val watchdog = launch {
                        delay(timeoutMs)
                        timedOut.set(true)
                        runCatching { stream.close() }
                    }
                    try {
                        stream.use { s ->
                            s.openInputStream().use { input -> input.readBytes().toString(Charsets.UTF_8) }
                        }
                    } finally {
                        watchdog.cancel()
                    }
                }
            } catch (e: Exception) {
                if (!isConnected) AgentState.adbConnected.value = false
                if (timedOut.get()) throw TimeoutException("adb shell timed out after ${timeoutMs}ms")
                throw e
            }
        }
    }

    suspend fun execBytes(cmd: String, maxBytes: Int, timeoutMs: Long): ByteArray = withContext(Dispatchers.IO) {
        val stream = openExec(cmd)
        withWatchdog(stream, timeoutMs) {
            stream.openInputStream().use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    if (out.size() + n > maxBytes) throw IllegalStateException("output exceeds $maxBytes bytes")
                    out.write(buffer, 0, n)
                }
                out.toByteArray()
            }
        }
    }

    suspend fun execWithInput(cmd: String, input: java.io.InputStream, size: Long, timeoutMs: Long): String = withContext(Dispatchers.IO) {
        val stream = openExec(cmd)
        withWatchdog(stream, timeoutMs) {
            val out = stream.openOutputStream()
            val buffer = ByteArray(64 * 1024)
            var sent = 0L
            while (sent < size) {
                val n = input.read(buffer, 0, minOf(buffer.size.toLong(), size - sent).toInt())
                if (n < 0) throw java.io.EOFException("upload ended after $sent of $size bytes")
                out.write(buffer, 0, n)
                sent += n
            }
            out.flush()
            stream.openInputStream().use { it.readBytes().toString(Charsets.UTF_8) }
        }
    }

    private suspend fun openExec(cmd: String): io.github.muntashirakon.adb.AdbStream = mutex.withLock {
        if (!isConnected) {
            AgentState.adbConnected.value = false
            throw IllegalStateException("ADB not connected")
        }
        openStream("exec:$cmd")
    }

    private suspend fun <T> withWatchdog(stream: io.github.muntashirakon.adb.AdbStream, timeoutMs: Long, block: () -> T): T {
        val timedOut = AtomicBoolean(false)
        return try {
            coroutineScope {
                val watchdog = launch {
                    delay(timeoutMs)
                    timedOut.set(true)
                    runCatching { stream.close() }
                }
                try {
                    stream.use { block() }
                } finally {
                    watchdog.cancel()
                }
            }
        } catch (e: Exception) {
            if (!isConnected) AgentState.adbConnected.value = false
            if (timedOut.get()) throw TimeoutException("adb exec timed out after ${timeoutMs}ms")
            throw e
        }
    }

    private fun loadIdentity(): Pair<PrivateKey, Certificate>? {
        if (!keyFile.exists() || !certFile.exists()) return null
        return runCatching {
            val key = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyFile.readBytes()))
            val cert = CertificateFactory.getInstance("X.509").generateCertificate(certFile.inputStream())
            key to cert
        }.getOrNull()
    }

    private fun generateIdentity(): Pair<PrivateKey, Certificate> {
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048, SecureRandom())
        val pair = generator.generateKeyPair()
        val algorithmName = "SHA512withRSA"
        val notBefore = Date()
        val notAfter = Date(System.currentTimeMillis() + TEN_YEARS_MS)
        val extensions = CertificateExtensions().apply {
            set("SubjectKeyIdentifier", SubjectKeyIdentifierExtension(KeyIdentifier(pair.public).identifier))
            set("PrivateKeyUsage", PrivateKeyUsageExtension(notBefore, notAfter))
        }
        val name = X500Name("CN=TV Companion Agent")
        val info = X509CertInfo().apply {
            set("version", CertificateVersion(2))
            set("serialNumber", CertificateSerialNumber(SecureRandom().nextInt() and Int.MAX_VALUE))
            set("algorithmID", CertificateAlgorithmId(AlgorithmId.get(algorithmName)))
            set("subject", CertificateSubjectName(name))
            set("key", CertificateX509Key(pair.public))
            set("validity", CertificateValidity(notBefore, notAfter))
            set("issuer", CertificateIssuerName(name))
            set("extensions", extensions)
        }
        val cert = X509CertImpl(info)
        cert.sign(pair.private, algorithmName)
        return pair.private to cert
    }

    private companion object {
        const val TAG = "AdbManager"
        const val PREF_PAIRED = "paired"
        const val TEN_YEARS_MS = 10L * 365 * 24 * 60 * 60 * 1000
    }
}
