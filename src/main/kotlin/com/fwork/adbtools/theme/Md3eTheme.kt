package com.fwork.adbtools.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fwork.adbtools.core.Prefs
import kotlin.math.pow

/** 预设主色（移植自 OpenSource / MD3-ClickGui-NextGen 的 NexusTheme 色板体系，升级为 MD3E 外观）。 */
val Md3eSwatchColors = listOf(
    Color(0xFF176B60), // 青碧
    Color(0xFF315F90), // 远洋
    Color(0xFF6750A4), // 长紫
    Color(0xFF95513C), // 日落
)

val Md3eSwatchNames = listOf("青碧", "远洋", "长紫", "日落")

private data class Md3ePalette(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val inversePrimary: Color,
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val tertiary: Color,
    val onTertiary: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color,
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val inverseSurface: Color,
    val inverseOnSurface: Color,
    val surfaceBright: Color,
    val surfaceDim: Color,
    val surfaceContainerLowest: Color,
    val surfaceContainerLow: Color,
    val surfaceContainer: Color,
    val surfaceContainerHigh: Color,
    val surfaceContainerHighest: Color,
    val outline: Color,
    val outlineVariant: Color,
    val error: Color,
    val onError: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,
)

private fun mix(base: Color, target: Color, amount: Float): Color = Color(
    base.red + (target.red - base.red) * amount,
    base.green + (target.green - base.green) * amount,
    base.blue + (target.blue - base.blue) * amount,
    1f,
)

private fun relativeLuminance(color: Color): Double {
    fun linear(channel: Float): Double {
        val value = channel.toDouble()
        return if (value <= 0.03928) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * linear(color.red) + 0.7152 * linear(color.green) + 0.0722 * linear(color.blue)
}

private fun readableOn(color: Color): Color {
    val darkText = Color(0xFF17201D)
    val backgroundLuminance = relativeLuminance(color)
    fun contrast(foreground: Color): Double {
        val foregroundLuminance = relativeLuminance(foreground)
        val lighter = maxOf(backgroundLuminance, foregroundLuminance)
        val darker = minOf(backgroundLuminance, foregroundLuminance)
        return (lighter + 0.05) / (darker + 0.05)
    }
    return if (contrast(darkText) >= contrast(Color.White)) darkText else Color.White
}

fun parseHexColor(hex: String): Color? {
    val value = hex.removePrefix("#").trim().toLongOrNull(16) ?: return null
    if (value > 0xFFFFFF) return null
    return Color(
        ((value shr 16) and 0xFF) / 255f,
        ((value shr 8) and 0xFF) / 255f,
        (value and 0xFF) / 255f,
        1f,
    )
}

private fun paletteFor(swatchIndex: Int, darkMode: Boolean, customColorHex: String?): Md3ePalette {
    val swatch = customColorHex?.let(::parseHexColor)
        ?: Md3eSwatchColors[swatchIndex.coerceIn(Md3eSwatchColors.indices)]
    return if (darkMode) {
        val primary = mix(swatch, Color.White, 0.34f)
        val primaryContainer = mix(swatch, Color.Black, 0.48f)
        val secondary = mix(swatch, Color.White, 0.52f)
        val secondaryContainer = mix(swatch, Color.Black, 0.34f)
        val tertiary = mix(swatch, Color.White, 0.66f)
        val tertiaryContainer = mix(swatch, Color.Black, 0.30f)
        Md3ePalette(
            primary = primary,
            onPrimary = readableOn(primary),
            primaryContainer = primaryContainer,
            onPrimaryContainer = readableOn(primaryContainer),
            inversePrimary = mix(swatch, Color.White, 0.58f),
            secondary = secondary,
            onSecondary = readableOn(secondary),
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = readableOn(secondaryContainer),
            tertiary = tertiary,
            onTertiary = readableOn(tertiary),
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = readableOn(tertiaryContainer),
            background = mix(swatch, Color.Black, 0.93f),
            onBackground = mix(swatch, Color.White, 0.88f),
            surface = mix(swatch, Color.Black, 0.86f),
            surfaceVariant = mix(swatch, Color.Black, 0.47f),
            onSurface = mix(swatch, Color.White, 0.90f),
            onSurfaceVariant = mix(swatch, Color.White, 0.68f),
            inverseSurface = mix(swatch, Color.White, 0.94f),
            inverseOnSurface = mix(swatch, Color.Black, 0.86f),
            surfaceBright = mix(swatch, Color.Black, 0.76f),
            surfaceDim = mix(swatch, Color.Black, 0.93f),
            surfaceContainerLowest = mix(swatch, Color.Black, 0.97f),
            surfaceContainerLow = mix(swatch, Color.Black, 0.76f),
            surfaceContainer = mix(swatch, Color.Black, 0.72f),
            surfaceContainerHigh = mix(swatch, Color.Black, 0.66f),
            surfaceContainerHighest = mix(swatch, Color.Black, 0.60f),
            outline = mix(swatch, Color.White, 0.50f),
            outlineVariant = mix(swatch, Color.White, 0.30f),
            error = Color(0xFFFFB4AB),
            onError = Color(0xFF690005),
            errorContainer = Color(0xFF93000A),
            onErrorContainer = Color(0xFFFFDAD6),
        )
    } else {
        val primary = mix(swatch, Color.Black, 0.18f)
        val primaryContainer = mix(swatch, Color.White, 0.78f)
        val secondary = mix(swatch, Color.Black, 0.34f)
        val secondaryContainer = mix(swatch, Color.White, 0.68f)
        val tertiary = mix(swatch, Color.White, 0.18f)
        val tertiaryContainer = mix(swatch, Color.White, 0.82f)
        Md3ePalette(
            primary = primary,
            onPrimary = readableOn(primary),
            primaryContainer = primaryContainer,
            onPrimaryContainer = readableOn(primaryContainer),
            inversePrimary = mix(swatch, Color.White, 0.42f),
            secondary = secondary,
            onSecondary = readableOn(secondary),
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = readableOn(secondaryContainer),
            tertiary = tertiary,
            onTertiary = readableOn(tertiary),
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = readableOn(tertiaryContainer),
            background = mix(swatch, Color.White, 0.96f),
            onBackground = mix(swatch, Color.Black, 0.82f),
            surface = mix(swatch, Color.White, 0.98f),
            surfaceVariant = mix(swatch, Color.White, 0.72f),
            onSurface = mix(swatch, Color.Black, 0.82f),
            onSurfaceVariant = mix(swatch, Color.Black, 0.54f),
            inverseSurface = mix(swatch, Color.Black, 0.86f),
            inverseOnSurface = Color.White,
            surfaceBright = Color.White,
            surfaceDim = mix(swatch, Color.White, 0.88f),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = mix(swatch, Color.White, 0.94f),
            surfaceContainer = mix(swatch, Color.White, 0.91f),
            surfaceContainerHigh = mix(swatch, Color.White, 0.87f),
            surfaceContainerHighest = mix(swatch, Color.White, 0.83f),
            outline = mix(swatch, Color.Black, 0.38f),
            outlineVariant = mix(swatch, Color.White, 0.56f),
            error = Color(0xFFBA1A1A),
            onError = Color.White,
            errorContainer = Color(0xFFFFDAD6),
            onErrorContainer = Color(0xFF410002),
        )
    }
}

fun md3eColorScheme(darkMode: Boolean, swatchIndex: Int, customHex: String?) = paletteFor(
    darkMode = darkMode,
    swatchIndex = swatchIndex,
    customColorHex = customHex,
).let { p ->
    if (darkMode) {
        darkColorScheme(
            primary = p.primary, onPrimary = p.onPrimary,
            primaryContainer = p.primaryContainer, onPrimaryContainer = p.onPrimaryContainer,
            inversePrimary = p.inversePrimary,
            secondary = p.secondary, onSecondary = p.onSecondary,
            secondaryContainer = p.secondaryContainer, onSecondaryContainer = p.onSecondaryContainer,
            tertiary = p.tertiary, onTertiary = p.onTertiary,
            tertiaryContainer = p.tertiaryContainer, onTertiaryContainer = p.onTertiaryContainer,
            background = p.background, onBackground = p.onBackground,
            surface = p.surface, surfaceVariant = p.surfaceVariant,
            onSurface = p.onSurface, onSurfaceVariant = p.onSurfaceVariant,
            inverseSurface = p.inverseSurface, inverseOnSurface = p.inverseOnSurface,
            error = p.error, onError = p.onError,
            errorContainer = p.errorContainer, onErrorContainer = p.onErrorContainer,
            outline = p.outline, outlineVariant = p.outlineVariant,
            surfaceBright = p.surfaceBright, surfaceDim = p.surfaceDim,
            surfaceContainerLowest = p.surfaceContainerLowest,
            surfaceContainerLow = p.surfaceContainerLow,
            surfaceContainer = p.surfaceContainer,
            surfaceContainerHigh = p.surfaceContainerHigh,
            surfaceContainerHighest = p.surfaceContainerHighest,
        )
    } else {
        lightColorScheme(
            primary = p.primary, onPrimary = p.onPrimary,
            primaryContainer = p.primaryContainer, onPrimaryContainer = p.onPrimaryContainer,
            inversePrimary = p.inversePrimary,
            secondary = p.secondary, onSecondary = p.onSecondary,
            secondaryContainer = p.secondaryContainer, onSecondaryContainer = p.onSecondaryContainer,
            tertiary = p.tertiary, onTertiary = p.onTertiary,
            tertiaryContainer = p.tertiaryContainer, onTertiaryContainer = p.onTertiaryContainer,
            background = p.background, onBackground = p.onBackground,
            surface = p.surface, surfaceVariant = p.surfaceVariant,
            onSurface = p.onSurface, onSurfaceVariant = p.onSurfaceVariant,
            inverseSurface = p.inverseSurface, inverseOnSurface = p.inverseOnSurface,
            error = p.error, onError = p.onError,
            errorContainer = p.errorContainer, onErrorContainer = p.onErrorContainer,
            outline = p.outline, outlineVariant = p.outlineVariant,
            surfaceBright = p.surfaceBright, surfaceDim = p.surfaceDim,
            surfaceContainerLowest = p.surfaceContainerLowest,
            surfaceContainerLow = p.surfaceContainerLow,
            surfaceContainer = p.surfaceContainer,
            surfaceContainerHigh = p.surfaceContainerHigh,
            surfaceContainerHighest = p.surfaceContainerHighest,
        )
    }
}

/** MD3E：更大圆角的形状层级（胶囊按钮见 PillToggle）。 */
val Md3eShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(34.dp),
)

/** MD3E 运动规格（弹簧）。 */
object Md3eMotion {
    val fast = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
    val juicy = spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
    val settle = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow)
}

private val Md3eTypography = Typography(
    displaySmall = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 21.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 15.sp),
    bodyMedium = TextStyle(fontSize = 13.sp),
    bodySmall = TextStyle(fontSize = 11.sp),
    labelLarge = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
)

@Composable
fun Md3eTheme(content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    LaunchedEffect(Unit) { Prefs.applyFirstRunTheme(systemDark) }
    MaterialTheme(
        colorScheme = md3eColorScheme(
            darkMode = Prefs.darkMode,
            swatchIndex = Prefs.swatchIndex,
            customHex = Prefs.customHex,
        ),
        typography = Md3eTypography,
        shapes = Md3eShapes,
        content = content,
    )
}
