package com.fwork.adbtools.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.loadSvgPainter
import androidx.compose.ui.unit.Density

/** 分区卡片：大圆角 + 标题头（MD3E 观感）。 */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: Painter? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                trailing?.invoke(this@Row)
            }
            content()
        }
    }
}

/** 文本输入（统一 label / singleLine）。宽度由调用方 Modifier 控制。 */
@Composable
fun LabeledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { ph -> { Text(ph) } },
        singleLine = true,
        modifier = modifier,
    )
}

/** 键值信息行。 */
@Composable
fun FieldRow(label: String, value: String?, modifier: Modifier = Modifier, labelWidth: Dp = 110.dp) {
    if (value.isNullOrBlank()) return
    Row(
        modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            Modifier.padding(end = 12.dp).width(labelWidth),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SelectionContainer {
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** 空态提示。 */
@Composable
fun EmptyHint(
    text: String,
    modifier: Modifier = Modifier,
    icon: Painter = svgIcon("folder_open"),
) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.outlineVariant)
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** MD3E 胶囊切换块（选中态填充 primaryContainer）。 */
@Composable
fun PillToggle(
    selected: Boolean,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        selected = selected,
        shape = RoundedCornerShape(999.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (selected) {
                Icon(
                    svgIcon("check_circle"),
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            )
        }
    }
}

/** 图标操作按钮。 */
@Composable
fun IconAction(
    icon: Painter,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = modifier) {
        Icon(icon, contentDescription = contentDescription, tint = tint.copy(alpha = if (enabled) 1f else 0.4f))
    }
}

/** 矢量图标缓存：从 res 目录的 icons 子目录按名称加载 SVG（构建时打进 jar）。 */
private object SvgIcons {
    private val cache = java.util.concurrent.ConcurrentHashMap<String, Painter>()

    fun get(name: String): Painter = cache.getOrPut(name) {
        val stream = checkNotNull(SvgIcons::class.java.getResourceAsStream("/icons/$name.svg")) {
            "Missing vector resource: /icons/$name.svg"
        }
        stream.use { loadSvgPainter(it, Density(1f)) }
    }
}

/** 引用项目 res/icons 下的矢量图。 */
fun svgIcon(name: String): Painter = SvgIcons.get(name)
