#!/usr/bin/env python3
"""
tools/check_resources.py - Resource sanity checker for Android resources in YMusic.
Verifies:
a) Duplicate file base names in the same resource folder (e.g. png and xml).
b) Unresolved @drawable, @mipmap, @color, @string, @style references in res XML and AndroidManifest.xml.
c) Adaptive icon XMLs (mipmap-anydpi-v26): background, foreground, monochrome exist and valid.
d) AndroidManifest.xml references (icon, roundIcon, theme, splash).
"""

import os
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent

# Library-provided resources from imported AARs (e.g. Media3 UI)
KNOWN_LIBRARY_RESOURCES = {
    "drawable": {
        "media3_icon_play",
        "media3_icon_pause",
        "media3_icon_previous",
        "media3_icon_next",
        "media3_icon_rewind",
        "media3_icon_fast_forward",
        "media3_icon_stop",
    }
}

def find_res_dirs():
    res_dirs = []
    for root, dirs, files in os.walk(REPO_ROOT):
        parts = Path(root).parts
        if any(p.startswith(".") or p in ("build", "bin", "node_modules") for p in parts):
            continue
        if os.path.basename(root) == "res":
            parent = os.path.basename(os.path.dirname(root))
            if parent in ("main", "androidMain", "release", "debug"):
                res_dirs.append(Path(root))
    return sorted(res_dirs)

def collect_resources(res_dirs):
    resources = {
        "drawable": set(),
        "mipmap": set(),
        "color": set(),
        "string": set(),
        "style": set(),
        "dimen": set(),
        "attr": set(),
        "xml": set(),
        "raw": set(),
        "font": set(),
        "id": set(),
        "layout": set(),
    }

    # Add known library resources
    for rtype, items in KNOWN_LIBRARY_RESOURCES.items():
        resources[rtype].update(items)

    duplicates = []

    for res_dir in res_dirs:
        for entry in res_dir.iterdir():
            if not entry.is_dir():
                continue
            folder_type = entry.name.split("-")[0]

            if folder_type in ("drawable", "mipmap", "xml", "raw", "font", "layout", "color"):
                seen_in_dir = {}
                for f in sorted(entry.iterdir()):
                    if f.is_file():
                        stem = f.stem
                        resources[folder_type].add(stem)
                        if stem in seen_in_dir:
                            duplicates.append((entry / f.name, seen_in_dir[stem]))
                        else:
                            seen_in_dir[stem] = entry / f.name

            if folder_type == "values":
                for f in sorted(entry.glob("*.xml")):
                    try:
                        tree = ET.parse(f)
                        root = tree.getroot()
                        for child in root:
                            tag = child.tag
                            name = child.attrib.get("name")
                            if not name:
                                continue
                            if tag in resources:
                                resources[tag].add(name)
                            elif tag == "item":
                                itype = child.attrib.get("type")
                                if itype in resources:
                                    resources[itype].add(name)
                    except Exception as e:
                        print(f"Error parsing values file {f}: {e}")

    return resources, duplicates

def scan_xml_references(res_dirs, manifest_path, resources):
    unresolved = []
    # Pattern to match @type/name or @+type/name, allowing dots in style/theme names
    ref_pattern = re.compile(r'@(?:\+)?([a-zA-Z0-9_]+)/([a-zA-Z0-9_.]+)')

    xml_files = []
    for res_dir in res_dirs:
        xml_files.extend(res_dir.rglob("*.xml"))
    if manifest_path.is_file():
        xml_files.append(manifest_path)

    for xml_path in sorted(set(xml_files)):
        try:
            with open(xml_path, "r", encoding="utf-8", errors="ignore") as fp:
                content = fp.read()
        except Exception:
            continue

        for match in ref_pattern.finditer(content):
            ref_type, ref_name = match.group(1), match.group(2)
            if ref_type.startswith("android"):
                continue  # Built-in platform resource e.g. @android:color/...

            # Strip style qualifier prefix if present (e.g. ?attr or android:)
            if ":" in ref_type or ":" in ref_name:
                continue

            # Only check known asset/value resource types
            if ref_type in ("color", "drawable", "mipmap", "string", "style", "xml", "raw", "font", "dimen", "layout"):
                # Style hierarchy: foo.bar inherits foo
                if ref_type == "style":
                    # Check exact name or base style name
                    if ref_name not in resources["style"] and ref_name.split(".")[0] not in resources["style"]:
                        unresolved.append((xml_path, ref_type, ref_name))
                else:
                    if ref_name not in resources.get(ref_type, set()):
                        unresolved.append((xml_path, ref_type, ref_name))

    return unresolved

def check_adaptive_icons(res_dirs, resources):
    issues = []
    ns = "{http://schemas.android.com/apk/res/android}"
    for res_dir in res_dirs:
        anydpi = res_dir / "mipmap-anydpi-v26"
        if not anydpi.is_dir():
            continue
        for f in sorted(anydpi.glob("*.xml")):
            try:
                tree = ET.parse(f)
                root = tree.getroot()
                if root.tag != "adaptive-icon":
                    continue

                for layer in ("background", "foreground", "monochrome"):
                    el = root.find(layer)
                    if el is not None:
                        drawable = el.attrib.get(ns + "drawable", "")
                        if drawable.startswith("@"):
                            m = re.match(r'@([a-zA-Z0-9_]+)/([a-zA-Z0-9_.]+)', drawable)
                            if m:
                                dtype, dname = m.group(1), m.group(2)
                                if dname not in resources.get(dtype, set()):
                                    issues.append(f"{f.name}: {layer} '{drawable}' not found in resources")
                        elif not drawable:
                            # Child may contain inline element (e.g. <color>)
                            if len(el) == 0:
                                issues.append(f"{f.name}: {layer} has no drawable attribute or inline content")
                    elif layer in ("background", "foreground"):
                        issues.append(f"{f.name}: missing required <{layer}>")
            except Exception as e:
                issues.append(f"{f.name}: XML parse error: {e}")
    return issues

def check_manifest(manifest_path, resources):
    issues = []
    if not manifest_path.is_file():
        return [f"Manifest not found: {manifest_path}"]

    try:
        tree = ET.parse(manifest_path)
        root = tree.getroot()
        ns = "{http://schemas.android.com/apk/res/android}"

        app = root.find("application")
        if app is not None:
            for attr in ("icon", "roundIcon", "theme", "banner"):
                val = app.attrib.get(ns + attr)
                if val and val.startswith("@") and not val.startswith("@android:"):
                    m = re.match(r'@([a-zA-Z0-9_]+)/([a-zA-Z0-9_.]+)', val)
                    if m:
                        rtype, rname = m.group(1), m.group(2)
                        if rname not in resources.get(rtype, set()):
                            issues.append(f"Manifest <application> android:{attr}='{val}' not found")

            for tag in ("activity", "activity-alias", "service", "receiver"):
                for comp in app.findall(tag):
                    theme = comp.attrib.get(ns + "theme")
                    if theme and theme.startswith("@") and not theme.startswith("@android:"):
                        m = re.match(r'@([a-zA-Z0-9_]+)/([a-zA-Z0-9_.]+)', theme)
                        if m:
                            rtype, rname = m.group(1), m.group(2)
                            if rname not in resources.get(rtype, set()):
                                issues.append(f"Manifest <{tag}> android:theme='{theme}' not found")
                    icon = comp.attrib.get(ns + "icon")
                    if icon and icon.startswith("@") and not icon.startswith("@android:"):
                        m = re.match(r'@([a-zA-Z0-9_]+)/([a-zA-Z0-9_.]+)', icon)
                        if m:
                            rtype, rname = m.group(1), m.group(2)
                            if rname not in resources.get(rtype, set()):
                                issues.append(f"Manifest <{tag}> android:icon='{icon}' not found")
    except Exception as e:
        issues.append(f"Manifest parse error: {e}")

    return issues

def check_splash_and_themes(res_dirs, resources):
    issues = []
    ns = "{http://schemas.android.com/apk/res/android}"

    # Check splash_icon exists
    if "splash_icon" not in resources.get("drawable", set()):
        issues.append("splash_icon drawable not found in resources")

    # Check values-v31 windowSplashScreen attributes
    for res_dir in res_dirs:
        v31_themes = res_dir / "values-v31" / "themes.xml"
        if v31_themes.is_file():
            try:
                tree = ET.parse(v31_themes)
                root = tree.getroot()
                found_splash_bg = False
                found_splash_icon = False
                for style in root.findall("style"):
                    for item in style.findall("item"):
                        name = item.attrib.get("name", "")
                        val = item.text.strip() if item.text else ""
                        if name == "android:windowSplashScreenBackground":
                            found_splash_bg = True
                            m = re.match(r'@([a-zA-Z0-9_]+)/([a-zA-Z0-9_.]+)', val)
                            if m and m.group(2) not in resources.get(m.group(1), set()):
                                issues.append(f"values-v31/themes.xml: windowSplashScreenBackground '{val}' not found")
                        elif name == "android:windowSplashScreenAnimatedIcon":
                            found_splash_icon = True
                            m = re.match(r'@([a-zA-Z0-9_]+)/([a-zA-Z0-9_.]+)', val)
                            if m and m.group(2) not in resources.get(m.group(1), set()):
                                issues.append(f"values-v31/themes.xml: windowSplashScreenAnimatedIcon '{val}' not found")
                if not found_splash_bg:
                    issues.append("values-v31/themes.xml: missing windowSplashScreenBackground")
                if not found_splash_icon:
                    issues.append("values-v31/themes.xml: missing windowSplashScreenAnimatedIcon")
            except Exception as e:
                issues.append(f"values-v31/themes.xml parse error: {e}")

    return issues

def main():
    print("=" * 65)
    print("Android Resource Sanity & Integrity Check")
    print("=" * 65)

    res_dirs = find_res_dirs()
    manifest_path = REPO_ROOT / "androidApp" / "src" / "main" / "AndroidManifest.xml"

    print(f"Scanned {len(res_dirs)} resource directories:")
    for d in res_dirs:
        print(f"  - {d.relative_to(REPO_ROOT)}")

    resources, duplicates = collect_resources(res_dirs)
    print("\nResource definition counts:")
    for k in sorted(resources.keys()):
        print(f"  {k:10s}: {len(resources[k]):3d}")

    # a) Duplicates
    print("\n--- [A] Same-Folder Duplicate File Base Names ---")
    if duplicates:
        for f1, f2 in duplicates:
            print(f"  FAIL: Duplicate file base names:\n    {f1}\n    {f2}")
    else:
        print("  PASS: No duplicate file base names in any folder.")

    # b) Unresolved XML references
    print("\n--- [B] XML Resource Reference Resolution ---")
    unresolved = scan_xml_references(res_dirs, manifest_path, resources)
    if unresolved:
        print(f"  FAIL: Found {len(unresolved)} unresolved references:")
        for xml_path, rtype, rname in unresolved:
            rel = xml_path.relative_to(REPO_ROOT)
            print(f"    {rel}: @{rtype}/{rname}")
    else:
        print("  PASS: All @drawable, @mipmap, @color, @string, @style references resolved.")

    # c) Adaptive icons
    print("\n--- [C] Adaptive Icon Integrity (mipmap-anydpi-v26) ---")
    adaptive_issues = check_adaptive_icons(res_dirs, resources)
    if adaptive_issues:
        for issue in adaptive_issues:
            print(f"  FAIL: {issue}")
    else:
        print("  PASS: Adaptive icon background, foreground, monochrome exist and resolve.")

    # d) Manifest references
    print("\n--- [D] AndroidManifest.xml Resource References ---")
    manifest_issues = check_manifest(manifest_path, resources)
    if manifest_issues:
        for issue in manifest_issues:
            print(f"  FAIL: {issue}")
    else:
        print("  PASS: Manifest icon, roundIcon, and theme references resolve.")

    # e) Splash and themes
    print("\n--- [E] Splash Screen & Theme Integrity ---")
    splash_issues = check_splash_and_themes(res_dirs, resources)
    if splash_issues:
        for issue in splash_issues:
            print(f"  FAIL: {issue}")
    else:
        print("  PASS: Android 12 splash screen icon & background attributes verified.")

    print("\n" + "=" * 65)
    has_errors = bool(duplicates or unresolved or adaptive_issues or manifest_issues or splash_issues)
    if has_errors:
        print("STATUS: FAILED (issues detected)")
        return 1
    else:
        print("STATUS: PASSED (all checks clean)")
        return 0

if __name__ == "__main__":
    sys.exit(main())
