package com.questorylabs.tvc.agent

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.questorylabs.tvc.agent.adb.AdbManager
import com.questorylabs.tvc.agent.control.CommandDispatcher

class AgentApp : Application() {
    lateinit var tokenStore: TokenStore
        private set
    lateinit var adb: AdbManager
        private set
    lateinit var dispatcher: CommandDispatcher
        private set

    override fun onCreate() {
        super.onCreate()
        tokenStore = TokenStore(this)
        adb = AdbManager(this)
        dispatcher = CommandDispatcher(this, adb)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW)
        )
    }

    companion object {
        const val CHANNEL_ID = "agent"
    }
}
