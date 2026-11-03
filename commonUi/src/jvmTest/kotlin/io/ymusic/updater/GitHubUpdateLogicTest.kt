package io.ymusic.updater

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GitHubUpdateLogicTest {

    @Test
    fun parseValidManifest() {
        val raw = """
            {
              "schema": 1,
              "tag": "v4.0.0-personal.8",
              "packageName": "io.ymusic.android.personal",
              "versionCode": 100050,
              "versionName": "4.0.0-personal.8",
              "publishedAt": "2026-11-03T12:00:00Z",
              "changelogId": 4,
              "notes": [
                "Feature 1",
                "Feature 2"
              ],
              "apks": [
                {
                  "fileName": "YMusic-4.0.0-personal.8-arm64-v8a-release.apk",
                  "sha256": "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789",
                  "size": 15000000,
                  "abi": "arm64-v8a"
                }
              ]
            }
        """.trimIndent()

        val manifest = GitHubUpdateLogic.parseManifest(raw)
        assertEquals("v4.0.0-personal.8", manifest.tag)
        assertEquals("io.ymusic.android.personal", manifest.packageName)
        assertEquals(100050, manifest.versionCode)
        assertEquals(1, manifest.apks.size)
        assertEquals("arm64-v8a", manifest.apks[0].abi)
    }

    @Test
    fun parseManifestWithUnknownFieldsIgnored() {
        val raw = """
            {
              "schema": 1,
              "tag": "v4.0.0-personal.8",
              "packageName": "io.ymusic.android.personal",
              "versionCode": 100050,
              "versionName": "4.0.0-personal.8",
              "futureFeatureFlag": true,
              "unknownMap": { "foo": "bar" },
              "apks": []
            }
        """.trimIndent()

        val manifest = GitHubUpdateLogic.parseManifest(raw)
        assertEquals("v4.0.0-personal.8", manifest.tag)
        assertEquals(100050, manifest.versionCode)
    }

    @Test
    fun rejectOversizedManifest() {
        val padding = "x".repeat(65 * 1024)
        val raw = """{"tag":"v1","packageName":"io.ymusic.android.personal","versionCode":1,"versionName":"1","padding":"$padding"}"""
        assertFailsWith<IllegalArgumentException> {
            GitHubUpdateLogic.parseManifest(raw)
        }
    }

    @Test
    fun rejectMalformedJson() {
        val raw = """{"tag":"v1","versionCode":"NOT_AN_INT"}"""
        assertFailsWith<Exception> {
            GitHubUpdateLogic.parseManifest(raw)
        }
    }

    @Test
    fun hostAllowlistExactMatching() {
        assertTrue(GitHubUpdateLogic.isAllowedHost("github.com"))
        assertTrue(GitHubUpdateLogic.isAllowedHost("objects.githubusercontent.com"))
        assertTrue(GitHubUpdateLogic.isAllowedHost("release-assets.githubusercontent.com"))
        assertTrue(GitHubUpdateLogic.isAllowedHost("github-releases.githubusercontent.com"))

        // Rejections (exact matching, no wildcards)
        assertFalse(GitHubUpdateLogic.isAllowedHost("evil.com"))
        assertFalse(GitHubUpdateLogic.isAllowedHost("github.com.attacker.com"))
        assertFalse(GitHubUpdateLogic.isAllowedHost("subdomain.github.com"))
        assertFalse(GitHubUpdateLogic.isAllowedHost("notgithubusercontent.com"))
    }

    @Test
    fun versionAndPackageComparisonRule() {
        val installedPkg = "io.ymusic.android.personal"
        val installedVc = 100018

        // Higher version, matching package -> available
        assertTrue(GitHubUpdateLogic.isUpdateAvailable(installedVc, 100019, installedPkg, installedPkg))

        // Same or lower version -> not available
        assertFalse(GitHubUpdateLogic.isUpdateAvailable(installedVc, 100018, installedPkg, installedPkg))
        assertFalse(GitHubUpdateLogic.isUpdateAvailable(installedVc, 100017, installedPkg, installedPkg))

        // Different package name -> not available
        assertFalse(GitHubUpdateLogic.isUpdateAvailable(installedVc, 100019, installedPkg, "io.ymusic.android"))
    }

    @Test
    fun urlDerivation() {
        val url = GitHubUpdateLogic.deriveApkDownloadUrl("v4.0.0-personal.8", "YMusic-app.apk")
        assertEquals("https://github.com/Govind-v-kartha/ymusic/releases/download/v4.0.0-personal.8/YMusic-app.apk", url)
    }

    @Test
    fun sha256Verification() {
        val content = "Hello YMusic Updater".encodeToByteArray()
        // SHA-256 for "Hello YMusic Updater"
        val expected = "427ab4ad05d96026cf1abdba9039f84ca8e31f0d5f3ee97aa8940e0ebe92d3e7"
        assertTrue(GitHubUpdateLogic.verifySha256(content, expected))
        assertFalse(GitHubUpdateLogic.verifySha256(content, "0000000000000000000000000000000000000000000000000000000000000000"))
    }
}
