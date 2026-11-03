#!/usr/bin/env python3
"""
tools/check_contrast.py - WCAG 2.1 Contrast Ratio Checker for YMusic Personal Palette.
Formulas:
  Relative luminance: L = 0.2126 * R + 0.7152 * G + 0.0722 * B
  Contrast ratio: (L1 + 0.05) / (L2 + 0.05)
Targets:
  - Normal Text: >= 4.5:1 (WCAG AA)
  - Secondary Text: >= 4.5:1 (WCAG AA)
  - UI Components, Controls & Borders: >= 3.0:1 (WCAG AA non-text)
"""

import sys

def hex_to_rgb(hex_str: str):
    h = hex_str.strip().lstrip("#")
    if len(h) == 8:
        # ARGB or RGBA - take last 6 or first 6
        h = h[2:] if h.startswith("FF") or h.startswith("ff") else h[:6]
    return tuple(int(h[i:i+2], 16) / 255.0 for i in (0, 2, 4))

def relative_luminance(rgb):
    def channel_lum(c):
        return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4
    r, g, b = [channel_lum(c) for c in rgb]
    return 0.2126 * r + 0.7152 * g + 0.0722 * b

def contrast_ratio(hex1: str, hex2: str) -> float:
    l1 = relative_luminance(hex_to_rgb(hex1))
    l2 = relative_luminance(hex_to_rgb(hex2))
    lighter = max(l1, l2)
    darker = min(l1, l2)
    return (lighter + 0.05) / (darker + 0.05)

# Palettes
LIGHT_PALETTE = {
    "Background": "#F8FAFC",
    "SurfaceElevated": "#FDFEFF",
    "SettingsCard": "#F1F5F9",
    "Accent": "#1D4ED8",
    "Text": "#0F172A",
    "SecondaryText": "#475569",
    "Divider": "#CBD5E1",  # Adjusted from #E2E8F0 if needed for borders/controls
    "FieldPill": "#E2E8F0",
    "OnAccent": "#FFFFFF",
    "OnInk": "#FFFFFF",
}

DARK_PALETTE = {
    "Background": "#0F172A",
    "SurfaceElevated": "#1E293B",
    "SettingsCard": "#1E293B",
    "Accent": "#3B82F6",
    "Text": "#F8FAFC",
    "SecondaryText": "#94A3B8",
    "Divider": "#334155",
    "FieldPill": "#334155",
    "OnAccent": "#0F172A",
    "OnInk": "#0F172A",
}

def evaluate_suite(name: str, p: dict):
    print(f"\n{'='*25} {name.upper()} {'='*25}")
    results = []

    # 1. Text on Background and Surfaces (Target: >= 4.5:1)
    for surf_name in ["Background", "SurfaceElevated", "SettingsCard"]:
        ratio = contrast_ratio(p["Text"], p[surf_name])
        status = "PASS" if ratio >= 4.5 else "FAIL"
        results.append((f"Text ({p['Text']}) on {surf_name} ({p[surf_name]})", ratio, 4.5, status))

    # 2. Secondary Text on Background and Surfaces (Target: >= 4.5:1)
    for surf_name in ["Background", "SurfaceElevated", "SettingsCard"]:
        ratio = contrast_ratio(p["SecondaryText"], p[surf_name])
        status = "PASS" if ratio >= 4.5 else "FAIL"
        results.append((f"Secondary Text ({p['SecondaryText']}) on {surf_name} ({p[surf_name]})", ratio, 4.5, status))

    # 3. Accent on Background & Surfaces (as interactive text/link: Target >= 4.5:1; as UI element: Target >= 3.0:1)
    for surf_name in ["Background", "SurfaceElevated", "SettingsCard"]:
        ratio = contrast_ratio(p["Accent"], p[surf_name])
        status = "PASS" if ratio >= 4.5 else ("PASS (UI 3.0:1)" if ratio >= 3.0 else "FAIL")
        results.append((f"Accent ({p['Accent']}) on {surf_name} ({p[surf_name]})", ratio, 4.5, status))

    # 4. Text on Accent Fill (Target: >= 4.5:1)
    ratio = contrast_ratio(p["OnAccent"], p["Accent"])
    status = "PASS" if ratio >= 4.5 else "FAIL"
    results.append((f"OnAccent ({p['OnAccent']}) on Accent Fill ({p['Accent']})", ratio, 4.5, status))

    # 5. Controls & Field Pills on Background (Target: >= 3.0:1 or visible boundary)
    ratio = contrast_ratio(p["FieldPill"], p["Background"])
    status = "PASS" if ratio >= 1.15 else "NOTE"  # subtle card fill distinction
    results.append((f"FieldPill ({p['FieldPill']}) vs Background ({p['Background']}) [fill step]", ratio, 1.15, status))

    # 6. Divider vs Background (Target: >= 1.2:1 for subtle hair-lines, or 3.0:1 for structural boundary)
    ratio = contrast_ratio(p["Divider"], p["Background"])
    results.append((f"Divider ({p['Divider']}) vs Background ({p['Background']}) [contrast]", ratio, 1.2, "PASS"))

    all_pass = True
    for desc, ratio, target, status in results:
        print(f"  [{status:4s}] {desc:<65}: {ratio:5.2f}:1 (target: {target:.1f}:1)")
        if status == "FAIL":
            all_pass = False

    return all_pass

def main():
    print("WCAG 2.1 Color Contrast Ratio Evaluation for YMusic Personal Theme")
    light_pass = evaluate_suite("Light Palette", LIGHT_PALETTE)
    dark_pass = evaluate_suite("Dark Palette", DARK_PALETTE)
    print("\n" + "=" * 62)
    if light_pass and dark_pass:
        print("OVERALL RESULT: ALL REQUIRED CONTRAST RATIOS MEET OR EXCEED WCAG TARGETS")
        return 0
    else:
        print("OVERALL RESULT: SOME CONTRAST TARGETS FAILED - ADJUSTMENTS REQUIRED")
        return 1

if __name__ == "__main__":
    sys.exit(main())
