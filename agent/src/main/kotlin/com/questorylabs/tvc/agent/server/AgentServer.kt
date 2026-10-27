package com.questorylabs.tvc.agent.server

import android.content.Context
import android.os.Build
import android.util.Log
import com.questorylabs.tvc.agent.ActivityKind
import com.questorylabs.tvc.agent.AgentState
import com.questorylabs.tvc.agent.BuildConfig
import com.questorylabs.tvc.agent.TokenStore
import com.questorylabs.tvc.agent.adb.AdbManager
import com.questorylabs.tvc.agent.control.AppLauncher
import com.questorylabs.tvc.agent.control.CommandDispatcher
import com.questorylabs.tvc.agent.control.ControlAccessibilityService
import com.questorylabs.tvc.agent.control.InputSwitcher
import com.questorylabs.tvc.agent.ui.OverlayController
import com.questorylabs.tvc.agent.ui.ToastKind
import com.questorylabs.tvc.shared.DeviceInfo
import com.questorylabs.tvc.shared.Protocol
import com.questorylabs.tvc.shared.Reply
import com.questorylabs.tvc.shared.Request
import com.questorylabs.tvc.shared.Requirement
import com.questorylabs.tvc.shared.ShellPolicy
import com.questorylabs.tvc.shared.Timeouts
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.contentLength
import io.ktor.server.request.header
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.utils.io.jvm.javaio.toInputStream
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.security.MessageDigest
import kotlin.time.Duration.Companion.seconds

class AgentServer(
    context: Context,
    private val tokenStore: TokenStore,
    private val dispatcher: CommandDispatcher,
    private val adb: AdbManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var engine: EmbeddedServer<*, *>? = null
    private val inputs = InputSwitcher(context)
    private val apps = AppLauncher(context)

    fun start() {
        scope.launch {
            try {
                val server = embeddedServer(CIO, port = Protocol.PORT, host = "0.0.0.0") { module() }
                engine = server
                server.start(wait = false)
                AgentState.serverRunning.value = true
                AgentState.serverError.value = null
            } catch (e: Exception) {
                Log.e(TAG, "server start failed", e)
                AgentState.serverRunning.value = false
                AgentState.serverError.value = e.message ?: e.toString()
            }
        }
    }

    fun stop() {
        runCatching { engine?.stop(500, 1000) }
        engine = null
        AgentState.serverRunning.value = false
        scope.cancel()
    }

    private fun authorised(header: String?): Boolean {
        val presented = header?.removePrefix("Bearer ")?.trim() ?: return false
        return MessageDigest.isEqual(presented.toByteArray(), tokenStore.token.toByteArray())
    }

    private fun Application.module() {
        install(ContentNegotiation) { json(Protocol.json) }
        install(WebSockets) {
            pingPeriod = 15.seconds
            timeout = 30.seconds
            maxFrameSize = 64 * 1024
        }
        intercept(ApplicationCallPipeline.Plugins) {
            if (!authorised(call.request.header("Authorization"))) {
                call.respondText("unauthorised", status = HttpStatusCode.Unauthorized)
                finish()
            }
        }
        routing {
            get("/info") {
                call.respond(
                    DeviceInfo(
                        name = Build.MODEL,
                        model = "${Build.MANUFACTURER} ${Build.MODEL}",
                        sdk = Build.VERSION.SDK_INT,
                        agentVersion = BuildConfig.VERSION_NAME,
                        accessibilityEnabled = ControlAccessibilityService.instance != null,
                        adbConnected = dispatcher.adbConnected(),
                        hasTvInputs = inputs.list().isNotEmpty(),
                        volume = dispatcher.volumeLevel,
                        maxVolume = dispatcher.volumeMax,
                        sleepRemainingSec = dispatcher.sleepRemainingSec(),
                        overlayAvailable = ControlAccessibilityService.instance != null,
                    )
                )
            }
            get(Protocol.SCREENSHOT_PATH) {
                if (!adb.isConnected) {
                    call.respondText("ADB tier not connected", status = HttpStatusCode.Conflict)
                    return@get
                }
                val png = runCatching { adb.execBytes(ShellPolicy.SCREENCAP, MAX_SCREENSHOT_BYTES, Timeouts.SCREENSHOT_MS) }
                    .getOrElse {
                        call.respondText(it.message ?: "screenshot failed", status = HttpStatusCode.InternalServerError)
                        return@get
                    }
                call.respondBytes(png, ContentType.Image.PNG)
            }
            post(Protocol.INSTALL_PATH) {
                val size = call.request.contentLength()
                val reply = when {
                    !adb.isConnected -> Reply.fail(-1, "ADB tier not connected", Requirement.ADB)
                    size == null || size <= 0 -> Reply.fail(-1, "Content-Length required")
                    size > Protocol.MAX_APK_BYTES -> Reply.fail(-1, "APK too large")
                    else -> runCatching {
                        val cmd = ShellPolicy.installCommand(size)
                        check(ShellPolicy.isAllowed(cmd)) { "command not allowed" }
                        call.receiveChannel().toInputStream().use { adb.execWithInput(cmd, it, size, Timeouts.INSTALL_MS) }
                    }.fold(
                        { out -> if (out.contains("Success")) Reply.ok(-1, out.trim()) else Reply.fail(-1, out.trim().ifEmpty { "install failed" }) },
                        { Reply.fail(-1, it.message ?: "install failed") },
                    ).also { if (it.ok) AgentState.log(ActivityKind.PACKAGE, "Installed an APK") }
                }
                call.respond(if (reply.ok) HttpStatusCode.OK else HttpStatusCode.BadRequest, reply)
            }
            get("/inputs") { call.respond(inputs.list()) }
            get("/apps") { call.respond(apps.list()) }
            webSocket(Protocol.WS_PATH) {
                val name = call.request.header(Protocol.CLIENT_HEADER)?.filter { it.isLetterOrDigit() || it in " -_." }?.trim()?.take(40)?.ifEmpty { null } ?: "Phone"
                val presented = call.request.header("Authorization")
                val session = AgentState.addClient(name)
                OverlayController.toast(ToastKind.PHONE, "$name connected", "Remote control")
                try {
                    for (frame in incoming) {
                        if (!authorised(presented)) {
                            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "token revoked"))
                            break
                        }
                        if (frame !is Frame.Text) continue
                        val text = frame.readText()
                        launch {
                            val request = runCatching { Protocol.json.decodeFromString(Request.serializer(), text) }.getOrNull()
                            val reply = if (request == null) {
                                Reply.fail(-1, "bad request")
                            } else {
                                runCatching { dispatcher.handle(request) }
                                    .getOrElse { Reply.fail(request.id, it.message ?: it.toString()) }
                            }
                            send(Frame.Text(Protocol.json.encodeToString(Reply.serializer(), reply)))
                        }
                    }
                } finally {
                    AgentState.removeClient(session.id)
                }
            }
        }
    }

    private companion object {
        const val TAG = "AgentServer"
        const val MAX_SCREENSHOT_BYTES = 32 * 1024 * 1024
    }
}
