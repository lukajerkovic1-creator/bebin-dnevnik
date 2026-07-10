package hr.bebindnevnik.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import hr.bebindnevnik.app.data.AppTheme

private val LightColors =
    lightColorScheme(
        primary =
            androidx.compose.ui.graphics
                .Color(0xFFA5005A),
        onPrimary = androidx.compose.ui.graphics.Color.White,
        primaryContainer =
            androidx.compose.ui.graphics
                .Color(0xFFFFD8E8),
        onPrimaryContainer =
            androidx.compose.ui.graphics
                .Color(0xFF3D0020),
        secondary =
            androidx.compose.ui.graphics
                .Color(0xFF765663),
        background =
            androidx.compose.ui.graphics
                .Color(0xFFFFF8FA),
        surface =
            androidx.compose.ui.graphics
                .Color(0xFFFFF8FA),
        error =
            androidx.compose.ui.graphics
                .Color(0xFFBA1A1A),
    )

private val DarkColors =
    darkColorScheme(
        primary =
            androidx.compose.ui.graphics
                .Color(0xFFFFAFD0),
        onPrimary =
            androidx.compose.ui.graphics
                .Color(0xFF620035),
        primaryContainer =
            androidx.compose.ui.graphics
                .Color(0xFF870049),
        onPrimaryContainer =
            androidx.compose.ui.graphics
                .Color(0xFFFFD8E8),
        secondary =
            androidx.compose.ui.graphics
                .Color(0xFFE5BDC9),
        background =
            androidx.compose.ui.graphics
                .Color(0xFF191114),
        surface =
            androidx.compose.ui.graphics
                .Color(0xFF191114),
        error =
            androidx.compose.ui.graphics
                .Color(0xFFFFB4AB),
    )

@Composable
fun BebinDnevnikTheme(
    theme: AppTheme,
    content: @Composable () -> Unit,
) {
    val dark =
        when (theme) {
            AppTheme.SUSTAV -> isSystemInDarkTheme()
            AppTheme.SVIJETLA -> false
            AppTheme.TAMNA -> true
        }
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}
