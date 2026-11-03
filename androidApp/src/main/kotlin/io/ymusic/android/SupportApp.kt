package io.ymusic.android

import android.content.Context
import android.content.pm.PackageManager

/**
 * The "Support Development" app, sold on Google Play: having it installed displays the premium badge.
 */
internal object SupportApp {
    const val PACKAGE = "com.kdev.ymusic.support"

    fun installed(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
}
