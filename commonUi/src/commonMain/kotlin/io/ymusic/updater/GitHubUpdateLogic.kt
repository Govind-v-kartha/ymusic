package io.ymusic.updater

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okio.Buffer
import okio.HashingSink
import okio.blackholeSink
import okio.buffer

data class GitHubUpdateManifest(
    val schema: Int = 1,
    val tag: String,
    val packageName: String,
    val versionCode: Int,
    val versionName: String,
    val publishedAt: String? = null,
    val changelogId: Int? = null,
    val notes: List<String> = emptyList(),
    val apks: List<GitHubUpdateApk> = emptyList(),
)

data class GitHubUpdateApk(
    val fileName: String,
    val sha256: String,
    val size: Long,
    val abi: String,
)

object GitHubUpdateLogic {
    const val UPDATE_JSON_URL = "https://github.com/Govind-v-kartha/ymusic/releases/latest/download/update.json"
    const val RELEASES_WEB_URL = "https://github.com/Govind-v-kartha/ymusic/releases"
    const val MAX_UPDATE_JSON_BYTES = 64 * 1024L // 64 KB cap

    // Exact allowed hosts (all https only, no wildcards)
    val ALLOWED_HOSTS = setOf(
        "github.com",
        "objects.githubusercontent.com",
        "release-assets.githubusercontent.com",
        "github-releases.githubusercontent.com",
    )

    fun isAllowedHost(host: String): Boolean =
        ALLOWED_HOSTS.contains(host.lowercase())

    fun deriveApkDownloadUrl(tag: String, fileName: String): String =
        "https://github.com/Govind-v-kartha/ymusic/releases/download/$tag/$fileName"

    fun isUpdateAvailable(
        installedVersionCode: Int,
        manifestVersionCode: Int,
        installedPackageName: String,
        manifestPackageName: String,
    ): Boolean =
        manifestPackageName == installedPackageName && manifestVersionCode > installedVersionCode

    fun parseManifest(rawJson: String, maxBytes: Long = MAX_UPDATE_JSON_BYTES): GitHubUpdateManifest {
        val byteLength = rawJson.encodeToByteArray().size
        if (byteLength > maxBytes) {
            throw IllegalArgumentException("update.json payload exceeded $maxBytes bytes (was $byteLength)")
        }

        val jsonElement = Json.parseToJsonElement(rawJson).jsonObject
        val schema = jsonElement["schema"]?.jsonPrimitive?.intOrNull ?: 1
        val tag = jsonElement["tag"]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalArgumentException("Missing tag in update.json")
        val packageName = jsonElement["packageName"]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalArgumentException("Missing packageName in update.json")
        val versionCode = jsonElement["versionCode"]?.jsonPrimitive?.intOrNull
            ?: throw IllegalArgumentException("Missing versionCode in update.json")
        val versionName = jsonElement["versionName"]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalArgumentException("Missing versionName in update.json")
        val publishedAt = jsonElement["publishedAt"]?.jsonPrimitive?.contentOrNull
        val changelogId = jsonElement["changelogId"]?.jsonPrimitive?.intOrNull

        val notes = jsonElement["notes"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()

        val apks = jsonElement["apks"]?.jsonArray?.mapNotNull { item ->
            val obj = item.jsonObject
            val fileName = obj["fileName"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val sha256 = obj["sha256"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val size = obj["size"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
            val abi = obj["abi"]?.jsonPrimitive?.contentOrNull ?: "universal"
            GitHubUpdateApk(fileName, sha256, size, abi)
        }.orEmpty()

        return GitHubUpdateManifest(
            schema = schema,
            tag = tag,
            packageName = packageName,
            versionCode = versionCode,
            versionName = versionName,
            publishedAt = publishedAt,
            changelogId = changelogId,
            notes = notes,
            apks = apks,
        )
    }

    fun verifySha256(content: ByteArray, expectedHex: String): Boolean {
        val buffer = Buffer()
        buffer.write(content)
        val hashing = HashingSink.sha256(blackholeSink())
        hashing.buffer().use { sink ->
            sink.writeAll(buffer)
            sink.flush()
        }
        return hashing.hash.hex().equals(expectedHex, ignoreCase = true)
    }
}
