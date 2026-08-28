package one.yufz.hmspush.hook.system

import android.app.AndroidAppHelper
import android.app.NotificationManager
import android.os.Binder
import android.os.Process
import de.robv.android.xposed.XposedHelpers
import one.yufz.hmspush.common.XMSF_FAKE_CONDITION_PROVIDER_PATH
import one.yufz.hmspush.hook.XLog
import one.yufz.hmspush.hook.platform.XiaomiPlatform
import one.yufz.xposed.callMethod
import one.yufz.xposed.get
import one.yufz.xposed.hookMethod

class HookSystemService {
    companion object {
        private const val TAG = "HookSystemService"

        /**
         * Probe the capability exported by the system-server hook. This is
         * intentionally not a lazy, permanently cached value: LSPosed can
         * initialize the XMSF process before the notification service finishes
         * starting, and a later notification must be able to observe readiness.
         */
        val isSystemHookReady: Boolean
            get() = try {
                val application = AndroidAppHelper.currentApplication()
                val nm = application.getSystemService(NotificationManager::class.java)
                nm.callMethod(
                    "isSystemConditionProviderEnabled",
                    XMSF_FAKE_CONDITION_PROVIDER_PATH,
                ) as? Boolean == true
            } catch (error: Throwable) {
                XLog.e(TAG, "isSystemHookReady error", error)
                false
            }
    }

    fun hook(classLoader: ClassLoader) {
        // The package-attribution bridge is useful on AOSP/Sony as well as
        // HyperOS. XiaomiPlatform only controls the vendor-specific hooks.
        val useXiaomiAttribution = XiaomiPlatform.isSupported(classLoader)

        val notificationManagerService = try {
            XposedHelpers.findClass(
                "com.android.server.notification.NotificationManagerService",
                classLoader,
            )
        } catch (error: Throwable) {
            XLog.e(TAG, "NotificationManagerService is unavailable", error)
            return
        }

        try {
            notificationManagerService.hookMethod("onStart") {
                doAfter {
                    installAfterStart(thisObject, useXiaomiAttribution)
                }
            }
        } catch (error: Throwable) {
            XLog.e(TAG, "failed to hook NotificationManagerService.onStart", error)
            return
        }

        if (!useXiaomiAttribution) {
            XLog.d(TAG, "installed portable notification bridge; skip Xiaomi vendor hooks")
            return
        }

        // Xiaomi's suspended-package check can otherwise reject a bridged
        // system-UID post. Keep this workaround confined to the vendor path.
        try {
            notificationManagerService.hookMethod(
                "isPackageSuspendedForUser",
                String::class.java,
                Int::class.javaPrimitiveType!!,
            ) {
                doBefore {
                    if (NmsPermissionHooker.isBridgeActive() &&
                        Binder.getCallingUid() == Process.SYSTEM_UID
                    ) {
                        result = false
                    }
                }
            }
        } catch (error: Throwable) {
            XLog.e(TAG, "skip optional Xiaomi suspension hook", error)
        }

        try {
            val shortcutService = XposedHelpers.findClass(
                "com.android.server.pm.ShortcutService",
                classLoader,
            )
            ShortcutPermissionHooker.hook(shortcutService)
        } catch (error: Throwable) {
            XLog.e(TAG, "skip optional Xiaomi shortcut hook", error)
        }
    }

    private fun installAfterStart(service: Any, useXiaomiAttribution: Boolean) {
        try {
            XLog.d(TAG, "onStart invoked; xiaomiAttribution=$useXiaomiAttribution")
            val stub = service.get<Any>("mService")
            val bridgeReady = NmsPermissionHooker.hook(
                stub.javaClass,
                useXiaomiAttribution,
            )
            hookSystemReadyFlag(stub.javaClass, bridgeReady)
            XLog.d(TAG, "notification bridge ready=$bridgeReady")
        } catch (error: Throwable) {
            XLog.e(TAG, "failed to install notification Binder bridge", error)
        }
    }

    private fun hookSystemReadyFlag(stubClass: Class<*>, bridgeReady: Boolean) {
        try {
            stubClass.hookMethod(
                "isSystemConditionProviderEnabled",
                String::class.java,
            ) {
                doBefore {
                    if (args.getOrNull(0) == XMSF_FAKE_CONDITION_PROVIDER_PATH) {
                        result = bridgeReady
                    }
                }
            }
        } catch (error: Throwable) {
            XLog.e(TAG, "failed to expose notification bridge capability", error)
        }
    }
}
