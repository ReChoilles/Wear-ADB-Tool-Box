package com.fwork.adbtools.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.flyfishxu.kadb.usb.KadbUsb
import com.flyfishxu.kadb.usb.KadbUsbDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** 桌面 USB 枚举（java-does-usb 后端）。 */
object UsbScanner {
    var devices by mutableStateOf<List<KadbUsbDevice>>(emptyList())
    var scanning by mutableStateOf(false)
    var lastError by mutableStateOf<String?>(null)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun refresh() {
        if (scanning) return
        scanning = true
        scope.launch {
            val result = runCatching { KadbUsb.devices() }
            scanning = false
            result
                .onSuccess {
                    devices = it
                    lastError = null
                    LogBus.info("USB 枚举完成：${it.size} 台")
                }
                .onFailure {
                    lastError = it.message ?: it::class.simpleName
                    LogBus.warn("USB 枚举失败：${lastError}")
                }
        }
    }

    fun describe(dev: KadbUsbDevice): String {
        val ids = "%04x:%04x".format(dev.vendorId, dev.productId)
        return dev.productName ?: (dev.manufacturer ?: "USB 设备").let { "$it ($ids)" }
    }
}
