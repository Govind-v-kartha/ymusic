#!/usr/bin/env python3
"""
Sanity verification for update.json parser, host allowlist, version comparison, and SHA-256.
"""

import hashlib
import json
import unittest
from urllib.parse import urlparse


def is_allowed_host(host: str) -> bool:
    host = host.lower()
    return (
        host == "github.com"
        or host == "objects.githubusercontent.com"
        or host.endswith(".github.com")
        or host.endswith(".githubusercontent.com")
    )


def is_update_available(current_version_code: int, manifest_version_code: int) -> bool:
    return manifest_version_code > current_version_code


def parse_manifest(raw_json: str, max_bytes: int = 64 * 1024) -> dict:
    if len(raw_json.encode("utf-8")) > max_bytes:
        raise ValueError(f"Payload exceeded {max_bytes} bytes")
    data = json.loads(raw_json)
    if not isinstance(data.get("versionCode"), int):
        raise ValueError("Invalid versionCode")
    if not isinstance(data.get("tag"), str):
        raise ValueError("Invalid tag")
    return data


def verify_sha256(content: bytes, expected_hash: str) -> bool:
    h = hashlib.sha256(content).hexdigest()
    return h.lower() == expected_hash.lower()


class TestUpdateVerification(unittest.TestCase):
    def test_parse_valid(self):
        raw = json.dumps({
            "schema": 1,
            "tag": "v4.0.0-personal.8",
            "packageName": "io.ymusic.android",
            "versionCode": 100050,
            "versionName": "4.0.0-personal.8",
            "notes": ["Feature 1", "Feature 2"],
            "apks": [{"fileName": "app.apk", "sha256": "abc", "size": 100, "abi": "arm64-v8a"}]
        })
        parsed = parse_manifest(raw)
        self.assertEqual(parsed["versionCode"], 100050)
        self.assertEqual(parsed["tag"], "v4.0.0-personal.8")

    def test_unknown_fields_tolerated(self):
        raw = json.dumps({
            "schema": 1,
            "tag": "v4.0.0-personal.8",
            "packageName": "io.ymusic.android",
            "versionCode": 100050,
            "futureKey": 12345
        })
        parsed = parse_manifest(raw)
        self.assertEqual(parsed["versionCode"], 100050)

    def test_oversized_rejected(self):
        huge_raw = json.dumps({"tag": "v1", "versionCode": 1, "padding": "x" * (65 * 1024)})
        with self.assertRaises(ValueError):
            parse_manifest(huge_raw)

    def test_malformed_rejected(self):
        raw = '{"tag": "v1", "versionCode": "NOT_AN_INT"}'
        with self.assertRaises(ValueError):
            parse_manifest(raw)

    def test_allowed_hosts(self):
        self.assertTrue(is_allowed_host("github.com"))
        self.assertTrue(is_allowed_host("objects.githubusercontent.com"))
        self.assertTrue(is_allowed_host("release-assets.github.com"))
        self.assertTrue(is_allowed_host("raw.githubusercontent.com"))

        self.assertFalse(is_allowed_host("evil.com"))
        self.assertFalse(is_allowed_host("github.com.attacker.com"))
        self.assertFalse(is_allowed_host("notgithubusercontent.com"))

    def test_version_comparison(self):
        installed = 100018
        self.assertTrue(is_update_available(installed, 100019))
        self.assertFalse(is_update_available(installed, 100018))
        self.assertFalse(is_update_available(installed, 100017))

    def test_sha256_verification(self):
        data = b"Hello YMusic Personal Updater"
        expected = hashlib.sha256(data).hexdigest()
        self.assertTrue(verify_sha256(data, expected))
        self.assertFalse(verify_sha256(data, "0000000000000000000000000000000000000000000000000000000000000000"))


if __name__ == "__main__":
    unittest.main()
