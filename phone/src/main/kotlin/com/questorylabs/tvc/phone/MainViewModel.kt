package com.questorylabs.tvc.phone

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.questorylabs.tvc.phone.data.DeviceStore
import com.questorylabs.tvc.phone.data.DiscoveredDevice
import com.questorylabs.tvc.phone.data.Discovery
import com.questorylabs.tvc.phone.net.AgentClient
import com.questorylabs.tvc.shared.AppEntry
import com.questorylabs.tvc.shared.Command
import com.questorylabs.tvc.shared.DebloatCatalog
import com.questorylabs.tvc.shared.DebloatList
import com.questorylabs.tvc.shared.DeviceInfo
import com.questorylabs.tvc.shared.MemoryStats
import com.questorylabs.tvc.shared.NowPlayingInfo
import com.questorylabs.tvc.shared.PrivateDnsState
import com.questorylabs.tvc.shared.Protocol
import com.questorylabs.tvc.shared.Reply
import com.questorylabs.tvc.shared.SavedDevice
import com.questorylabs.tvc.shared.Scene
import com.questorylabs.tvc.shared.Scenes
import com.questorylabs.tvc.shared.Timeouts
import com.questorylabs.tvc.shared.TvInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer

data class DeviceUi(
    val device: SavedDevice,
    val connected: Boolean = false,
    val info: DeviceInfo? = null,
    val inputs: List<TvInput> = emptyList(),
    val apps: List<AppEntry> = emptyList(),
    val disabledPackages: Set<String>? = null,
    val lastError: String? = null,
    val streamerInputId: String? = null,
    val latencyMs: Long? = null,
    val nowPlaying: NowPlayingInfo? = null,
    val memory: MemoryStats? = null,
    val privateDns: PrivateDnsState? = null,
)

data class SceneRun(val sceneId: String, val step: Int, val total: Int, val label: String, val error: String? = null, val done: Boolean = false)

sealed interface InstallState {
    data object Idle : InstallState
    data class Running(val name: String) : InstallState
    data class Finished(val name: String, val ok: Boolean, val message: String) : InstallState
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val store = DeviceStore(app)
    private val discovery = Discovery(app)
    private val prefs = app.getSharedPreferences("ui", Context.MODE_PRIVATE)
    private val clients = mutableMapOf<String, AgentClient>()
    private val clientDevices = mutableMapOf<String, SavedDevice>()
    private val watchers = mutableMapOf<String, Job>()
    private var discoveryJob: Job? = null
    private var sceneJob: Job? = null
    private val clientName = Build.MODEL.ifBlank { "Phone" }

    val devices: StateFlow<List<SavedDevice>> = store.devices.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val discovered = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val discovering = MutableStateFlow(false)
    val states = MutableStateFlow<Map<String, DeviceUi>>(emptyMap())
    val selectedHost = MutableStateFlow<String?>(null)
    val incomingLink = MutableStateFlow<SavedDevice?>(null)
    val catalog: DebloatList = DebloatCatalog.parse(app.assets.open("packages.json").bufferedReader().readText())
    val scenes = MutableStateFlow(Scenes.decode(prefs.getString(SCENES_KEY, null)))
    val sceneRun = MutableStateFlow<SceneRun?>(null)
    val install = MutableStateFlow<InstallState>(InstallState.Idle)

    init {
        viewModelScope.launch {
            devices.collect { list ->
                list.forEach { ensureClient(it) }
                clients.keys.filterNot { host -> list.any { it.host == host } }.forEach { host ->
                    dropClient(host)
                    states.update { it - host }
                }
                if (selectedHost.value == null || list.none { it.host == selectedHost.value }) selectedHost.value = list.firstOrNull()?.host
            }
        }
    }

    private fun dropClient(host: String) {
        watchers.remove(host)?.cancel()
        clients.remove(host)?.close()
        clientDevices.remove(host)
    }

    private fun ensureClient(device: SavedDevice) {
        if (clientDevices[device.host] == device) return
        dropClient(device.host)
        val client = AgentClient(device, clientName).also { clients[device.host] = it }
        clientDevices[device.host] = device
        states.update { it + (device.host to DeviceUi(device, streamerInputId = prefs.getString("streamer:${device.host}", null))) }
        client.start()
        watchers[device.host] = viewModelScope.launch {
            client.connected.collect { connected ->
                updateState(device.host) { it.copy(connected = connected) }
                if (connected) refresh(device.host)
            }
        }
    }

    private fun updateState(host: String, block: (DeviceUi) -> DeviceUi) {
        states.update { map -> map[host]?.let { map + (host to block(it)) } ?: map }
    }

    fun refresh(host: String) {
        val client = clients[host] ?: return
        viewModelScope.launch {
            client.info().onSuccess { info ->
                updateState(host) { it.copy(info = info, lastError = null) }
                if (info.hasTvInputs) client.inputs().onSuccess { list -> updateState(host) { it.copy(inputs = list) } }
                if (info.adbConnected) refreshDisabled(host)
            }.onFailure { e -> updateState(host) { it.copy(lastError = e.message) } }
            client.apps().onSuccess { list -> updateState(host) { it.copy(apps = list) } }
            ping(host)
        }
    }

    fun refreshInfo(host: String) {
        val client = clients[host] ?: return
        viewModelScope.launch { client.info().onSuccess { info -> updateState(host) { it.copy(info = info) } } }
    }

    fun ping(host: String) {
        val client = clients[host] ?: return
        viewModelScope.launch {
            val start = System.nanoTime()
            val reply = client.send(Command.Ping, Timeouts.DEFAULT_MS)
            val ms = (System.nanoTime() - start) / 1_000_000
            updateState(host) { it.copy(latencyMs = if (reply.ok) ms else null) }
        }
    }

    fun refreshDisabled(host: String) {
        viewModelScope.launch {
            val reply = send(host, Command.DisabledPackages)
            if (reply.ok) updateState(host) { it.copy(disabledPackages = DebloatCatalog.parseDisabledPackages(reply.output.orEmpty())) }
        }
    }

    suspend fun send(host: String, command: Command): Reply {
        val client = clients[host] ?: return Reply.fail(-1, "unknown device")
        val reply = client.send(command, Timeouts.forCommand(command))
        updateState(host) { it.copy(lastError = if (reply.ok) null else reply.error) }
        return reply
    }

    fun fire(host: String, command: Command) {
        viewModelScope.launch { send(host, command) }
    }

    private suspend fun <T> query(host: String, command: Command, serializer: KSerializer<T>): T? {
        val reply = send(host, command)
        if (!reply.ok) return null
        return runCatching { Protocol.json.decodeFromString(serializer, reply.output.orEmpty()) }.getOrNull()
    }

    fun setDebloat(host: String, pkg: String, disable: Boolean) {
        viewModelScope.launch {
            val reply = send(host, Command.Debloat(pkg, disable))
            if (reply.ok) refreshDisabled(host)
        }
    }

    fun setDebloatBulk(host: String, packages: List<String>, disable: Boolean) {
        viewModelScope.launch {
            packages.forEach { send(host, Command.Debloat(it, disable)) }
            refreshDisabled(host)
        }
    }

    fun sleepTimer(host: String, minutes: Int) {
        viewModelScope.launch {
            send(host, Command.SleepTimer(minutes))
            refreshInfo(host)
        }
    }

    fun reboot(host: String) = fire(host, Command.Reboot)

    fun refreshNowPlaying(host: String) {
        viewModelScope.launch {
            query(host, Command.NowPlaying, NowPlayingInfo.serializer())?.let { np -> updateState(host) { it.copy(nowPlaying = np) } }
        }
    }

    fun refreshMemory(host: String) {
        viewModelScope.launch {
            query(host, Command.Memory, MemoryStats.serializer())?.let { m -> updateState(host) { it.copy(memory = m) } }
        }
    }

    fun freeMemory(host: String) {
        viewModelScope.launch {
            query(host, Command.FreeMemory, MemoryStats.serializer())?.let { m -> updateState(host) { it.copy(memory = m) } }
        }
    }

    fun refreshPrivateDns(host: String) {
        viewModelScope.launch {
            query(host, Command.PrivateDnsStatus, PrivateDnsState.serializer())?.let { d -> updateState(host) { it.copy(privateDns = d) } }
        }
    }

    fun setPrivateDns(host: String, hostname: String?) {
        viewModelScope.launch {
            query(host, Command.PrivateDns(hostname), PrivateDnsState.serializer())?.let { d -> updateState(host) { it.copy(privateDns = d) } }
        }
    }

    fun forceStop(host: String, pkg: String) = fire(host, Command.ForceStop(pkg))

    suspend fun screenshot(host: String): Result<ByteArray> =
        clients[host]?.screenshot() ?: Result.failure(IllegalStateException("unknown device"))

    fun installApk(host: String, uri: Uri) {
        val client = clients[host] ?: return
        if (install.value is InstallState.Running) return
        val resolver = getApplication<Application>().contentResolver
        viewModelScope.launch {
            val (name, size) = withContext(Dispatchers.IO) {
                runCatching {
                    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                        if (!c.moveToFirst()) null
                        else (c.getString(0) ?: "APK") to (if (c.isNull(1)) -1L else c.getLong(1))
                    }
                }.getOrNull() ?: ("APK" to -1L)
            }
            if (size <= 0 || size > Protocol.MAX_APK_BYTES) {
                install.value = InstallState.Finished(name, false, if (size <= 0) "Unknown file size" else "APK too large")
                return@launch
            }
            install.value = InstallState.Running(name)
            val reply = withContext(Dispatchers.IO) {
                runCatching { resolver.openInputStream(uri) }.getOrNull()?.use { client.install(size, it) }
                    ?: Reply.fail(-1, "Cannot open file")
            }
            install.value = InstallState.Finished(name, reply.ok, if (reply.ok) "Installed" else reply.error ?: "Install failed")
            if (reply.ok) refresh(host)
        }
    }

    fun clearInstall() {
        if (install.value !is InstallState.Running) install.value = InstallState.Idle
    }

    fun saveScene(scene: Scene): String? {
        Scenes.validate(scene)?.let { return it }
        scenes.update { list -> if (list.any { it.id == scene.id }) list.map { if (it.id == scene.id) scene else it } else list + scene }
        persistScenes()
        return null
    }

    fun deleteScene(id: String) {
        scenes.update { list -> list.filterNot { it.id == id } }
        persistScenes()
    }

    private fun persistScenes() {
        prefs.edit().putString(SCENES_KEY, Scenes.encode(scenes.value)).apply()
    }

    fun runScene(host: String, scene: Scene) {
        if (sceneJob?.isActive == true || Scenes.validate(scene) != null) return
        sceneJob = viewModelScope.launch {
            val total = scene.steps.size
            val overlay = states.value[host]?.info?.overlayAvailable == true
            for ((i, step) in scene.steps.withIndex()) {
                if (step.delayMs > 0) delay(step.delayMs)
                val label = Scenes.describe(step.command)
                sceneRun.value = SceneRun(scene.id, i, total, label)
                if (overlay) send(host, Command.Notify(scene.name, label, i.toFloat() / total))
                val reply = send(host, step.command)
                if (!reply.ok) {
                    sceneRun.value = SceneRun(scene.id, i, total, label, error = reply.error ?: "Step failed", done = true)
                    if (overlay) send(host, Command.Notify(scene.name, "Stopped: ${reply.error ?: label}", null))
                    return@launch
                }
            }
            sceneRun.value = SceneRun(scene.id, total, total, "Done", done = true)
            if (overlay) send(host, Command.Notify(scene.name, "Done", 1f))
            refreshInfo(host)
        }
    }

    fun cancelScene() {
        sceneJob?.cancel()
        sceneRun.value = null
    }

    fun setStreamerInput(host: String, inputId: String?) {
        prefs.edit().putString("streamer:$host", inputId).apply()
        updateState(host) { it.copy(streamerInputId = inputId) }
    }

    fun jumpToStreamer(tvHost: String) {
        val tv = states.value[tvHost] ?: return
        val inputId = tv.streamerInputId ?: return
        viewModelScope.launch {
            send(tvHost, Command.SwitchInput(inputId))
            val streamer = states.value.values.firstOrNull { it.device.host != tvHost && it.info?.hasTvInputs == false }
                ?: states.value.values.firstOrNull { it.device.host != tvHost }
            streamer?.let { selectedHost.value = it.device.host }
        }
    }

    fun addDevice(device: SavedDevice) {
        viewModelScope.launch { store.upsert(device) }
    }

    fun removeDevice(host: String) {
        viewModelScope.launch { store.remove(host) }
    }

    fun startDiscovery() {
        if (discoveryJob?.isActive == true) return
        discovered.value = emptyList()
        discovering.value = true
        discoveryJob = viewModelScope.launch {
            try {
                discovery.discover().collect { found ->
                    discovered.update { list -> list.filterNot { it.host == found.host } + found }
                }
            } finally {
                discovering.value = false
            }
        }
    }

    fun stopDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = null
    }

    override fun onCleared() {
        clients.values.forEach { it.close() }
        super.onCleared()
    }

    companion object {
        private const val SCENES_KEY = "scenes"

        fun parsePayload(text: String): SavedDevice? {
            val uri = runCatching { android.net.Uri.parse(text.trim()) }.getOrNull() ?: return null
            if (uri.scheme != "tvc") return null
            val host = uri.host ?: return null
            val token = uri.getQueryParameter("token") ?: return null
            val name = uri.getQueryParameter("name") ?: host
            return SavedDevice(name = name, host = host, token = token, port = if (uri.port > 0) uri.port else 7887)
        }
    }
}
