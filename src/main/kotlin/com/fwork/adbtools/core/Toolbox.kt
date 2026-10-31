package com.fwork.adbtools.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.flyfishxu.kadb.Kadb
import com.flyfishxu.kadb.KadbOptions
import com.flyfishxu.kadb.exception.AdbAuthException
import com.flyfishxu.kadb.mdns.MdnsEndpoint
import com.flyfishxu.kadb.usb.KadbUsb
import com.flyfishxu.kadb.usb.KadbUsbDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 连接历史（手动地址记忆）。 */
object HistoryStore {
    private val props = java.util.Properties()

    fun load(): List<String> {
        val f = AppDirs.historyFile
        if (!f.exists()) return emptyList()
        runCatching {
            java.io.FileInputStream(f).use { props.load(it) }
        }
        return props.stringPropertyNames()
            .filter { it.startsWith("h") }
            .mapNotNull { props.getProperty(it)?.takeIf(String::isNotBlank) }
            .distinct()
    }

    @Synchronized
    fun add(target: String) {
        if (target.isBlank()) return
        val updated = (listOf(target) + load().filter { it != target }).take(10)
        props.clear()
        updated.forEachIndexed { i, v -> props.setProperty("h$i", v) }
        runCatching {
            java.io.FileOutputStream(AppDirs.historyFile).use { props.store(it, "connect history") }
        }
    }
}

/** 全局连接模型。 */
object Toolbox {
    val links = mutableStateListOf<DeviceLink>()
    var activeIndex by mutableIntStateOf(0)
    val active: DeviceLink? get() = links.getOrNull(activeIndex)

    var busy by mutableStateOf(false)
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun options(): KadbOptions = KadbOptions(delayedAckMode = Prefs.delayedAckMode)

    fun disconnect(link: DeviceLink) {
        link.disconnect()
        links.removeAll { it === link }
        if (activeIndex >= links.size) activeIndex = (links.size - 1).coerceAtLeast(0)
        LogBus.info("已断开：${link.label}")
    }

    fun disconnectAll() {
        links.toList().forEach { it.disconnect() }
        links.clear()
        activeIndex = 0
    }

    /** TCP 直连（含模拟器）。 */
    fun connectTcp(host: String, port: Int, onDone: (DeviceLink?) -> Unit = {}) {
        if (host.isBlank() || port !in 1..65535) {
            Toast.show("地址或端口无效")
            return
        }
        busy = true
        scope.launch {
            val created = runCatching {
                withContext(Dispatchers.IO) {
                    val kadb = Kadb.create(
                        host = host.trim(),
                        port = port,
                        connectTimeout = Prefs.connectTimeoutSec * 1000,
                        socketTimeout = Prefs.socketTimeoutSec * 1000,
                        options = options(),
                    )
                    kadb.shell("echo ok") // 触发鉴权 + 可用性检查
                    kadb
                }
            }
            busy = false
            created
                .onFailure { t ->
                    val msg = friendlyError(t)
                    LogBus.error("连接失败 $host:$port —— $msg")
                    Toast.show("连接失败：" + msg)
                    onDone(null)
                }
                .onSuccess { kadb ->
                    val label = "$host:$port"
                    val target = DeviceLink(label, LinkType.TCP, kadb)
                    links.add(target)
                    activeIndex = links.indexOf(target)
                    Toast.show("已连接 $label")
                    LogBus.ok("已连接 $label")
                    HistoryStore.add("$host:$port")
                    target.refreshProps()
                    onDone(target)
                }
        }
    }

    /** USB 直连（桌面 JVM）。 */
    fun connectUsb(dev: KadbUsbDevice) {
        busy = true
        scope.launch {
            val created = runCatching {
                withContext(Dispatchers.IO) { KadbUsb.create(dev) }
            }
            busy = false
            created
                .onFailure { t ->
                    val msg = friendlyError(t)
                    LogBus.error("USB 连接失败 —— $msg")
                    Toast.show("USB 连接失败：" + msg)
                }
                .onSuccess { kadb ->
                    val label = dev.productName ?: "USB 设备 ${dev.vendorId.toString(16)}:${dev.productId.toString(16)}"
                    val target = DeviceLink(label, LinkType.USB, kadb)
                    links.add(target)
                    activeIndex = links.indexOf(target)
                    Toast.show("已连接（USB）$label")
                    LogBus.ok("已连接（USB）$label")
                    target.refreshProps()
                }
        }
    }

    /** mDNS 端点 → TCP 连接。 */
    fun connectEndpoint(ep: MdnsEndpoint) = connectTcp(ep.host, ep.port)

    /** 无线配对（Android 11+）。 */
    fun pair(host: String, port: Int, pairingCode: String, name: String) {
        if (host.isBlank() || port !in 1..65535) {
            Toast.show("配对地址或端口无效")
            return
        }
        if (pairingCode.trim().length != 6) {
            Toast.show("配对码应为 6 位数字")
            return
        }
        busy = true
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    Kadb.pair(host.trim(), port, pairingCode.trim(), name.ifBlank { "PC" })
                }
            }.onSuccess {
                busy = false
                Toast.show("配对成功 —— 可连接 $host:$port")
                LogBus.ok("配对成功 $host:$port")
                HistoryStore.add("$host:$port")
            }.onFailure { t ->
                busy = false
                val msg = friendlyError(t)
                LogBus.error("配对失败 $host:$port —— $msg")
                Toast.show("配对失败：" + msg)
            }
        }
    }

    fun friendlyError(t: Throwable): String = when {
        t is AdbAuthException -> "设备拒绝了本机密钥（请在设备上允许调试）"
        else -> t.message?.take(160) ?: t::class.simpleName ?: "未知错误"
    }
}
