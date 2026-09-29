package com.aectann.battlecity.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import battlecity.shared.generated.resources.Res
import battlecity.shared.generated.resources.ironroost_pixel
import org.jetbrains.compose.resources.Font
import kotlin.math.floor

/**
 * The size of one pixel of the UI's pixel art, in dp.
 *
 * Frames, bevels, rivets and icons are all drawn on this grid, rounded down to whole device
 * pixels so an edge is never smeared across two. On a desktop browser at 1x that is 2 px, the
 * same scale the board's 16-pixel tiles land near at 720p.
 */
internal val PixelUnitDp = 2.dp

/** [PixelUnitDp] in whole device pixels, never less than one. */
internal fun Density.pixelUnit(): Float = floor(PixelUnitDp.toPx()).coerceAtLeast(1f)

/**
 * Languages the pixel font has no glyphs for. Their screens use the system font throughout
 * rather than a pixel Latin digit next to a system ideograph on every line. Only the store
 * builds ship them: the web build is English only. See docs/fonts/README.md.
 */
private val SystemFontLanguages = setOf("zh", "ja", "ko", "hi")

@Composable
internal fun rememberPixelFontFamily(): FontFamily {
    val regular = Font(Res.font.ironroost_pixel, FontWeight.Normal)
    val bold = Font(Res.font.ironroost_pixel, FontWeight.Bold)
    val language = Locale.current.language
    return remember(regular, bold, language) {
        if (language in SystemFontLanguages) FontFamily.Default else FontFamily(regular, bold)
    }
}

/**
 * The type scale. Ironroost Pixel draws a pixel of its own at about 0.092 em, so 11, 22, 33 and
 * 44 sp put one font pixel on one, two, three and four UI units' worth of screen and stay crisp;
 * body text at 16 sp is the one step off that grid, because 11 is too small to read and 22 too
 * large to set a paragraph in.
 */
@Immutable
internal class PixelTypography(val family: FontFamily) {
    val caption = style(11.sp, 14.sp)
    val body = style(16.sp, 21.sp)
    val label = style(16.sp, 20.sp)
    val heading = style(22.sp, 28.sp)
    val title = style(33.sp, 40.sp, FontWeight.Bold)
    val display = style(44.sp, 52.sp, FontWeight.Bold)

    private fun style(size: TextUnit, lineHeight: TextUnit, weight: FontWeight = FontWeight.Normal) =
        TextStyle(fontFamily = family, fontSize = size, lineHeight = lineHeight, fontWeight = weight, letterSpacing = 0.sp)

    /** Every Material slot mapped onto the scale, so a bare Text is already in the pixel font. */
    fun asMaterial() = Typography(
        displayLarge = display,
        displayMedium = display,
        displaySmall = title,
        headlineLarge = title,
        headlineMedium = heading,
        headlineSmall = heading,
        titleLarge = heading,
        titleMedium = label,
        titleSmall = label,
        bodyLarge = body,
        bodyMedium = body,
        bodySmall = caption,
        labelLarge = label,
        labelMedium = caption,
        labelSmall = caption
    )
}

/** Falls back to the system font, not a crash, for a host that skips [com.aectann.battlecity.TanksTheme]. */
internal val LocalPixelType = staticCompositionLocalOf { PixelTypography(FontFamily.Default) }

/** A hard one-unit drop shadow, the way pixel titles are lettered: no blur, straight down-right. */
@Composable
internal fun TextStyle.shadowed(color: Color = Ink): TextStyle {
    val unit = with(LocalDensity.current) { pixelUnit() }
    return copy(shadow = Shadow(color = color, offset = Offset(unit, unit), blurRadius = 0f))
}

/**
 * Whether the player is steering the menus with keys right now.
 *
 * Focus exists either way — Enter on the menu starts the focused entry — but the cursor and the
 * lit frame only show once a key has been used, and hide again at the next tap or click, so a
 * touch or mouse player never sees a selection they did not make.
 */
@Stable
internal class PixelInputMode {
    var keyboard by mutableStateOf(false)

    /**
     * A slider has focus and wants left and right for itself; the menu keys leave them alone.
     * A plain field: only key handlers read it.
     */
    var horizontalKeysTaken = false
}

internal val LocalPixelInputMode = staticCompositionLocalOf { PixelInputMode() }

/**
 * The clip player the whole app shares, so a menu button can click like a game button does.
 * Null outside [App]: a host that embeds only the game screen gets one owned by that screen.
 */
internal val LocalTanksSound = staticCompositionLocalOf<TanksSoundBank?> { null }

/**
 * Everything the pixel UI reads from above: the type scale — handed to Material too, so a bare
 * Text is already in the pixel font — the keyboard-cursor state, and the pointer watch that
 * hides the cursor again.
 */
@Composable
internal fun PixelTheme(colorScheme: ColorScheme, content: @Composable () -> Unit) {
    val family = rememberPixelFontFamily()
    val type = remember(family) { PixelTypography(family) }
    val inputMode = remember { PixelInputMode() }
    MaterialTheme(colorScheme = colorScheme, typography = type.asMaterial()) {
        CompositionLocalProvider(
            LocalPixelType provides type,
            LocalPixelInputMode provides inputMode
        ) {
            Box(Modifier.pixelPointerResetsKeyboard(inputMode)) { content() }
        }
    }
}
