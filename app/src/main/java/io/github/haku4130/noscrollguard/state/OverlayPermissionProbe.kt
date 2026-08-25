package io.github.haku4130.noscrollguard.state

import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import io.github.haku4130.noscrollguard.Constants

/**
 * Whether the guarded app may draw over other apps.
 *
 * Catching scroll events is only half the job — without the overlay permission the app
 * cannot put its blocking screen up, so it reports itself as fully active while nothing
 * happens. That failure is completely silent, which is what makes it worth watching.
 *
 * Reading another package's app-op needs GET_APP_OPS_STATS, which carries the
 * `development` flag and is granted over ADB like the others.
 *
 * Restoring it is not possible: that needs MANAGE_APP_OPS_MODES, which is
 * signature-only. Detection and a notification is all we can offer.
 */
object OverlayPermissionProbe {

    /** @return true if allowed, false if revoked, null if it could not be determined. */
    fun isAllowed(context: Context): Boolean? = try {
        val uid = context.packageManager
            .getPackageUid(Constants.NOSCROLL_PACKAGE, PackageManager.PackageInfoFlags.of(0))
        val appOps = context.getSystemService(AppOpsManager::class.java)
        when (appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, uid, Constants.NOSCROLL_PACKAGE)) {
            AppOpsManager.MODE_ALLOWED -> true
            AppOpsManager.MODE_IGNORED, AppOpsManager.MODE_ERRORED -> false
            else -> null
        }
    } catch (e: Exception) {
        null
    }
}
