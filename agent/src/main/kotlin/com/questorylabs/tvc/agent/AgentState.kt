package com.questorylabs.tvc.agent

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

data class ClientSession(val id: Long, val name: String, val connectedAtMs: Long)

enum class ActivityKind { INPUT, APP, VOLUME, MEDIA, TEXT, SCENE, SYSTEM, PACKAGE }

data class ActivityEntry(val kind: ActivityKind, val text: String, val atMs: Long)

object AgentState {
    val serverRunning = MutableStateFlow(false)
    val serverError = MutableStateFlow<String?>(null)
    val localAddress = MutableStateFlow<String?>(null)
    val accessibilityEnabled = MutableStateFlow(false)
    val adbConnected = MutableStateFlow(false)
    val adbMessage = MutableStateFlow<String?>(null)
    val lastCommand = MutableStateFlow<String?>(null)
    val clients = MutableStateFlow<List<ClientSession>>(emptyList())
    val activity = MutableStateFlow<List<ActivityEntry>>(emptyList())
    val sleepAtElapsedMs = MutableStateFlow<Long?>(null)

    private val nextClientId = AtomicLong(1)

    fun addClient(name: String): ClientSession {
        val session = ClientSession(nextClientId.getAndIncrement(), name, System.currentTimeMillis())
        clients.update { it + session }
        return session
    }

    fun removeClient(id: Long) {
        clients.update { list -> list.filterNot { it.id == id } }
    }

    fun log(kind: ActivityKind, text: String) {
        activity.update { (listOf(ActivityEntry(kind, text, System.currentTimeMillis())) + it).take(MAX_ACTIVITY) }
    }

    private const val MAX_ACTIVITY = 20
}
