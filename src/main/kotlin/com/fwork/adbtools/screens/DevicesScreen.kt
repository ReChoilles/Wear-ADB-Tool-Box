package com.fwork.adbtools.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import com.fwork.adbtools.ui.icons.Bolt
import com.fwork.adbtools.ui.icons.Android
import com.fwork.adbtools.ui.icons.Refresh
import com.fwork.adbtools.ui.icons.Search
import com.fwork.adbtools.ui.icons.Usb
import com.fwork.adbtools.ui.icons.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.flyfishxu.kadb.mdns.MdnsServiceType
import com.flyfishxu.kadb.usb.KadbUsbDevice
import com.fwork.adbtools.core.HistoryStore
import com.fwork.adbtools.core.LinkState
import com.fwork.adbtools.core.LogBus
import com.fwork.adbtools.core.MdnsScanner
import com.fwork.adbtools.core.Toolbox
import com.fwork.adbtools.core.Toast
import com.fwork.adbtools.core.UsbScanner
import com.fwork.adbtools.ui.EmptyHint
import com.fwork.adbtools.ui.FieldRow
import com.fwork.adbtools.ui.IconAction
import com.fwork.adbtools.ui.LabeledField
import com.fwork.adbtools.ui.PillToggle
import com.fwork.adbtools.ui.SectionCard
import com.fwork.adbtools.ui.StatusPill
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun DevicesScreen() {
    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CurrentConnectionCard()
        ManualTcpCard()
        PairingCard()
        MdnsCard()
        UsbCard()
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun CurrentConnectionCard() {
    val link = Toolbox.active
    val scope = rememberCoroutineScope()

    // 连接存活监测
    LaunchedEffect(link) {
        if (link == null) return@LaunchedEffect
        while (true) {
            delay(4000)
            if (link.state == LinkState.CONNECTED) {
                val alive = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    runCatching { link.kadb.connectionCheck() }.getOrDefault(false)
                }
                if (!alive) link.state = LinkState.FAILED
            }
        }
    }

    SectionCard(
        title = "当前连接",
        subtitle = link?.label,
        icon = androidx.compose.material.icons.Icons.Filled.Android,
        trailing = { StatusPill(link?.state) },
    ) {
        if (link == null) {
            EmptyHint(
                "尚未连接任何设备 —— 使用下方 TCP / mDNS / USB 建立连接",
                icon = androidx.compose.material.icons.Icons.Filled.Android,
            )
        } else {
            link.props?.let { p ->
                FieldRow("型号", listOfNotNull(p.brand.takeIf { it.isNotBlank() }, p.model.takeIf { it.isNotBlank() }).joinToString(" ").takeIf { it.isNotBlank() })
                FieldRow("Android", p.android.takeIf { it.isNotBlank() }?.let { "$it (SDK ${p.sdk})" })
                FieldRow("序列号", p.serial)
                FieldRow("CPU", p.abi)
                FieldRow("安全补丁", p.securityPatch)
                FieldRow("电量", p.batteryLevel)
                link.lastError?.let { FieldRow("最近错误", it) }
            } ?: run {
                Text(
                    "读取设备信息中…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = {
                    Toolbox.disconnect(link)
                }) { Text("断开") }
                OutlinedButton(onClick = {
                    scope.launch {
                        link.refreshProps()
                        Toast.show("已刷新设备信息")
                    }
                }) { Text("刷新信息") }
                OutlinedButton(onClick = {
                    scope.launch {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { link.kadb.resetConnection() }
                        Toast.show("adb 传输已复位")
                        LogBus.info("resetConnection：传输已复位")
                    }
                }) { Text("复位传输") }
            }
        }
    }
}

@Composable
private fun ManualTcpCard() {
    var host by remember { mutableStateOf("127.0.0.1") }
    var port by remember { mutableStateOf("") }
    var history by remember { mutableStateOf(HistoryStore.load()) }

    SectionCard(
        title = "手动 TCP 连接",
        subtitle = "模拟器填 127.0.0.1:5555；设备为“无线调试”的 IP 与端口",
        icon = Icons.Filled.Wifi,
        trailing = {
            if (Toolbox.busy) CircularProgressIndicator(Modifier.height(18.dp))
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            LabeledField("主机", host, { host = it }, Modifier.weight(1.6f))
            LabeledField("端口", port, { port = it }, Modifier.weight(1f), placeholder = "5555")
            Button(
                onClick = { Toolbox.connectTcp(host, port.toIntOrNull() ?: 0) },
                enabled = !Toolbox.busy,
            ) { Text("连接") }
        }

        if (history.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                history.take(8).forEach { target ->
                    PillToggle(selected = false, text = target, onClick = {
                        val parts = target.split(":")
                        if (parts.size == 2) {
                            host = parts[0]
                            port = parts[1]
                            Toolbox.connectTcp(host, parts[1].toIntOrNull() ?: 0)
                        }
                    })
                }
            }
        }
    }
}

@Composable
private fun PairingCard() {
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }

    SectionCard(
        title = "无线配对（Android 11+）",
        subtitle = "设备 → 开发者选项 → 无线调试 → 使用配对码配对设备",
        icon = androidx.compose.material.icons.Icons.Filled.Bolt,
        trailing = {
            if (Toolbox.busy) CircularProgressIndicator(Modifier.height(18.dp))
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            LabeledField("主机", host, { host = it }, Modifier.weight(1.4f))
            LabeledField("配对端口", port, { port = it }, Modifier.weight(1f))
            LabeledField("配对码", code, { code = it.filter { ch -> ch.isDigit() }.take(6) }, Modifier.weight(1f))
            LabeledField("主机名", name, { name = it }, Modifier.weight(1.2f), placeholder = "PC/ToolBox")
            Button(
                onClick = { Toolbox.pair(host, port.toIntOrNull() ?: 0, code, name) },
                enabled = !Toolbox.busy,
            ) { Text("配对") }
        }
        Text(
            "配对成功后，同一 IP 的“无线调试”端口会出现在 mDNS 列表，或直接在上方手动连接。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MdnsCard() {
    SectionCard(
        title = "无线设备发现（mDNS）",
        subtitle = "_tls-connect / _tls-pairing / _adb 服务发现（JmDNS）",
        icon = Icons.Filled.Search,
        trailing = {
            PillToggle(
                selected = MdnsScanner.running,
                text = if (MdnsScanner.running) "已开启" else "未开启",
                onClick = { MdnsScanner.toggle() },
            )
        },
    ) {
        if (!MdnsScanner.running) {
            EmptyHint("开启后自动发现局域网内的 ADB TLS 服务", icon = Icons.Filled.Search)
        } else {
            val list = MdnsScanner.endpoints
            if (list.isEmpty()) {
                Text(
                    "扫描中…（设备需开启“无线调试”）",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else list.forEach { ep ->
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(ep.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "${ep.host}:${ep.port} · ${typeLabel(ep.serviceType)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { Toolbox.connectEndpoint(ep) }) { Text("连接") }
                }
            }
        }
    }
}

private fun typeLabel(type: MdnsServiceType): String = when (type) {
    MdnsServiceType.TLS_CONNECT -> "可连接（TLS）"
    MdnsServiceType.TLS_PAIRING -> "可配对（TLS）"
    MdnsServiceType.ADB -> "ADB"
}

@Composable
private fun UsbCard() {
    LaunchedEffect(Unit) { UsbScanner.refresh() }

    SectionCard(
        title = "USB 设备",
        subtitle = "桌面独立 USB 通道（不依赖 adb server；首次使用需在设备上允许 USB 调试）",
        icon = Icons.Filled.Usb,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (UsbScanner.scanning) CircularProgressIndicator(Modifier.height(16.dp))
                IconAction(Icons.Filled.Refresh, "重新枚举", onClick = { UsbScanner.refresh() })
            }
        },
    ) {
        if (UsbScanner.lastError != null) {
            Text(
                "USB 不可用：${UsbScanner.lastError}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (UsbScanner.devices.isEmpty()) {
            EmptyHint("未发现 ADB USB 设备 —— 连接后点击右上角刷新", icon = Icons.Filled.Usb)
        } else {
            UsbScanner.devices.forEach { dev ->
                UsbRow(dev)
            }
        }
    }
}

@Composable
private fun UsbRow(dev: KadbUsbDevice) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(dev.productName ?: dev.manufacturer ?: "USB 设备", style = MaterialTheme.typography.bodyLarge)
            Text(
                "VID:${dev.vendorId.toString(16)} PID:${dev.productId.toString(16)}  serial=${dev.serialNumber ?: "-"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(
            onClick = { Toolbox.connectUsb(dev) },
            enabled = dev.isConnected && !Toolbox.busy,
        ) { Text("连接") }
    }
}
