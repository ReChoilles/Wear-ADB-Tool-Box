package com.fwork.adbtools.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.flyfishxu.kadb.DelayedAckMode
import com.flyfishxu.kadb.cert.KadbCert
import com.flyfishxu.kadb.cert.KadbIdentitySnapshot
import com.fwork.adbtools.core.FilePickers
import com.fwork.adbtools.core.LogBus
import com.fwork.adbtools.core.Prefs
import com.fwork.adbtools.core.Toast
import com.fwork.adbtools.theme.Md3eSwatchColors
import com.fwork.adbtools.theme.Md3eSwatchNames
import com.fwork.adbtools.theme.parseHexColor
import com.fwork.adbtools.ui.FieldRow
import com.fwork.adbtools.ui.LabeledField
import com.fwork.adbtools.ui.PillToggle
import com.fwork.adbtools.ui.SectionCard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val CERT_DATE_FMT = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)

@Composable
fun SettingsScreen() {
    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ThemeCard()
        IdentityCard()
        ConnectionCard()
        AboutCard()
    }
}

@Composable
private fun ThemeCard() {
    val selectedSwatch = if (Prefs.customHex == null) Prefs.swatchIndex.coerceIn(Md3eSwatchColors.indices) else -1

    SectionCard(
        title = "外观",
        subtitle = "深色模式 / 预设主色 / 自定义主色（MD3E 动态生成整套配色）",
        icon = Icons.Filled.Palette,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("深色模式", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Switch(
                checked = Prefs.darkMode,
                onCheckedChange = { on ->
                    Prefs.darkMode = on
                    Prefs.persist()
                },
            )
        }

        Text(
            "预设主色",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Md3eSwatchColors.forEachIndexed { index, color ->
                val selected = selectedSwatch == index
                Surface(
                    onClick = {
                        Prefs.swatchIndex = index
                        Prefs.customHex = null
                        Prefs.persist()
                    },
                    selected = selected,
                    shape = RoundedCornerShape(999.dp),
                    color = color,
                    border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface) else null,
                ) {
                    Text(
                        Md3eSwatchNames.getOrElse(index) { "色板" },
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                    )
                }
            }
        }

        val custom = Prefs.customHex
        if (custom != null) {
            Text(
                "当前自定义主色：$custom",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        var hexInput by remember { mutableStateOf("") }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LabeledField("自定义主色（#RRGGBB）", hexInput, { hexInput = it }, Modifier.weight(1f))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = parseHexColor(hexInput) ?: MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.size(40.dp),
            ) {}
            Button(onClick = {
                val text = hexInput.trim()
                val parsed = parseHexColor(text)
                if (text.isEmpty() || parsed == null) {
                    Toast.show("无效颜色，格式如 #7C4DFF")
                } else {
                    Prefs.customHex = text
                    Prefs.swatchIndex = Md3eSwatchColors.size // 标记“非预设”；customHex 优先生效
                    Prefs.persist()
                    Toast.show("自定义主色已应用")
                    LogBus.info("主题色更新：$text")
                }
            }) {
                Text("应用")
            }
            if (custom != null) {
                OutlinedButton(onClick = {
                    Prefs.customHex = null
                    Prefs.swatchIndex = Prefs.swatchIndex.coerceIn(Md3eSwatchColors.indices)
                    Prefs.persist()
                    Toast.show("已恢复预设主色")
                }) {
                    Text("恢复预设")
                }
            }
        }
    }
}

@Composable
private fun IdentityCard() {
    var snapshot by remember { mutableStateOf<KadbIdentitySnapshot?>(null) }
    var busy by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // 读取指纹（首次会生成 RSA 密钥，较耗时，放 IO）
    LaunchedEffect(Unit) {
        busy = true
        snapshot = runCatching {
            withContext(Dispatchers.IO) { KadbCert.ensureReady() }
        }.getOrNull()
        busy = false
    }

    SectionCard(
        title = "主机身份（ADB 密钥）",
        subtitle = "kadb 自管密钥 —— 持久化于 %APPDATA%\\WearADBToolBox\\kadb\\adbkey",
        icon = Icons.Filled.Key,
        trailing = {
            if (busy) Text("处理中…", style = MaterialTheme.typography.labelMedium)
        },
    ) {
        val snap = snapshot
        if (snap == null) {
            Text(
                if (busy) "正在生成 / 读取密钥…" else "密钥未就绪（配置失败或已清除）",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            FieldRow("SHA-256 指纹", snap.fingerprintSha256)
            FieldRow("有效期至", CERT_DATE_FMT.format(Date(snap.notAfterEpochMillis)))
        }
        Text(
            "设备端授权弹窗显示的指纹即为此密钥的指纹；轮换后需重新授权本机。",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = {
                    busy = true
                    scope.launch(Dispatchers.IO) {
                        val res = runCatching { KadbCert.rotate() }
                        busy = false
                        res.fold(
                            onSuccess = {
                                snapshot = it
                                Toast.show("已轮换密钥（设备需重新授权）")
                                LogBus.ok("KadbCert.rotate 成功")
                            },
                            onFailure = {
                                Toast.show("轮换失败：" + (it.message ?: "未知错误"))
                                LogBus.warn("rotate 失败：${it.message}")
                            },
                        )
                    }
                },
                enabled = !busy,
            ) {
                Icon(Icons.Filled.RestartAlt, contentDescription = null)
                Text("轮换密钥")
            }
            OutlinedButton(
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        val file = FilePickers.pickAnyFile("导入 PEM 私钥") ?: return@launch
                        busy = true
                        val res = runCatching { KadbCert.importPrivateKey(file.readBytes()) }
                        busy = false
                        res.fold(
                            onSuccess = {
                                snapshot = it
                                Toast.show("已导入私钥：${file.name}")
                                LogBus.ok("importPrivateKey 成功")
                            },
                            onFailure = {
                                Toast.show("导入失败：" + (it.message ?: "未知错误"))
                                LogBus.warn("importPrivateKey 失败：${it.message}")
                            },
                        )
                    }
                },
            ) {
                Icon(Icons.Filled.OpenInNew, contentDescription = null)
                Text("导入私钥")
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        val bytes = runCatching { KadbCert.exportPrivateKeyOrNull() }.getOrNull()
                        if (bytes == null) {
                            Toast.show("当前无私钥可导出")
                        } else {
                            val target = FilePickers.pickSaveFile("导出私钥", "adbkey.pem")
                            if (target == null) {
                                Toast.show("已取消导出")
                            } else {
                                val save = runCatching { target.writeBytes(bytes) }
                                if (save.isSuccess) {
                                    Toast.show("已导出到 ${target.absolutePath}")
                                    LogBus.ok("导出私钥 → ${target.absolutePath}")
                                } else {
                                    Toast.show("写入文件失败")
                                }
                            }
                        }
                    }
                },
            ) {
                Icon(Icons.Filled.Save, contentDescription = null)
                Text("导出私钥")
            }
            Button(
                onClick = { confirmClear = true },
                enabled = !busy,
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null)
                Text("清除身份")
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清除主机身份") },
            text = {
                Text(
                    "将清除当前 ADB 私钥/证书。已授权过的设备会“忘记本机”，重新连接时需在设备端再次允许 USB 调试。\n\n（重启应用后会自动生成新身份）",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    busy = true
                    scope.launch(Dispatchers.IO) {
                        val res = runCatching { KadbCert.clear() }
                        busy = false
                        res.fold(
                            onSuccess = {
                                snapshot = null
                                Toast.show("已清除身份")
                                LogBus.warn("主机身份已清除")
                            },
                            onFailure = {
                                Toast.show("清除失败：" + (it.message ?: "未知错误"))
                            },
                        )
                    }
                }) { Text("清除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun ConnectionCard() {
    SectionCard(
        title = "连接参数",
        subtitle = "超时与 delayed-ack —— 仅对之后新建的连接生效",
        icon = Icons.Filled.Link,
    ) {
        var connectText by remember { mutableStateOf(Prefs.connectTimeoutSec.toString()) }
        var socketText by remember { mutableStateOf(Prefs.socketTimeoutSec.toString()) }

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LabeledField("连接超时（秒，0=默认）", connectText, { connectText = it }, Modifier.weight(1f))
            LabeledField("SoTimeout（秒，0=默认）", socketText, { socketText = it }, Modifier.weight(1f))
        }

        Text(
            "delayed-ack 模式（KadbOptions.DelayedAckMode）",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DelayedAckMode.entries.forEach { mode ->
                PillToggle(
                    selected = Prefs.delayedAckMode == mode,
                    text = when (mode) {
                        DelayedAckMode.AOSP_DEFAULT -> "AOSP 默认"
                        DelayedAckMode.ENABLED -> "启用"
                        DelayedAckMode.DISABLED -> "禁用"
                        else -> mode.name
                    },
                    onClick = {
                        Prefs.delayedAckMode = mode
                        Prefs.persist()
                        Toast.show("delayed-ack = ${mode.name}（新连接生效）")
                    },
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = {
                val c = connectText.trim().toIntOrNull()?.coerceIn(0, 3600)
                val s = socketText.trim().toIntOrNull()?.coerceIn(0, 3600)
                if (c == null || s == null) {
                    Toast.show("请输入 0–3600 的数字（0 表示默认）")
                } else {
                    Prefs.connectTimeoutSec = c
                    Prefs.socketTimeoutSec = s
                    Prefs.persist()
                    Toast.show("已应用：连接 ${c}s / SoTimeout ${s}s")
                    LogBus.info("连接参数更新：connect=${c}s socket=${s}s delayedAck=${Prefs.delayedAckMode.name}")
                }
            }) {
                Text("应用")
            }
        }
    }
}

@Composable
private fun AboutCard() {
    SectionCard(title = "关于", icon = Icons.Filled.Info) {
        FieldRow("应用", "Wear ADB Tool Box（com.fwork.adbtools）")
        FieldRow("界面", "Kotlin · Compose Multiplatform Desktop · Material 3 Expressive")
        FieldRow("ADB 核心", "flyfishxu Kadb 2.1.5（纯 Kotlin 实现，无需 adb 二进制）")
        FieldRow("设备发现", "kadb-mdns 2.1.4 · USB 直连（net.codecrete.usb）")
        FieldRow("TLS", "Conscrypt 2.7.0（SPAKE2 + TLS 安全配对）")
    }
}
