package com.fwork.adbtools.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import com.fwork.adbtools.ui.icons.Apps
import com.fwork.adbtools.ui.icons.CheckCircle
import com.fwork.adbtools.ui.icons.Close
import com.fwork.adbtools.ui.icons.Computer
import com.fwork.adbtools.ui.icons.Description
import com.fwork.adbtools.ui.icons.Folder
import com.fwork.adbtools.ui.icons.Info
import com.fwork.adbtools.ui.icons.Link
import com.fwork.adbtools.ui.icons.Settings
import com.fwork.adbtools.ui.icons.Terminal
import com.fwork.adbtools.ui.icons.Usb
import com.fwork.adbtools.ui.icons.Wifi
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.fwork.adbtools.core.LinkType
import com.fwork.adbtools.core.LinkState
import com.fwork.adbtools.core.Toast
import com.fwork.adbtools.core.Toolbox
import com.fwork.adbtools.screens.AppsScreen
import com.fwork.adbtools.screens.DevicesScreen
import com.fwork.adbtools.screens.FilesScreen
import com.fwork.adbtools.screens.ForwardScreen
import com.fwork.adbtools.screens.LogcatScreen
import com.fwork.adbtools.screens.SettingsScreen
import com.fwork.adbtools.screens.ShellScreen

object Root {
    var tab by mutableIntStateOf(0)
}

private data class TabSpec(
    val icon: ImageVector,
    val label: String,
    val screen: @Composable () -> Unit,
)

private val Tabs = listOf(
    TabSpec(Icons.Filled.Computer, "设备") { DevicesScreen() },
    TabSpec(Icons.Filled.Terminal, "终端") { ShellScreen() },
    TabSpec(Icons.Filled.Folder, "文件") { FilesScreen() },
    TabSpec(Icons.Filled.Apps, "应用") { AppsScreen() },
    TabSpec(Icons.Filled.Link, "转发") { ForwardScreen() },
    TabSpec(Icons.Filled.Description, "日志") { LogcatScreen() },
    TabSpec(Icons.Filled.Settings, "设置") { SettingsScreen() },
)

@Composable
fun AppRoot() {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(Modifier.fillMaxSize()) {
            NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                Spacer(Modifier.height(6.dp))
                Tabs.forEachIndexed { index, tab ->
                    NavigationRailItem(
                        selected = Root.tab == index,
                        onClick = { Root.tab = index },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }

            Column(Modifier.fillMaxSize()) {
                HeaderBar()
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                AnimatedContent(
                    targetState = Root.tab,
                    transitionSpec = {
                        val dir = (targetState - initialState).coerceIn(-1, 1)
                        (slideInHorizontally(tween(240)) { it / 10 * dir } + fadeIn(tween(240)))
                            .togetherWith(fadeOut(tween(130)))
                    },
                    label = "tab-content",
                ) { index ->
                    Box(Modifier.fillMaxSize()) {
                        Tabs[index].screen()
                    }
                }
            }
        }

        ToastHost(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 66.dp),
        )
    }
}

/** 连接设备型图标。 */
private fun iconFor(type: LinkType): ImageVector = when (type) {
    LinkType.USB -> Icons.Filled.Usb
    LinkType.TCP -> Icons.Filled.Wifi
}

private enum class PickerState { HIDDEN, OPEN }

@Composable
private fun HeaderBar() {
    var picker by remember { mutableStateOf(PickerState.HIDDEN) }
    val active = Toolbox.active

                Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column {
                Text(
                    "Wear ADB Tool Box",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    "Kadb · 无 adb 二进制驱动",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.weight(1f))

            // 当前设备选择器
            Surface(
                onClick = { picker = if (picker == PickerState.OPEN) PickerState.HIDDEN else PickerState.OPEN },
                shape = MaterialTheme.shapes.small,
                color = if (picker == PickerState.OPEN) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        if (active == null) Icons.Filled.Close else Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = if (active == null) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        active?.label ?: "未连接设备",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (active == null) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface,
                    )
                    if (Toolbox.links.isNotEmpty()) {
                        Text(
                            Toolbox.links.size.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            StatusPill(active?.state)
        }

        AnimatedVisibility(visible = picker == PickerState.OPEN, modifier = Modifier.align(Alignment.End)) {
            ElevatedCard(
                Modifier
                    .padding(top = 10.dp)
                    .widthIn(max = 520.dp),
            ) {
                Column(Modifier.padding(10.dp)) {
                    if (Toolbox.links.isEmpty()) {
                        Text(
                            "暂无已连接设备 —— 在“设备”页建立连接",
                            Modifier.padding(10.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Toolbox.links.forEachIndexed { index, link ->
                            Surface(
                                onClick = {
                                    Toolbox.activeIndex = index
                                    picker = PickerState.HIDDEN
                                },
                                selected = index == Toolbox.activeIndex,
                                shape = MaterialTheme.shapes.small,
                                color = if (index == Toolbox.activeIndex) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surface,
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Icon(
                                        iconFor(link.type),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Column(Modifier.weight(1f)) {
                                        Text(link.label, style = MaterialTheme.typography.bodyLarge)
                                        link.props?.let {
                                            Text(
                                                "${it.brand} ${it.model} · Android ${it.android}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                    IconButton(onClick = {
                                        Toolbox.disconnect(link)
                                        picker = PickerState.HIDDEN
                                    }) {
                                        Icon(
                                            Icons.Filled.Close,
                                            contentDescription = "断开",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 连接状态胶囊。 */
@Composable
fun StatusPill(state: LinkState?) {
    val (color, text) = when (state) {
        LinkState.CONNECTED -> MaterialTheme.colorScheme.primary to "已连接"
        LinkState.CONNECTING -> MaterialTheme.colorScheme.tertiary to "连接中"
        LinkState.FAILED -> MaterialTheme.colorScheme.error to "失败"
        null, LinkState.CLOSED -> MaterialTheme.colorScheme.outlineVariant to "未连接"
    }
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = 0.14f),
    ) {
        Text(
            text,
            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = color,
        )
    }
}

@Composable
private fun ToastHost(modifier: Modifier = Modifier) {
    val msg = Toast.message
    LaunchedEffect(Toast.tick) {
        if (Toast.message != null) {
            kotlinx.coroutines.delay(3200)
            Toast.message = null
        }
    }
    AnimatedVisibility(
        visible = msg != null,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut(),
        modifier = modifier,
    ) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.inverseSurface,
            shadowElevation = 6.dp,
        ) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    Icons.Filled.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.inverseOnSurface,
                )
                Text(
                    msg ?: "",
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
