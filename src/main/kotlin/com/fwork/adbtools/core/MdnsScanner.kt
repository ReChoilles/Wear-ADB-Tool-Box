package com.fwork.adbtools.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.flyfishxu.kadb.mdns.KadbMdnsJvm
import com.flyfishxu.kadb.mdns.MdnsConfig
import com.flyfishxu.kadb.mdns.MdnsEndpoint
import com.flyfishxu.kadb.mdns.MdnsServiceType
import com.flyfishxu.kadb.mdns.MdnsStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** mDNS 无线调试发现（kadb-mdns / JmDNS）。 */
object MdnsScanner {
    var status by mutableStateOf(MdnsStatus.STOPPED)
    var endpoints by mutableStateOf<List<MdnsEndpoint>>(emptyList())

    private var impl: KadbMdnsJvm? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val running: Boolean get() = impl != null

    fun toggle() {
        if (running) stop() else start()
    }

    fun start() {
        if (running) return
        val config = MdnsConfig(serviceTypes = Prefs.mdnsTypes)
        val mdns = KadbMdnsJvm(config)
        mdns.start()
        impl = mdns
        scope.launch {
            mdns.state.collect { snapshot ->
                status = snapshot.status
                endpoints = snapshot.allDevices
            }
        }
        LogBus.info("mDNS 扫描已启动")
        Toast.show("开始发现无线调试设备…")
    }

    fun stop() {
        impl?.close()
        impl = null
        status = MdnsStatus.STOPPED
        endpoints = emptyList()
        LogBus.info("mDNS 扫描已停止")
    }
}
