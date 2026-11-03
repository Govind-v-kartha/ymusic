package io.ymusic.personal

import androidx.compose.ui.graphics.Color
import io.ymusic.ui.DefaultAccent
import io.ymusic.ui.DesignPalette

/**
 * Personal Warm Orange design palette for YMusic.
 * Derived from the Coral Music Player Logo with warm neutral surfaces
 * and strict WCAG 2.1 AA contrast compliance across light and dark modes.
 */
val PersonalLightPalette = DesignPalette(
    isDark = false,
    Accent = Color(0xFFC2410C),
    InkStrong = Color(0xFF1F1712),
    Ink = Color(0xFF1F1712),
    InkMuted = Color(0xFF43362E),
    InkSoft = Color(0xFF6B5B50),
    InkSecondary = Color(0xFF6B5B50),
    InkFaint = Color(0xFF8C7B70),
    IconDots = Color(0xFF8C7B70),
    OnInk = Color.White,
    OnAccent = Color.White,
    Placeholder = Color(0xFFE6DAD0),
    PlaceholderDim = Color(0xFFF4EDE7),
    PlaceholderIcon = Color(0xFFB7A99E),
    StackNear = Color(0xFFE6DAD0),
    StackFar = Color(0xFFF4EDE7),
    StackTab = Color(0xFFE6DAD0),
    Outline = Color(0xFFD9C8BC),
    OutlineSoft = Color(0xFFE6DAD0),
    Divider = Color(0xFFE6DAD0),
    DividerMini = Color(0xFFE6DAD0),
    Surface = Color(0xFFFBF7F3),
    SurfaceDim = Color(0xFFF4EDE7),
    SurfaceElevated = Color(0xFFFFFDFB),
    SettingsCard = Color(0xFFF4EDE7),
    FieldPill = Color(0xFFE6DAD0),
    BrandGreen = Color(0xFFC2410C),
)

val PersonalDarkPalette = DesignPalette(
    isDark = true,
    Accent = Color(0xFFF43F18),
    InkStrong = Color(0xFFFAF6F2),
    Ink = Color(0xFFFAF6F2),
    InkMuted = Color(0xFFE6DAD0),
    InkSoft = Color(0xFFD9C8BC),
    InkSecondary = Color(0xFFB7A99E),
    InkFaint = Color(0xFF8C7B70),
    IconDots = Color(0xFFB7A99E),
    OnInk = Color(0xFF151210),
    OnAccent = Color(0xFF151210),
    Placeholder = Color(0xFF221C18),
    PlaceholderDim = Color(0xFF1B1613),
    PlaceholderIcon = Color(0xFF8C7B70),
    StackNear = Color(0xFF221C18),
    StackFar = Color(0xFF1B1613),
    StackTab = Color(0xFF221C18),
    Outline = Color(0xFF4A3E37),
    OutlineSoft = Color(0xFF332924),
    Divider = Color(0xFF3A302A),
    DividerMini = Color(0xFF2E2520),
    Surface = Color(0xFF151210),
    SurfaceDim = Color(0xFF1A1512),
    SurfaceElevated = Color(0xFF221C18),
    SettingsCard = Color(0xFF2A221D),
    FieldPill = Color(0xFF3A302A),
    BrandGreen = Color(0xFFF43F18),
)

val PersonalAmoledPalette = PersonalDarkPalette.copy(
    Surface = Color.Black,
    SurfaceDim = Color.Black,
    SurfaceElevated = Color.Black,
    SettingsCard = Color.Black,
)

/**
 * Transforms any base [DesignPalette] into the personal Warm Orange palette,
 * preserving dark/light/amoled mode selection and custom accents.
 */
fun personalDesignPalette(base: DesignPalette): DesignPalette {
    val template = when {
        base.Surface == Color.Black -> PersonalAmoledPalette
        base.isDark -> PersonalDarkPalette
        else -> PersonalLightPalette
    }
    return if (base.Accent == DefaultAccent) template else template.copy(Accent = base.Accent)
}
