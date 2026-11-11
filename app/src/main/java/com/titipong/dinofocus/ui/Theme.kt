package com.titipong.dinofocus.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.titipong.dinofocus.R

/** Same color tokens as the web app (light + dark). */
data class Pal(
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val border: Color,
    val fg: Color,
    val muted: Color,
    val accent: Color,
    val accentGlow: Color,
    val green: Color = Color(0xFF22C55E),
    val red: Color = Color(0xFFEF4444),
    val yellow: Color,
    val dark: Boolean,
)

private val DarkPal = Pal(
    bg = Color(0xFF0F0F1A), surface = Color(0xFF1A1A2E), surface2 = Color(0xFF16213E),
    border = Color(0xFF2A2A4A), fg = Color(0xFFE8E8F0), muted = Color(0xFF8888AA),
    accent = Color(0xFF7C3AED), accentGlow = Color(0xFF9B5DE5), yellow = Color(0xFFF59E0B), dark = true,
)
private val LightPal = Pal(
    bg = Color(0xFFF5F3FF), surface = Color(0xFFFFFFFF), surface2 = Color(0xFFEDE9FE),
    border = Color(0xFFC4B5FD), fg = Color(0xFF1E1B4B), muted = Color(0xFF6B7280),
    accent = Color(0xFF7C3AED), accentGlow = Color(0xFF8B5CF6), yellow = Color(0xFFD97706), dark = false,
)

val LocalPal = staticCompositionLocalOf { LightPal }
val PixelFont = FontFamily(Font(R.font.press_start_2p))

/** True when the phone has animations switched off (Settings > Accessibility > Remove animations). */
val LocalReduceMotion = staticCompositionLocalOf { false }

val WORK_COLOR = Color(0xFF9B5DE5)
val SHORT_COLOR = Color(0xFF22C55E)

fun rarityColors(r: com.titipong.dinofocus.Rarity, p: Pal): Pair<Color, Color> = when (r) {
    com.titipong.dinofocus.Rarity.COMMON -> p.muted.copy(alpha = 0.2f) to p.muted
    com.titipong.dinofocus.Rarity.RARE -> Color(0xFF3B82F6).copy(alpha = 0.2f) to Color(0xFF60A5FA)
    com.titipong.dinofocus.Rarity.EPIC -> p.accent.copy(alpha = 0.2f) to p.accentGlow
    com.titipong.dinofocus.Rarity.LEGENDARY -> p.yellow.copy(alpha = 0.2f) to p.yellow
}

@Composable
fun DinoTheme(content: @Composable () -> Unit) {
    val pal = if (isSystemInDarkTheme()) DarkPal else LightPal
    val ctx = LocalContext.current
    val reduce = remember {
        runCatching {
            android.provider.Settings.Global.getFloat(ctx.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
    CompositionLocalProvider(LocalPal provides pal, LocalReduceMotion provides reduce, content = content)
}
