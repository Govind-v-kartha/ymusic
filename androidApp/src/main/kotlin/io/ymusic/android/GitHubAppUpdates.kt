package io.ymusic.android

import android.content.Context
import io.ymusic.ui.AppUpdateStatus
import io.ymusic.ui.AppUpdates
import io.ymusic.ui.UpdateCheck
import io.ymusic.ui.UpdateDownload
import io.ymusic.updater.GitHubUpdateApk
import io.ymusic.updater.GitHubUpdateLogic
import io.ymusic.updater.GitHubUpdateManifest
import kmpcommon.log.Logger
import kmphttp.KmpHttpClient
import kmphttp.RequestBuilder
import kmphttp.asyncSource
import kmphttp.toHttpUrlOrNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.Buffer
import okio.HashingSink
import okio.blackholeSink
import okio.buffer
import okio.sink
import okio.source
import okio.use
import org.jetbrains.compose.resources.StringResource
import projectcommon.error.DISK_FULL_CODE
import projectcommon.error.errorCode
import java.io.File

/**
 * Android in-app update service querying GitHub Releases for personal builds.
 *
 * Manifest URL: https://github.com/Govind-v-kartha/ymusic/releases/latest/download/update.json
 * APK URL: https://github.com/Govind-v-kartha/ymusic/releases/download/<tag>/<fileName>
 */
class GitHubAppUpdates(
    private val context: Context,
    private val client: KmpHttpClient,
    private val currentVersionCode: Int,
    private val scope: CoroutineScope,
    private val logger: Logger?,
) : AppUpdates {

    companion object {
        private const val BROWSER_FALLBACK_AFTER_MS = 60 * 60 * 1000L
    }

    private data class UpdateOffer(
        val manifest: GitHubUpdateManifest,
        val apk: GitHubUpdateApk,
        val downloadUrl: String,
    ) {
        val release: String get() = "${manifest.versionCode}-${apk.sha256}"
    }

    private val offer = MutableStateFlow<UpdateOffer?>(null)

    private class Tracked(val release: String, val download: UpdateDownload)
    private val tracked = MutableStateFlow<Tracked?>(null)
    private val lock = Any()

    private class Following(val release: String, val scope: CoroutineScope)

    @Volatile private var following: Following? = null

    override val status: StateFlow<AppUpdateStatus> = combine(offer, tracked) { update, tracked ->
        if (update == null) {
            AppUpdateStatus.None
        } else {
            offered(update, tracked?.takeIf { it.release == update.release }?.download ?: UpdateDownload.Idle)
        }
    }.stateIn(scope, SharingStarted.Eagerly, AppUpdateStatus.None)

    override val blockedReason: StringResource? = null

    private val downloadDir = File(context.filesDir, "update")

    @Volatile private var installAfterPermission = false
    private val log get() = logger?.fork("update")

    private fun offered(update: UpdateOffer, download: UpdateDownload) = AppUpdateStatus.Offered(
        versionCode = update.manifest.versionCode,
        versionName = update.manifest.versionName,
        notes = update.manifest.notes.joinToString("\n"),
        changelogUrl = "${GitHubUpdateLogic.RELEASES_WEB_URL}/tag/${update.manifest.tag}",
        installInApp = true,
        downloadPage = GitHubUpdateLogic.RELEASES_WEB_URL,
        download = download,
    )

    init {
        scope.launch {
            offer.map { it?.release }.distinctUntilChanged().collect { release ->
                if (release != null) follow(release)
            }
        }
    }

    fun pruneDownloads() {
        downloadDir.listFiles().orEmpty().forEach { file ->
            val vc = file.name.removePrefix("YMusic-").substringBefore('.').substringBefore('-').toIntOrNull()
            if (vc == null || vc <= currentVersionCode) file.delete()
        }
    }

    private suspend fun follow(release: String) = coroutineScope {
        val update = offer.value?.takeIf { it.release == release } ?: return@coroutineScope
        following = Following(release, this)
        val apk = apkFile(update)
        val downloaded = withContext(Dispatchers.IO) {
            apk.isFile && sha256Of(apk) == update.apk.sha256
        }
        set(release, if (downloaded) UpdateDownload.Ready else UpdateDownload.Idle)
        awaitCancellation()
    }

    private fun set(release: String, download: UpdateDownload) {
        synchronized(lock) {
            if (offer.value?.release == release) tracked.value = Tracked(release, download)
        }
    }

    private fun current(): Pair<UpdateOffer, UpdateDownload>? {
        val update = offer.value ?: return null
        return update to (tracked.value?.takeIf { it.release == update.release }?.download ?: UpdateDownload.Idle)
    }

    override fun download() {
        val (update, _) = current() ?: return
        if (canInstall()) start(update) else needInstallPermission(update, install = false)
    }

    private fun start(update: UpdateOffer) {
        val release = update.release
        val releaseScope = following?.takeIf { it.release == release }?.scope ?: return
        releaseScope.launch {
            val claimed = synchronized(lock) {
                val download = tracked.value?.takeIf { it.release == release }?.download
                val busy = download is UpdateDownload.Running || download is UpdateDownload.Ready ||
                    download is UpdateDownload.Installing
                if (offer.value?.release != release || busy) {
                    false
                } else {
                    tracked.value = Tracked(release, UpdateDownload.Running(0, update.apk.size))
                    true
                }
            }
            if (!claimed) return@launch
            try {
                withContext(Dispatchers.IO) { fetch(update) }
                // Post-download archive, package, version, and full certificate verification
                val verifyResult = withContext(Dispatchers.IO) {
                    ApkSignatureVerifier.verify(
                        context = context,
                        apkFile = apkFile(update),
                        installedVersionCode = currentVersionCode,
                    )
                }
                when (verifyResult) {
                    is ApkSignatureVerifier.VerificationResult.Valid -> {
                        set(release, UpdateDownload.Ready)
                    }
                    is ApkSignatureVerifier.VerificationResult.Error -> {
                        log?.captureError(Exception(verifyResult.message), "Signature verification failed: ${verifyResult.code}")
                        withContext(Dispatchers.IO) { apkFile(update).delete() }
                        set(release, UpdateDownload.Failed(verifyResult.code))
                    }
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                withContext(Dispatchers.IO) { partFile(update).delete() }
                if (error.errorCode() == DISK_FULL_CODE) {
                    log?.log(Logger.Level.WARN, "storage full during update download", error)
                    set(release, UpdateDownload.Failed(DISK_FULL_CODE))
                } else {
                    log?.captureError(error, "downloading update APK failed")
                    set(release, UpdateDownload.Failed("E91012"))
                }
            }
        }
    }

    private suspend fun fetch(update: UpdateOffer) {
        downloadDir.mkdirs()
        val part = partFile(update)
        downloadDir.listFiles().orEmpty().filter { it.name.endsWith(".part") && it != part }.forEach { it.delete() }

        // Download with size cap & SHA-256 verification
        downloadApkSafely(
            client = client,
            url = update.downloadUrl,
            part = part,
            expectedSize = update.apk.size,
            expectedSha256 = update.apk.sha256,
        ) { done ->
            set(update.release, UpdateDownload.Running(done, update.apk.size))
        }

        check(part.renameTo(apkFile(update))) { "could not move the APK into place" }
    }

    override fun installAndRestart() {
        val (update, download) = current() ?: return
        if (download !is UpdateDownload.Ready) return
        if (!canInstall()) return needInstallPermission(update, install = true)
        set(update.release, UpdateDownload.Installing)
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val apk = apkFile(update)
                    // Verify signature one last time before committing session
                    val verifyResult = ApkSignatureVerifier.verify(
                        context = context,
                        apkFile = apk,
                        installedVersionCode = currentVersionCode,
                    )
                    if (verifyResult is ApkSignatureVerifier.VerificationResult.Error) {
                        apk.delete()
                        throw IllegalStateException("Pre-install verify failed: ${verifyResult.message}")
                    }
                    PackageInstallerHelper.commit(context, apk)
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                log?.captureError(error, "installing update failed")
                set(update.release, UpdateDownload.Failed("E91013"))
            }
        }
    }

    fun onInstallEnded() {
        val (update, download) = current() ?: return
        if (download is UpdateDownload.Installing) set(update.release, UpdateDownload.Ready)
    }

    private fun needInstallPermission(update: UpdateOffer, install: Boolean) {
        installAfterPermission = install
        val apk = apkFile(update)
        val offerBrowser = !install || (apk.isFile && System.currentTimeMillis() - apk.lastModified() >= BROWSER_FALLBACK_AFTER_MS)
        set(update.release, UpdateDownload.NeedsInstallPermission(offerBrowser = offerBrowser))
    }

    override fun allowInstall() {
        PackageInstallerHelper.openInstallPermissionSettings(context)
    }

    fun onResume() {
        val (update, download) = current() ?: return
        if (download !is UpdateDownload.NeedsInstallPermission || !canInstall()) return
        if (installAfterPermission) {
            set(update.release, UpdateDownload.Ready)
            installAndRestart()
        } else {
            set(update.release, UpdateDownload.Idle)
            start(update)
        }
    }

    private fun canInstall(): Boolean = PackageInstallerHelper.canInstall(context)

    override suspend fun checkNow(): UpdateCheck {
        return try {
            val manifest = withContext(Dispatchers.IO) { fetchUpdateManifest(client) }
            val installedPackage = context.packageName
            if (manifest == null) {
                UpdateCheck.Failed
            } else if (GitHubUpdateLogic.isUpdateAvailable(currentVersionCode, manifest.versionCode, installedPackage, manifest.packageName)) {
                val apk = manifest.apks.firstOrNull { it.abi == "arm64-v8a" } ?: manifest.apks.firstOrNull()
                if (apk != null) {
                    val url = GitHubUpdateLogic.deriveApkDownloadUrl(manifest.tag, apk.fileName)
                    offer.value = UpdateOffer(manifest, apk, url)
                    UpdateCheck.Found
                } else {
                    UpdateCheck.UpToDate
                }
            } else {
                offer.value = null
                UpdateCheck.UpToDate
            }
        } catch (e: Exception) {
            log?.log(Logger.Level.WARN, "Update check failed", e)
            UpdateCheck.Failed
        }
    }

    private fun partFile(update: UpdateOffer) = File(downloadDir, "YMusic-${update.manifest.versionCode}-${update.apk.sha256.take(16)}.apk.part")
    private fun apkFile(update: UpdateOffer) = File(downloadDir, "YMusic-${update.manifest.versionCode}.apk")

    private fun sha256Of(file: File): String {
        val hashing = HashingSink.sha256(blackholeSink())
        hashing.buffer().use { out -> file.source().use { out.writeAll(it) } }
        return hashing.hash.hex()
    }
}

/** Pure helper to fetch and strictly parse update.json with 64KB cap and HTTPS enforcement. */
internal suspend fun fetchUpdateManifest(client: KmpHttpClient): GitHubUpdateManifest? {
    val req = RequestBuilder()
        .url(GitHubUpdateLogic.UPDATE_JSON_URL)
        .header("Cache-Control", "no-cache")
        .header("Accept", "application/json")
        .get()
        .build()

    val response = client.execute(req)
    try {
        if (!response.isSuccessful) return null
        val source = response.body.asyncSource()
        val buffer = Buffer()
        var totalRead = 0L
        while (true) {
            val read = source.read(buffer, 8192L)
            if (read == -1L) break
            totalRead += read
            if (totalRead > GitHubUpdateLogic.MAX_UPDATE_JSON_BYTES) {
                throw IllegalStateException("update.json exceeded 64 KB cap ($totalRead bytes)")
            }
        }
        val text = buffer.readUtf8()
        return GitHubUpdateLogic.parseManifest(text)
    } finally {
        response.close()
    }
}

/** Pure helper to download APK with size cap and exact host allowlist check. */
internal suspend fun downloadApkSafely(
    client: KmpHttpClient,
    url: String,
    part: File,
    expectedSize: Long,
    expectedSha256: String,
    onProgress: (Long) -> Unit,
) {
    var currentUrl = url
    var hops = 0
    val maxHops = 5

    while (hops < maxHops) {
        val parsedUri = currentUrl.toHttpUrlOrNull() ?: throw IllegalArgumentException("Malformed APK URL: $currentUrl")
        if (parsedUri.scheme != "https") {
            throw SecurityException("Insecure non-https download rejected: $currentUrl")
        }
        val host = parsedUri.host
        if (!GitHubUpdateLogic.isAllowedHost(host)) {
            throw SecurityException("Disallowed download host: $host")
        }

        val req = RequestBuilder()
            .url(currentUrl)
            .header("Cache-Control", "no-store")
            .get()
            .build()

        val resp = client.execute(req)
        try {
            if (resp.code in 300..399) {
                val location = resp.header("Location") ?: throw IllegalStateException("Redirect without Location header")
                val redirectUri = parsedUri.resolve(location) ?: throw IllegalStateException("Malformed redirect Location: $location")
                currentUrl = redirectUri.toString()
                hops++
                continue
            }
            if (!resp.isSuccessful) {
                throw IllegalStateException("HTTP ${resp.code} downloading APK")
            }

            // Stream body to file while computing SHA-256 and enforcing size limit (size + 5% margin)
            val maxAllowedBytes = expectedSize + (expectedSize / 20).coerceAtLeast(1024 * 1024L)
            val bodySource = resp.body.asyncSource()
            val hashing = HashingSink.sha256(part.sink())
            hashing.buffer().use { sink ->
                val chunk = Buffer()
                var done = 0L
                onProgress(0L)
                while (true) {
                    val read = bodySource.read(chunk, 64 * 1024L)
                    if (read == -1L) break
                    sink.write(chunk, read)
                    done += read
                    if (done > maxAllowedBytes) {
                        throw IllegalStateException("Downloaded bytes $done exceeded limit $maxAllowedBytes")
                    }
                    onProgress(done)
                }
                sink.flush()
            }

            val downloadedHash = hashing.hash.hex()
            if (!downloadedHash.equals(expectedSha256, ignoreCase = true)) {
                part.delete()
                throw IllegalStateException("SHA-256 mismatch: expected $expectedSha256, got $downloadedHash")
            }
            return
        } finally {
            resp.close()
        }
    }
    throw IllegalStateException("Too many redirects downloading APK")
}
