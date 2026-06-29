package com.hzvtc.zhanting2026

import android.database.Cursor
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TabletAndroid
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hzvtc.zhanting2026.data.ClientCommand
import com.hzvtc.zhanting2026.data.ControlDevice
import com.hzvtc.zhanting2026.data.ControlModule
import com.hzvtc.zhanting2026.data.DisplayDevices
import com.hzvtc.zhanting2026.data.DisplayDevice
import com.hzvtc.zhanting2026.data.clientCommandsFor
import com.hzvtc.zhanting2026.ui.theme.ZhanTing2026Theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        setContent {
            ZhanTing2026Theme {
                ZhanTingControlApp()
            }
        }
    }
}

@Composable
fun ZhanTingControlApp(viewModel: DashboardViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var serverInput by remember(state.serverUrl) { mutableStateOf(state.serverUrl) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }
            if (bytes != null) {
                viewModel.selectKvFile(context.displayName(uri), bytes)
            }
        }
    }

    Scaffold { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF3F5F0))
                .padding(innerPadding)
        ) {
            Image(
                painter = painterResource(R.drawable.bg_new),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.18f
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xDDF3F5F0))
            )

            BoxWithConstraints(Modifier.fillMaxSize()) {
                val tablet = maxWidth >= 920.dp
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    contentPadding = PaddingValues(
                        start = if (tablet) 28.dp else 14.dp,
                        top = if (tablet) 28.dp else 16.dp,
                        end = if (tablet) 28.dp else 14.dp,
                        bottom = 32.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 1480.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            TopBar(
                                tablet = tablet,
                                serverInput = serverInput,
                                onServerInputChange = { serverInput = it },
                                onSaveServer = { viewModel.saveServerUrl(serverInput) }
                            )
                            HeroPanel(
                                tablet = tablet,
                                state = state,
                                onOpenHall = viewModel::openHall,
                                onCloseHall = viewModel::closeHall
                            )
                            StatusGrid(state = state, tablet = tablet)
                            AdbPanel(state = state, onWake = viewModel::wakeAndroidScreensOnly)
                            ClientWorkbench(
                                state = state,
                                tablet = tablet,
                                onSelectClient = viewModel::selectClient,
                                onCommand = viewModel::postClientCommand,
                                onPickImage = { imagePicker.launch("image/jpeg") },
                                onUploadImage = viewModel::uploadSelectedClientKv,
                                onPreviewImage = viewModel::previewSelectedClientImage,
                                onRefreshResources = viewModel::fetchSelectedClientResources,
                                onRefreshScreenshot = viewModel::refreshScreenshotManually
                            )
                            HardwareWorkbench(
                                state = state,
                                tablet = tablet,
                                onSelectModule = viewModel::selectModule,
                                onModuleAction = viewModel::controlSelectedModule,
                                onDeviceAction = viewModel::controlDevice,
                                onTerminalAction = viewModel::controlTerminal
                            )
                            LogPanel(state = state)
                        }
                    }
                }
            }

            if (state.busyMessage.isNotBlank()) {
                BusyToast(
                    message = state.busyMessage,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 14.dp, start = 14.dp, end = 14.dp)
                )
            }
        }
    }
}

@Composable
private fun TopBar(
    tablet: Boolean,
    serverInput: String,
    onServerInputChange: (String) -> Unit,
    onSaveServer: () -> Unit
) {
    if (tablet) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            TitleBlock(Modifier.weight(1f))
            ServerInput(
                value = serverInput,
                onValueChange = onServerInputChange,
                onSave = onSaveServer,
                modifier = Modifier.widthIn(min = 360.dp, max = 520.dp)
            )
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TitleBlock()
            ServerInput(
                value = serverInput,
                onValueChange = onServerInputChange,
                onSave = onSaveServer,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun TitleBlock(modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Eyebrow("ZhanTing 2026")
        Text(
            text = "杭转中心展厅控制台",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Black,
            color = AppText
        )
    }
}

@Composable
private fun ServerInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "唯一服务端",
            style = MaterialTheme.typography.labelMedium,
            color = AppMuted
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = onSave) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "保存并重连")
                }
            }
        )
    }
}

@Composable
private fun HeroPanel(
    tablet: Boolean,
    state: DashboardState,
    onOpenHall: () -> Unit,
    onCloseHall: () -> Unit
) {
    Panel {
        if (tablet) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HeroCopy(Modifier.weight(1f))
                Column(
                    modifier = Modifier.widthIn(min = 360.dp, max = 460.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MasterButton(
                        text = "一键开馆",
                        icon = Icons.Default.WbSunny,
                        enabled = !state.isBusy,
                        colors = listOf(Color(0xFF0E8F7A), Color(0xFF156EAF)),
                        onClick = onOpenHall
                    )
                    MasterButton(
                        text = "一键闭馆",
                        icon = Icons.Default.DarkMode,
                        enabled = !state.isBusy,
                        colors = listOf(Color(0xFF293A35), Color(0xFFC7362F)),
                        onClick = onCloseHall
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                HeroCopy()
                MasterButton(
                    text = "一键开馆",
                    icon = Icons.Default.WbSunny,
                    enabled = !state.isBusy,
                    colors = listOf(Color(0xFF0E8F7A), Color(0xFF156EAF)),
                    onClick = onOpenHall
                )
                MasterButton(
                    text = "一键闭馆",
                    icon = Icons.Default.DarkMode,
                    enabled = !state.isBusy,
                    colors = listOf(Color(0xFF293A35), Color(0xFFC7362F)),
                    onClick = onCloseHall
                )
            }
        }
    }
}

@Composable
private fun HeroCopy(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Eyebrow("Exhibition Power")
        Text(
            text = "一键展厅硬件开关",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            color = AppText
        )
        Text(
            text = "服务端负责展厅硬件与设备发现；展示电脑的静音、KV、视频、重启和关机由单台客户端执行。",
            style = MaterialTheme.typography.bodyLarge,
            color = AppMuted
        )
    }
}

@Composable
private fun MasterButton(
    text: String,
    icon: ImageVector,
    colors: List<Color>,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .clip(AppShape)
            .background(Brush.linearGradient(colors)),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
        contentPadding = PaddingValues(horizontal = 18.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(30.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun StatusGrid(state: DashboardState, tablet: Boolean) {
    val hardwareCount = state.modules.sumOf { module -> module.devices.count { it.ip.isNotBlank() } }
    val onlineCount = DisplayDevices.count { state.onlineDevices.contains(it.address) }
    ResponsiveGrid(count = 4, columns = if (tablet) 4 else 2) { index, modifier ->
        when (index) {
            0 -> StatusCard("服务端", state.health, Icons.Default.CheckCircle, modifier)
            1 -> StatusCard("WebSocket", state.wsStatus, Icons.Default.Wifi, modifier)
            2 -> StatusCard("在线客户端", "$onlineCount/${DisplayDevices.size}", Icons.Default.Computer, modifier)
            3 -> StatusCard("硬件控制器", "$hardwareCount 台", Icons.Default.Router, modifier)
        }
    }
}

@Composable
private fun StatusCard(label: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Panel(modifier = modifier, contentPadding = PaddingValues(16.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, contentDescription = null, tint = AppAccent, modifier = Modifier.size(30.dp))
            Column {
                Text(label, style = MaterialTheme.typography.bodySmall, color = AppMuted)
                Text(
                    value,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = AppText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun AdbPanel(state: DashboardState, onWake: () -> Unit) {
    Panel {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.TabletAndroid, contentDescription = null, tint = AppBlue, modifier = Modifier.size(34.dp))
            Column(Modifier.weight(1f)) {
                Eyebrow("Server ADB")
                Text("安卓屏 ADB 控制", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = AppText)
                Text(state.adbDetail, style = MaterialTheme.typography.bodyMedium, color = AppMuted)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(state.adbStatus, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = AppText)
                Button(
                    onClick = onWake,
                    enabled = !state.isBusy,
                    colors = ButtonDefaults.buttonColors(containerColor = AppBlue)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("唤醒安卓屏")
                }
            }
        }
    }
}

@Composable
private fun HardwareWorkbench(
    state: DashboardState,
    tablet: Boolean,
    onSelectModule: (Int) -> Unit,
    onModuleAction: (Boolean) -> Unit,
    onDeviceAction: (Int, Boolean) -> Unit,
    onTerminalAction: (Int, Int, Boolean) -> Unit
) {
    if (tablet) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ModuleSelector(
                modules = state.modules,
                selectedIndex = state.selectedModuleIndex,
                onSelect = onSelectModule,
                modifier = Modifier.width(270.dp),
                vertical = true
            )
            ModuleDetail(
                state = state,
                tablet = true,
                onModuleAction = onModuleAction,
                onDeviceAction = onDeviceAction,
                onTerminalAction = onTerminalAction,
                modifier = Modifier.weight(1f)
            )
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ModuleSelector(
                modules = state.modules,
                selectedIndex = state.selectedModuleIndex,
                onSelect = onSelectModule
            )
            ModuleDetail(
                state = state,
                tablet = false,
                onModuleAction = onModuleAction,
                onDeviceAction = onDeviceAction,
                onTerminalAction = onTerminalAction
            )
        }
    }
}

@Composable
private fun ModuleSelector(
    modules: List<ControlModule>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    vertical: Boolean = false
) {
    Panel(modifier = modifier) {
        SectionTitle("硬件模块", "${modules.size} 组")
        if (vertical) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                modules.forEachIndexed { index, module ->
                    ModuleButton(module, selectedIndex == index, onClick = { onSelect(index) }, Modifier.fillMaxWidth())
                }
            }
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(modules) { index, module ->
                    ModuleButton(
                        module = module,
                        selected = selectedIndex == index,
                        onClick = { onSelect(index) },
                        modifier = Modifier.widthIn(min = 136.dp, max = 190.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ModuleButton(module: ControlModule, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = AppShape,
        color = if (selected) Color(0x1A0E8F7A) else Color.Transparent,
        border = BorderStroke(1.dp, if (selected) Color(0x550E8F7A) else Color.Transparent)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(module.name, color = AppText, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${module.devices.size} 台控制器", style = MaterialTheme.typography.bodySmall, color = AppMuted)
        }
    }
}

@Composable
private fun ModuleDetail(
    state: DashboardState,
    tablet: Boolean,
    onModuleAction: (Boolean) -> Unit,
    onDeviceAction: (Int, Boolean) -> Unit,
    onTerminalAction: (Int, Int, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val module = state.modules.getOrNull(state.selectedModuleIndex)
    Panel(modifier = modifier) {
        if (module == null) {
            Text("未读取到硬件模块", color = AppMuted)
            return@Panel
        }

        val terminalCount = module.devices.sumOf { it.terminals.size }
        HeaderRow(
            eyebrow = "Hardware Module",
            title = module.name,
            trailing = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onModuleAction(true) }, enabled = !state.isBusy) {
                        Text("模块全开")
                    }
                    OutlinedButton(
                        onClick = { onModuleAction(false) },
                        enabled = !state.isBusy,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AppRed)
                    ) {
                        Text("模块全关")
                    }
                }
            }
        )
        Text("${module.devices.size} 台控制器 · $terminalCount 个回路/终端", color = AppMuted)
        Spacer(Modifier.height(14.dp))
        ResponsiveGrid(count = module.devices.size, columns = if (tablet) 2 else 1) { index, itemModifier ->
            ControlDeviceCard(
                device = module.devices[index],
                deviceIndex = index,
                busy = state.isBusy,
                onDeviceAction = onDeviceAction,
                onTerminalAction = onTerminalAction,
                modifier = itemModifier
            )
        }
    }
}

@Composable
private fun ControlDeviceCard(
    device: ControlDevice,
    deviceIndex: Int,
    busy: Boolean,
    onDeviceAction: (Int, Boolean) -> Unit,
    onTerminalAction: (Int, Int, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val title = device.name.ifBlank { "控制器 ${deviceIndex + 1}" }
    val protocol = if (device.terminals.any { isUdpCommand(it.openCommand) }) "UDP/TCP" else "TCP"
    Surface(
        modifier = modifier,
        shape = AppShape,
        color = Color(0xBBFFFFFF),
        border = BorderStroke(1.dp, AppLine)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(BorderStroke(0.dp, Color.Transparent))
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.Black, color = AppText)
                    Text(
                        text = "${device.ip.ifBlank { "客户端类条目，不在硬件模块中控制" }} · $protocol",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppMuted,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1
                    )
                }
                if (device.ip.isNotBlank()) {
                    IconButton(onClick = { onDeviceAction(deviceIndex, true) }, enabled = !busy) {
                        Icon(Icons.Default.WbSunny, contentDescription = "整台开启")
                    }
                    IconButton(onClick = { onDeviceAction(deviceIndex, false) }, enabled = !busy) {
                        Icon(Icons.Default.DarkMode, contentDescription = "整台关闭", tint = AppRed)
                    }
                }
            }

            device.terminals.forEachIndexed { terminalIndex, terminal ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(BorderStroke(0.5.dp, Color(0x14303030)))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        terminal.name,
                        modifier = Modifier.weight(1f),
                        color = AppText,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    OutlinedButton(
                        onClick = { onTerminalAction(deviceIndex, terminalIndex, true) },
                        enabled = !busy && device.ip.isNotBlank() && terminal.openCommand.isNotBlank(),
                        contentPadding = PaddingValues(horizontal = 14.dp)
                    ) {
                        Text("开")
                    }
                    OutlinedButton(
                        onClick = { onTerminalAction(deviceIndex, terminalIndex, false) },
                        enabled = !busy && device.ip.isNotBlank() && terminal.closeCommand.isNotBlank(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AppRed),
                        contentPadding = PaddingValues(horizontal = 14.dp)
                    ) {
                        Text("关")
                    }
                }
            }
        }
    }
}

@Composable
private fun ClientWorkbench(
    state: DashboardState,
    tablet: Boolean,
    onSelectClient: (String) -> Unit,
    onCommand: (ClientCommand) -> Unit,
    onPickImage: () -> Unit,
    onUploadImage: () -> Unit,
    onPreviewImage: () -> Unit,
    onRefreshResources: () -> Unit,
    onRefreshScreenshot: () -> Unit
) {
    if (tablet) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ClientSelector(
                state = state,
                onSelectClient = onSelectClient,
                onCommand = onCommand,
                modifier = Modifier.width(330.dp)
            )
            ClientDetail(
                state = state,
                tablet = true,
                onPickImage = onPickImage,
                onUploadImage = onUploadImage,
                onPreviewImage = onPreviewImage,
                onRefreshResources = onRefreshResources,
                onRefreshScreenshot = onRefreshScreenshot,
                modifier = Modifier.weight(1f)
            )
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ClientSelector(
                state = state,
                onSelectClient = onSelectClient,
                onCommand = onCommand
            )
            ClientDetail(
                state = state,
                tablet = false,
                onPickImage = onPickImage,
                onUploadImage = onUploadImage,
                onPreviewImage = onPreviewImage,
                onRefreshResources = onRefreshResources,
                onRefreshScreenshot = onRefreshScreenshot
            )
        }
    }
}

@Composable
private fun ClientSelector(
    state: DashboardState,
    onSelectClient: (String) -> Unit,
    onCommand: (ClientCommand) -> Unit,
    modifier: Modifier = Modifier
) {
    Panel(modifier = modifier) {
        SectionTitle("单机客户端", "WinUI Client :8899")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DisplayDevices.forEach { device ->
                val selected = state.selectedClientAddr == device.address
                ClientDeviceDrawer(
                    device = device,
                    selected = selected,
                    online = state.onlineDevices.contains(device.address),
                    busy = state.isBusy,
                    onClick = { onSelectClient(device.address) },
                    onCommand = onCommand
                )
            }
        }
    }
}

@Composable
private fun ClientDeviceDrawer(
    device: DisplayDevice,
    selected: Boolean,
    online: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
    onCommand: (ClientCommand) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(if (selected) 6.dp else 0.dp)) {
        ClientButton(
            device = device,
            selected = selected,
            online = online,
            onClick = onClick
        )
        if (selected) {
            ClientCommandDrawer(
                device = device,
                busy = busy,
                onCommand = onCommand
            )
        }
    }
}

@Composable
private fun ClientButton(
    device: DisplayDevice,
    selected: Boolean,
    online: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = AppShape,
        color = if (selected) Color(0x1A0E8F7A) else Color(0x80FFFFFF),
        border = BorderStroke(1.dp, if (online) Color(0x660E8F7A) else if (selected) Color(0x440E8F7A) else Color.Transparent)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(device.name, color = AppText, fontWeight = FontWeight.Bold)
                Text(device.address, color = AppMuted, style = MaterialTheme.typography.bodySmall)
            }
            StatusPill(text = if (online) "在线" else "未上报", online = online)
        }
    }
}

@Composable
private fun ClientCommandDrawer(
    device: DisplayDevice,
    busy: Boolean,
    onCommand: (ClientCommand) -> Unit
) {
    val commands = clientCommandsFor(device)
    Surface(
        shape = AppShape,
        color = Color(0xF7F9FCF8),
        border = BorderStroke(1.dp, Color(0x330E8F7A))
    ) {
        Column(Modifier.padding(12.dp)) {
            SectionTitle("可用功能", "${commands.size} 项")
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val columns = if (maxWidth < 260.dp) 1 else 2
                ResponsiveGrid(
                    count = commands.size,
                    columns = columns,
                    horizontalSpacing = 8.dp,
                    verticalSpacing = 8.dp
                ) { index, itemModifier ->
                    ClientCommandButton(
                        command = commands[index],
                        enabled = !busy,
                        onClick = { onCommand(commands[index]) },
                        modifier = itemModifier
                    )
                }
            }
        }
    }
}

@Composable
private fun ClientDetail(
    state: DashboardState,
    tablet: Boolean,
    onPickImage: () -> Unit,
    onUploadImage: () -> Unit,
    onPreviewImage: () -> Unit,
    onRefreshResources: () -> Unit,
    onRefreshScreenshot: () -> Unit,
    modifier: Modifier = Modifier
) {
    val client = DisplayDevices.firstOrNull { it.address == state.selectedClientAddr } ?: DisplayDevices.first()
    val online = state.onlineDevices.contains(client.address)

    Panel(modifier = modifier) {
        HeaderRow(
            eyebrow = "Selected Client",
            title = client.name,
            subtitle = "${client.address}:8899",
            trailing = { StatusPill(if (online) "在线" else "未上报", online) }
        )
        Spacer(Modifier.height(14.dp))
        ScreenshotPanel(state = state, onRefresh = onRefreshScreenshot)
        Spacer(Modifier.height(14.dp))
        if (tablet) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                KvPanel(
                    state = state,
                    onPickImage = onPickImage,
                    onUploadImage = onUploadImage,
                    onPreviewImage = onPreviewImage,
                    modifier = Modifier.weight(1f)
                )
                ResourcePanel(
                    state = state,
                    onRefresh = onRefreshResources,
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                KvPanel(
                    state = state,
                    onPickImage = onPickImage,
                    onUploadImage = onUploadImage,
                    onPreviewImage = onPreviewImage
                )
                ResourcePanel(state = state, onRefresh = onRefreshResources)
            }
        }
    }
}

@Composable
private fun ClientCommandButton(
    command: ClientCommand,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val contentColor = if (command.danger) AppRed else AppAccentStrong
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(46.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = contentColor),
        contentPadding = PaddingValues(horizontal = 10.dp)
    ) {
        Icon(
            imageVector = if (command.danger) Icons.Default.PowerSettingsNew else commandIcon(command.endpoint),
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(command.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ScreenshotPanel(state: DashboardState, onRefresh: () -> Unit) {
    SubPanel {
        SectionTitle("当前截图", state.screenshotStatus)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            OutlinedButton(onClick = onRefresh, enabled = !state.isBusy) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("刷新当前截图")
            }
        }
        Spacer(Modifier.height(10.dp))
        RemoteImageBox(
            url = state.screenshotPreviewUrl,
            emptyText = "暂无截图",
            emptyTextColor = Color(0xFFD9F2E9),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color(0xFF18201D), AppShape)
        )
    }
}

@Composable
private fun KvPanel(
    state: DashboardState,
    onPickImage: () -> Unit,
    onUploadImage: () -> Unit,
    onPreviewImage: () -> Unit,
    modifier: Modifier = Modifier
) {
    SubPanel(modifier = modifier) {
        SectionTitle("单机 KV", state.imageStatus)
        Text(
            text = state.selectedImageName ?: "未选择文件",
            color = AppMuted,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            OutlinedButton(onClick = onPickImage, enabled = !state.isBusy) {
                Icon(Icons.Default.Image, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("选择图片")
            }
            OutlinedButton(onClick = onUploadImage, enabled = !state.isBusy) {
                Icon(Icons.Default.UploadFile, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("上传到当前单机")
            }
            OutlinedButton(onClick = onPreviewImage, enabled = !state.isBusy) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("预览当前单机")
            }
        }
        Spacer(Modifier.height(10.dp))
        RemoteImageBox(
            url = state.imagePreviewUrl,
            emptyText = "暂无预览",
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .background(Color(0xCCFFFFFF), AppShape)
        )
    }
}

@Composable
private fun ResourcePanel(state: DashboardState, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    SubPanel(modifier = modifier) {
        SectionTitle("单机资源", "当前客户端")
        OutlinedButton(onClick = onRefresh, enabled = !state.isBusy) {
            Icon(Icons.Default.Memory, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("读取当前单机资源")
        }
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(360.dp)
                .background(Color(0xFF18201D), AppShape)
                .padding(12.dp)
        ) {
            Text(
                text = state.clientResources,
                color = Color(0xFFD9F2E9),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            )
        }
    }
}

@Composable
private fun LogPanel(state: DashboardState) {
    Panel {
        SectionTitle("执行日志", state.actionState)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .background(Color(0xFF18201D), AppShape)
                .padding(12.dp)
        ) {
            val logs = state.logs.ifEmpty { listOf("等待操作...") }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                logs.forEach { line ->
                    Text(line, color = Color(0xFFD9F2E9), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun RemoteImageBox(
    url: String,
    emptyText: String,
    modifier: Modifier = Modifier,
    emptyTextColor: Color = AppMuted
) {
    var image by remember(url) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var loading by remember(url) { mutableStateOf(false) }

    LaunchedEffect(url) {
        image = null
        if (url.isBlank()) return@LaunchedEffect
        loading = true
        image = withContext(Dispatchers.IO) {
            runCatching {
                (URL(url).openConnection()).apply {
                    connectTimeout = 6_000
                    readTimeout = 6_000
                    useCaches = false
                }.getInputStream().use { stream ->
                    BitmapFactory.decodeStream(stream)?.asImageBitmap()
                }
            }.getOrNull()
        }
        loading = false
    }

    Box(
        modifier = modifier
            .clip(AppShape)
            .border(1.dp, AppLine, AppShape),
        contentAlignment = Alignment.Center
    ) {
        when {
            image != null -> Image(
                bitmap = image!!,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
            loading -> CircularProgressIndicator(color = AppAccent)
            else -> Text(emptyText, color = emptyTextColor, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun HeaderRow(
    eyebrow: String,
    title: String,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Eyebrow(eyebrow)
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = AppText)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, color = AppMuted)
            }
        }
        trailing?.invoke()
    }
}

@Composable
private fun Panel(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = AppShape,
        color = Color(0xEEFFFFFF),
        border = BorderStroke(1.dp, AppLine),
        shadowElevation = 2.dp
    ) {
        Column(Modifier.padding(contentPadding)) {
            content()
        }
    }
}

@Composable
private fun SubPanel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = AppShape,
        color = Color(0x88FFFFFF),
        border = BorderStroke(1.dp, AppLine)
    ) {
        Column(Modifier.padding(16.dp)) {
            content()
        }
    }
}

@Composable
private fun SectionTitle(title: String, detail: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, modifier = Modifier.weight(1f), color = AppText, fontWeight = FontWeight.Black)
        Text(detail, color = AppMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun StatusPill(text: String, online: Boolean) {
    val color = if (online) AppAccentStrong else AppRed
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.28f))
    ) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun Eyebrow(text: String) {
    Text(
        text = text,
        color = AppAccentStrong,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Black
    )
}

@Composable
private fun BusyToast(message: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.widthIn(max = 560.dp),
        shape = AppShape,
        color = Color(0xFFFFF9E8),
        border = BorderStroke(1.dp, Color(0x52D58A00)),
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = AppAmber)
            Text(message, color = Color(0xFF7A4B00), fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun ResponsiveGrid(
    count: Int,
    columns: Int,
    horizontalSpacing: Dp = 12.dp,
    verticalSpacing: Dp = 12.dp,
    content: @Composable (Int, Modifier) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(verticalSpacing)) {
        var index = 0
        while (index < count) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(horizontalSpacing)
            ) {
                repeat(columns) { column ->
                    val itemIndex = index + column
                    if (itemIndex < count) {
                        content(itemIndex, Modifier.weight(1f))
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
            index += columns
        }
    }
}

private fun commandIcon(endpoint: String): ImageVector {
    return when {
        endpoint.contains("Volume", ignoreCase = true) -> Icons.Default.Wifi
        endpoint.contains("Video", ignoreCase = true) -> Icons.Default.Computer
        endpoint.contains("Wallpaper", ignoreCase = true) -> Icons.Default.Image
        endpoint.contains("Click", ignoreCase = true) -> Icons.AutoMirrored.Filled.Send
        else -> Icons.Default.Refresh
    }
}

private fun isUdpCommand(command: String): Boolean {
    val normalized = command.trim().uppercase()
    return normalized == "3A 30 30 31 30 30 30 42 30 30 30 30 31 30 30 30 31 33 45 0D 0A" ||
        normalized == "3A 30 30 31 30 30 30 42 30 30 30 30 31 30 30 30 32 33 44 0D 0A"
}

private fun android.content.Context.displayName(uri: Uri): String {
    var cursor: Cursor? = null
    return try {
        cursor = contentResolver.query(uri, null, null, null, null)
        val nameIndex = cursor?.getColumnIndex(OpenableColumns.DISPLAY_NAME) ?: -1
        if (cursor != null && cursor.moveToFirst() && nameIndex >= 0) {
            cursor.getString(nameIndex)
        } else {
            "bg.jpg"
        }
    } finally {
        cursor?.close()
    }
}

private val AppShape = RoundedCornerShape(8.dp)
private val AppText = Color(0xFF15201B)
private val AppMuted = Color(0xFF64716B)
private val AppLine = Color(0x24303030)
private val AppAccent = Color(0xFF0E8F7A)
private val AppAccentStrong = Color(0xFF075F52)
private val AppBlue = Color(0xFF2367C6)
private val AppRed = Color(0xFFC7362F)
private val AppAmber = Color(0xFFD58A00)

@Preview(showBackground = true, widthDp = 420)
@Composable
private fun PreviewApp() {
    ZhanTing2026Theme {
        Text("ZhanTing 2026")
    }
}
