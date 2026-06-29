package com.hzvtc.zhanting2026.data

import android.content.Context
import org.json.JSONArray

data class TerminalDevice(
    val name: String,
    val openCommand: String,
    val closeCommand: String
)

data class ControlDevice(
    val ip: String,
    val name: String,
    val onceControl: Pair<String, String>?,
    val terminals: List<TerminalDevice>
)

data class ControlModule(
    val name: String,
    val devices: List<ControlDevice>
)

data class DisplayDevice(
    val key: String,
    val name: String,
    val address: String
)

data class ClientCommand(
    val label: String,
    val endpoint: String,
    val danger: Boolean = false,
    val deviceKeys: Set<String> = emptySet()
) {
    fun isAvailableFor(device: DisplayDevice): Boolean {
        return deviceKeys.isEmpty() || device.key in deviceKeys
    }
}

fun loadControlModules(context: Context): List<ControlModule> {
    val json = context.assets.open("control-devices.json").use { input ->
        input.bufferedReader(Charsets.UTF_8).readText()
    }
    val modules = JSONArray(json)
    return buildList {
        for (moduleIndex in 0 until modules.length()) {
            val module = modules.getJSONObject(moduleIndex)
            val devicesJson = module.getJSONArray("control_devices")
            val devices = buildList {
                for (deviceIndex in 0 until devicesJson.length()) {
                    val device = devicesJson.getJSONObject(deviceIndex)
                    val terminalsJson = device.getJSONArray("terminal_devices")
                    val terminals = buildList {
                        for (terminalIndex in 0 until terminalsJson.length()) {
                            val terminal = terminalsJson.getJSONObject(terminalIndex)
                            add(
                                TerminalDevice(
                                    name = terminal.optString("terminal_name"),
                                    openCommand = terminal.optString("terminal_open"),
                                    closeCommand = terminal.optString("terminal_close")
                                )
                            )
                        }
                    }

                    val onceControl = device.optJSONArray("once_control")?.let { once ->
                        if (once.length() >= 2) {
                            once.optString(0) to once.optString(1)
                        } else {
                            null
                        }
                    }

                    add(
                        ControlDevice(
                            ip = device.optString("device_ip"),
                            name = device.optString("device_name"),
                            onceControl = onceControl,
                            terminals = terminals
                        )
                    )
                }
            }

            add(
                ControlModule(
                    name = module.optString("module"),
                    devices = devices
                )
            )
        }
    }
}

val DisplayDevices = listOf(
    DisplayDevice("huanyingdaping", "欢迎大屏", "192.168.5.214"),
    DisplayDevice("zhinengdaolan", "智能导览", "192.168.5.221"),
    DisplayDevice("145xingdong", "145行动", "192.168.5.230"),
    DisplayDevice("xuanchuanpian", "宣传片", "192.168.5.213"),
    DisplayDevice("fazhanlicheng", "发展历程", "192.168.5.229"),
    DisplayDevice("gongjice", "供给侧", "192.168.5.217"),
    DisplayDevice("chengguotupu", "成果图谱", "192.168.5.228"),
    DisplayDevice("xuqiuce", "需求侧", "192.168.5.202"),
    DisplayDevice("qiyetupu", "企业图谱", "192.168.5.227"),
    DisplayDevice("fuwuce", "服务侧", "192.168.5.211"),
    DisplayDevice("gainianyanzheng", "概念验证", "192.168.5.231"),
    DisplayDevice("kejijinrong", "科技金融", "192.168.5.226"),
    DisplayDevice("zaiti", "载体", "192.168.5.212"),
    DisplayDevice("zaitixiaoping", "载体-小屏", "192.168.5.219"),
    DisplayDevice("anzhuoping", "安卓屏", "192.168.5.215"),
    DisplayDevice("kejifuwu", "科技服务", "192.168.5.225"),
    DisplayDevice("jigouyurencai", "机构与人才", "192.168.5.224"),
    DisplayDevice("zhengcezhichi", "政策支持", "192.168.5.223"),
    DisplayDevice("pinpaixuanchuan", "品牌宣传", "192.168.5.232"),
    DisplayDevice("jiashicang", "驾驶舱", "192.168.5.218"),
    DisplayDevice("dashiji", "大事记", "192.168.5.250"),
    DisplayDevice("qianmingping", "签名屏", "192.168.5.222")
)

val ClientCommands = listOf(
    ClientCommand("静音", "muteSysVolume"),
    ClientCommand("取消静音", "unMuteSysVolume"),
    ClientCommand("测试音量", "testVolume"),
    ClientCommand("音量 +", "enlargeSysVolume"),
    ClientCommand("音量 -", "reduceSysVolume"),
    ClientCommand("模拟双击", "simulateClick"),
    ClientCommand("播放视频 1", "playVideo1", deviceKeys = setOf(PROMO_VIDEO_KEY)),
    ClientCommand("播放视频 2", "playVideo2", deviceKeys = setOf(PROMO_VIDEO_KEY)),
    ClientCommand("启动播放器", "startPlayVideo", deviceKeys = setOf(WELCOME_SCREEN_KEY)),
    ClientCommand("关闭播放器", "closeVideoPlayer", deviceKeys = setOf(WELCOME_SCREEN_KEY)),
    ClientCommand("刷新 KV", "setWallpaper"),
    ClientCommand("取消关机", "cancelPoweroff"),
    ClientCommand("重启单机", "rebootSys", danger = true),
    ClientCommand("关闭单机", "poweroff", danger = true)
)

fun clientCommandsFor(device: DisplayDevice): List<ClientCommand> {
    return ClientCommands.filter { it.isAvailableFor(device) }
}

private const val WELCOME_SCREEN_KEY = "huanyingdaping"
private const val PROMO_VIDEO_KEY = "xuanchuanpian"
