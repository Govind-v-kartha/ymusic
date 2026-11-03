#!/usr/bin/env python3
"""
reproduce_control_patch.py - Validates and tests the Debian control patching logic
used in desktopApp/build.gradle.kts (addDebMetadata task).
Ensures:
1. Exactly one stanza (no internal blank lines).
2. Required fields present: Package, Version, Architecture, Maintainer, Description.
3. No field values are empty.
4. Maintainer and Homepage are cleanly updated or inserted.
5. Multi-line continuation lines (e.g. Description) are preserved identically.
6. File terminates with exactly one newline.
"""

import sys

def patch_debian_control(raw_control: str, maintainer: str, homepage: str) -> str:
    fields = []
    current_key = None
    current_val_lines = []

    for line in raw_control.splitlines():
        trimmed = line.rstrip('\r\n')
        if not trimmed:
            # Skip blank lines (must not produce stanzas)
            continue
        if trimmed.startswith(' ') or trimmed.startswith('\t'):
            if current_key is not None:
                current_val_lines.append(trimmed)
        elif ':' in trimmed:
            colon = trimmed.index(':')
            key = trimmed[:colon].strip()
            val = trimmed[colon + 1:].strip()
            if current_key is not None:
                fields.append((current_key, "\n".join(current_val_lines)))
            current_key = key
            current_val_lines = [val] if val else []

    if current_key is not None:
        fields.append((current_key, "\n".join(current_val_lines)))

    # Update or insert Maintainer & Homepage
    def upsert(k: str, v: str):
        for idx, (existing_k, _) in enumerate(fields):
            if existing_k.lower() == k.lower():
                fields[idx] = (existing_k, v)
                return
        fields.append((k, v))

    upsert("Maintainer", maintainer)
    upsert("Homepage", homepage)

    # Validate required fields
    required = ["Package", "Version", "Architecture", "Maintainer", "Description"]
    for req in required:
        matching = [f for f in fields if f[0].lower() == req.lower()]
        assert len(matching) == 1, f"Required field missing or duplicated: {req}"
        assert matching[0][1].strip(), f"Field '{req}' cannot be empty"

    out_lines = []
    for k, v in fields:
        sub_lines = v.split("\n")
        out_lines.append(f"{k}: {sub_lines[0]}")
        for continuation in sub_lines[1:]:
            out_lines.append(continuation)

    result = "\n".join(out_lines) + "\n"

    # Invariants verification
    assert "\n\n" not in result, "Control file must not contain multiple stanzas (blank lines detected)"
    assert result.endswith("\n") and not result.endswith("\n\n"), "Control file must end with single newline"

    return result


def main():
    sample_jpackage_control = """Package: ymusic-personal
Version: 4.0.0
Section: sound
Installed-Size: 123456
Priority: optional
Architecture: amd64
Maintainer: YMusic
Description: Music player
 A modern multiplatform music client.
 .
 Built with Compose Multiplatform.
"""

    sample_no_maintainer = """Package: ymusic-personal
Version: 4.0.0
Section: sound
Installed-Size: 123456
Architecture: amd64
Description: Music player
"""

    maintainer = "Govind-v-kartha <https://t.me/SethuramaIyer_CBI>"
    homepage = "https://github.com/Govind-v-kartha/ymusic"

    print("--- Test 1: jpackage output with existing Maintainer and multi-line Description ---")
    patched1 = patch_debian_control(sample_jpackage_control, maintainer, homepage)
    print(patched1)
    assert "Maintainer: Govind-v-kartha <https://t.me/SethuramaIyer_CBI>" in patched1
    assert "Homepage: https://github.com/Govind-v-kartha/ymusic" in patched1
    assert "Architecture: amd64" in patched1
    assert " .\\n" in patched1 or "\n .\n" in patched1
    print("Test 1 passed successfully.\n")

    print("--- Test 2: control without Maintainer or Homepage ---")
    patched2 = patch_debian_control(sample_no_maintainer, maintainer, homepage)
    print(patched2)
    assert "Maintainer: Govind-v-kartha <https://t.me/SethuramaIyer_CBI>" in patched2
    assert "Homepage: https://github.com/Govind-v-kartha/ymusic" in patched2
    print("Test 2 passed successfully.\n")

    print("--- Test 3: Old faulty logic demonstration ---")
    old_broken = sample_jpackage_control.strip()
    import re
    # The old regex replaced text on line but left \\n
    old_replaced = re.sub(r"(?m)^Maintainer:.*$", "", old_broken)
    old_result = old_replaced + f"\nMaintainer: {maintainer}\nHomepage: {homepage}\n"
    print("Old faulty output contains double newline:")
    has_double_newline = "\n\n" in old_result
    print(f"Has \\n\\n (multiple stanzas): {has_double_newline}")
    assert has_double_newline, "Old logic should produce double newline"
    print("Old fault successfully reproduced and explained.\n")

    print("All reproduction and validation tests passed!")

if __name__ == "__main__":
    main()

