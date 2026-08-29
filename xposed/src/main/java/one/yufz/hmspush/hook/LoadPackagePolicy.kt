package one.yufz.hmspush.hook

import one.yufz.hmspush.common.ANDROID_PACKAGE_NAME
import one.yufz.hmspush.common.HMS_PACKAGE_NAME

/**
 * Keeps app-only hooks out of the system_server host process.
 *
 * LSPosed can report a system provider's package name while loading its
 * classloader inside system_server. In that callback processName is often
 * null, so the host process must be used as the primary signal.
 */
internal object LoadPackagePolicy {
    private const val SYSTEM_SERVER_PROCESS = "system_server"
    private const val SYSTEM_UID = 1000

    internal enum class Route {
        SYSTEM_HOOK,
        SYSTEM_SKIP,
        APP,
    }

    fun route(
        packageName: String?,
        loadProcessName: String?,
        hostProcessName: String?,
        hostUid: Int,
    ): Route {
        val knownSystemHost = isKnownSystemHost(hostProcessName, loadProcessName, hostUid)

        // A positively identified system host always wins over package names:
        // an APK classloader can be observed while framework providers are
        // being installed, and must never run app hooks in system_server.
        if (knownSystemHost) {
            return if (packageName == ANDROID_PACKAGE_NAME) {
                Route.SYSTEM_HOOK
            } else {
                Route.SYSTEM_SKIP
            }
        }

        // These packages have their own app-process hooks and can share the
        // system UID on vendor builds. Keep them out of the conservative
        // unknown-host fallback below.
        if (packageName == "com.android.systemui" || packageName == HMS_PACKAGE_NAME) {
            return Route.APP
        }

        // The canonical android package is the only entry point that may
        // install the system bridge. With a real system UID and no process
        // probes it remains safe to treat it as the early system entry.
        if (packageName == ANDROID_PACKAGE_NAME) {
            return if (hostUid == SYSTEM_UID &&
                hostProcessName.isNullOrBlank() &&
                loadProcessName.isNullOrBlank()
            ) {
                Route.SYSTEM_HOOK
            } else {
                // It must never fall through to FakeDevice in an app host.
                Route.SYSTEM_SKIP
            }
        }

        // If both process probes are unavailable, a system UID callback is
        // still safer to classify as framework than to run FakeDevice.
        if (hostUid == SYSTEM_UID &&
            hostProcessName.isNullOrBlank() &&
            loadProcessName.isNullOrBlank()
        ) {
            return Route.SYSTEM_SKIP
        }

        return Route.APP
    }

    private fun isKnownSystemHost(
        hostProcessName: String?,
        loadProcessName: String?,
        hostUid: Int,
    ): Boolean {
        // Process names are not an authority boundary: an app can choose a
        // custom process name. Require the actual system-server UID as well.
        if (hostUid != SYSTEM_UID) {
            return false
        }

        val host = hostProcessName?.takeIf { it.isNotBlank() }
        if (host != null) return host == SYSTEM_SERVER_PROCESS || host == ANDROID_PACKAGE_NAME

        // Fallback for older LSPosed/MIUI builds where the host helper is not
        // initialized yet but LoadPackageParam still carries the process.
        val loadProcess = loadProcessName?.takeIf { it.isNotBlank() }
        if (loadProcess != null) return loadProcess == SYSTEM_SERVER_PROCESS || loadProcess == ANDROID_PACKAGE_NAME

        return false
    }
}
