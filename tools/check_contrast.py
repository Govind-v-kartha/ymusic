#!/usr/bin/env python3
"""
WCAG 2.1 Contrast Ratio Verification for Personal Orange Palette.
"""

def lum(r, g, b):
    def channel(c):
        c = c / 255.0
        return c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4
    return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b)

def contrast(hex1, hex2):
    c1 = [int(hex1[i:i+2], 16) for i in (1, 3, 5)]
    c2 = [int(hex2[i:i+2], 16) for i in (1, 3, 5)]
    l1, l2 = lum(*c1), lum(*c2)
    return (max(l1, l2) + 0.05) / (min(l1, l2) + 0.05)

light = {
    "Background": "#FBF7F3",
    "Card": "#FFFDFB",
    "SettingsCard": "#F4EDE7",
    "Accent": "#C2410C",
    "Text": "#1F1712",
    "SecondaryText": "#6B5B50",
    "Divider": "#E6DAD0",
    "FieldOutline": "#D9C8BC",
    "OnAccent": "#FFFFFF",
    "Error": "#DC2626",
    "Success": "#16A34A",
    "Warning": "#D97706",
}

dark = {
    "Background": "#151210",
    "Card": "#221C18",
    "SettingsCard": "#2A221D",
    "Accent": "#F43F18",
    "AccentText": "#FB923C",
    "Text": "#FAF6F2",
    "SecondaryText": "#B7A99E",
    "Divider": "#3A302A",
    "FieldOutline": "#4A3E37",
    "OnAccent": "#151210",
    "Error": "#EF4444",
    "Success": "#22C55E",
    "Warning": "#F59E0B",
}

def main():
    print("=== LIGHT WARM ORANGE PALETTE ===")
    print(f"Text on Background:        {contrast(light['Text'], light['Background']):.2f}:1 (Target >= 4.5)")
    print(f"Text on Card:              {contrast(light['Text'], light['Card']):.2f}:1 (Target >= 4.5)")
    print(f"Secondary Text on BG:      {contrast(light['SecondaryText'], light['Background']):.2f}:1 (Target >= 4.5)")
    print(f"Secondary Text on Card:    {contrast(light['SecondaryText'], light['Card']):.2f}:1 (Target >= 4.5)")
    print(f"Accent on Background:      {contrast(light['Accent'], light['Background']):.2f}:1 (Target >= 4.5)")
    print(f"Accent on Card:            {contrast(light['Accent'], light['Card']):.2f}:1 (Target >= 4.5)")
    print(f"OnAccent on Accent:        {contrast(light['OnAccent'], light['Accent']):.2f}:1 (Target >= 4.5)")
    print(f"FieldOutline on BG:        {contrast(light['FieldOutline'], light['Background']):.2f}:1 (Border/Control)")
    print(f"Divider on BG:             {contrast(light['Divider'], light['Background']):.2f}:1")

    print("\n=== DARK WARM ORANGE PALETTE ===")
    print(f"Text on Background:        {contrast(dark['Text'], dark['Background']):.2f}:1 (Target >= 4.5)")
    print(f"Text on Card:              {contrast(dark['Text'], dark['Card']):.2f}:1 (Target >= 4.5)")
    print(f"Secondary Text on BG:      {contrast(dark['SecondaryText'], dark['Background']):.2f}:1 (Target >= 4.5)")
    print(f"Secondary Text on Card:    {contrast(dark['SecondaryText'], dark['Card']):.2f}:1 (Target >= 4.5)")
    print(f"Accent on Background:      {contrast(dark['Accent'], dark['Background']):.2f}:1 (Target >= 3.0 UI / 4.5)")
    print(f"Accent on Card:            {contrast(dark['Accent'], dark['Card']):.2f}:1 (Target >= 3.0 UI / 4.5)")
    print(f"AccentText on Card:        {contrast(dark['AccentText'], dark['Card']):.2f}:1 (Target >= 4.5 Text)")
    print(f"OnAccent (Dark) on Accent: {contrast(dark['OnAccent'], dark['Accent']):.2f}:1 (Target >= 4.5)")
    print(f"FieldOutline on BG:        {contrast(dark['FieldOutline'], dark['Background']):.2f}:1 (Border/Control)")
    print(f"Divider on BG:             {contrast(dark['Divider'], dark['Background']):.2f}:1")

if __name__ == "__main__":
    main()
