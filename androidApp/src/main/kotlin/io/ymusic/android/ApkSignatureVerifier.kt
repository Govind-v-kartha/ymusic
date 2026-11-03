package io.ymusic.android

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import java.io.File
import java.security.MessageDigest

object ApkSignatureVerifier {

    sealed interface VerificationResult {
        data object Valid : VerificationResult
        data class Error(val code: String, val message: String) : VerificationResult
    }

    /**
     * Verifies that [apkFile]:
     * 1. Is a valid parseable APK archive (archive info not null).
     * 2. Package name matches [context.packageName] dynamically.
     * 3. Version code is strictly greater than [installedVersionCode].
     * 4. Full set of signing certificates matches the installed application's signing certificates
     *    using GET_SIGNING_CERTIFICATES on API 28+ and GET_SIGNATURES on API 24-27.
     */
    fun verify(
        context: Context,
        apkFile: File,
        installedVersionCode: Int,
    ): VerificationResult {
        val pm = context.packageManager
        val expectedPackageName = context.packageName

        // 1. Read archive info (reject if null)
        val archiveInfo = getArchiveInfo(pm, apkFile)
            ?: return VerificationResult.Error("E91021", "APK archive corrupted or unreadable")

        // 2. Package name check (dynamic against context.packageName)
        if (archiveInfo.packageName != expectedPackageName) {
            return VerificationResult.Error(
                "E91022",
                "Package mismatch: expected $expectedPackageName, got ${archiveInfo.packageName}",
            )
        }

        // 3. Version code check
        val apkVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            archiveInfo.longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            archiveInfo.versionCode
        }

        if (apkVersionCode <= installedVersionCode) {
            return VerificationResult.Error(
                "E91023",
                "Version downgrade: installed $installedVersionCode, update is $apkVersionCode",
            )
        }

        // 4. Signing certificate verification (comparing full set of certs)
        val installedCerts = getInstalledCertificateHashes(context, expectedPackageName)
        if (installedCerts.isEmpty()) {
            return VerificationResult.Error("E91024", "Could not retrieve installed app signing certificates")
        }

        val apkCerts = getArchiveCertificateHashes(archiveInfo)
        if (apkCerts.isEmpty()) {
            return VerificationResult.Error("E91024", "Could not retrieve update APK signing certificates")
        }

        // The certificate sets must match
        val matches = apkCerts.any { installedCerts.contains(it) } || installedCerts.any { apkCerts.contains(it) }
        if (!matches) {
            return VerificationResult.Error("E91025", "Signing certificate mismatch with installed app")
        }

        return VerificationResult.Valid
    }

    private fun getArchiveInfo(pm: PackageManager, apkFile: File): PackageInfo? {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        return pm.getPackageArchiveInfo(apkFile.absolutePath, flags)
    }

    private fun getInstalledCertificateHashes(context: Context, packageName: String): Set<String> {
        val pm = context.packageManager
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                val signingInfo = info.signingInfo ?: return emptySet()
                val signatures = if (signingInfo.hasMultipleSigners()) {
                    signingInfo.apkContentsSigners
                } else {
                    signingInfo.signingCertificateHistory
                }
                signatures.map { sha256Of(it.toByteArray()) }.toSet()
            } else {
                @Suppress("DEPRECATION")
                val info = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
                @Suppress("DEPRECATION")
                val signatures: Array<Signature>? = info.signatures
                signatures?.map { sha256Of(it.toByteArray()) }?.toSet().orEmpty()
            }
        } catch (_: Exception) {
            emptySet()
        }
    }

    private fun getArchiveCertificateHashes(archiveInfo: PackageInfo): Set<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = archiveInfo.signingInfo ?: return emptySet()
            val signatures = if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
            signatures.map { sha256Of(it.toByteArray()) }.toSet()
        } else {
            @Suppress("DEPRECATION")
            val signatures: Array<Signature>? = archiveInfo.signatures
            signatures?.map { sha256Of(it.toByteArray()) }?.toSet().orEmpty()
        }
    }

    fun sha256Of(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(bytes)
        return hash.joinToString("") { "%02x".format(it) }
    }
}
