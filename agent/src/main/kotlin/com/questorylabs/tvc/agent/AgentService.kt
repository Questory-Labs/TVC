package com.questorylabs.tvc.agent

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import com.questorylabs.tvc.agent.server.AgentServer
import com.questorylabs.tvc.agent.ui.MainActivity
import com.questorylabs.tvc.shared.Protocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.NetworkInterface

class AgentService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var server: AgentServer? = null
    private var nsdManager: NsdManager? = null
    private var registration: NsdManager.RegistrationListener? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
        val app = application as AgentApp
        server = AgentServer(this, app.tokenStore, app.dispatcher, app.adb).also { it.start() }
        registerNsd()
        scope.launch {
            while (true) {
                AgentState.localAddress.value = localIpv4()
                delay(10_000)
            }
        }
        scope.launch {
            delay(3_000)
            app.adb.connectIfPaired()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        scope.cancel()
        unregisterNsd()
        server?.stop()
        server = null
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, AgentApp.CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    private fun registerNsd() {
        val info = NsdServiceInfo().apply {
            serviceName = "TV Companion ${Build.MODEL}".take(63)
            serviceType = Protocol.NSD_SERVICE_TYPE
            port = Protocol.PORT
            setAttribute("model", Build.MODEL.take(100))
            setAttribute("sdk", Build.VERSION.SDK_INT.toString())
            setAttribute("v", Protocol.VERSION.toString())
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                Log.i(TAG, "NSD registered ${serviceInfo.serviceName}")
            }
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "NSD registration failed $errorCode")
            }
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {}
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
        }
        nsdManager = getSystemService(NsdManager::class.java).also {
            it.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
        }
        registration = listener
    }

    private fun unregisterNsd() {
        val listener = registration ?: return
        runCatching { nsdManager?.unregisterService(listener) }
        registration = null
    }

    companion object {
        private const val TAG = "AgentService"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, AgentService::class.java))
        }

        fun localIpv4(): String? = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { it.isSiteLocalAddress }
                ?.hostAddress
        }.getOrNull()
    }
}
