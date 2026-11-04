package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

enum class GlassEffectMode(val labelFa: String) {
    AUTO("خودکار"),
    ON("روشن"),
    OFF("خاموش")
}

enum class TextSizePreset(val labelFa: String, val scaleMultiplier: Float) {
    SMALL("کوچک", 0.90f),
    MEDIUM("متوسط", 1.00f),
    LARGE("بزرگ", 1.15f),
    EXTRA_LARGE("خیلی بزرگ", 1.32f)
}

enum class MedoThemeId(val id: String, val nameFa: String, val nameEn: String) {
    DARK("dark", "تیره", "Dark"),
    AMOLED("amoled", "تاریک مطلق (AMOLED)", "AMOLED Black"),
    LIGHT("light", "روشن", "Light"),
    SEPIA("sepia", "سپیا", "Sepia"),
    BLUE_GREY("blue_grey", "خاکستری آبی", "Blue-Grey"),
    MIDNIGHT("midnight", "نیمه‌شب", "Midnight"),
    FOREST("forest", "جنگل", "Forest"),
    NIGHT_PURPLE("night_purple", "بنفش شب", "Night Purple"),
    BLUE_PAPER("blue_paper", "کاغذ آبی", "Blue Paper"),
    MINT("mint", "نعنا", "Mint"),
    SUNSET("sunset", "غروب", "Sunset");

    companion object {
        fun fromId(id: String): MedoThemeId = entries.find { it.id == id } ?: DARK
    }
}

data class MedoThemeTokens(
    val themeId: MedoThemeId,
    val isDark: Boolean,
    val gradientStart: Color,
    val gradientMiddle: Color,
    val gradientEnd: Color,
    val surfaceTint: Color,
    val readingSurfaceTint: Color,
    val surfaceAlpha: Float,
    val readingSurfaceAlpha: Float,
    val borderAlpha: Float,
    val highlightAlpha: Float,
    val blurRadiusDp: Float,
    val textPrimary: Color,
    val textSecondary: Color,
    val accent: Color,
    val accentOn: Color,
    val correct: Color,
    val wrong: Color,
    val warning: Color
)

object MedoThemeCatalog {

    val allThemes: List<MedoThemeTokens> = listOf(
        MedoThemeTokens(
            themeId = MedoThemeId.DARK,
            isDark = true,
            gradientStart = Color(0xFF0F172A),
            gradientMiddle = Color(0xFF1E293B),
            gradientEnd = Color(0xFF0B1120),
            surfaceTint = Color(0xFF1E293B),
            readingSurfaceTint = Color(0xFF0F172A),
            surfaceAlpha = 0.78f,
            readingSurfaceAlpha = 0.94f,
            borderAlpha = 0.20f,
            highlightAlpha = 0.12f,
            blurRadiusDp = 22f,
            textPrimary = Color(0xFFF8FAFC),
            textSecondary = Color(0xFFCBD5E1),
            accent = Color(0xFF38BDF8),
            accentOn = Color(0xFF082F49),
            correct = Color(0xFF4ADE80),
            wrong = Color(0xFFF87171),
            warning = Color(0xFFFBBF24)
        ),
        MedoThemeTokens(
            themeId = MedoThemeId.AMOLED,
            isDark = true,
            gradientStart = Color(0xFF000000),
            gradientMiddle = Color(0xFF090D16),
            gradientEnd = Color(0xFF000000),
            surfaceTint = Color(0xFF111827),
            readingSurfaceTint = Color(0xFF05070B),
            surfaceAlpha = 0.82f,
            readingSurfaceAlpha = 0.96f,
            borderAlpha = 0.24f,
            highlightAlpha = 0.10f,
            blurRadiusDp = 18f,
            textPrimary = Color(0xFFFFFFFF),
            textSecondary = Color(0xFFD1D5DB),
            accent = Color(0xFF60A5FA),
            accentOn = Color(0xFF051B3B),
            correct = Color(0xFF4ADE80),
            wrong = Color(0xFFF87171),
            warning = Color(0xFFFBBF24)
        ),
        MedoThemeTokens(
            themeId = MedoThemeId.LIGHT,
            isDark = false,
            gradientStart = Color(0xFFF8FAFC),
            gradientMiddle = Color(0xFFEFF6FF),
            gradientEnd = Color(0xFFE2E8F0),
            surfaceTint = Color(0xFFFFFFFF),
            readingSurfaceTint = Color(0xFFFFFFFF),
            surfaceAlpha = 0.82f,
            readingSurfaceAlpha = 0.95f,
            borderAlpha = 0.25f,
            highlightAlpha = 0.45f,
            blurRadiusDp = 20f,
            textPrimary = Color(0xFF0F172A),
            textSecondary = Color(0xFF334155),
            accent = Color(0xFF0284C7),
            accentOn = Color(0xFFFFFFFF),
            correct = Color(0xFF15803D),
            wrong = Color(0xFFB91C1C),
            warning = Color(0xFFB45309)
        ),
        MedoThemeTokens(
            themeId = MedoThemeId.SEPIA,
            isDark = false,
            gradientStart = Color(0xFFFDF6E3),
            gradientMiddle = Color(0xFFF5EAB9),
            gradientEnd = Color(0xFFEFE1CE),
            surfaceTint = Color(0xFFFAF3E0),
            readingSurfaceTint = Color(0xFFFFFDF7),
            surfaceAlpha = 0.84f,
            readingSurfaceAlpha = 0.95f,
            borderAlpha = 0.24f,
            highlightAlpha = 0.35f,
            blurRadiusDp = 20f,
            textPrimary = Color(0xFF2B2118),
            textSecondary = Color(0xFF4A3B2C),
            accent = Color(0xFF9A3412),
            accentOn = Color(0xFFFFFFFF),
            correct = Color(0xFF166534),
            wrong = Color(0xFF991B1B),
            warning = Color(0xFF92400E)
        ),
        MedoThemeTokens(
            themeId = MedoThemeId.BLUE_GREY,
            isDark = true,
            gradientStart = Color(0xFF1E293B),
            gradientMiddle = Color(0xFF273549),
            gradientEnd = Color(0xFF0F172A),
            surfaceTint = Color(0xFF1E293B),
            readingSurfaceTint = Color(0xFF131C2E),
            surfaceAlpha = 0.80f,
            readingSurfaceAlpha = 0.94f,
            borderAlpha = 0.20f,
            highlightAlpha = 0.14f,
            blurRadiusDp = 22f,
            textPrimary = Color(0xFFF1F5F9),
            textSecondary = Color(0xFFCBD5E1),
            accent = Color(0xFF38BDF8),
            accentOn = Color(0xFF082F49),
            correct = Color(0xFF4ADE80),
            wrong = Color(0xFFF87171),
            warning = Color(0xFFFBBF24)
        ),
        MedoThemeTokens(
            themeId = MedoThemeId.MIDNIGHT,
            isDark = true,
            gradientStart = Color(0xFF090D1F),
            gradientMiddle = Color(0xFF111936),
            gradientEnd = Color(0xFF060914),
            surfaceTint = Color(0xFF131B38),
            readingSurfaceTint = Color(0xFF0A0F24),
            surfaceAlpha = 0.80f,
            readingSurfaceAlpha = 0.94f,
            borderAlpha = 0.20f,
            highlightAlpha = 0.12f,
            blurRadiusDp = 24f,
            textPrimary = Color(0xFFF8FAFC),
            textSecondary = Color(0xFFCBD5E1),
            accent = Color(0xFF818CF8),
            accentOn = Color(0xFF1E1B4B),
            correct = Color(0xFF4ADE80),
            wrong = Color(0xFFFB7185),
            warning = Color(0xFFFBBF24)
        ),
        MedoThemeTokens(
            themeId = MedoThemeId.FOREST,
            isDark = true,
            gradientStart = Color(0xFF061E16),
            gradientMiddle = Color(0xFF0B2E23),
            gradientEnd = Color(0xFF04140F),
            surfaceTint = Color(0xFF0D3528),
            readingSurfaceTint = Color(0xFF061C15),
            surfaceAlpha = 0.80f,
            readingSurfaceAlpha = 0.94f,
            borderAlpha = 0.20f,
            highlightAlpha = 0.12f,
            blurRadiusDp = 22f,
            textPrimary = Color(0xFFECFDF5),
            textSecondary = Color(0xFFA7F3D0),
            accent = Color(0xFF34D399),
            accentOn = Color(0xFF022C22),
            correct = Color(0xFF4ADE80),
            wrong = Color(0xFFFCA5A5),
            warning = Color(0xFFFCD34D)
        ),
        MedoThemeTokens(
            themeId = MedoThemeId.NIGHT_PURPLE,
            isDark = true,
            gradientStart = Color(0xFF1A1025),
            gradientMiddle = Color(0xFF261738),
            gradientEnd = Color(0xFF11091A),
            surfaceTint = Color(0xFF2A183D),
            readingSurfaceTint = Color(0xFF160C21),
            surfaceAlpha = 0.80f,
            readingSurfaceAlpha = 0.94f,
            borderAlpha = 0.20f,
            highlightAlpha = 0.14f,
            blurRadiusDp = 24f,
            textPrimary = Color(0xFFFAF5FF),
            textSecondary = Color(0xFFE9D5FF),
            accent = Color(0xFFC084FC),
            accentOn = Color(0xFF2E1065),
            correct = Color(0xFF4ADE80),
            wrong = Color(0xFFFDA4AF),
            warning = Color(0xFFFDE047)
        ),
        MedoThemeTokens(
            themeId = MedoThemeId.BLUE_PAPER,
            isDark = false,
            gradientStart = Color(0xFFF0F7FF),
            gradientMiddle = Color(0xFFE0EFFE),
            gradientEnd = Color(0xFFD8E8FA),
            surfaceTint = Color(0xFFF8FBFF),
            readingSurfaceTint = Color(0xFFFFFFFF),
            surfaceAlpha = 0.84f,
            readingSurfaceAlpha = 0.95f,
            borderAlpha = 0.24f,
            highlightAlpha = 0.45f,
            blurRadiusDp = 20f,
            textPrimary = Color(0xFF0C1928),
            textSecondary = Color(0xFF1E3A5F),
            accent = Color(0xFF0369A1),
            accentOn = Color(0xFFFFFFFF),
            correct = Color(0xFF15803D),
            wrong = Color(0xFFB91C1C),
            warning = Color(0xFFB45309)
        ),
        MedoThemeTokens(
            themeId = MedoThemeId.MINT,
            isDark = false,
            gradientStart = Color(0xFFF0FDF4),
            gradientMiddle = Color(0xFFDCFCE7),
            gradientEnd = Color(0xFFD1FAE5),
            surfaceTint = Color(0xFFF7FEE7),
            readingSurfaceTint = Color(0xFFFFFFFF),
            surfaceAlpha = 0.84f,
            readingSurfaceAlpha = 0.95f,
            borderAlpha = 0.24f,
            highlightAlpha = 0.45f,
            blurRadiusDp = 20f,
            textPrimary = Color(0xFF052E16),
            textSecondary = Color(0xFF14532D),
            accent = Color(0xFF047857),
            accentOn = Color(0xFFFFFFFF),
            correct = Color(0xFF15803D),
            wrong = Color(0xFFB91C1C),
            warning = Color(0xFFB45309)
        ),
        MedoThemeTokens(
            themeId = MedoThemeId.SUNSET,
            isDark = true,
            gradientStart = Color(0xFF23121B),
            gradientMiddle = Color(0xFF331824),
            gradientEnd = Color(0xFF170B12),
            surfaceTint = Color(0xFF381B28),
            readingSurfaceTint = Color(0xFF1E0E16),
            surfaceAlpha = 0.80f,
            readingSurfaceAlpha = 0.94f,
            borderAlpha = 0.20f,
            highlightAlpha = 0.14f,
            blurRadiusDp = 22f,
            textPrimary = Color(0xFFFFF1F2),
            textSecondary = Color(0xFFFECDD3),
            accent = Color(0xFFFB923C),
            accentOn = Color(0xFF431407),
            correct = Color(0xFF4ADE80),
            wrong = Color(0xFFFDA4AF),
            warning = Color(0xFFFACC15)
        )
    )

    fun getTokens(id: MedoThemeId): MedoThemeTokens =
        allThemes.firstOrNull { it.themeId == id } ?: allThemes.first()

    /**
     * Alpha-composites [foreground] with [alpha] over an opaque [background] color.
     */
    fun compositeOver(foreground: Color, alpha: Float, background: Color): Color {
        val a = alpha.coerceIn(0f, 1f)
        val r = foreground.red * a + background.red * (1f - a)
        val g = foreground.green * a + background.green * (1f - a)
        val b = foreground.blue * a + background.blue * (1f - a)
        return Color(r, g, b, 1f)
    }

    /**
     * Computes WCAG 2.1 relative luminance of a Color.
     */
    fun relativeLuminance(color: Color): Double {
        fun channelLum(c: Float): Double {
            val v = c.toDouble()
            return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channelLum(color.red) +
            0.7152 * channelLum(color.green) +
            0.0722 * channelLum(color.blue)
    }

    /**
     * Computes WCAG 2.1 contrast ratio between two opaque colors (range 1.0 to 21.0).
     */
    fun contrastRatio(c1: Color, c2: Color): Double {
        val l1 = relativeLuminance(c1)
        val l2 = relativeLuminance(c2)
        val lighter = max(l1, l2)
        val darker = min(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    /**
     * Computes the worst-case contrast ratio of [textColor] on a translucent panel
     * composited over all three stops of the theme's background gradient.
     */
    fun worstCaseContrast(
        textColor: Color,
        panelTint: Color,
        panelAlpha: Float,
        theme: MedoThemeTokens
    ): Double {
        val stops = listOf(theme.gradientStart, theme.gradientMiddle, theme.gradientEnd)
        return stops.minOf { stop ->
            val effectiveBg = compositeOver(panelTint, panelAlpha, stop)
            contrastRatio(textColor, effectiveBg)
        }
    }
}
