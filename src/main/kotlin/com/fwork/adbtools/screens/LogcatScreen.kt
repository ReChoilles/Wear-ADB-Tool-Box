package com.fwork.adbtools.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import com.fwork.adbtools.ui.icons.Delete
import com.fwork.adbtools.ui.icons.Feed
import com.fwork.adbtools.ui.icons.PlayArrow
import com.fwork.adbtools.ui.icons.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flyfishxu.kadb.shell.AdbShellPacket
import com.flyfishxu.kadb.shell.AdbShellStream
import com.flyfishxu.kadb.stream.AdbStream
import com.fwork.adbtools.core.DeviceLink
import com.fwork.adbtools.core.LogBus
import com.fwork.adbtools.core.Toolbox
import com.fwork.adbtools.ui.EmptyHint
import com.fwork.adbtools.ui.IconAction
import com.fwork.adbtools.ui.LabeledField
import com.fwork.adbtools.ui.SectionCard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** logcat 会话：shell_v2 用 openShell（协议分包），旧设备回退 legacy（shell:命令 原始流）。 */
class LogcatViewModel(private val link: DeviceLink) {
    var view by mutableStateOf("")
    var running by mutableStateOf(false)
        private set

    private val term = TerminalBuffer()
    private val decoder = Utf8ChunkDecoder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var ticker: Job? = null
    private var v2Stream: AdbShellStream? = null
    private var legacyStream: AdbStream? = null

    fun start() {
        if (running) return
        running = true
        term.clear()
        view = ""
        scope.launch {
            appendLocal("[启动 logcat -v time…]\n")
            val shellV2 = runCatching {
                withContext(Dispatchers.IO) { link.kadb.supportsFeature("shell_v2") }
            }.getOrDefault(false)
            if (shellV2) runV2() else runLegacy()
        }
    }

    /** 停止抓取（保留缓冲，可再次开始）。 */
    fun stop() {
        running = false
        runCatching { v2Stream?.close() }
        runCatching { legacyStream?.close() }
        v2Stream = null
        legacyStream = null
    }

    /** 页面销毁：停止 + 取消后台协程。 */
    fun shutdown() {
        stop()
        scope.cancel()
    }

    fun clear() {
        term.clear()
        view = ""
    }

    private fun appendLocal(text: String) {
        term.append(text)
        view = term.snapshot()
    }

    private fun beginViewTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                delay(150)
                view = term.snapshot()
            }
        }
    }

    private suspend fun runV2() {
        val stream = runCatching {
            withContext(Dispatchers.IO) { link.kadb.openShell("logcat -v time") }
        }.getOrNull()
        if (stream == null) {
            appendLocal("[打开 logcat 失败 —— 请检查设备连接]\n")
            LogBus.warn("logcat（shell_v2）打开失败")
            running = false
            return
        }
        v2Stream = stream
        beginViewTicker()
        try {
            while (true) {
                val packet = withContext(Dispatchers.IO) { stream.read() }
                when (packet) {
                    is AdbShellPacket.StdOut -> term.append(String(packet.payload, Charsets.UTF_8))
                    is AdbShellPacket.StdError -> term.append(String(packet.payload, Charsets.UTF_8))
                    is AdbShellPacket.Exit -> {
                        appendLocal("\n[logcat 已退出]\n")
                        return
                    }
                }
            }
        } catch (t: Throwable) {
            if (!link.closed && running) appendLocal("\n[流断开：" + (t.message ?: t.javaClass.simpleName) + "]\n")
        } finally {
            running = false
            runCatching { stream.close() }
            v2Stream = null
            view = term.snapshot()
        }
    }

    private suspend fun runLegacy() {
        val stream = runCatching {
            withContext(Dispatchers.IO) { link.kadb.open("shell:logcat -v time") }
        }.getOrNull()
        if (stream == null) {
            appendLocal("[打开 logcat 失败 —— 请检查设备连接]\n")
            LogBus.warn("logcat（legacy）打开失败")
            running = false
            return
        }
        legacyStream = stream
        beginViewTicker()
        try {
            while (true) {
                val bytes = withContext(Dispatchers.IO) { stream.source.readByteArray(1) }
                term.append(decoder.decode(bytes))
            }
        } catch (t: Throwable) {
            if (!link.closed && running) appendLocal("\n[流断开：" + (t.message ?: t.javaClass.simpleName) + "]\n")
        } finally {
            running = false
            runCatching { stream.close() }
            legacyStream = null
            view = term.snapshot()
        }
    }
}

@Composable
fun LogcatScreen() {
    val link = Toolbox.active
    if (link == null) {
        Column(
            Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionCard(title = "日志", icon = Icons.Filled.Feed) {
                EmptyHint("连接设备后可实时查看 logcat 日志（本机文本过滤）", icon = Icons.Filled.Feed)
            }
        }
        return
    }

    val vm = remember(link) { LogcatViewModel(link) }

    DisposableEffect(link) {
        vm.start()
        vm.clear() // 每次进入清屏重抓
        onDispose { vm.shutdown() }
    }

    Column(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionCard(
            title = "设备日志（logcat -v time）",
            subtitle = "进入页面自动开始；shell_v2 优先，旧设备回退 legacy",
            icon = Icons.Filled.Feed,
            trailing = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (vm.running) {
                        Button(onClick = { vm.stop() }) {
                            androidx.compose.material3.Icon(Icons.Filled.Stop, contentDescription = null)
                            Text("停止")
                        }
                    } else {
                        Button(onClick = { vm.start() }) {
                            androidx.compose.material3.Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Text("开始")
                        }
                    }
                    IconAction(Icons.Filled.Delete, "清屏", onClick = { vm.clear() })
                }
            },
        ) {
            var filter by remember { mutableStateOf("") }
            LabeledField("本机过滤（包含匹配，空=全部）", filter, { filter = it }, Modifier.fillMaxWidth())

            BoxWithConstraints(Modifier.fillMaxWidth().height(460.dp)) {
                val scroll = rememberScrollState()
                val filteredView = remember(vm.view, filter) {
                    if (filter.isBlank()) vm.view
                    else vm.view.lineSequence().filter { it.contains(filter, ignoreCase = true) }.joinToString("\n")
                }
                LaunchedEffect(filteredView.length) {
                    scroll.animateScrollTo(scroll.maxValue)
                }
                SelectionContainer(
                    Modifier.fillMaxSize().verticalScroll(scroll),
                ) {
                    Text(
                        filteredView.ifEmpty { "[暂无日志 —— 点击开始]" },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        softWrap = true,
                        maxLines = Int.MAX_VALUE,
                        overflow = TextOverflow.Visible,
                    )
                }
            }
        }
    }
}
