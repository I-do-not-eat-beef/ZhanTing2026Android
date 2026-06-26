package com.hzvtc.zhanting2026

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hzvtc.zhanting2026.data.ClientCommands
import com.hzvtc.zhanting2026.data.ClientCommand
import com.hzvtc.zhanting2026.data.ControlDevice
import com.hzvtc.zhanting2026.data.ControlModule
import com.hzvtc.zhanting2026.data.DisplayDevices
import com.hzvtc.zhanting2026.data.TerminalDevice
import com.hzvtc.zhanting2026.data.loadControlModules
import com.hzvtc.zhanting2026.network.ZhanTingApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

data class DashboardState(
    val serverUrl: String = DEFAULT_SERVER_URL,
    val modules: List<ControlModule> = emptyList(),
    val selectedModuleIndex: Int = 0,
    val selectedClientAddr: String = DisplayDevices.first().address,
    val onlineDevices: Set<String> = emptySet(),
    val isBusy: Boolean = false,
    val actionState: String = "待命",
    val health: String = "待检测",
    val wsStatus: String = "未连接",
    val adbStatus: String = "待命",
    val adbDetail: String = "由唯一服务端执行 ADB 唤醒和安卓应用启动",
    val clientResources: String = "未查询",
    val imageStatus: String = "等待图片",
    val selectedImageName: String? = null,
    val imagePreviewUrl: String = "",
    val screenshotStatus: String = "等待截图",
    val screenshotPreviewUrl: String = "",
    val busyMessage: String = "",
    val logs: List<String> = emptyList()
)

enum class HallOperation {
    Open,
    Close,
    Operation
}

class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences("zhanting-control", Context.MODE_PRIVATE)
    private val api = ZhanTingApi()
    private val okHttp = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var webSocketGeneration = 0
    private var selectedKvBytes: ByteArray? = null

    private val _state = MutableStateFlow(
        DashboardState(
            serverUrl = getInitialServerUrl(),
            selectedClientAddr = preferences.getString(KEY_SELECTED_CLIENT, DisplayDevices.first().address)
                ?: DisplayDevices.first().address
        )
    )
    val state: StateFlow<DashboardState> = _state

    init {
        runCatching { loadControlModules(application) }
            .onSuccess { modules ->
                _state.update { it.copy(modules = modules) }
            }
            .onFailure { error ->
                addLog("硬件配置读取失败：${error.message ?: error.javaClass.simpleName}")
            }

        connectWebSocket()
        viewModelScope.launch { checkHealth() }
        viewModelScope.launch { refreshSelectedClientScreenshot(manual = false) }
        startTimers()
    }

    fun saveServerUrl(value: String) {
        val normalized = normalizeHttpUrl(value)
        preferences.edit().putString(KEY_SERVER_URL, normalized).apply()
        _state.update { it.copy(serverUrl = normalized) }
        addLog("服务端已切换到 $normalized")
        connectWebSocket()
        viewModelScope.launch { checkHealth() }
        viewModelScope.launch { refreshSelectedClientScreenshot(manual = false) }
    }

    fun selectModule(index: Int) {
        val modules = _state.value.modules
        if (index in modules.indices) {
            _state.update { it.copy(selectedModuleIndex = index) }
        }
    }

    fun selectClient(address: String) {
        if (DisplayDevices.none { it.address == address }) {
            return
        }

        preferences.edit().putString(KEY_SELECTED_CLIENT, address).apply()
        _state.update {
            it.copy(
                selectedClientAddr = address,
                clientResources = "未查询",
                imageStatus = "等待图片",
                imagePreviewUrl = "",
                screenshotStatus = "等待截图",
                screenshotPreviewUrl = ""
            )
        }
        viewModelScope.launch { refreshSelectedClientScreenshot(manual = false) }
    }

    fun selectKvFile(fileName: String, bytes: ByteArray) {
        selectedKvBytes = bytes
        _state.update {
            it.copy(
                selectedImageName = fileName,
                imageStatus = "已选择 $fileName"
            )
        }
    }

    fun openHall() {
        launchExclusive(HallOperation.Open) {
            controlHall(isOpen = true)
        }
    }

    fun closeHall() {
        launchExclusive(HallOperation.Close) {
            controlHall(isOpen = false)
        }
    }

    fun wakeAndroidScreensOnly() {
        launchExclusive {
            wakeAndroidScreens()
        }
    }

    fun controlSelectedModule(isOpen: Boolean) {
        launchExclusive {
            selectedModule()?.let { controlModule(it, isOpen) }
        }
    }

    fun controlDevice(deviceIndex: Int, isOpen: Boolean) {
        launchExclusive {
            selectedModule()?.devices?.getOrNull(deviceIndex)?.let { controlDevice(it, isOpen) }
        }
    }

    fun controlTerminal(deviceIndex: Int, terminalIndex: Int, isOpen: Boolean) {
        launchExclusive {
            val device = selectedModule()?.devices?.getOrNull(deviceIndex) ?: return@launchExclusive
            val terminal = device.terminals.getOrNull(terminalIndex) ?: return@launchExclusive
            sendTerminalCommand(device, terminal, isOpen)
        }
    }

    fun postClientCommand(command: ClientCommand) {
        launchExclusive {
            postSelectedClientCommand(command)
        }
    }

    fun uploadSelectedClientKv() {
        launchExclusive {
            val bytes = selectedKvBytes
            val fileName = _state.value.selectedImageName
            if (bytes == null || fileName.isNullOrBlank()) {
                addLog("请先选择图片文件")
                return@launchExclusive
            }

            uploadSelectedClientKv(fileName, bytes)
        }
    }

    fun previewSelectedClientImage() {
        launchExclusive {
            val client = selectedClient()
            _state.update {
                it.copy(
                    imagePreviewUrl = buildSelectedClientImageUrl(),
                    imageStatus = "${client.name} 预览"
                )
            }
            addLog("已刷新 ${client.name} KV 预览")
        }
    }

    fun fetchSelectedClientResources() {
        launchExclusive {
            val client = selectedClient()
            val response = api.getText("http://${client.address}:8899/zhanting/toDo/getDeviceResInfo")
            if (!response.isSuccessful) {
                throw IllegalStateException(response.body.ifBlank { "${client.name} 资源读取失败 ${response.code}" })
            }

            _state.update { it.copy(clientResources = formatJsonText(response.body)) }
            addLog("${client.name} (${client.address}) 资源读取完成")
        }
    }

    fun refreshScreenshotManually() {
        launchExclusive {
            refreshSelectedClientScreenshot(manual = true)
        }
    }

    override fun onCleared() {
        reconnectJob?.cancel()
        webSocketGeneration++
        webSocket?.cancel()
        okHttp.dispatcher.executorService.shutdown()
        super.onCleared()
    }

    private fun startTimers() {
        viewModelScope.launch {
            while (true) {
                delay(10_000)
                checkHealth()
            }
        }

        viewModelScope.launch {
            while (true) {
                delay(5_000)
                refreshSelectedClientScreenshot(manual = false)
            }
        }
    }

    private fun launchExclusive(
        operation: HallOperation = HallOperation.Operation,
        task: suspend () -> Unit
    ) {
        if (_state.value.isBusy) {
            return
        }

        viewModelScope.launch {
            val busyMessage = when (operation) {
                HallOperation.Open -> "正在执行开馆流程，请不要离开应用或息屏"
                HallOperation.Close -> "正在执行闭馆流程，请不要离开应用或息屏"
                HallOperation.Operation -> ""
            }

            _state.update {
                it.copy(
                    isBusy = true,
                    actionState = "执行中",
                    busyMessage = busyMessage
                )
            }
            if (busyMessage.isNotBlank()) {
                addLog(busyMessage)
            }

            try {
                task()
                _state.update { it.copy(actionState = "完成") }
            } catch (error: Throwable) {
                _state.update { it.copy(actionState = "失败") }
                addLog("操作失败：${error.message ?: error.javaClass.simpleName}")
            } finally {
                _state.update {
                    it.copy(
                        isBusy = false,
                        busyMessage = ""
                    )
                }
            }
        }
    }

    private suspend fun controlHall(isOpen: Boolean) {
        addLog("======== 开始${if (isOpen) "开馆" else "闭馆"}流程 ========")

        if (isOpen) {
            addLog("开馆顺序：先开启展厅硬件，再唤醒电脑和安卓屏")
            _state.value.modules.forEach { module ->
                controlModule(module, isOpen = true, compactLog = true)
            }
            wakeComputers()
            wakeAndroidScreens()
        } else {
            addLog("闭馆顺序：先关闭展示电脑，再关闭展厅硬件")
            shutdownOnlineClients()
            addLog("等待电脑进入关机流程，随后关闭硬件")
            delay(12_000)
            _state.value.modules.forEach { module ->
                controlModule(module, isOpen = false, compactLog = true)
            }
        }

        addLog("======== ${if (isOpen) "开馆" else "闭馆"}流程完成 ========")
    }

    private suspend fun shutdownOnlineClients() = coroutineScope {
        val onlineClients = DisplayDevices.filter { _state.value.onlineDevices.contains(it.address) }
        val clients = onlineClients.ifEmpty { DisplayDevices }
        addLog("准备关闭 ${clients.size} 台展示电脑${if (onlineClients.isEmpty()) "（当前无在线列表，按全量列表执行）" else ""}")

        val results = clients.map { client ->
            async {
                runCatching {
                    val response = api.postForm(
                        "http://${client.address}:8899/zhanting/toDo/poweroff",
                        emptyMap(),
                        timeoutMs = 5_000
                    )
                    if (!response.isSuccessful) {
                        error(response.body.ifBlank { "${client.name} ${response.code}" })
                    }
                    client.name
                }
            }
        }.map { it.await() }

        val successCount = results.count { it.isSuccess }
        addLog("展示电脑关机命令已发送：$successCount/${clients.size}")
        results.filter { it.isFailure }.forEach { result ->
            addLog("电脑关机失败：${result.exceptionOrNull()?.message ?: "未知错误"}")
        }
    }

    private suspend fun controlModule(module: ControlModule, isOpen: Boolean, compactLog: Boolean = false) {
        if (!compactLog) {
            addLog("模块 ${module.name} ${if (isOpen) "开启" else "关闭"}中")
        }

        module.devices.forEach { device ->
            controlDevice(device, isOpen, compactLog)
        }
    }

    private suspend fun controlDevice(device: ControlDevice, isOpen: Boolean, compactLog: Boolean = false) {
        if (device.ip.isBlank()) {
            if (!compactLog) {
                addLog("${device.name.ifBlank { "该条目" }}不是硬件控制器，已跳过")
            }
            return
        }

        val onceControl = device.onceControl
        if (onceControl != null) {
            val command = if (isOpen) onceControl.first else onceControl.second
            sendHardwareCommand(device.ip, command, "${device.name.ifBlank { device.ip }} ${if (isOpen) "总开" else "总关"}")
            delay(1_500)
            return
        }

        device.terminals.forEach { terminal ->
            val command = if (isOpen) terminal.openCommand else terminal.closeCommand
            sendTerminalCommand(device, terminal, isOpen)
            delay(if (isUdpCommand(command)) 5_000 else 1_500)
        }
    }

    private suspend fun sendTerminalCommand(device: ControlDevice, terminal: TerminalDevice, isOpen: Boolean) {
        val command = if (isOpen) terminal.openCommand else terminal.closeCommand
        if (device.ip.isBlank() || command.isBlank()) {
            throw IllegalStateException("设备 IP 或指令为空")
        }

        sendHardwareCommand(device.ip, command, "${terminal.name} ${if (isOpen) "开" else "关"}")
    }

    private suspend fun sendHardwareCommand(ip: String, hexMessage: String, label: String) {
        val path = if (isUdpCommand(hexMessage)) "sendUDP" else "sendTCP"
        addLog("$label -> $ip · $path")

        val response = api.postForm(
            "${_state.value.serverUrl}zhanting/device/$path",
            mapOf("ip" to ip, "hexMessage" to hexMessage)
        )
        if (!response.isSuccessful) {
            throw IllegalStateException(response.body.ifBlank { "$path ${response.code}" })
        }

        addLog("完成：${response.body.ifBlank { "OK" }}")
    }

    private suspend fun wakeComputers() {
        val response = api.postEmpty("${_state.value.serverUrl}zhanting/computer/system/wake")
        if (!response.isSuccessful) {
            throw IllegalStateException(response.body.ifBlank { "电脑唤醒失败" })
        }

        addLog("已通过服务端发送电脑 Wake-on-LAN")
    }

    private suspend fun wakeAndroidScreens() {
        _state.update {
            it.copy(
                adbStatus = "执行中",
                adbDetail = "正在通过服务端连接安卓屏并启动应用"
            )
        }

        val response = api.postEmpty("${_state.value.serverUrl}zhanting/adb/wake")
        if (!response.isSuccessful) {
            _state.update {
                it.copy(
                    adbStatus = "失败",
                    adbDetail = response.body.ifBlank { "安卓屏唤醒失败" }
                )
            }
            throw IllegalStateException(response.body.ifBlank { "安卓屏唤醒失败" })
        }

        val result = response.body.takeIf { it.isNotBlank() }?.let { runCatching { JSONObject(it) }.getOrNull() }
        val devices = result?.optJSONArray("devices")
        val total = result?.optInt("total", -1)?.takeIf { it >= 0 } ?: devices?.length()
        val successCount = result?.optInt("successCount", -1)?.takeIf { it >= 0 } ?: countSuccessfulDevices(devices)
        val message = result?.optString("message")?.takeIf { it.isNotBlank() } ?: "已通过服务端唤醒安卓屏"
        val success = result?.optBoolean("success", true) ?: true

        _state.update {
            it.copy(
                adbStatus = if (success) "完成" else "部分失败",
                adbDetail = if (total != null && successCount != null) {
                    "$successCount/$total 台安卓屏已唤醒或启动应用"
                } else {
                    message
                }
            )
        }

        addLog(message)
        logFailedAdbDevices(devices)
    }

    private suspend fun postSelectedClientCommand(command: ClientCommand) {
        val client = selectedClient()
        val response = api.postForm(
            "http://${client.address}:8899/zhanting/toDo/${command.endpoint}",
            emptyMap()
        )

        if (!response.isSuccessful) {
            throw IllegalStateException(response.body.ifBlank { "${client.name} ${command.endpoint} ${response.code}" })
        }

        addLog("${client.name} (${client.address}) 执行 ${command.label} 完成")
    }

    private suspend fun uploadSelectedClientKv(fileName: String, bytes: ByteArray) {
        val client = selectedClient()
        val response = api.uploadFile(
            "http://${client.address}:8899/zhanting/computer/image/upload",
            "file",
            fileName,
            bytes
        )

        if (!response.isSuccessful) {
            throw IllegalStateException(response.body.ifBlank { "${client.name} KV 上传失败" })
        }

        selectedKvBytes = null
        _state.update {
            it.copy(
                selectedImageName = null,
                imageStatus = "${client.name} KV 已上传并已执行更换",
                imagePreviewUrl = buildSelectedClientImageUrl()
            )
        }
        addLog("${client.name} (${client.address}) KV 上传并更换完成：${response.body.ifBlank { "OK" }}")
    }

    private suspend fun refreshSelectedClientScreenshot(manual: Boolean) {
        val client = selectedClient()
        val screenshotUrl = buildSelectedClientScreenshotUrl()

        if (manual) {
            _state.update { it.copy(screenshotStatus = "读取截图中") }
        }

        runCatching {
            val response = api.headOrGet(screenshotUrl)
            if (!response.isSuccessful) {
                _state.update {
                    it.copy(
                        screenshotPreviewUrl = "",
                        screenshotStatus = if (response.code == 404) "暂无截图" else "截图读取失败 ${response.code}"
                    )
                }
                if (manual) {
                    addLog("${client.name} (${client.address}) 暂无可用截图")
                }
                return
            }

            _state.update {
                it.copy(
                    screenshotPreviewUrl = screenshotUrl,
                    screenshotStatus = "${client.name} ${nowTime()}"
                )
            }
            if (manual) {
                addLog("${client.name} (${client.address}) 截图已刷新")
            }
        }.onFailure { error ->
            _state.update {
                it.copy(
                    screenshotPreviewUrl = "",
                    screenshotStatus = "截图读取失败"
                )
            }
            if (manual) {
                addLog("${client.name} (${client.address}) 截图读取失败：${error.message ?: error.javaClass.simpleName}")
            }
        }
    }

    private suspend fun checkHealth() {
        runCatching {
            api.getText("${_state.value.serverUrl}health")
        }.onSuccess { response ->
            _state.update {
                it.copy(health = if (response.isSuccessful) "运行中" else "异常 ${response.code}")
            }
        }.onFailure {
            _state.update { it.copy(health = "不可达") }
        }
    }

    private fun connectWebSocket() {
        reconnectJob?.cancel()
        val generation = ++webSocketGeneration
        webSocket?.cancel()

        val wsUrl = "${_state.value.serverUrl.replace(Regex("^http", RegexOption.IGNORE_CASE), "ws")}ws?device=android-control"
        _state.update { it.copy(wsStatus = "连接中") }

        val request = Request.Builder().url(wsUrl).build()
        webSocket = okHttp.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    if (generation != webSocketGeneration) return
                    _state.update { it.copy(wsStatus = "已连接") }
                    webSocket.send("1")
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (generation != webSocketGeneration) return
                    handleWebSocketMessage(text)
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    if (generation != webSocketGeneration) return
                    handleWebSocketMessage(bytes.utf8())
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (generation != webSocketGeneration) return
                    _state.update { it.copy(wsStatus = "已断开") }
                    scheduleReconnect(generation)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (generation != webSocketGeneration) return
                    _state.update { it.copy(wsStatus = "错误") }
                    scheduleReconnect(generation)
                }
            }
        )
    }

    private fun scheduleReconnect(generation: Int) {
        reconnectJob?.cancel()
        reconnectJob = viewModelScope.launch {
            delay(5_000)
            if (generation == webSocketGeneration) {
                connectWebSocket()
            }
        }
    }

    private fun handleWebSocketMessage(text: String) {
        runCatching {
            val array = JSONArray(text)
            buildSet {
                for (index in 0 until array.length()) {
                    add(array.optString(index))
                }
            }
        }.onSuccess { devices ->
            _state.update { it.copy(onlineDevices = devices) }
        }.onFailure {
            addLog("WebSocket 消息：$text")
        }
    }

    private fun selectedModule(): ControlModule? {
        val state = _state.value
        return state.modules.getOrNull(state.selectedModuleIndex)
    }

    private fun selectedClient() =
        DisplayDevices.firstOrNull { it.address == _state.value.selectedClientAddr } ?: DisplayDevices.first()

    private fun buildSelectedClientImageUrl(): String {
        val client = selectedClient()
        return "http://${client.address}:8899/zhanting/toDo/getImg?path=bg.jpg&t=${System.currentTimeMillis()}"
    }

    private fun buildSelectedClientScreenshotUrl(): String {
        val client = selectedClient()
        val path = URLEncoder.encode("${client.address}.jpg", Charsets.UTF_8.name())
        return "${_state.value.serverUrl}zhanting/computer/image/download?path=$path&t=${System.currentTimeMillis()}"
    }

    private fun addLog(message: String) {
        _state.update { state ->
            state.copy(logs = listOf("${nowTime()}  $message") + state.logs.take(119))
        }
    }

    private fun getInitialServerUrl(): String {
        val stored = preferences.getString(KEY_SERVER_URL, null)
        val normalized = stored?.let(::normalizeHttpUrl) ?: DEFAULT_SERVER_URL
        return if (normalized in LEGACY_SERVER_URLS) {
            preferences.edit().putString(KEY_SERVER_URL, DEFAULT_SERVER_URL).apply()
            DEFAULT_SERVER_URL
        } else {
            normalized
        }
    }

    private fun normalizeHttpUrl(value: String): String {
        val trimmed = value.trim().ifBlank { DEFAULT_SERVER_URL }
        val withProtocol = if (trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true)) {
            trimmed
        } else {
            "http://$trimmed"
        }
        return if (withProtocol.endsWith("/")) withProtocol else "$withProtocol/"
    }

    private fun formatJsonText(text: String): String {
        return runCatching {
            when {
                text.trimStart().startsWith("[") -> JSONArray(text).toString(2)
                text.trimStart().startsWith("{") -> JSONObject(text).toString(2)
                else -> text
            }
        }.getOrDefault(text)
    }

    private fun isUdpCommand(command: String): Boolean {
        val normalized = command.trim().uppercase()
        return normalized == "3A 30 30 31 30 30 30 42 30 30 30 30 31 30 30 30 31 33 45 0D 0A" ||
            normalized == "3A 30 30 31 30 30 30 42 30 30 30 30 31 30 30 30 32 33 44 0D 0A"
    }

    private fun countSuccessfulDevices(devices: JSONArray?): Int? {
        if (devices == null) return null
        var count = 0
        for (index in 0 until devices.length()) {
            if (devices.optJSONObject(index)?.optBoolean("success", false) == true) {
                count++
            }
        }
        return count
    }

    private fun logFailedAdbDevices(devices: JSONArray?) {
        if (devices == null) return
        for (index in 0 until devices.length()) {
            val device = devices.optJSONObject(index) ?: continue
            if (!device.optBoolean("success", true)) {
                val name = device.optString("ip").ifBlank { device.optString("serial").ifBlank { "未知设备" } }
                val message = device.optString("message").ifBlank { "无返回信息" }
                addLog("ADB 失败：$name · $message")
            }
        }
    }

    private fun nowTime(): String = DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date())

    companion object {
        private const val KEY_SERVER_URL = "zt-server-url"
        private const val KEY_SELECTED_CLIENT = "zt-selected-client"
    }
}

const val DEFAULT_SERVER_URL = "http://192.168.5.32:8880/"

private val LEGACY_SERVER_URLS = setOf("http://192.168.5.88:8880/")
