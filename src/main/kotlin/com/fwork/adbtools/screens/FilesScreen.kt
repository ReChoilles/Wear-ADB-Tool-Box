package com.fwork.adbtools.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import com.fwork.adbtools.ui.icons.Add
import com.fwork.adbtools.ui.icons.ChevronLeft
import com.fwork.adbtools.ui.icons.ChevronRight
import com.fwork.adbtools.ui.icons.Computer
import com.fwork.adbtools.ui.icons.Delete
import com.fwork.adbtools.ui.icons.Description
import com.fwork.adbtools.ui.icons.Download
import com.fwork.adbtools.ui.icons.Folder
import com.fwork.adbtools.ui.icons.Refresh
import com.fwork.adbtools.ui.icons.Smartphone
import com.fwork.adbtools.ui.icons.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fwork.adbtools.core.DeviceLink
import com.fwork.adbtools.core.FilePickers
import com.fwork.adbtools.core.LogBus
import com.fwork.adbtools.core.Toast
import com.fwork.adbtools.core.Toolbox
import com.fwork.adbtools.core.adbQuote
import com.fwork.adbtools.ui.EmptyHint
import com.fwork.adbtools.ui.IconAction
import com.fwork.adbtools.ui.LabeledField
import com.fwork.adbtools.ui.PillToggle
import com.fwork.adbtools.ui.SectionCard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.Buffer
import okio.Sink
import okio.Source
import okio.Timeout
import okio.sink
import okio.source
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 目录位（stat mode 的 S_IFDIR 位）。 */
private const val DIR_BIT = 0x4000

internal fun humanSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> String.format(Locale.ROOT, "%.1f GB", bytes / 1073741824.0)
    bytes >= 1L shl 20 -> String.format(Locale.ROOT, "%.1f MB", bytes / 1048576.0)
    bytes >= 1L shl 10 -> String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0)
    else -> "$bytes B"
}

internal fun joinRemote(dir: String, name: String): String =
    if (dir.endsWith("/")) dir + name else "$dir/$name"

internal fun parentRemote(p: String): String? {
    val t = p.trim().trimEnd('/')
    if (t.isEmpty()) return null
    val i = t.lastIndexOf('/')
    return if (i <= 0) "/" else t.substring(0, i)
}

data class LocalEntry(
    val name: String,
    val path: String,
    val isDir: Boolean,
    val size: Long,
    val timeMs: Long,
)

data class RemoteEntry(
    val name: String,
    val isDir: Boolean,
    val size: Long,
    val mtimeSec: Long,
)

private data class RemoteFile(val absPath: String, val relPath: String, val size: Long)

/** 文件管理（本机 <-> 设备：push/pull/列目录/新建/删除，sync V1/V2 自适应）。 */
class FilesViewModel(private val link: DeviceLink) {
    var localPath by mutableStateOf(System.getProperty("user.home") ?: "C:\\")
    var remotePath by mutableStateOf("/sdcard")
    var localEntries by mutableStateOf<List<LocalEntry>>(emptyList())
    var remoteEntries by mutableStateOf<List<RemoteEntry>>(emptyList())

    val localSel = androidx.compose.runtime.mutableStateListOf<String>()
    val remoteSel = androidx.compose.runtime.mutableStateListOf<String>()

    var busy by mutableStateOf(false)
        private set
    var progressText by mutableStateOf("")
    var progressFrac by mutableStateOf<Float?>(null)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun refreshLocal() {
        val dir = File(localPath).takeIf { it.isDirectory } ?: run {
            Toast.show("本机目录不可用：${localPath}")
            return
        }
        scope.launch {
            val list = withContext(Dispatchers.IO) {
                runCatching {
                    dir.listFiles()?.map {
                        LocalEntry(
                            name = it.name,
                            path = it.absolutePath,
                            isDir = it.isDirectory,
                            size = if (it.isDirectory) 0 else it.length(),
                            timeMs = it.lastModified(),
                        )
                    }
                }.getOrNull()
            }
            localSel.clear()
            localEntries = (list ?: emptyList()).sortedWith(
                compareByDescending<LocalEntry> { it.isDir }.thenBy { it.name.lowercase() },
            )
        }
    }

    fun refreshRemote() {
        scope.launch {
            val res = withContext(Dispatchers.IO) {
                runCatching { link.kadb.openSync().use { it.list(remotePath.trim()) } }
            }
            res
                .onSuccess { raw ->
                    remoteSel.clear()
                    remoteEntries = raw
                        .filter { it.errorCode == null && it.name != "." && it.name != ".." }
                        .map { RemoteEntry(it.name, (it.mode and DIR_BIT) != 0, it.size, it.mtimeSec) }
                        .sortedWith(
                            compareByDescending<RemoteEntry> { it.isDir }.thenBy { it.name.lowercase() },
                        )
                }
                .onFailure {
                    remoteSel.clear()
                    remoteEntries = emptyList()
                    Toast.show("列目录失败：${Toolbox.friendlyError(it)}")
                    LogBus.warn("list $remotePath 失败：${it.message}")
                }
        }
    }

    fun enterLocal(e: LocalEntry) {
        localPath = e.path
        refreshLocal()
    }

    fun upLocal() {
        File(localPath).parentFile?.let {
            localPath = it.absolutePath
            refreshLocal()
        }
    }

    fun browseLocal() {
        scope.launch {
            val dir = FilePickers.pickDirectory("选择本机目录") ?: return@launch
            localPath = dir.absolutePath
            refreshLocal()
        }
    }

    fun enterRemote(e: RemoteEntry) {
        remotePath = joinRemote(remotePath, e.name)
        refreshRemote()
    }

    fun upRemote() {
        remotePath = parentRemote(remotePath) ?: "/"
        refreshRemote()
    }

    fun toggleLocal(name: String) {
        if (localSel.contains(name)) localSel.remove(name) else localSel.add(name)
    }

    fun toggleRemote(name: String) {
        if (remoteSel.contains(name)) remoteSel.remove(name) else remoteSel.add(name)
    }

    fun clearLocalSel() = localSel.clear()

    fun clearRemoteSel() = remoteSel.clear()

    /** 推送所选（递归目录 / 文件，带分块进度）。 */
    fun pushSelected() {
        if (busy || localSel.isEmpty()) return
        val picks = localEntries.filter { localSel.contains(it.name) }
        if (picks.isEmpty()) return
        busy = true
        progressText = "准备推送…"
        progressFrac = null
        scope.launch {
            val res = runCatching {
                withContext(Dispatchers.IO) {
                    link.kadb.openSync().use { sync ->
                        val dirs = mutableListOf<Pair<File, String>>() // (目录, 相对路径)
                        val files = mutableListOf<RemoteFile>()
                        var total = 0L

                        fun walk(dir: File, prefix: String) {
                            dirs.add(dir to prefix)
                            dir.listFiles()?.sortedBy { it.name }?.forEach { child ->
                                val rel = "$prefix/${child.name}"
                                if (child.isDirectory) walk(child, rel)
                                else {
                                    files.add(RemoteFile(child.absolutePath, rel, child.length()))
                                    total += child.length()
                                }
                            }
                        }

                        picks.forEach { e ->
                            val f = File(e.path)
                            if (f.isDirectory) walk(f, e.name)
                            else {
                                files.add(RemoteFile(f.absolutePath, e.name, f.length()))
                                total += f.length()
                            }
                        }

                        for ((_, rel) in dirs) {
                            progressText = "创建目录 $rel"
                            link.shell("mkdir -p " + adbQuote(joinRemote(remotePath, rel)))
                        }

                        var base = 0L
                        for ((index, item) in files.withIndex()) {
                            val target = joinRemote(remotePath, item.relPath)
                            progressText = "推送 (${index + 1}/${files.size}) ${item.relPath}"
                            val pushed = CountingSource(File(item.absPath), base, total) { overall ->
                                if (total > 0) progressFrac = (overall.toFloat() / total).coerceIn(0f, 1f)
                                progressText = "推送 ${item.relPath} ${humanSize(overall)} / ${humanSize(total)}"
                            }
                            sync.send(pushed, target, 420, File(item.absPath).lastModified())
                            base += item.size
                        }
                        files.size
                    }
                }
            }
            busy = false
            progressFrac = null
            res
                .onSuccess { n ->
                    progressText = "推送完成（$n 个文件）"
                    Toast.show("推送完成（$n 个文件）")
                    LogBus.ok("push → ${link.label} 完成")
                    refreshRemote()
                }
                .onFailure {
                    progressText = ""
                    Toast.show("推送失败：" + Toolbox.friendlyError(it))
                    LogBus.error("push 失败：${it.message}")
                }
        }
    }

    /** 拉取所选（递归目录 / 文件，带分块进度）。 */
    fun pullSelected() {
        if (busy || remoteSel.isEmpty()) return
        val picks = remoteEntries.filter { remoteSel.contains(it.name) }
        if (picks.isEmpty()) return
        busy = true
        progressText = "准备拉取…"
        progressFrac = null
        scope.launch {
            val res = runCatching {
                withContext(Dispatchers.IO) {
                    link.kadb.openSync().use { sync ->
                        val dirs = mutableListOf<String>()
                        val files = mutableListOf<RemoteFile>()
                        var total = 0L

                        fun walk(absDir: String, prefix: String) {
                            val list = sync.list(absDir)
                            for (e in list) {
                                if (e.errorCode != null || e.name == "." || e.name == "..") continue
                                val isDir = (e.mode and DIR_BIT) != 0
                                val childAbs = joinRemote(absDir, e.name)
                                val childRel = if (prefix.isEmpty()) e.name else "$prefix/${e.name}"
                                if (isDir) {
                                    dirs.add(childRel)
                                    walk(childAbs, childRel)
                                } else {
                                    files.add(RemoteFile(childAbs, childRel, e.size))
                                    total += e.size
                                }
                            }
                        }

                        picks.forEach { p ->
                            if (p.isDir) {
                                dirs.add(p.name)
                                walk(joinRemote(remotePath, p.name), p.name)
                            } else {
                                files.add(RemoteFile(joinRemote(remotePath, p.name), p.name, p.size))
                                total += p.size
                            }
                        }

                        val baseDir = File(localPath)
                        for (rel in dirs) {
                            progressText = "创建目录 $rel"
                            File(baseDir, rel).mkdirs()
                        }

                        var base = 0L
                        for ((index, item) in files.withIndex()) {
                            val dest = File(baseDir, item.relPath)
                            dest.parentFile?.mkdirs()
                            progressText = "拉取 (${index + 1}/${files.size}) ${item.relPath}"
                            val counting = CountingSink(dest.sink()) { chunk ->
                                base += chunk
                                if (total > 0) progressFrac = (base.toFloat() / total).coerceIn(0f, 1f)
                                progressText = "拉取 ${item.relPath} ${humanSize(minOf(base, total))} / ${humanSize(total)}"
                            }
                            sync.recv(counting, item.absPath)
                            counting.close()
                        }
                        files.size
                    }
                }
            }
            busy = false
            progressFrac = null
            res
                .onSuccess { n ->
                    progressText = "拉取完成（$n 个文件）"
                    Toast.show("拉取完成（$n 个文件）")
                    LogBus.ok("pull ← ${link.label} 完成")
                    refreshLocal()
                }
                .onFailure {
                    progressText = ""
                    Toast.show("拉取失败：" + Toolbox.friendlyError(it))
                    LogBus.error("pull 失败：${it.message}")
                }
        }
    }

    /** 设备端新建目录。 */
    fun mkdir(name: String) {
        val n = name.trim()
        if (n.isEmpty()) return
        if (n.contains("/") || n == "." || n == "..") {
            Toast.show("非法目录名")
            return
        }
        scope.launch {
            val res = withContext(Dispatchers.IO) {
                runCatching { link.shell("mkdir -p " + adbQuote(joinRemote(remotePath, n))) }
            }
            res
                .onSuccess { resp ->
                    if (resp.exitCode == 0) {
                        Toast.show("已创建 $n")
                        refreshRemote()
                    } else {
                        Toast.show("创建失败：" + resp.allOutput.take(120))
                    }
                }
                .onFailure { Toast.show("创建失败：" + Toolbox.friendlyError(it)) }
        }
    }

    /** 设备端删除所选（rm -rf，需确认）。 */
    fun deleteSelected() {
        val targets = remoteEntries.filter { remoteSel.contains(it.name) }.map { joinRemote(remotePath, it.name) }
        if (targets.isEmpty() || busy) return
        busy = true
        scope.launch {
            val cmd = "rm -rf " + targets.joinToString(" ") { adbQuote(it) }
            val res = withContext(Dispatchers.IO) { runCatching { link.shell(cmd) } }
            busy = false
            res
                .onSuccess { resp ->
                    if (resp.exitCode == 0) {
                        Toast.show("已删除 ${targets.size} 项")
                        remoteSel.clear()
                        refreshRemote()
                    } else {
                        Toast.show("删除失败：" + resp.allOutput.take(120))
                    }
                }
                .onFailure { Toast.show("删除失败：" + Toolbox.friendlyError(it)) }
        }
    }
}

/** 统计进度的 okio Source。 */
private class CountingSource(
    file: File,
    private val base: Long,
    private val total: Long,
    private val onProgress: (Long) -> Unit,
) : Source {
    private val inner = file.source()

    override fun read(sink: Buffer, byteCount: Long): Long {
        val n = inner.read(sink, byteCount)
        if (n > 0L) {
            onProgress(base + n)
        }
        return n
    }

    override fun timeout(): Timeout = inner.timeout()

    override fun close() = inner.close()
}

/** 统计进度的 okio Sink。 */
private class CountingSink(
    private val inner: Sink,
    private val onProgress: (Long) -> Unit,
) : Sink {
    override fun write(source: Buffer, byteCount: Long) {
        inner.write(source, byteCount)
        onProgress(byteCount)
    }

    override fun flush() = inner.flush()

    override fun timeout(): Timeout = inner.timeout()

    override fun close() = inner.close()
}

@Composable
fun FilesScreen() {
    val link = Toolbox.active
    if (link == null) {
        Column(
            Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionCard(title = "文件管理", icon = Icons.Filled.Computer) {
                EmptyHint("连接设备后可进行文件推送 / 拉取 / 目录管理", icon = Icons.Filled.Computer)
            }
        }
        return
    }

    val vm = remember(link) { FilesViewModel(link) }
    LaunchedEffect(link) {
        vm.refreshLocal()
        vm.refreshRemote()
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ProgressStrip(vm)
        LocalPane(vm)
        RemotePane(vm)
    }
}

private val TIME_FMT = SimpleDateFormat("MM-dd HH:mm", Locale.ROOT)

@Composable
private fun ProgressStrip(vm: FilesViewModel) {
    if (vm.progressText.isEmpty()) return
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val frac = vm.progressFrac
        if (frac != null) {
            LinearProgressIndicator(
                progress = { frac },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Text(
                vm.progressText,
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LocalPane(vm: FilesViewModel) {
    SectionCard(
        title = "本机（${vm.localPath}）",
        icon = Icons.Filled.Computer,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconAction(Icons.Filled.ChevronLeft, "上级目录", onClick = { vm.upLocal() })
                IconAction(Icons.Filled.Folder, "浏览目录", onClick = { vm.browseLocal() })
                IconAction(Icons.Filled.Refresh, "刷新", onClick = { vm.refreshLocal() })
            }
        },
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            File.listRoots().take(6).forEach { root ->
                PillToggle(
                    selected = vm.localPath.equals(root.absolutePath, ignoreCase = true),
                    text = root.absolutePath,
                    onClick = {
                        vm.localPath = root.absolutePath
                        vm.refreshLocal()
                    },
                )
            }
        }

        EntryList(
            entries = vm.localEntries,
            selected = vm.localSel,
            header = "点击选择；文件夹点箭头进入",
            emptyText = "目录为空 / 无法读取",
            nameOf = { it.name },
            isDirOf = { it.isDir },
            metaOf = { e ->
                (if (e.isDir) "目录" else humanSize(e.size)) + " · " + TIME_FMT.format(Date(e.timeMs))
            },
            iconOf = { if (it.isDir) Icons.Filled.Folder else Icons.Filled.Description },
            onOpen = { if (it.isDir) vm.enterLocal(it) else Unit },
            onSelect = { vm.toggleLocal(it.name) },
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { vm.clearLocalSel() },
                enabled = vm.localSel.isNotEmpty(),
            ) { Text("清除选择（${vm.localSel.size}）") }
            Button(
                onClick = { vm.pushSelected() },
                enabled = !vm.busy && vm.localSel.isNotEmpty(),
            ) {
                Icon(Icons.Filled.Upload, contentDescription = null)
                Text("推送到设备")
            }
        }
    }
}

@Composable
private fun RemotePane(vm: FilesViewModel) {
    var newFolder by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }

    SectionCard(
        title = "设备端（${vm.remotePath}）",
        icon = Icons.Filled.Smartphone,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconAction(Icons.Filled.ChevronLeft, "上级目录", onClick = { vm.upRemote() })
                IconAction(Icons.Filled.Refresh, "刷新", onClick = { vm.refreshRemote() })
            }
        },
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            listOf("/sdcard", "/storage/emulated/0", "/data/local/tmp").forEach { path ->
                PillToggle(
                    selected = vm.remotePath == path,
                    text = path,
                    onClick = {
                        vm.remotePath = path
                        vm.refreshRemote()
                    },
                )
            }
        }

        EntryList(
            entries = vm.remoteEntries,
            selected = vm.remoteSel,
            header = "点击选择；文件夹点箭头进入",
            emptyText = "目录为空 / 设备拒绝访问（可尝试 /sdcard）",
            nameOf = { it.name },
            isDirOf = { it.isDir },
            metaOf = { e ->
                (if (e.isDir) "目录" else humanSize(e.size)) + " · " + TIME_FMT.format(Date(e.mtimeSec * 1000))
            },
            iconOf = { if (it.isDir) Icons.Filled.Folder else Icons.Filled.Description },
            onOpen = { if (it.isDir) vm.enterRemote(it) else Unit },
            onSelect = { vm.toggleRemote(it.name) },
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LabeledField("新建文件夹名称", newFolder, { newFolder = it }, Modifier.weight(1.2f))
            IconButton(onClick = {
                vm.mkdir(newFolder)
                newFolder = ""
            }) {
                Icon(Icons.Filled.Add, contentDescription = "创建文件夹")
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { vm.clearRemoteSel() },
                enabled = vm.remoteSel.isNotEmpty(),
            ) { Text("清除选择（${vm.remoteSel.size}）") }
            Button(
                onClick = { vm.pullSelected() },
                enabled = !vm.busy && vm.remoteSel.isNotEmpty(),
            ) {
                Icon(Icons.Filled.Download, contentDescription = null)
                Text("拉取到本机")
            }
            Button(
                onClick = { confirmDelete = true },
                enabled = !vm.busy && vm.remoteSel.isNotEmpty(),
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null)
                Text("删除所选")
            }
        }

        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text("确认删除") },
                text = {
                    Text(
                        "将在设备端执行 rm -rf，共 ${vm.remoteSel.size} 个项目：\n" +
                            vm.remoteSel.joinToString("\n") { it } +
                            "\n\n该操作不可恢复，是否继续？",
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        confirmDelete = false
                        vm.deleteSelected()
                    }) { Text("删除") }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDelete = false }) { Text("取消") }
                },
            )
        }
    }
}

@Composable
private fun <T> EntryList(
    entries: List<T>,
    selected: List<String>,
    header: String,
    emptyText: String,
    nameOf: (T) -> String,
    isDirOf: (T) -> Boolean,
    metaOf: (T) -> String,
    iconOf: (T) -> ImageVector,
    onOpen: (T) -> Unit,
    onSelect: (T) -> Unit,
) {
    Text(
        header,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (entries.isEmpty()) {
        EmptyHint(emptyText)
        return
    }
    Box(Modifier.height(320.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            entries.forEach { e ->
                val name = nameOf(e)
                val isDir = isDirOf(e)
                val sel = selected.contains(name)
                Surface(
                    onClick = { onSelect(e) },
                    selected = sel,
                    shape = MaterialTheme.shapes.small,
                    color = if (sel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Row(
                        Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            iconOf(e),
                            contentDescription = null,
                            tint = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                name,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                metaOf(e),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (isDir) {
                            IconButton(onClick = { onOpen(e) }) {
                                Icon(
                                    Icons.Filled.ChevronRight,
                                    contentDescription = "进入 $name",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
