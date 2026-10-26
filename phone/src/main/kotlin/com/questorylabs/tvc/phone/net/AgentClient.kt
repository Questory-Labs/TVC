package com.questorylabs.tvc.phone.net

import android.util.Log
import com.questorylabs.tvc.shared.AppEntry
import com.questorylabs.tvc.shared.Command
import com.questorylabs.tvc.shared.DeviceInfo
import com.questorylabs.tvc.shared.Protocol
import com.questorylabs.tvc.shared.Reply
import com.questorylabs.tvc.shared.Request
import com.questorylabs.tvc.shared.SavedDevice
import com.questorylabs.tvc.shared.Timeouts
import com.questorylabs.tvc.shared.TvInput
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.jvm.javaio.toByteReadChannel
import java.io.InputStream
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class AgentClient(private val device: SavedDevice, private val clientName: String) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) { json(Protocol.json) }
        install(WebSockets)
        install(HttpTimeout) {
            requestTimeoutMillis = 5_000
            connectTimeoutMillis = 3_000
        }
        defaultRequest {
            header("Authorization", "Bearer ${device.token}")
            header(Protocol.CLIENT_HEADER, clientName)
        }
    }
    private val base = "http://${device.host}:${device.port}"
    private val nextId = AtomicInteger(1)
    private val pending = ConcurrentHashMap<Int, CompletableDeferred<Reply>>()
    private val sendMutex = Mutex()
    private var session: DefaultClientWebSocketSession? = null
    private var reconnectJob: Job? = null

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected

    fun start() {
        if (reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            var backoff = 500L
            while (true) {
                try {
                    runSession()
                    backoff = 500L
                } catch (e: Exception) {
                    Log.d(TAG, "ws session ended: ${e.message}")
                }
                _connected.value = false
                failAllPending("disconnected")
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(10_000L)
            }
        }
    }

    private suspend fun runSession() {
        val ws = client.webSocketSession("ws://${device.host}:${device.port}${Protocol.WS_PATH}")
        session = ws
        _connected.value = true
        try {
            for (frame in ws.incoming) {
                if (frame !is Frame.Text) continue
                val reply = runCatching { Protocol.json.decodeFromString(Reply.serializer(), frame.readText()) }.getOrNull() ?: continue
                pending.remove(reply.id)?.complete(reply)
            }
        } finally {
            session = null
        }
    }

    suspend fun send(command: Command, timeoutMs: Long): Reply {
        val ws = session ?: return Reply.fail(-1, "not connected")
        val id = nextId.getAndIncrement()
        val deferred = CompletableDeferred<Reply>()
        pending[id] = deferred
        return try {
            sendMutex.withLock {
                ws.send(Frame.Text(Protocol.json.encodeToString(Request.serializer(), Request(id, command))))
            }
            withTimeout(timeoutMs) { deferred.await() }
        } catch (e: Exception) {
            pending.remove(id)
            if (e is CancellationException && e !is TimeoutCancellationException) throw e
            Reply.fail(id, e.message ?: "send failed")
        }
    }

    suspend fun info(): Result<DeviceInfo> = runCatching {
        val response = client.get("$base/info")
        if (response.status == HttpStatusCode.Unauthorized) throw IllegalStateException("wrong token")
        response.body()
    }

    suspend fun inputs(): Result<List<TvInput>> = runCatching { client.get("$base/inputs").body() }

    suspend fun apps(): Result<List<AppEntry>> = runCatching { client.get("$base/apps").body() }

    suspend fun screenshot(): Result<ByteArray> = runCatching {
        val response = client.get("$base${Protocol.SCREENSHOT_PATH}") {
            timeout {
                requestTimeoutMillis = Timeouts.SCREENSHOT_MS
                socketTimeoutMillis = Timeouts.SCREENSHOT_MS
            }
        }
        if (response.status != HttpStatusCode.OK) throw IllegalStateException(response.bodyAsText().take(200))
        response.body<ByteArray>()
    }

    suspend fun install(size: Long, stream: InputStream): Reply = runCatching {
        val response = client.post("$base${Protocol.INSTALL_PATH}") {
            timeout {
                requestTimeoutMillis = Timeouts.INSTALL_MS
                socketTimeoutMillis = Timeouts.INSTALL_MS
            }
            setBody(object : OutgoingContent.ReadChannelContent() {
                override val contentLength: Long = size
                override val contentType: ContentType = APK_TYPE
                override fun readFrom(): ByteReadChannel = stream.toByteReadChannel()
            })
        }
        if (response.contentType()?.match(ContentType.Application.Json) == true) response.body<Reply>()
        else Reply.fail(-1, response.bodyAsText().take(200).ifEmpty { "HTTP ${response.status.value}" })
    }.getOrElse { Reply.fail(-1, it.message ?: "install failed") }

    private fun failAllPending(reason: String) {
        pending.keys.toList().forEach { id -> pending.remove(id)?.complete(Reply.fail(id, reason)) }
    }

    fun close() {
        reconnectJob?.cancel()
        scope.cancel()
        client.close()
    }

    private companion object {
        const val TAG = "AgentClient"
        val APK_TYPE = ContentType("application", "vnd.android.package-archive")
    }
}
