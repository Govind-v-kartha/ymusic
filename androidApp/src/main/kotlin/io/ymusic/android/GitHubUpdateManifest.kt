package io.ymusic.android

import kotlinx.serialization.Serializable

@Serializable
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

@Serializable
data class GitHubUpdateApk(
    val fileName: String,
    val sha256: String,
    val size: Long,
    val abi: String,
)

