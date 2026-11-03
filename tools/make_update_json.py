#!/usr/bin/env python3
"""
Generate update.json metadata file for release assets.
Reads package name, versionCode, versionName, SHA-256 and size from APK(s)
and combines with changelog notes.
"""

import argparse
import hashlib
import json
import os
import re
import subprocess
import sys
from datetime import datetime, timezone
from typing import Dict, List, Optional, Tuple


def compute_sha256_and_size(file_path: str) -> Tuple[str, int]:
    """Compute hex SHA-256 and byte size of a file."""
    hasher = hashlib.sha256()
    size = 0
    with open(file_path, "rb") as f:
        while chunk := f.read(64 * 1024):
            hasher.update(chunk)
            size += len(chunk)
    return hasher.hexdigest(), size


def get_latest_changelog_notes(changelog_path: str) -> Tuple[Optional[int], List[str]]:
    """Read the newest entry's id and notes list from changelog.json."""
    if not os.path.exists(changelog_path):
        return None, []
    try:
        with open(changelog_path, "r", encoding="utf-8") as f:
            data = json.load(f)
            if isinstance(data, list) and len(data) > 0:
                first = data[0]
                return first.get("id"), first.get("notes", [])
    except Exception as e:
        print(f"Warning: Failed to read changelog from {changelog_path}: {e}", file=sys.stderr)
    return None, []


def extract_apk_badging(apk_path: str, aapt2_path: str = "aapt2") -> Tuple[str, int, str]:
    """
    Extract (package_name, version_code, version_name) from APK via aapt2 dump badging.
    Falls back to aapt if aapt2 fails or is not available.
    """
    for cmd_bin in [aapt2_path, "aapt2", "aapt"]:
        try:
            # aapt2 dump badging <apk>
            res = subprocess.run(
                [cmd_bin, "dump", "badging", apk_path],
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
                check=True,
            )
            out = res.stdout
            pkg_match = re.search(r"package:\s+name='([^']+)'", out)
            vc_match = re.search(r"versionCode='([^']+)'", out)
            vn_match = re.search(r"versionName='([^']+)'", out)
            if pkg_match and vc_match and vn_match:
                return pkg_match.group(1), int(vc_match.group(1)), vn_match.group(1)
        except Exception:
            continue

    raise RuntimeError(f"Could not extract badging from {apk_path} using aapt2/aapt")


def infer_abi_from_filename(filename: str) -> str:
    """Infer ABI architecture from APK filename or default to universal."""
    lower = filename.lower()
    if "arm64-v8a" in lower:
        return "arm64-v8a"
    if "armeabi-v7a" in lower:
        return "armeabi-v7a"
    if "x86_64" in lower:
        return "x86_64"
    if "x86" in lower:
        return "x86"
    return "universal"


def build_update_json(
    tag: str,
    apk_paths: List[str],
    changelog_path: Optional[str] = None,
    aapt2_path: str = "aapt2",
    override_pkg: Optional[str] = None,
    override_vc: Optional[int] = None,
    override_vn: Optional[str] = None,
    override_notes: Optional[List[str]] = None,
) -> Dict:
    """Build the dictionary representing update.json."""
    if not apk_paths:
        raise ValueError("At least one APK path is required")

    package_name = override_pkg
    version_code = override_vc
    version_name = override_vn

    apks_data = []

    for path in apk_paths:
        if not os.path.exists(path):
            raise FileNotFoundError(f"APK not found: {path}")

        file_name = os.path.basename(path)
        sha256, size = compute_sha256_and_size(path)
        abi = infer_abi_from_filename(file_name)

        if package_name is None or version_code is None or version_name is None:
            try:
                pkg, vc, vn = extract_apk_badging(path, aapt2_path=aapt2_path)
                if package_name is None:
                    package_name = pkg
                if version_code is None:
                    version_code = vc
                if version_name is None:
                    version_name = vn
            except Exception as e:
                if override_pkg and override_vc and override_vn:
                    package_name = override_pkg
                    version_code = override_vc
                    version_name = override_vn
                else:
                    raise RuntimeError(f"Failed to extract badging and no overrides provided: {e}")

        apks_data.append({
            "fileName": file_name,
            "sha256": sha256,
            "size": size,
            "abi": abi,
        })

    if override_notes is not None:
        notes = override_notes
        changelog_id = None
    elif changelog_path and os.path.exists(changelog_path):
        changelog_id, notes = get_latest_changelog_notes(changelog_path)
    else:
        changelog_id, notes = None, []

    published_at = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")

    return {
        "schema": 1,
        "tag": tag,
        "packageName": package_name or "io.ymusic.android",
        "versionCode": version_code or 0,
        "versionName": version_name or "1.0.0",
        "publishedAt": published_at,
        "changelogId": changelog_id,
        "notes": notes,
        "apks": apks_data,
    }


def main():
    parser = argparse.ArgumentParser(description="Generate update.json for GitHub Releases")
    parser.add_argument("--tag", required=True, help="Release git tag (e.g. v4.0.0-personal.8)")
    parser.add_argument("--apk", nargs="+", dest="apks", required=True, help="Path to APK file(s)")
    parser.add_argument("--changelog", help="Path to changelog.json")
    parser.add_argument("--output", default="update.json", help="Output path for update.json")
    parser.add_argument("--aapt2", default="aapt2", help="Path to aapt2 binary")
    parser.add_argument("--package-name", help="Override package name")
    parser.add_argument("--version-code", type=int, help="Override version code")
    parser.add_argument("--version-name", help="Override version name")

    args = parser.parse_args()

    data = build_update_json(
        tag=args.tag,
        apk_paths=args.apks,
        changelog_path=args.changelog,
        aapt2_path=args.aapt2,
        override_pkg=args.package_name,
        override_vc=args.version_code,
        override_vn=args.version_name,
    )

    with open(args.output, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2)

    print(f"Generated {args.output} successfully.")


if __name__ == "__main__":
    main()
