#!/usr/bin/env python3
"""
Re-runnable asset generator for YMusic branding.
Extracts the foreground graphic from branding/logo-source.png using boundary flood fill,
scales content so its bounding circle strictly fits within the 66dp safe circle of a 108dp canvas,
and generates all Android, iOS, Desktop, and in-app icon assets.
"""

import os
import sys
import math
from collections import deque
import numpy as np
from PIL import Image, ImageDraw, ImageFilter
import cv2

def main():
    source_path = os.path.join("branding", "logo-source.png")
    if not os.path.isfile(source_path):
        print(f"Error: {source_path} not found.")
        sys.exit(1)

    print(f"Reading source logo: {source_path}")
    source_img = Image.open(source_path).convert("RGB")
    src_w, src_h = source_img.size
    arr = np.array(source_img, dtype=np.uint8)

    # 1. Sample background color at borders
    top_edge = arr[0, :, :]
    bg_r = int(np.median(top_edge[:, 0]))
    bg_g = int(np.median(top_edge[:, 1]))
    bg_b = int(np.median(top_edge[:, 2]))
    bg_hex = f"#{bg_r:02X}{bg_g:02X}{bg_b:02X}"
    print(f"Sampled background color: {bg_hex} (RGB {bg_r}, {bg_g}, {bg_b})")

    # 2. Border flood fill to remove outer white background without touching internal highlights
    print("Running border flood-fill...")
    visited = np.zeros((src_h, src_w), dtype=bool)
    q = deque()

    # Seed border pixels where sum >= 248 * 3
    for x in range(src_w):
        for y in [0, src_h - 1]:
            if int(arr[y, x, 0]) + int(arr[y, x, 1]) + int(arr[y, x, 2]) >= 248 * 3:
                visited[y, x] = True
                q.append((x, y))
    for y in range(src_h):
        for x in [0, src_w - 1]:
            if not visited[y, x] and int(arr[y, x, 0]) + int(arr[y, x, 1]) + int(arr[y, x, 2]) >= 248 * 3:
                visited[y, x] = True
                q.append((x, y))

    while q:
        cx, cy = q.popleft()
        for dx, dy in [(-1, 0), (1, 0), (0, -1), (0, 1)]:
            nx, ny = cx + dx, cy + dy
            if 0 <= nx < src_w and 0 <= ny < src_h and not visited[ny, nx]:
                if int(arr[ny, nx, 0]) + int(arr[ny, nx, 1]) + int(arr[ny, nx, 2]) >= 245 * 3:
                    visited[ny, nx] = True
                    q.append((nx, ny))

    # Binary mask: 0 = background, 255 = foreground artwork
    fg_mask_arr = np.where(visited, 0, 255).astype(np.uint8)
    fg_mask = Image.fromarray(fg_mask_arr, mode="L")
    # Soft edge feathering (1.2px gaussian blur)
    feathered_mask = fg_mask.filter(ImageFilter.GaussianBlur(radius=1.2))

    # Cutout RGBA image
    cutout = Image.new("RGBA", (src_w, src_h), (0, 0, 0, 0))
    cutout.paste(source_img, (0, 0))
    cutout.putalpha(feathered_mask)

    # 3. Calculate bounding circle
    fg_y, fg_x = np.where(fg_mask_arr > 0)
    min_x, max_x = int(np.min(fg_x)), int(np.max(fg_x))
    min_y, max_y = int(np.min(fg_y)), int(np.max(fg_y))
    bbox_w = max_x - min_x + 1
    bbox_h = max_y - min_y + 1
    center_x = (min_x + max_x) / 2.0
    center_y = (min_y + max_y) / 2.0

    distances = np.sqrt((fg_x - center_x) ** 2 + (fg_y - center_y) ** 2)
    max_radius = float(np.max(distances))
    bounding_circle_diam = max_radius * 2.0

    print(f"Content Bounding Box: {bbox_w}x{bbox_h} at ({min_x}, {min_y})")
    print(f"Content Center: ({center_x:.1f}, {center_y:.1f})")
    print(f"Bounding Circle Diameter: {bounding_circle_diam:.1f}px")

    # Crop tightly to the content bounding box
    tight_cutout = cutout.crop((min_x, min_y, max_x + 1, max_y + 1))

    # 4. Composite verification on light and dark surfaces
    test_dark = Image.new("RGBA", (src_w, src_h), (18, 19, 22, 255))
    test_dark_comp = Image.alpha_composite(test_dark, cutout)
    test_light = Image.new("RGBA", (src_w, src_h), (245, 245, 245, 255))
    test_light_comp = Image.alpha_composite(test_light, cutout)
    os.makedirs(os.path.join("branding", "test"), exist_ok=True)
    test_dark_comp.save(os.path.join("branding", "test", "composite_dark.png"))
    test_light_comp.save(os.path.join("branding", "test", "composite_light.png"))
    print("Verification: saved composite_dark.png and composite_light.png")

    # Helper: place tight cutout centered on a canvas of target_size,
    # ensuring bounding circle fits inside safe_diameter.
    def make_icon_content(target_size, safe_fraction=66.0 / 108.0):
        canvas = Image.new("RGBA", (target_size, target_size), (0, 0, 0, 0))
        max_allowed_diam = target_size * safe_fraction
        scale = max_allowed_diam / bounding_circle_diam
        new_w = max(1, int(round(bbox_w * scale)))
        new_h = max(1, int(round(bbox_h * scale)))
        scaled_img = tight_cutout.resize((new_w, new_h), Image.Resampling.LANCZOS)
        pos_x = (target_size - new_w) // 2
        pos_y = (target_size - new_h) // 2
        canvas.paste(scaled_img, (pos_x, pos_y), scaled_img)
        return canvas

    # 5. Android Adaptive Icons (Foreground & Background)
    # Background XML
    bg_xml_path = os.path.join("androidApp", "src", "main", "res", "values", "ic_launcher_background.xml")
    with open(bg_xml_path, "w", encoding="utf-8") as f:
        f.write(f"""<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="ic_launcher_background">{bg_hex}</color>
</resources>
""")
    print(f"Updated {bg_xml_path} with background {bg_hex}")

    # Adaptive Foreground PNGs (108dp canvas, 66dp safe circle)
    densities_adaptive = {
        "mipmap-mdpi": 108,
        "mipmap-hdpi": 162,
        "mipmap-xhdpi": 216,
        "mipmap-xxhdpi": 324,
        "mipmap-xxxhdpi": 432,
    }
    for folder, size in densities_adaptive.items():
        out_dir = os.path.join("androidApp", "src", "main", "res", folder)
        os.makedirs(out_dir, exist_ok=True)
        fg_icon = make_icon_content(size, safe_fraction=66.0 / 108.0)
        fg_path = os.path.join(out_dir, "ic_launcher_foreground.png")
        fg_icon.save(fg_path)

    # 6. Android Legacy Mipmaps (Square & Round)
    densities_legacy = {
        "mipmap-mdpi": 48,
        "mipmap-hdpi": 72,
        "mipmap-xhdpi": 96,
        "mipmap-xxhdpi": 144,
        "mipmap-xxxhdpi": 192,
    }
    for folder, size in densities_legacy.items():
        out_dir = os.path.join("androidApp", "src", "main", "res", folder)
        os.makedirs(out_dir, exist_ok=True)

        # Square legacy with rounded rect card
        sq_card = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        draw_sq = ImageDraw.Draw(sq_card)
        corner_r = int(round(size * 0.18))
        draw_sq.rounded_rectangle([(1, 1), (size - 2, size - 2)], radius=corner_r, fill=(bg_r, bg_g, bg_b, 255))
        content_sq = make_icon_content(size, safe_fraction=0.72)
        sq_final = Image.alpha_composite(sq_card, content_sq)
        sq_final.save(os.path.join(out_dir, "ic_launcher.png"))

        # Round legacy with circular card
        rd_card = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        draw_rd = ImageDraw.Draw(rd_card)
        draw_rd.ellipse([(1, 1), (size - 2, size - 2)], fill=(bg_r, bg_g, bg_b, 255))
        content_rd = make_icon_content(size, safe_fraction=0.68)
        rd_final = Image.alpha_composite(rd_card, content_rd)
        rd_final.save(os.path.join(out_dir, "ic_launcher_round.png"))

    # Update mipmap-anydpi-v26 XMLs
    anydpi_dir = os.path.join("androidApp", "src", "main", "res", "mipmap-anydpi-v26")
    os.makedirs(anydpi_dir, exist_ok=True)
    adaptive_xml = """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background"/>
    <foreground android:drawable="@mipmap/ic_launcher_foreground"/>
    <monochrome android:drawable="@drawable/ic_launcher_monochrome"/>
</adaptive-icon>
"""
    with open(os.path.join(anydpi_dir, "ic_launcher.xml"), "w", encoding="utf-8") as f:
        f.write(adaptive_xml)
    with open(os.path.join(anydpi_dir, "ic_launcher_round.xml"), "w", encoding="utf-8") as f:
        f.write(adaptive_xml)

    # 7. Extract contours for vector assets (monochrome, splash, ymusic_logo)
    # Binary threshold on cutout
    gray_mask = (fg_mask_arr > 128).astype(np.uint8) * 255
    contours, _ = cv2.findContours(gray_mask, cv2.RETR_TREE, cv2.CHAIN_APPROX_SIMPLE)

    # Filter significant contours
    valid_contours = []
    for c in contours:
        if cv2.contourArea(c) > 200:
            approx = cv2.approxPolyDP(c, epsilon=2.0, closed=True)
            valid_contours.append(approx)

    print(f"Extracted {len(valid_contours)} vector paths for drawables.")

    # Generate Android Vector XML string
    def build_vector_xml(width_dp, height_dp, vp_w, vp_h, paths_data, fill_color="#002892", group_transform=None):
        xml_lines = [
            '<?xml version="1.0" encoding="utf-8"?>',
            f'<vector xmlns:android="http://schemas.android.com/apk/res/android"',
            f'    android:width="{width_dp}dp"',
            f'    android:height="{height_dp}dp"',
            f'    android:viewportWidth="{vp_w}"',
            f'    android:viewportHeight="{vp_h}">',
        ]
        if group_transform:
            tx = group_transform.get("tx", 0)
            ty = group_transform.get("ty", 0)
            sx = group_transform.get("sx", 1.0)
            sy = group_transform.get("sy", 1.0)
            xml_lines.append(f'    <group android:translateX="{tx}" android:translateY="{ty}" android:scaleX="{sx}" android:scaleY="{sy}">')

        indent = "        " if group_transform else "    "
        for p in paths_data:
            xml_lines.append(f'{indent}<path')
            xml_lines.append(f'{indent}    android:fillColor="{fill_color}"')
            xml_lines.append(f'{indent}    android:pathData="{p}" />')

        if group_transform:
            xml_lines.append('    </group>')
        xml_lines.append('</vector>\n')
        return "\n".join(xml_lines)

    # Convert contour points to SVG path data (relative to bbox center)
    svg_paths = []
    for cnt in valid_contours:
        d = []
        for i, pt in enumerate(cnt):
            x = pt[0][0] - min_x
            y = pt[0][1] - min_y
            cmd = "M" if i == 0 else "L"
            d.append(f"{cmd}{x:.1f},{y:.1f}")
        d.append("Z")
        svg_paths.append(" ".join(d))

    # Adaptive monochrome: 108dp canvas, safe zone 66dp
    mono_vp = 1080
    mono_safe = 660
    mono_scale = mono_safe / bounding_circle_diam
    mono_tx = (mono_vp - (bbox_w * mono_scale)) / 2.0
    mono_ty = (mono_vp - (bbox_h * mono_scale)) / 2.0
    mono_xml = build_vector_xml(
        width_dp=108, height_dp=108, vp_w=mono_vp, vp_h=mono_vp,
        paths_data=svg_paths, fill_color="#FFFFFF",
        group_transform={"tx": mono_tx, "ty": mono_ty, "sx": mono_scale, "sy": mono_scale}
    )
    drawable_dir = os.path.join("androidApp", "src", "main", "res", "drawable")
    os.makedirs(drawable_dir, exist_ok=True)
    with open(os.path.join(drawable_dir, "ic_launcher_monochrome.xml"), "w", encoding="utf-8") as f:
        f.write(mono_xml)

    # Android 12 Splash Icon: 160dp canvas, safe zone 120dp
    splash_vp = 1600
    splash_scale = 1200 / bounding_circle_diam
    splash_tx = (splash_vp - (bbox_w * splash_scale)) / 2.0
    splash_ty = (splash_vp - (bbox_h * splash_scale)) / 2.0
    splash_xml = build_vector_xml(
        width_dp=160, height_dp=160, vp_w=splash_vp, vp_h=splash_vp,
        paths_data=svg_paths, fill_color="#002892",
        group_transform={"tx": splash_tx, "ty": splash_ty, "sx": splash_scale, "sy": splash_scale}
    )
    with open(os.path.join(drawable_dir, "splash_icon.xml"), "w", encoding="utf-8") as f:
        f.write(splash_xml)

    # Notification icon: clean white silhouette at 24dp
    notif_vp = 240
    notif_scale = 200 / bounding_circle_diam
    notif_tx = (notif_vp - (bbox_w * notif_scale)) / 2.0
    notif_ty = (notif_vp - (bbox_h * notif_scale)) / 2.0
    notif_xml = build_vector_xml(
        width_dp=24, height_dp=24, vp_w=notif_vp, vp_h=notif_vp,
        paths_data=svg_paths, fill_color="#FFFFFF",
        group_transform={"tx": notif_tx, "ty": notif_ty, "sx": notif_scale, "sy": notif_scale}
    )
    with open(os.path.join(drawable_dir, "ic_notification.xml"), "w", encoding="utf-8") as f:
        f.write(notif_xml)


    # In-app Compose logo: commonUi ymusic_logo.xml (192dp)
    logo_vp = 1024
    logo_scale = 880 / bounding_circle_diam
    logo_tx = (logo_vp - (bbox_w * logo_scale)) / 2.0
    logo_ty = (logo_vp - (bbox_h * logo_scale)) / 2.0
    logo_xml = build_vector_xml(
        width_dp=192, height_dp=192, vp_w=logo_vp, vp_h=logo_vp,
        paths_data=svg_paths, fill_color="#002892",
        group_transform={"tx": logo_tx, "ty": logo_ty, "sx": logo_scale, "sy": logo_scale}
    )
    common_drawable_dir = os.path.join("commonUi", "src", "commonMain", "composeResources", "drawable")
    with open(os.path.join(common_drawable_dir, "ymusic_logo.xml"), "w", encoding="utf-8") as f:
        f.write(logo_xml)

    # Desktop Dock Tile: desktopApp ymusic_logo_dock.xml
    desktop_drawable_dir = os.path.join("desktopApp", "src", "commonMain", "composeResources", "drawable")
    os.makedirs(desktop_drawable_dir, exist_ok=True)
    with open(os.path.join(desktop_drawable_dir, "ymusic_logo_dock.xml"), "w", encoding="utf-8") as f:
        f.write(logo_xml)

    # 8. iOS AppIcon: 1024x1024, mode RGB (NO ALPHA), background #FEFEFE
    ios_icon_dir = os.path.join("iosApp", "Assets.xcassets", "AppIcon.appiconset")
    os.makedirs(ios_icon_dir, exist_ok=True)
    ios_canvas = Image.new("RGB", (1024, 1024), (bg_r, bg_g, bg_b))
    ios_content = make_icon_content(1024, safe_fraction=0.75)
    ios_canvas.paste(ios_content, (0, 0), ios_content)
    ios_icon_path = os.path.join(ios_icon_dir, "AppIcon.png")
    ios_canvas.save(ios_icon_path)
    print(f"iOS AppIcon saved: {ios_icon_path} ({ios_canvas.size}, {ios_canvas.mode}, alpha={('A' in ios_canvas.mode)})")

    # iOS SVG twin
    ios_svg_path = os.path.join("iosApp", "AppIcon.svg")
    with open(ios_svg_path, "w", encoding="utf-8") as f:
        f.write(f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024" width="1024" height="1024">
  <rect width="1024" height="1024" fill="{bg_hex}"/>
  <g transform="translate({logo_tx:.1f}, {logo_ty:.1f}) scale({logo_scale:.4f})">
""")
        for p in svg_paths:
            f.write(f'    <path fill="#002892" d="{p}"/>\n')
        f.write("  </g>\n</svg>\n")

    # 9. Desktop assets: ymusic.png, ymusic.ico, ymusic.icns
    desktop_png = make_icon_content(512, safe_fraction=0.85)
    desktop_png.save(os.path.join("desktopApp", "ymusic.png"))

    desktop_ico_path = os.path.join("desktopApp", "ymusic.ico")
    desktop_png.save(
        desktop_ico_path,
        format="ICO",
        sizes=[(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)]
    )

    desktop_icns_path = os.path.join("desktopApp", "ymusic.icns")
    desktop_png.save(desktop_icns_path, format="ICNS")
    print(f"Desktop assets generated: ymusic.png, ymusic.ico, ymusic.icns")

    print("Asset generation complete!")

if __name__ == "__main__":
    main()
