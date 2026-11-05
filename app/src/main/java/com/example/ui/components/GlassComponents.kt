package com.example.ui.components

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.GlassEffectMode
import com.example.ui.theme.LocalMedoSettings
import com.example.ui.theme.LocalMedoTheme
import com.example.util.PersianTextUtils

/**
 * Procedural soft clinical gradient background (per-theme, zero image assets).
 */
@Composable
fun MedoProceduralBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val tokens = LocalMedoTheme.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            tokens.gradientStart,
                            tokens.gradientMiddle,
                            tokens.gradientEnd
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(size.width, size.height)
                    )
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            tokens.accent.copy(alpha = if (tokens.isDark) 0.12f else 0.08f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.85f, size.height * 0.12f),
                        radius = size.minDimension * 0.65f
                    )
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            tokens.correct.copy(alpha = if (tokens.isDark) 0.08f else 0.06f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.15f, size.height * 0.85f),
                        radius = size.minDimension * 0.55f
                    )
                )
            },
        content = content
    )
}

/**
 * Translucent frosted GlassCard with hairline inner border and top specular highlight.
 * Uses `wrapContentHeight()` and adaptive padding so boxes always fit their text cleanly
 * without clipping or excessive empty space.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    isReadingSurface: Boolean = false,
    cornerRadius: Dp = 20.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val tokens = LocalMedoTheme.current
    val settings = LocalMedoSettings.current
    val glassEnabled = when (settings.glassEffectMode) {
        GlassEffectMode.ON -> true
        GlassEffectMode.OFF -> false
        GlassEffectMode.AUTO -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    }

    val baseAlpha = if (isReadingSurface) {
        tokens.readingSurfaceAlpha
    } else if (glassEnabled) {
        tokens.surfaceAlpha
    } else {
        (tokens.surfaceAlpha + 0.14f).coerceAtMost(0.96f)
    }

    val baseTint = if (isReadingSurface) tokens.readingSurfaceTint else tokens.surfaceTint
    val shape = RoundedCornerShape(cornerRadius)
    val borderColor = if (tokens.isDark) {
        Color.White.copy(alpha = tokens.borderAlpha)
    } else {
        tokens.accent.copy(alpha = tokens.borderAlpha)
    }
    val topHighlightColor = Color.White.copy(alpha = tokens.highlightAlpha)

    val cardModifier = modifier
        .wrapContentHeight()
        .clip(shape)
        .background(
            brush = Brush.verticalGradient(
                colors = listOf(
                    baseTint.copy(alpha = baseAlpha),
                    baseTint.copy(alpha = (baseAlpha + 0.04f).coerceAtMost(0.98f))
                )
            ),
            shape = shape
        )
        .border(BorderStroke(1.dp, borderColor), shape)
        .drawBehind {
            drawLine(
                color = topHighlightColor,
                start = Offset(cornerRadius.toPx() * 0.6f, 1.dp.toPx()),
                end = Offset(size.width - cornerRadius.toPx() * 0.6f, 1.dp.toPx()),
                strokeWidth = 1.5.dp.toPx()
            )
        }
        .let { mod ->
            if (onClick != null) {
                mod.clickable(onClick = onClick)
            } else {
                mod
            }
        }
        .padding(contentPadding)

    Box(modifier = cardModifier) {
        content()
    }
}

/**
 * Adaptive pill component that expands vertically (`heightIn(min = 36.dp)`) and supports up to 2 lines
 * so Persian labels never get clipped or squished at any font scale.
 */
@Composable
fun MedoPill(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = LocalMedoTheme.current.accent.copy(alpha = 0.16f),
    contentColor: Color = LocalMedoTheme.current.accent,
    onClick: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(14.dp)
    val mod = if (onClick != null) {
        modifier
            .minimumInteractiveComponentSize()
            .heightIn(min = 40.dp)
            .clip(shape)
            .clickable(onClick = onClick)
    } else {
        modifier
            .heightIn(min = 32.dp)
            .clip(shape)
    }
    Surface(
        modifier = mod,
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.3f))
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun MedoProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    strokeWidth: Dp = 4.5.dp,
    showPercentageText: Boolean = true
) {
    val tokens = LocalMedoTheme.current
    val settings = LocalMedoSettings.current
    val clamped = progress.coerceIn(0f, 1f)
    val pct = (clamped * 100).toInt()
    val effectiveSize = (size * settings.textSizePreset.scaleMultiplier.coerceIn(1f, 1.35f))

    Box(
        modifier = modifier
            .size(effectiveSize)
            .semantics { contentDescription = "پیشرفت $pct درصد" },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            drawArc(
                color = tokens.textSecondary.copy(alpha = 0.2f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = stroke
            )
            drawArc(
                color = if (clamped >= 0.8f) tokens.correct else tokens.accent,
                startAngle = -90f,
                sweepAngle = 360f * clamped,
                useCenter = false,
                style = stroke
            )
        }
        if (showPercentageText) {
            Text(
                text = "${PersianTextUtils.formatNumber(pct, settings.usePersianDigits)}٪",
                style = MaterialTheme.typography.labelSmall,
                color = tokens.textPrimary,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

enum class OptionVisualState {
    NEUTRAL,
    SELECTED,
    CORRECT,
    WRONG
}

/**
 * Accessible clinical option row:
 * - Letter badge (الف، ب، ج، د)
 * - Correct/wrong never relies on color alone (always displays ✓ Check or ✗ Close glyph)
 * - Optional community vote share bar & percentage
 * - Minimum 48dp touch target, flexible height that wraps long multi-line clinical options cleanly.
 */
@Composable
fun ClinicalOptionRow(
    index: Int,
    text: String,
    state: OptionVisualState,
    votePercent: Int? = null,
    showVotes: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalMedoTheme.current
    val settings = LocalMedoSettings.current

    val borderColor by animateColorAsState(
        targetValue = when (state) {
            OptionVisualState.CORRECT -> tokens.correct
            OptionVisualState.WRONG -> tokens.wrong
            OptionVisualState.SELECTED -> tokens.accent
            OptionVisualState.NEUTRAL -> tokens.textSecondary.copy(alpha = 0.28f)
        },
        animationSpec = tween(durationMillis = if (settings.reduceMotion) 0 else 180),
        label = "optionBorder"
    )

    val bgColor by animateColorAsState(
        targetValue = when (state) {
            OptionVisualState.CORRECT -> tokens.correct.copy(alpha = 0.16f)
            OptionVisualState.WRONG -> tokens.wrong.copy(alpha = 0.16f)
            OptionVisualState.SELECTED -> tokens.accent.copy(alpha = 0.16f)
            OptionVisualState.NEUTRAL -> tokens.readingSurfaceTint.copy(alpha = tokens.readingSurfaceAlpha)
        },
        animationSpec = tween(durationMillis = if (settings.reduceMotion) 0 else 180),
        label = "optionBg"
    )

    val badgeLetter = PersianTextUtils.optionBadgeLetter(index)
    val stateLabel = when (state) {
        OptionVisualState.CORRECT -> "پاسخ صحیح"
        OptionVisualState.WRONG -> "پاسخ نادرست"
        OptionVisualState.SELECTED -> "انتخاب شده"
        OptionVisualState.NEUTRAL -> "گزینه $badgeLetter"
    }

    val shape = RoundedCornerShape(18.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .defaultMinSize(minHeight = 52.dp)
            .clip(shape)
            .background(bgColor, shape)
            .border(BorderStroke(if (state == OptionVisualState.NEUTRAL) 1.dp else 2.dp, borderColor), shape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "$stateLabel: $text" }
            .testTag("option_row_$index")
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        when (state) {
                            OptionVisualState.CORRECT -> tokens.correct
                            OptionVisualState.WRONG -> tokens.wrong
                            OptionVisualState.SELECTED -> tokens.accent
                            OptionVisualState.NEUTRAL -> tokens.surfaceTint
                        }
                    )
                    .border(1.dp, borderColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                when (state) {
                    OptionVisualState.CORRECT -> Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "صحیح",
                        tint = if (tokens.isDark) Color(0xFF052E16) else Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    OptionVisualState.WRONG -> Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "غلط",
                        tint = if (tokens.isDark) Color(0xFF450A0A) else Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    else -> Text(
                        text = badgeLetter,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (state == OptionVisualState.SELECTED) tokens.accentOn else tokens.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = PersianTextUtils.formatDigits(text, settings.usePersianDigits),
                style = MaterialTheme.typography.bodyLarge,
                color = tokens.textPrimary,
                modifier = Modifier.weight(1f)
            )

            if (state == OptionVisualState.CORRECT || state == OptionVisualState.WRONG) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (state == OptionVisualState.CORRECT) "✓ صحیح" else "✗ نادرست",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (state == OptionVisualState.CORRECT) tokens.correct else tokens.wrong,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (showVotes && votePercent != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(tokens.textSecondary.copy(alpha = 0.15f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth((votePercent.coerceIn(0, 100)) / 100f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                when (state) {
                                    OptionVisualState.CORRECT -> tokens.correct
                                    OptionVisualState.WRONG -> tokens.wrong
                                    else -> tokens.accent
                                }
                            )
                    )
                }
                Text(
                    text = "${PersianTextUtils.formatNumber(votePercent, settings.usePersianDigits)}٪ پاسخ‌دهندگان",
                    style = MaterialTheme.typography.labelMedium,
                    color = tokens.textSecondary
                )
            }
        }
    }
}

/**
 * Adaptive primary button using `heightIn(min = 48.dp)` and `wrapContentHeight()`
 * so buttons grow dynamically to fit multi-word Persian text and 2.0x font scale.
 */
@Composable
fun MedoPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val tokens = LocalMedoTheme.current
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = tokens.accent,
            contentColor = tokens.accentOn
        ),
        modifier = modifier
            .heightIn(min = 48.dp)
            .wrapContentHeight()
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Adaptive secondary button using `heightIn(min = 48.dp)` and `wrapContentHeight()`
 * so buttons never clip or overflow their text.
 */
@Composable
fun MedoSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val tokens = LocalMedoTheme.current
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        border = BorderStroke(1.dp, tokens.accent.copy(alpha = 0.6f)),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = tokens.textPrimary
        ),
        modifier = modifier
            .heightIn(min = 48.dp)
            .wrapContentHeight()
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
    }
}
