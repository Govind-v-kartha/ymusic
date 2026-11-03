package io.ymusic.personal

import androidx.compose.ui.graphics.Color
import io.ymusic.ui.DefaultAccent
import io.ymusic.ui.DesignPalette

/**
 * Personal Royal Blue design palette for YMusic.
 * Derived from the royal blue logo with strict WCAG 2.1 AA contrast compliance.
 */
val PersonalLightPalette = DesignPalette(
    isDark = false,
    Accent = Color(0xFF1D4ED8),
    InkStrong = Color(0xFF0F172A),
    Ink = Color(0xFF0F172A),
    InkMuted = Color(0xFF334155),
    InkSoft = Color(0xFF475569),
    InkSecondary = Color(0xFF475569),
    InkFaint = Color(0xFF64748B),
    IconDots = Color(0xFF64748B),
    OnInk = Color.White,
    OnAccent = Color.White,
    Placeholder = Color(0xFFE2E8F0),
    PlaceholderDim = Color(0xFFF1F5F9),
    PlaceholderIcon = Color(0xFF94A3B8),
    StackNear = Color(0xFFE2E8F0),
    StackFar = Color(0xFFF1F5F9),
    StackTab = Color(0xFFE2E8F0),
    Outline = Color(0xFFCBD5E1),
    OutlineSoft = Color(0xFFE2E8F0),
    Divider = Color(0xFFE2E8F0),
    DividerMini = Color(0xFFE2E8F0),
    Surface = Color(0xFFF8FAFC),
    SurfaceDim = Color(0xFFF1F5F9),
    SurfaceElevated = Color(0xFFFDFEFF),
    SettingsCard = Color(0xFFF1F5F9),
    FieldPill = Color(0xFFE2E8F0),
    BrandGreen = Color(0xFF1D4ED8),
)

val PersonalDarkPalette = DesignPalette(
    isDark = true,
    Accent = Color(0xFF3B82F6),
    InkStrong = Color(0xFFF8FAFC),
    Ink = Color(0xFFF8FAFC),
    InkMuted = Color(0xFFE2E8F0),
    InkSoft = Color(0xFFCBD5E1),
    InkSecondary = Color(0xFF94A3B8),
    InkFaint = Color(0xFF64748B),
    IconDots = Color(0xFF94A3B8),
    OnInk = Color(0xFF0F172A),
    OnAccent = Color(0xFF0F172A),
    Placeholder = Color(0xFF1E293B),
    PlaceholderDim = Color(0xFF172033),
    PlaceholderIcon = Color(0xFF64748B),
    StackNear = Color(0xFF1E293B),
    StackFar = Color(0xFF182234),
    StackTab = Color(0xFF1E293B),
    Outline = Color(0xFF334155),
    OutlineSoft = Color(0xFF243247),
    Divider = Color(0xFF334155),
    DividerMini = Color(0xFF283548),
    Surface = Color(0xFF0F172A),
    SurfaceDim = Color(0xFF131D31),
    SurfaceElevated = Color(0xFF1E293B),
    SettingsCard = Color(0xFF1E293B),
    FieldPill = Color(0xFF334155),
    BrandGreen = Color(0xFF3B82F6),
)

val PersonalAmoledPalette = PersonalDarkPalette.copy(
    Surface = Color.Black,
    SurfaceDim = Color.Black,
    SurfaceElevated = Color.Black,
    SettingsCard = Color.Black,
)

/**
 * Transforms any base [DesignPalette] into the personal Royal Blue palette,
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

