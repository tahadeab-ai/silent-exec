package com.silentexec.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silentexec.android.core.ThemeMode

object SilentExecColors {
    val CanvasDark = Color(0xFF0B0F17)
    val ElevatedCardDark = Color(0xFF131A26)
    val SubtleBorderDark = Color(0xFF1D2738)
    val PrimaryAccent = Color(0xFF38BDF8)
    val Success = Color(0xFF10B981)
    val Warning = Color(0xFFF59E0B)
    val Error = Color(0xFFEF4444)
    val TextPrimaryDark = Color(0xFFF1F5F9)
    val TextSecondaryDark = Color(0xFF94A3B8)
}

private val DarkColors = darkColorScheme(
    primary = SilentExecColors.PrimaryAccent,
    onPrimary = Color(0xFF062033),
    primaryContainer = Color(0xFF0F2942),
    onPrimaryContainer = Color(0xFFBAE6FD),
    secondary = SilentExecColors.Success,
    onSecondary = Color(0xFF042F22),
    secondaryContainer = Color(0xFF0F2C26),
    onSecondaryContainer = Color(0xFFA7F3D0),
    tertiary = SilentExecColors.Warning,
    onTertiary = Color(0xFF2B1900),
    background = SilentExecColors.CanvasDark,
    onBackground = SilentExecColors.TextPrimaryDark,
    surface = SilentExecColors.ElevatedCardDark,
    onSurface = SilentExecColors.TextPrimaryDark,
    surfaceVariant = Color(0xFF192233),
    onSurfaceVariant = SilentExecColors.TextSecondaryDark,
    outline = SilentExecColors.SubtleBorderDark,
    error = SilentExecColors.Error,
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFF2B1217),
    onErrorContainer = Color(0xFFFCA5A5)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0284C7),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF0C4A6E),
    secondary = Color(0xFF059669),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD1FAE5),
    onSecondaryContainer = Color(0xFF064E3B),
    tertiary = Color(0xFFD97706),
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1),
    error = Color(0xFFDC2626),
    onError = Color(0xFFFFFFFF)
)

private val SilentExecShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

private val SilentExecTypography = Typography(
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    )
)

@Composable
fun SilentExecTheme(
    themeMode: ThemeMode,
    isArabic: Boolean,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val colors = if (darkTheme) DarkColors else LightColors
    val layoutDirection = if (isArabic) LayoutDirection.Rtl else LayoutDirection.Ltr

    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        MaterialTheme(
            colorScheme = colors,
            shapes = SilentExecShapes,
            typography = SilentExecTypography,
            content = content
        )
    }
}
