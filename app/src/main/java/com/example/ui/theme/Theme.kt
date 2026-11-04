package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.view.WindowCompat

data class MedoSettingsState(
    val selectedThemeId: MedoThemeId = MedoThemeId.DARK,
    val followSystemTheme: Boolean = false,
    val preferredLightThemeId: MedoThemeId = MedoThemeId.LIGHT,
    val preferredDarkThemeId: MedoThemeId = MedoThemeId.DARK,
    val glassEffectMode: GlassEffectMode = GlassEffectMode.AUTO,
    val reduceMotion: Boolean = false,
    val usePersianDigits: Boolean = true,
    val textSizePreset: TextSizePreset = TextSizePreset.MEDIUM,
    val lineSpacingMultiplier: Float = 1.0f,
    val fontChoice: MedoFontChoice = MedoFontChoice.VAZIRMATN,
    val enableHaptics: Boolean = true,
    val enableSwipeGestures: Boolean = true,
    val dailyNewCardsLimit: Int = 20,
    val examNegativeMarkingRatio: Int = 3, // 1/3 negative marking
    val dailyAutoBackupToDocuments: Boolean = true
)

val LocalMedoTheme = staticCompositionLocalOf { MedoThemeCatalog.getTokens(MedoThemeId.DARK) }
val LocalMedoSettings = staticCompositionLocalOf { MedoSettingsState() }

@Composable
fun MedoNextTheme(
    settings: MedoSettingsState = MedoSettingsState(),
    isRtl: Boolean = true,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val effectiveThemeId = if (settings.followSystemTheme) {
        if (systemDark) settings.preferredDarkThemeId else settings.preferredLightThemeId
    } else {
        settings.selectedThemeId
    }

    val tokens = MedoThemeCatalog.getTokens(effectiveThemeId)

    val colorScheme = if (tokens.isDark) {
        darkColorScheme(
            primary = tokens.accent,
            onPrimary = tokens.accentOn,
            secondary = tokens.correct,
            tertiary = tokens.warning,
            background = tokens.gradientStart,
            surface = tokens.surfaceTint,
            onBackground = tokens.textPrimary,
            onSurface = tokens.textPrimary,
            onSurfaceVariant = tokens.textSecondary,
            error = tokens.wrong
        )
    } else {
        lightColorScheme(
            primary = tokens.accent,
            onPrimary = tokens.accentOn,
            secondary = tokens.correct,
            tertiary = tokens.warning,
            background = tokens.gradientStart,
            surface = tokens.surfaceTint,
            onBackground = tokens.textPrimary,
            onSurface = tokens.textPrimary,
            onSurfaceVariant = tokens.textSecondary,
            error = tokens.wrong
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                @Suppress("DEPRECATION")
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                @Suppress("DEPRECATION")
                window.navigationBarColor = tokens.gradientEnd.toArgb()
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !tokens.isDark
                insetsController.isAppearanceLightNavigationBars = !tokens.isDark
            }
        }
    }

    val typography = createMedoTypography(
        scaleMultiplier = settings.textSizePreset.scaleMultiplier,
        lineSpacingMultiplier = settings.lineSpacingMultiplier,
        fontChoice = settings.fontChoice
    )

    CompositionLocalProvider(
        LocalMedoTheme provides tokens,
        LocalMedoSettings provides settings,
        LocalLayoutDirection provides if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content
        )
    }
}
