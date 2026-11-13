package com.fwork.adbtools.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fwork.adbtools.core.DeviceLink
import com.fwork.adbtools.core.FilePickers
import com.fwork.adbtools.core.LogBus
import com.fwork.adbtools.core.Toast
import com.fwork.adbtools.core.Toolbox
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
import java.io.File
import com.fwork.adbtools.ui.svgIcon

enum class PkgKind(val label: String, val flag: String) {
    USER("用户", "-3"),
    SYSTEM("系统", "-s"),
    ALL("全部", ""),
}

/** 应用管理：pm list packages / install / installMultiple / uninstall。 */
class AppsViewModel(private val link: DeviceLink) {
    var kind by mutableStateOf(PkgKind.USER)
    var filter by mutableStateOf("")
    var packages by mutableStateOf<List<String>>(emptyList())
    var loading by mutableStateOf(false)
        private set
    var pickedApks by mutableStateOf<List<File>>(emptyList())
        private set
    var installing by mutableStateOf(false)
        private set
    var installMessage by mutableStateOf("")
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun refresh() {
        loading = true
        scope.launch {
            val cmd = "pm list packages ${kind.flag}".trim()
            val res = withContext(Dispatchers.IO) {
                runCatching { link.shell(cmd) }
            }
            loading = false
            res
                .onSuccess { resp ->
                    packages = resp.allOutput.lineSequence()
                        .map(String::trim)
                        .filter { it.startsWith("package:") }
                        .map { it.removePrefix("package:").substringBefore(' ') }
                        .filter(String::isNotBlank)
                        .distinct()
                        .sorted()
                        .toList()
                    LogBus.info("包列表已刷新：${packages.size} 项（${kind.label}）")
                }
                .onFailure {
                    Toast.show("读取包列表失败：" + Toolbox.friendlyError(it))
                }
        }
    }

    fun uninstall(name: String) {
        loading = true
        scope.launch {
            val res = withContext(Dispatchers.IO) {
                runCatching { link.kadb.uninstall(name) }
            }
            loading = false
            res
                .onSuccess {
                    Toast.show("已卸载 $name")
                    LogBus.ok("uninstall $name 成功")
                    packages = packages.filter { it != name }
                    refresh()
                }
                .onFailure {
                    Toast.show("卸载失败 $name：" + (it.message?.take(140) ?: "未知错误"))
                    LogBus.warn("uninstall $name 失败")
                }
        }
    }

    fun pickApks() {
        scope.launch {
            val picked = FilePickers.pickApks()
            if (picked.isNotEmpty()) {
                pickedApks = picked
                installMessage = "已选择 ${picked.size} 个 APK（合计 ${humanSize(picked.sumOf { it.length() })}）"
            }
        }
    }

    fun install(options: List<String>) {
        if (pickedApks.isEmpty() || installing) return
        installing = true
        installMessage = "推送并安装中…（大文件可能较慢）"
        scope.launch {
            val res = withContext(Dispatchers.IO) {
                runCatching {
                    if (pickedApks.size == 1) {
                        link.kadb.install(pickedApks[0], *options.toTypedArray())
                    } else {
                        link.kadb.installMultiple(pickedApks, *options.toTypedArray())
                    }
                }
            }
            installing = false
            res
                .onSuccess {
                    installMessage = "安装成功"
                    Toast.show("安装成功")
                    LogBus.ok("APK 安装完成（${pickedApks.size} 个文件）")
                }
                .onFailure {
                    installMessage = "安装失败：" + (it.message?.take(200) ?: it.javaClass.simpleName)
                    Toast.show("安装失败")
                    LogBus.error("install 失败：${it.message}")
                }
        }
    }
}

@Composable
fun AppsScreen() {
    val link = Toolbox.active
    if (link == null) {
        Column(
            Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionCard(title = "应用管理", icon = svgIcon("apps")) {
                EmptyHint("连接设备后可安装 / 卸载 / 查看应用包", icon = svgIcon("apps"))
            }
        }
        return
    }

    val vm = remember(link) { AppsViewModel(link) }
    LaunchedEffect(link) { vm.refresh() }

    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        InstallCard(vm)
        PackageListCard(vm)
    }
}

@Composable
private fun InstallCard(vm: AppsViewModel) {
    var replace by remember { mutableStateOf(true) }   // -r
    var grant by remember { mutableStateOf(true) }     // -g
    var downgrade by remember { mutableStateOf(false) } // -d
    var test by remember { mutableStateOf(false) }     // -t

    SectionCard(
        title = "APK 安装",
        subtitle = "kadb.install / installMultiple —— 单包或拆分包",
        icon = svgIcon("install_mobile"),
        trailing = {
            if (vm.installing) Text("安装中…", style = MaterialTheme.typography.labelMedium)
        },
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = { vm.pickApks() }, enabled = !vm.installing) {
                Icon(svgIcon("cloud_upload"), contentDescription = null)
                Text("选择 APK…")
            }
            Button(
                onClick = {
                    vm.install(
                        buildList {
                            if (replace) add("-r")
                            if (grant) add("-g")
                            if (downgrade) add("-d")
                            if (test) add("-t")
                        },
                    )
                },
                enabled = !vm.installing && vm.pickedApks.isNotEmpty(),
            ) {
                Text(if (vm.pickedApks.size > 1) "安装（${vm.pickedApks.size} 个）" else "安装")
            }
        }

        if (vm.pickedApks.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                vm.pickedApks.take(8).forEach { apk ->
                    Text(
                        "${apk.name} · ${humanSize(apk.length())}",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            PillToggle(replace, "-r  替换", { replace = !replace })
            PillToggle(grant, "-g  授权", { grant = !grant })
            PillToggle(downgrade, "-d  降级", { downgrade = !downgrade })
            PillToggle(test, "-t  测试", { test = !test })
        }

        if (vm.installMessage.isNotEmpty()) {
            Text(
                vm.installMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PackageListCard(vm: AppsViewModel) {
    var confirmTarget by remember { mutableStateOf<String?>(null) }

    SectionCard(
        title = "应用包列表",
        subtitle = "`pm list packages`—— 卸载走 `cmd package uninstall`",
        icon = svgIcon("apps"),
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (vm.loading) Text("加载中…", style = MaterialTheme.typography.labelMedium)
                IconAction(svgIcon("refresh"), "刷新列表", onClick = { vm.refresh() })
            }
        },
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PkgKind.entries.forEach { k ->
                PillToggle(
                    selected = vm.kind == k,
                    text = k.label,
                    onClick = {
                        vm.kind = k
                        vm.refresh()
                    },
                )
            }
            LabeledField("搜索", vm.filter, { vm.filter = it }, Modifier.weight(1f), placeholder = "包名关键词")
        }

        val shown = remember(vm.packages, vm.filter) {
            if (vm.filter.isBlank()) vm.packages
            else vm.packages.filter { it.contains(vm.filter, ignoreCase = true) }
        }

        if (shown.isEmpty()) {
            EmptyHint("没有匹配的应用包", icon = svgIcon("search"))
        } else {
            Box(Modifier.height(360.dp)) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    shown.forEach { name ->
                        Surface(
                            onClick = { confirmTarget = name },
                            shape = MaterialTheme.shapes.extraSmall,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    name,
                                    Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                IconButton(onClick = { confirmTarget = name }) {
                                    Icon(
                                        svgIcon("delete"),
                                        contentDescription = "卸载",
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Text(
                "点击行或右侧图标进行卸载确认 · 共 ${shown.size} 项",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        confirmTarget?.let { target ->
            AlertDialog(
                onDismissRequest = { confirmTarget = null },
                title = { Text("卸载应用") },
                text = { Text("确定要卸载 \"$target\" 吗？") },
                confirmButton = {
                    TextButton(onClick = {
                        confirmTarget = null
                        vm.uninstall(target)
                    }) { Text("卸载") }
                },
                dismissButton = {
                    TextButton(onClick = { confirmTarget = null }) { Text("取消") }
                },
            )
        }
    }
}
