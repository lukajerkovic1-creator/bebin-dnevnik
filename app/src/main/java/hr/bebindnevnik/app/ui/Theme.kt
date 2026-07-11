package hr.bebindnevnik.app.ui

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import hr.bebindnevnik.app.data.AppTheme

internal object BabyPalette {
    val Fuchsia = Color(0xFFB00062)
    val FuchsiaDark = Color(0xFF79003F)
    val Pink = Color(0xFFFFD8E8)
    val PinkSoft = Color(0xFFFFEEF4)
    val Cream = Color(0xFFFFF9F5)
    val Plum = Color(0xFF302229)
    val Lavender = Color(0xFFE9DDF7)
    val Mint = Color(0xFFD8EFE8)
    val Peach = Color(0xFFFFE1D2)
}

internal object BabyDimensions {
    val CardCorner = 28.dp
    val ControlCorner = 18.dp
    val CardPadding = 20.dp
    val IllustrationSmall = 70.dp
    val IllustrationMedium = 88.dp
    val TouchTarget = 48.dp
}

private val LightColors =
    lightColorScheme(
        primary = BabyPalette.Fuchsia,
        onPrimary = Color.White,
        primaryContainer = BabyPalette.Pink,
        onPrimaryContainer = Color(0xFF3E001F),
        secondary = Color(0xFF7E5265),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFFFD9E5),
        onSecondaryContainer = Color(0xFF32101F),
        tertiary = Color(0xFF626047),
        tertiaryContainer = Color(0xFFE9E5BE),
        background = BabyPalette.Cream,
        onBackground = BabyPalette.Plum,
        surface = Color(0xFFFFFBFC),
        onSurface = BabyPalette.Plum,
        surfaceVariant = BabyPalette.PinkSoft,
        onSurfaceVariant = Color(0xFF58424B),
        outline = Color(0xFF8C717C),
        outlineVariant = Color(0xFFE3C2CF),
        error = Color(0xFFBA1A1A),
    )

private val DarkColors =
    darkColorScheme(
        primary = Color(0xFFFFAED0),
        onPrimary = Color(0xFF5D0031),
        primaryContainer = Color(0xFF810047),
        onPrimaryContainer = Color(0xFFFFD8E8),
        secondary = Color(0xFFF0B8CD),
        onSecondary = Color(0xFF482533),
        secondaryContainer = Color(0xFF603B4B),
        onSecondaryContainer = Color(0xFFFFD9E5),
        tertiary = Color(0xFFCDC99F),
        tertiaryContainer = Color(0xFF4B492F),
        background = Color(0xFF1B1216),
        onBackground = Color(0xFFF3DEE6),
        surface = Color(0xFF21171B),
        onSurface = Color(0xFFF3DEE6),
        surfaceVariant = Color(0xFF4D3A42),
        onSurfaceVariant = Color(0xFFD8C1CA),
        outline = Color(0xFFA98B97),
        outlineVariant = Color(0xFF59414B),
        error = Color(0xFFFFB4AB),
    )

private val BabyShapes =
    Shapes(
        extraSmall = RoundedCornerShape(10.dp),
        small = RoundedCornerShape(14.dp),
        medium = RoundedCornerShape(BabyDimensions.ControlCorner),
        large = RoundedCornerShape(BabyDimensions.CardCorner),
        extraLarge = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
    )

private val BabyTypography =
    Typography().let { defaults ->
        Typography(
            displaySmall = defaults.displaySmall.copy(fontWeight = FontWeight.Bold),
            headlineMedium = defaults.headlineMedium.copy(fontWeight = FontWeight.Bold),
            headlineSmall = defaults.headlineSmall.copy(fontWeight = FontWeight.Bold),
            titleLarge = defaults.titleLarge.copy(fontWeight = FontWeight.Bold),
            titleMedium = defaults.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            labelLarge = defaults.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            bodyLarge = TextStyle.Default.merge(defaults.bodyLarge),
        )
    }

@Composable
fun BebinDnevnikTheme(
    theme: AppTheme,
    content: @Composable () -> Unit,
) {
    val dark =
        when (theme) {
            AppTheme.SUSTAV -> androidx.compose.foundation.isSystemInDarkTheme()
            AppTheme.SVIJETLA -> false
            AppTheme.TAMNA -> true
        }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = BabyTypography,
        shapes = BabyShapes,
        content = content,
    )
}
