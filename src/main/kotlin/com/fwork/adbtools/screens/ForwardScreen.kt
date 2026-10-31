package com.fwork.adbtools.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fwork.adbtools.core.DeviceLink
import com.fwork.adbtools.core.LogBus
import com.fwork.adbtools.core.Toast
import com.fwork.adbtools.core.Toolbox
import com.fwork.adbtools.ui.EmptyHint
import com.fwork.adbtools.ui.IconAction
import com.fwork.adbtools.ui.LabeledField
import com.fwork.adbtools.ui.SectionCard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private class ForwardEntry(val hostPort: Int, val targetPort: Int, val handle: AutoCloseable) {
    fun describe(): String = "localhost:$hostPort  →  设备 localhost:$targetPort"
}

/** 转发管理（tcpForward）+ adbd root 管理。 */
class ForwardViewModel(private val link: DeviceLink) {
    val entries = mutableStateListOf<ForwardEntry>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun add(hostPortText: String, targetPortText: String, onAdded: (String) -> Unit = {}) {
        val hostPort = hostPortText.trim().toIntOrNull()
        val targetPort = targetPortText.trim().toIntOrNull()
        if (hostPort == null || targetPort == null ||
            hostPort !in 1..65535 || targetPort !in 1..65535
        ) {
            Toast.show("端口需为 1–65535 的数字")
            return
        }
        if (entries.any { it.hostPort == hostPort }) {
            Toast.show("本地端口 $hostPort 已在转发中")
            return
        }
        scope.launch {
            val res = withContext(Dispatchers.IO) {
                runCatching { link.kadb.tcpForward(hostPort, targetPort) }
            }
            res
                .onSuccess { handle ->
                    entries.add(ForwardEntry(hostPort, targetPort, handle))
                    Toast.show("转发中 localhost:$hostPort → 设备 $targetPort")
                    LogBus.ok("tcp 转发：$hostPort → $targetPort（${link.label}）")
                    onAdded("")
                }
                .onFailure {
                    Toast.show("开启转发失败：" + Toolbox.friendlyError(it))
                    LogBus.warn("tcp 转发失败 $hostPort→$targetPort：${it.message}")
                }
        }
    }

    fun close(entry: ForwardEntry) {
        entries.remove(entry)
        runCatching { entry.handle.close() }
        LogBus.info("已停止转发 localhost:${entry.hostPort}")
    }

    fun closeAll() {
        entries.toList().forEach { runCatching { it.handle.close() } }
        entries.clear()
    }

    /** adbd 以 root 重启（打 root 后可访问受限目录）。 */
    fun root(onResult: (String) -> Unit) {
        scope.launch {
            val res = withContext(Dispatchers.IO) {
                runCatching { link.rootAdbd() }
            }
            res
                .onSuccess {
                    LogBus.ok("root：${it.trim().ifEmpty { "已重启为 root" }}")
                    onResult(it.trim().ifEmpty { "已重启为 root" })
                }
                .onFailure {
                    LogBus.warn("root 失败：${Toolbox.friendlyError(it)}")
                    onResult("失败：${Toolbox.friendlyError(it)}")
                }
        }
    }

    fun unroot(onResult: (String) -> Unit) {
        scope.launch {
            val res = withContext(Dispatchers.IO) {
                runCatching { link.unrootAdbd() }
            }
            res
                .onSuccess {
                    LogBus.ok("unroot：${it.trim().ifEmpty { "已恢复非 root" }}")
                    onResult(it.trim().ifEmpty { "已恢复非 root" })
                }
                .onFailure {
                    LogBus.warn("unroot 失败：${Toolbox.friendlyError(it)}")
                    onResult("失败：${Toolbox.friendlyError(it)}")
                }
        }
    }

    /** 复位与服务端的连接（异常半开时使用）。 */
    fun resetConnection(onResult: () -> Unit) {
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { link.kadb.resetConnection() }
            }
            LogBus.info("已复位连接状态（下次操作自动重连）")
            Toast.show("已复位连接状态")
            onResult()
        }
    }
}

@Composable
fun ForwardScreen() {
    val link = Toolbox.active
    if (link == null) {
        Column(
            Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionCard(title = "端口转发", icon = Icons.Filled.Link) {
                EmptyHint("连接设备后可将本机端口转发到设备端口（如 localhost:8000 → 设备 Cutehttpd 8080）", icon = Icons.Filled.Link)
            }
        }
        return
    }

    val vm = remember(link) { ForwardViewModel(link) }
    DisposableEffect(link) {
        onDispose { vm.closeAll() }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PortForwardCard(vm)
        AdbdCard(vm)
    }
}

@Composable
private fun PortForwardCard(vm: ForwardViewModel) {
    var hostPort by remember { mutableStateOf("") }
    var targetPort by remember { mutableStateOf("") }

    SectionCard(
        title = "端口转发",
        subtitle = "Kadb.tcpForward —— 本机监听 → 设备 localhost",
        icon = Icons.Filled.Link,
        trailing = {
            if (vm.entries.isNotEmpty()) {
                IconAction(Icons.Filled.RestartAlt, "全部停止", onClick = {
                    vm.entries.forEach { runCatching { it.handle.close() } }
                    vm.entries.clear()
                    LogBus.info("已停止全部转发")
                })
            }
        },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LabeledField("本机端口", hostPort, { hostPort = it }, Modifier.weight(1f), placeholder = "如 8000")
            LabeledField("设备端口", targetPort, { targetPort = it }, Modifier.weight(1f), placeholder = "如 8080")
            Button(onClick = {
                vm.add(hostPort, targetPort)
                hostPort = ""
                targetPort = ""
            }) {
                Text("添加")
            }
        }

        if (vm.entries.isEmpty()) {
            EmptyHint("暂无转发规则 —— 示例：本机 8000 → 设备 8080")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                vm.entries.toList().forEach { entry ->
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                entry.describe(),
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            IconAction(Icons.Filled.Close, "停止该转发", onClick = { vm.close(entry) })
                        }
                    }
                }
            }
            Text(
                "常见用途：chrome://inspect 调试（→ 9222）、无线 ADB（→ 5555）、局域网设备服务",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AdbdCard(vm: ForwardViewModel) {
    var message by remember { mutableStateOf("") }

    SectionCard(
        title = "adbd 管理",
        subtitle = "root / unroot —— 仅工程调试（userdebug 全板）可用",
        icon = Icons.Filled.Power,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { vm.root { message = it } }) {
                Icon(Icons.Filled.Bolt, contentDescription = null)
                Text("以 root 重启 adbd")
            }
            Button(onClick = { vm.unroot { message = it } }) {
                Text("恢复非 root")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { vm.resetConnection() }) {
                Icon(Icons.Filled.RestartAlt, contentDescription = null)
                Text("复位连接状态")
            }
        }
        if (message.isNotEmpty()) {
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
