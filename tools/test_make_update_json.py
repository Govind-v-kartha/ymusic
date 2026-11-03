#!/usr/bin/env python3
"""
Unit tests for make_update_json.py
"""

import json
import os
import tempfile
import unittest

from make_update_json import (
    build_update_json,
    compute_sha256_and_size,
    get_latest_changelog_notes,
    infer_abi_from_filename,
)


class TestMakeUpdateJson(unittest.TestCase):
    def test_infer_abi(self):
        self.assertEqual(infer_abi_from_filename("YMusic-1.0.0-arm64-v8a-release.apk"), "arm64-v8a")
        self.assertEqual(infer_abi_from_filename("YMusic-1.0.0-armeabi-v7a-release.apk"), "armeabi-v7a")
        self.assertEqual(infer_abi_from_filename("YMusic-1.0.0-x86_64-release.apk"), "x86_64")
        self.assertEqual(infer_abi_from_filename("YMusic-universal.apk"), "universal")

    def test_changelog_notes(self):
        with tempfile.NamedTemporaryFile("w+", delete=False, suffix=".json", encoding="utf-8") as f:
            json.dump([
                {"id": 4, "version": "Update", "notes": ["Note 1", "Note 2"]},
                {"id": 3, "version": "Old", "notes": ["Old Note"]}
            ], f)
            temp_path = f.name

        try:
            cid, notes = get_latest_changelog_notes(temp_path)
            self.assertEqual(cid, 4)
            self.assertEqual(notes, ["Note 1", "Note 2"])
        finally:
            os.remove(temp_path)

    def test_build_update_json_with_mock_apk(self):
        with tempfile.NamedTemporaryFile("wb", delete=False, suffix="-arm64-v8a-release.apk") as apk_f:
            apk_f.write(b"PK\x03\x04mock_apk_content_for_testing_1234567890")
            apk_path = apk_f.name

        with tempfile.NamedTemporaryFile("w+", delete=False, suffix=".json", encoding="utf-8") as cl_f:
            json.dump([{"id": 4, "notes": ["Added in-app updater"]}], cl_f)
            cl_path = cl_f.name

        try:
            res = build_update_json(
                tag="v4.0.0-personal.8",
                apk_paths=[apk_path],
                changelog_path=cl_path,
                override_pkg="io.ymusic.android",
                override_vc=100042,
                override_vn="4.0.0-personal.8",
            )

            self.assertEqual(res["schema"], 1)
            self.assertEqual(res["tag"], "v4.0.0-personal.8")
            self.assertEqual(res["packageName"], "io.ymusic.android")
            self.assertEqual(res["versionCode"], 100042)
            self.assertEqual(res["versionName"], "4.0.0-personal.8")
            self.assertEqual(res["changelogId"], 4)
            self.assertEqual(res["notes"], ["Added in-app updater"])
            self.assertEqual(len(res["apks"]), 1)

            apk_obj = res["apks"][0]
            self.assertEqual(apk_obj["abi"], "arm64-v8a")
            self.assertEqual(apk_obj["size"], len(b"PK\x03\x04mock_apk_content_for_testing_1234567890"))
            self.assertIn("sha256", apk_obj)
            self.assertEqual(len(apk_obj["sha256"]), 64)
        finally:
            os.remove(apk_path)
            os.remove(cl_path)


if __name__ == "__main__":
    unittest.main()
