package com.fwork.adbtools.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.AssistChip
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flyfishxu.kadb.shell.AdbPtyShellSession
import com.flyfishxu.kadb.shell.AdbShellPacket
import com.flyfishxu.kadb.shell.AdbShellResponse
import com.flyfishxu.kadb.stream.AdbStream
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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CharsetDecoder
import java.nio.charset.CodingErrorAction
import kotlin.math.max
import com.fwork.adbtools.ui.svgIcon

@Composable
fun ShellScreen() {
    val link = Toolbox.active
    if (link == null) {
        Column(
            Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionCard(title = "终端", icon = svgIcon("terminal")) {
                EmptyHint("连接设备后即可使用 PTY 交互终端与一次性执行", icon = svgIcon("terminal"))
            }
        }
        return
    }

    val session = remember(link) { TerminalSession(link) }
    val exec = remember(link) { OneShotExec(link) }

    LaunchedEffect(link) { session.startIfNeeded() }
    DisposableEffect(link) {
        onDispose { session.stop() }
    }

    Column(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionCard(
            title = "PTY 交互终端",
            subtitle = "shell_v2 优先；旧设备回退 legacy 交互（shell:）",
            icon = svgIcon("terminal"),
            trailing = {
                IconAction(svgIcon("restart_alt"), "重连会话", onClick = { session.restart() })
            },
        ) {
            Column {
                BoxWithConstraints(Modifier.fillMaxWidth().height(340.dp)) {
                    val density = LocalDensity.current
                    val fontPx = with(density) { 12.sp.toPx() }
                    val maxPxW = with(density) { maxWidth.toPx() }
                    val maxPxH = with(density) { maxHeight.toPx() }
                    val cols = max(28, (maxPxW / fontPx / 0.6f).toInt())
                    val rows = max(8, (maxPxH / (fontPx * 1.28f)).toInt())
                    LaunchedEffect(cols, rows) { session.resize(rows, cols) }

                    val scroll = rememberScrollState()
                    LaunchedEffect(session.view.length, session.autoScroll) {
                        if (session.autoScroll) scroll.animateScrollTo(scroll.maxValue)
                    }
                    SelectionContainer(
                        Modifier.fillMaxSize().verticalScroll(scroll),
                    ) {
                        Text(
                            session.view.ifEmpty { "[等待会话输出…]" },
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            softWrap = true,
                            maxLines = Int.MAX_VALUE,
                            overflow = TextOverflow.Visible,
                        )
                    }
                }

                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    var input by remember { mutableStateOf("") }
                    LabeledField(
                        label = "命令",
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = {
                            session.write(input + "\r")
                            input = ""
                        },
                    ) {
                        Text("发送")
                    }
                }
            }
        }

        OneShotCard(exec)
    }
}

@Composable
private fun OneShotCard(exec: OneShotExec) {
    SectionCard(
        title = "一次性执行",
        subtitle = "kadb.shell(command)：stdout / stderr / exit code",
        icon = svgIcon("send"),
    ) {
        var cmd by remember { mutableStateOf("uname -a") }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LabeledField("命令", cmd, { cmd = it }, Modifier.weight(1f))
            Button(
                onClick = { exec.run(cmd) },
                enabled = !exec.busy,
            ) {
                Text(if (exec.busy) "执行中…" else "执行")
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("getprop", "top -n 1", "df -h", "ps -A").forEach { preset ->
                AssistChip(onClick = { exec.run(preset) }, label = { Text(preset) })
            }
        }

        if (exec.history.isNotEmpty()) {
            exec.history.take(6).forEach { (command, resp) ->
                OutputBlock(command, resp)
            }
        }
    }
}

@Composable
private fun OutputBlock(command: String, resp: AdbShellResponse) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            "$ $command   (exit=${resp.exitCode})",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        SelectionContainer(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                resp.allOutput.ifBlank { "(无输出)" },
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
            )
        }
    }
}

/** 终端缓冲：剔除 ANSI 转义序列、忽略 \r（行式渲染，稳定可读）。 */
/** ASCII 控制字符码点（避免不可见字符字面量）。 */
private const val ESC = 27
private const val BEL = 7

class TerminalBuffer(private val maxChars: Int = 200_000) {
    private val sb = StringBuilder()

    @Synchronized
    fun append(text: String) {
        var i = 0
        val n = text.length
        while (i < n) {
            val c = text[i]
            when {
                c.code == ESC -> i = skipAnsi(text, i) - 1
                c == '\r' -> Unit // 行式渲染：忽略回车
                c == '\n' -> sb.append('\n')
                c == '\t' -> sb.append("    ")
                else -> sb.append(c)
            }
            i++
        }
        compact()
    }

    @Synchronized
    fun clear() {
        sb.setLength(0)
    }

    @Synchronized
    fun snapshot(): String = sb.toString()

    private fun compact() {
        if (sb.length <= maxChars) return
        val drop = sb.length - maxChars
        var cut = sb.indexOf('\n', drop)
        if (cut < 0) cut = drop
        sb.delete(0, cut.coerceAtMost(sb.length))
    }

    private fun skipAnsi(text: String, start: Int): Int {
        var i = start + 1
        if (i >= text.length) return i
        val mode = text[i]
        if (mode == '[') {
            i++
            while (i < text.length) {
                val ch = text[i]
                if (ch in '0'..'9' || ch == ';' || ch == '?' || ch == ' ') i++
                else {
                    i++ // 吃掉终止字母
                    break
                }
            }
            return i
        }
        if (mode == ']') { // OSC 序列，直到 BEL 结束
            i = text.indexOf(BEL.toChar(), i)
            return if (i < 0) text.length else i + 1
        }
        return start + 2
    }
}

/** 增量 UTF-8 解码：跨分块保留不完整的字节序列到下一块，避免伪字符。 */
internal class Utf8ChunkDecoder {
    private val decoder: CharsetDecoder = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPLACE)
        .onUnmappableCharacter(CodingErrorAction.REPLACE)
    private var pending = ByteArray(0)

    /** 追加一块字节，返回可显示文本（不完整序列保留到下一块）。 */
    fun decode(bytes: ByteArray): String {
        val data = if (pending.isEmpty()) bytes else pending + bytes
        val inBuf = ByteBuffer.wrap(data)
        val out = StringBuilder(data.size)
        while (inBuf.hasRemaining()) {
            val charBuf = CharBuffer.allocate(max(32, inBuf.remaining()))
            decoder.decode(inBuf, charBuf, false)
            val text = charBuf.flip().toString()
            if (text.isEmpty()) break // UNDERFLOW：结尾是不完整序列
            out.append(text)
        }
        val rest = ByteArray(inBuf.remaining())
        if (rest.isNotEmpty()) {
            inBuf.get(rest)
        }
        pending = rest
        return out.toString()
    }
}

/** 终端会话：PTY（shell_v2）/ legacy（shell:）双模式。 */
class TerminalSession(private val link: DeviceLink) {
    var view by mutableStateOf("")
    var autoScroll by mutableStateOf(true)

    private val term = TerminalBuffer()
    private val decoder = Utf8ChunkDecoder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var started = false
    private var stopped = false
    private var ptySession: AdbPtyShellSession? = null
    private var legacyStream: AdbStream? = null

    fun startIfNeeded() {
        if (started || stopped) return
        started = true
        scope.launch { runLoop() }
    }

    fun stop() {
        stopped = true
        runCatching { ptySession?.close() }
        runCatching { legacyStream?.close() }
        ptySession = null
        legacyStream = null
        scope.cancel()
    }

    fun restart() {
        stop()
        term.clear()
        view = ""
        started = false
        stopped = false
        startIfNeeded()
        Toast.show("已重启终端会话")
    }

    fun write(input: String) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                when {
                    ptySession != null -> ptySession?.write(input)
                    legacyStream != null -> {
                        legacyStream?.sink?.writeUtf8(input)
                        legacyStream?.sink?.flush()
                    }
                }
            }.onFailure {
                Toast.show("写入失败：" + (it.message ?: "未知错误"))
            }
        }
    }

    fun resize(rows: Int, cols: Int) {
        val pty = ptySession ?: return
        scope.launch(Dispatchers.IO) {
            runCatching { pty.resize(rows, cols) }
        }
    }

    private suspend fun runLoop() {
        appendLocal("[正在打开 shell 会话…]\n")
        val v2 = runCatching {
            withContext(Dispatchers.IO) { link.kadb.supportsFeature("shell_v2") }
        }.getOrDefault(false)
        if (v2) runPtyLoop() else runLegacyLoop()
    }

    private suspend fun runPtyLoop() {
        val session = runCatching {
            withContext(Dispatchers.IO) { link.kadb.openPtyShellSession("", "xterm-256color") }
        }.getOrNull()
        if (session == null) {
            appendFail()
            return
        }
        ptySession = session
        beginViewTicker()

        try {
            while (true) {
                val packet = withContext(Dispatchers.IO) { session.read() }
                when (packet) {
                    is AdbShellPacket.StdOut -> term.append(String(packet.payload, Charsets.UTF_8))
                    is AdbShellPacket.StdError -> term.append(String(packet.payload, Charsets.UTF_8))
                    is AdbShellPacket.Exit -> {
                        val exit = packet.payload.getOrNull(0)?.toInt()?.and(0xff)
                        appendLocal("\n[进程已退出，exit=${exit ?: "-"}]\n")
                        return
                    }
                }
            }
        } catch (t: Throwable) {
            if (!link.closed && !stopped) {
                appendLocal("\n[会话断开：" + (t.message ?: t.javaClass.simpleName) + "]\n")
            }
        } finally {
            view = term.snapshot()
            runCatching { session.close() }
        }
    }

    private suspend fun runLegacyLoop() {
        val stream = runCatching {
            withContext(Dispatchers.IO) { link.kadb.open("shell:") }
        }.getOrNull()
        if (stream == null) {
            appendFail()
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
            if (!link.closed && !stopped) {
                appendLocal("\n[会话断开：" + (t.message ?: t.javaClass.simpleName) + "]\n")
            }
        } finally {
            view = term.snapshot()
            runCatching { stream.close() }
        }
    }

    private fun beginViewTicker() {
        scope.launch {
            while (isActive) {
                delay(120)
                view = term.snapshot()
            }
        }
    }

    private fun appendFail() {
        appendLocal("[打开会话失败 —— 请检查设备连接]\n")
        LogBus.warn("shell 会话打开失败")
    }

    private fun appendLocal(text: String) {
        term.append(text)
        view = term.snapshot()
    }
}

/** 一次性命令执行器。 */
class OneShotExec(private val link: DeviceLink) {
    var busy by mutableStateOf(false)
        private set
    val history = androidx.compose.runtime.mutableStateListOf<Pair<String, AdbShellResponse>>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun run(command: String) {
        if (command.isBlank() || busy) return
        busy = true
        scope.launch {
            val resp = withContext(Dispatchers.IO) {
                runCatching { link.shell(command) }
            }
            busy = false
            resp
                .onSuccess {
                    history.add(0, command to it)
                    if (history.size > 24) history.removeRange(history.size - 1, history.size)
                    if (it.exitCode != 0) LogBus.warn("命令非零退出：$command（exit=${it.exitCode}）")
                }
                .onFailure {
                    LogBus.error("执行失败：${Toolbox.friendlyError(it)}")
                    Toast.show("执行失败：" + Toolbox.friendlyError(it))
                }
        }
    }
}
