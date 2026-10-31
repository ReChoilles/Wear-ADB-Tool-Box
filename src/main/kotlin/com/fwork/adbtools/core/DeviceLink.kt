package com.fwork.adbtools.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.flyfishxu.kadb.Kadb
import com.flyfishxu.kadb.shell.AdbShellResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class LinkType { TCP, USB }

enum class LinkState { CONNECTING, CONNECTED, FAILED, CLOSED }

data class DeviceProps(
    val model: String,
    val brand: String,
    val device: String,
    val android: String,
    val sdk: String,
    val serial: String,
    val abi: String,
    val securityPatch: String,
    val batteryLevel: String? = null,
)

object PropParser {
    private val line = Regex("\\[([^\\[\\]]+)\\]:\\s*\\[(.*?)\\]")
    fun parse(raw: String): Map<String, String> =
        line.findAll(raw).associate { it.groupValues[1] to it.groupValues[2] }
}

/** ADB 分引流 shell 转义（与 AOSP escape_arg 一致）。 */
fun adbQuote(arg: String): String = "'" + arg.replace("'", "'\\''") + "'"

class DeviceLink(
    val label: String,
    val type: LinkType,
    val kadb: Kadb,
) {
    var state by mutableStateOf(LinkState.CONNECTED)
    var props by mutableStateOf<DeviceProps?>(null)
    var lastError by mutableStateOf<String?>(null)

    suspend fun shell(command: String): AdbShellResponse =
        withContext(Dispatchers.IO) { kadb.shell(command) }

    suspend fun rootAdbd(): String = withContext(Dispatchers.IO) { kadb.root() }

    suspend fun unrootAdbd(): String = withContext(Dispatchers.IO) { kadb.unroot() }

    /** 读取设备属性（getprop + 电池信息）。 */
    suspend fun refreshProps() {
        try {
            val res = shell("getprop")
            val map = PropParser.parse(res.allOutput)
            val battery = runCatching {
                PropParser.parse(shell("dumpsys battery").allOutput)["level"]
            }.getOrNull()
            props = DeviceProps(
                model = map["ro.product.model"].orEmpty(),
                brand = map["ro.product.brand"].orEmpty(),
                device = map["ro.product.device"].orEmpty(),
                android = map["ro.build.version.release"].orEmpty(),
                sdk = map["ro.build.version.sdk"].orEmpty(),
                serial = map["ro.serialno"].orEmpty().ifBlank { map["ro.boot.serialno"].orEmpty() },
                abi = map["ro.product.cpu.abi"].orEmpty(),
                securityPatch = map["ro.build.version.security_patch"].orEmpty(),
                batteryLevel = battery?.let { "$it%" },
            )
            state = LinkState.CONNECTED
            lastError = null
        } catch (t: Throwable) {
            lastError = t.message
        }
    }

    @Volatile
    var closed = false
        private set

    fun disconnect() {
        if (closed) return
        closed = true
        runCatching { kadb.close() }
        state = LinkState.CLOSED
    }
}
