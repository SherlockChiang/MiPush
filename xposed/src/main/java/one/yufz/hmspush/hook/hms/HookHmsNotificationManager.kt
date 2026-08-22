package one.yufz.hmspush.hook.hms

import android.app.Notification
import android.app.NotificationManager
import one.yufz.hmspush.common.HMS_PACKAGE_NAME
import one.yufz.hmspush.hook.XLog
import one.yufz.hmspush.hook.hms.nm.SystemNotificationManager
import one.yufz.xposed.hookAllMethods

/**
 * Keeps XMSF's own framework notifications from tripping the HyperOS package
 * attribution check.
 *
 * XMSF's BackgroundActivityStartEnabler calls android.app.NotificationManager
 * directly, bypassing the injected NotificationManagerEx hook. On affected
 * HyperOS builds the framework supplies com.xiaomi.xmsf as both pkg and opPkg
 * and rejects that system-uid call. The hidden service path used here keeps
 * pkg=com.xiaomi.xmsf while using opPkg=android, which is the attribution used
 * by the stock XMSF notification path on those builds.
 */
object HookHmsNotificationManager {
    private const val TAG = "HookHmsNotificationManager"
    private const val INITIALIZING_TAG = "MPF.BAFE"
    private const val INITIALIZING_ID = 0

    fun hook() {
        try {
            NotificationManager::class.java.hookAllMethods("notify") {
                doBefore {
                    val notification = args.lastOrNull() as? Notification ?: return@doBefore
                    val id = args.getOrNull(args.lastIndex - 1) as? Int ?: return@doBefore
                    val tag = args.getOrNull(args.lastIndex - 2) as? String

                    // BackgroundActivityStartEnabler uses this short-lived,
                    // muted notification solely to capture a background-start
                    // whitelist token. Restrict the workaround to that exact
                    // record so normal XMSF and third-party attribution remains
                    // untouched when the system hook is available.
                    if (id != INITIALIZING_ID || tag != INITIALIZING_TAG) {
                        return@doBefore
                    }

                    try {
                        SystemNotificationManager.notify(HMS_PACKAGE_NAME, tag, id, notification)
                        // A result set in beforeHookedMethod skips the original
                        // framework implementation, avoiding the rejected
                        // pkg/opPkg pair.
                        result = null
                        XLog.d(TAG, "routed XMSF notify through platform opPkg")
                    } catch (t: Throwable) {
                        // A notification must never take down XMSF. If a vendor
                        // service rejects even the platform-attributed fallback,
                        // swallow this one status notification and leave the
                        // process alive; other push handling can continue.
                        XLog.e(TAG, "XMSF notify fallback failed", t)
                        result = null
                    }
                }
            }
        } catch (t: Throwable) {
            // OEMs may expose a different NotificationManager surface. A
            // failed optional hook must not prevent XMSF from starting.
            XLog.e(TAG, "failed to hook framework notify", t)
        }
    }
}
