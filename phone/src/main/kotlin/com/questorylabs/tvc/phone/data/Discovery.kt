package com.questorylabs.tvc.phone.data

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import com.questorylabs.tvc.shared.Protocol
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet4Address
import kotlin.coroutines.resume

data class DiscoveredDevice(val name: String, val host: String, val port: Int, val model: String?)

class Discovery(private val context: Context) {
    fun discover(): Flow<DiscoveredDevice> = callbackFlow {
        val nsd = context.getSystemService(NsdManager::class.java)
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
        val lock = wifi?.createMulticastLock("tvcompanion")?.apply { setReferenceCounted(false); acquire() }

        val found = Channel<NsdServiceInfo>(MAX_PENDING_RESOLVES)
        val listener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "discovery start failed $errorCode")
                close()
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                found.trySend(serviceInfo)
            }
        }
        launch {
            for (info in found) {
                withTimeoutOrNull(RESOLVE_TIMEOUT_MS) { resolve(nsd, info) }?.let { send(it) }
            }
        }
        nsd.discoverServices(Protocol.NSD_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        awaitClose {
            runCatching { nsd.stopServiceDiscovery(listener) }
            runCatching { lock?.release() }
            found.close()
        }
    }

    private suspend fun resolve(nsd: NsdManager, serviceInfo: NsdServiceInfo): DiscoveredDevice? =
        suspendCancellableCoroutine { cont ->
            @Suppress("DEPRECATION")
            nsd.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                    Log.w(TAG, "resolve failed $errorCode")
                    if (cont.isActive) cont.resume(null)
                }
                override fun onServiceResolved(info: NsdServiceInfo) {
                    @Suppress("DEPRECATION")
                    val host = info.host as? Inet4Address
                    val address = host?.hostAddress
                    val model = info.attributes["model"]?.toString(Charsets.UTF_8)
                    if (cont.isActive) cont.resume(address?.let { DiscoveredDevice(info.serviceName, it, info.port, model) })
                }
            })
        }

    private companion object {
        const val TAG = "Discovery"
        const val MAX_PENDING_RESOLVES = 32
        const val RESOLVE_TIMEOUT_MS = 5_000L
    }
}
