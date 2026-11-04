package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

val VazirmatnFontFamily = FontFamily(
    Font(R.font.vazirmatn, FontWeight.Normal),
    Font(R.font.vazirmatn, FontWeight.Medium),
    Font(R.font.vazirmatn, FontWeight.SemiBold),
    Font(R.font.vazirmatn, FontWeight.Bold)
)

val BNazaninStyleFontFamily = FontFamily(
    Font(R.font.markazi_text, FontWeight.Normal),
    Font(R.font.markazi_text, FontWeight.Medium),
    Font(R.font.markazi_text, FontWeight.SemiBold),
    Font(R.font.markazi_text, FontWeight.Bold)
)

val AmiriNaskhFontFamily = FontFamily(
    Font(R.font.amiri, FontWeight.Normal),
    Font(R.font.amiri, FontWeight.Bold)
)

val InterFontFamily = FontFamily(
    Font(R.font.inter, FontWeight.Normal),
    Font(R.font.inter, FontWeight.Medium),
    Font(R.font.inter, FontWeight.SemiBold),
    Font(R.font.inter, FontWeight.Bold)
)

val RobotoFontFamily = FontFamily(
    Font(R.font.roboto, FontWeight.Normal),
    Font(R.font.roboto, FontWeight.Medium),
    Font(R.font.roboto, FontWeight.Bold)
)

val JetBrainsMonoFontFamily = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Normal),
    Font(R.font.jetbrains_mono, FontWeight.Medium),
    Font(R.font.jetbrains_mono, FontWeight.Bold)
)

enum class MedoFontChoice(
    val id: String,
    val labelFa: String,
    val isPersianPrimary: Boolean
) {
    VAZIRMATN("vazirmatn", "وزیرمتن (Vazirmatn)", true),
    B_NAZANIN("b_nazanin", "بی نازنین / مرکزی (B Nazanin)", true),
    AMIRI("amiri", "امیری نسخ (Amiri)", true),
    INTER("inter", "اینتر (Inter - English)", false),
    ROBOTO("roboto", "روبوتو (Roboto - English)", false),
    JETBRAINS_MONO("jetbrains_mono", "جت‌برینز مونو (JetBrains Mono)", false);

    fun toComposeFontFamily(): FontFamily = when (this) {
        VAZIRMATN -> VazirmatnFontFamily
        B_NAZANIN -> BNazaninStyleFontFamily
        AMIRI -> AmiriNaskhFontFamily
        INTER -> InterFontFamily
        ROBOTO -> RobotoFontFamily
        JETBRAINS_MONO -> JetBrainsMonoFontFamily
    }

    companion object {
        fun fromId(id: String?): MedoFontChoice =
            entries.find { it.id == id } ?: VAZIRMATN
    }
}

fun createMedoTypography(
    scaleMultiplier: Float = 1.0f,
    lineSpacingMultiplier: Float = 1.0f,
    fontChoice: MedoFontChoice = MedoFontChoice.VAZIRMATN
): Typography {
    val activeFamily = fontChoice.toComposeFontFamily()
    fun scaledSp(base: Float) = (base * scaleMultiplier).sp
    fun scaledLineHeight(base: Float) = (base * scaleMultiplier * lineSpacingMultiplier).sp

    return Typography(
        displayLarge = TextStyle(
            fontFamily = activeFamily,
            fontWeight = FontWeight.Bold,
            fontSize = scaledSp(32f),
            lineHeight = scaledLineHeight(44f)
        ),
        headlineLarge = TextStyle(
            fontFamily = activeFamily,
            fontWeight = FontWeight.Bold,
            fontSize = scaledSp(24f),
            lineHeight = scaledLineHeight(36f)
        ),
        headlineMedium = TextStyle(
            fontFamily = activeFamily,
            fontWeight = FontWeight.Bold,
            fontSize = scaledSp(20f),
            lineHeight = scaledLineHeight(30f)
        ),
        titleLarge = TextStyle(
            fontFamily = activeFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = scaledSp(18f),
            lineHeight = scaledLineHeight(28f)
        ),
        titleMedium = TextStyle(
            fontFamily = activeFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = scaledSp(16f),
            lineHeight = scaledLineHeight(24f)
        ),
        bodyLarge = TextStyle(
            fontFamily = activeFamily,
            fontWeight = FontWeight.Normal,
            fontSize = scaledSp(16f),
            lineHeight = scaledLineHeight(27f)
        ),
        bodyMedium = TextStyle(
            fontFamily = activeFamily,
            fontWeight = FontWeight.Normal,
            fontSize = scaledSp(14f),
            lineHeight = scaledLineHeight(23f)
        ),
        labelLarge = TextStyle(
            fontFamily = activeFamily,
            fontWeight = FontWeight.Medium,
            fontSize = scaledSp(13f),
            lineHeight = scaledLineHeight(20f)
        ),
        labelMedium = TextStyle(
            fontFamily = activeFamily,
            fontWeight = FontWeight.Medium,
            fontSize = scaledSp(12f),
            lineHeight = scaledLineHeight(18f)
        )
    )
}

val Typography = createMedoTypography()
