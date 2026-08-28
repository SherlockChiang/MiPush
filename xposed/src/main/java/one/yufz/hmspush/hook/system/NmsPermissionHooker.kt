package one.yufz.hmspush.hook.system

import android.app.AndroidAppHelper
import android.app.Notification
import android.app.NotificationChannelGroup
import android.content.Context
import android.os.Binder
import android.os.Build
import android.os.Process
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers.findClass
import de.robv.android.xposed.XposedHelpers.findMethodExact
import one.yufz.hmspush.common.HMS_PACKAGE_NAME
import one.yufz.hmspush.hook.XLog
import one.yufz.hmspush.hook.platform.NotificationBridgePolicy
import one.yufz.xposed.HookCallback
import one.yufz.xposed.HookContext
import one.yufz.xposed.hook
import one.yufz.xposed.hookMethod
import java.util.ArrayDeque

/**
 * Lets XMSF use the system notification service for another package.
 *
 * The caller is still XMSF when a hidden NotificationManager method reaches
 * system_server. Clearing the Binder identity gives the system UID permission
 * to operate on the target package; the package/opPackage pair is then selected
 * separately for HyperOS and AOSP/Sony by [NotificationBridgePolicy].
 */
object NmsPermissionHooker {
    private const val TAG = "NmsPermissionHooker"

    @Volatile
    private var bridgeActive = false

    private val hookLock = Any()
    private var hookedStub: Class<*>? = null
    private var useXiaomiAttribution = false

    /** A frame is pushed for every invocation, including non-XMSF calls. */
    private data class IdentityFrame(val token: Long?)

    private val clearedIdentities = ThreadLocal.withInitial { ArrayDeque<IdentityFrame>() }

    fun isBridgeActive(): Boolean = bridgeActive

    private fun fromHms(): Boolean = try {
        Binder.getCallingUid() == getPackageUid(HMS_PACKAGE_NAME)
    } catch (_: Throwable) {
        false
    }

    private fun getPackageUid(packageName: String): Int =
        getContext().packageManager.getPackageUid(packageName, 0)

    private fun getContext(): Context = AndroidAppHelper.currentApplication()

    private fun beginPermission(packageName: String?): Boolean {
        val token = if (!packageName.isNullOrEmpty() && fromHms()) {
            try {
                Binder.clearCallingIdentity()
            } catch (error: Throwable) {
                XLog.e(TAG, "clearCallingIdentity failed", error)
                null
            }
        } else {
            null
        }
        clearedIdentities.get().addLast(IdentityFrame(token))
        return token != null
    }

    private fun restoreCallingIdentity() {
        val frames = clearedIdentities.get()
        if (frames.isEmpty()) return
        val frame = frames.removeLast()
        frame.token?.let {
            try {
                Binder.restoreCallingIdentity(it)
            } catch (error: Throwable) {
                XLog.e(TAG, "restoreCallingIdentity failed", error)
            }
        }
        if (frames.isEmpty()) {
            clearedIdentities.remove()
        }
    }

    private fun hookPermission(
        targetPackageNameParamIndex: Int,
        hookExtra: (XC_MethodHook.MethodHookParam.() -> Unit)? = null,
    ): HookCallback = {
        doBefore {
            val packageName = args.getOrNull(targetPackageNameParamIndex) as? String
            if (beginPermission(packageName)) {
                hookExtra?.invoke(this)
            }
        }
        doAfter {
            restoreCallingIdentity()
        }
    }

    private fun tryInstall(label: String, install: () -> Unit): Boolean {
        return try {
            install()
            XLog.d(TAG, "hooked $label")
            true
        } catch (error: Throwable) {
            // OEMs frequently remove or rename one of these hidden methods.
            // A missing optional method must not disable the remaining bridge.
            XLog.e(TAG, "unable to hook $label", error)
            false
        }
    }

    /**
     * Install the Binder hooks. Returns true when the enqueue hook, which is
     * the minimum needed for package attribution, was installed.
     */
    fun hook(classINotificationManager: Class<*>, xiaomiAttribution: Boolean = false): Boolean {
        synchronized(hookLock) {
            if (hookedStub === classINotificationManager) {
                return bridgeActive
            }
            hookedStub = classINotificationManager
            useXiaomiAttribution = xiaomiAttribution
            bridgeActive = false
        }

        tryInstall("areNotificationsEnabledForPackage") {
            findMethodExact(
                classINotificationManager,
                "areNotificationsEnabledForPackage",
                String::class.java,
                Int::class.javaPrimitiveType!!,
            ).hook(hookPermission(0))
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            tryInstall("getNotificationChannelForPackage(R+)") {
                findMethodExact(
                    classINotificationManager,
                    "getNotificationChannelForPackage",
                    String::class.java,
                    Int::class.javaPrimitiveType!!,
                    String::class.java,
                    String::class.java,
                    Boolean::class.javaPrimitiveType!!,
                ).hook(hookPermission(0))
            }
        } else {
            tryInstall("getNotificationChannelForPackage") {
                findMethodExact(
                    classINotificationManager,
                    "getNotificationChannelForPackage",
                    String::class.java,
                    Int::class.javaPrimitiveType!!,
                    String::class.java,
                    Boolean::class.javaPrimitiveType!!,
                ).hook(hookPermission(0))
            }
        }

        tryInstall("getNotificationChannelsForPackage") {
            findMethodExact(
                classINotificationManager,
                "getNotificationChannelsForPackage",
                String::class.java,
                Int::class.javaPrimitiveType!!,
                Boolean::class.javaPrimitiveType!!,
            ).hook(hookPermission(0))
        }

        val enqueueHooked = tryInstall("enqueueNotificationWithTag") {
            findMethodExact(
                classINotificationManager,
                "enqueueNotificationWithTag",
                String::class.java,
                String::class.java,
                String::class.java,
                Int::class.javaPrimitiveType!!,
                Notification::class.java,
                Int::class.javaPrimitiveType!!,
            ).hook(hookPermission(0) {
                val targetPackage = args.getOrNull(0) as? String ?: return@hookPermission
                // AOSP requires opPkg=android for a system-uid cross-package
                // call. HyperOS uses XMSF to select its focus renderer.
                args[1] = NotificationBridgePolicy.operationPackage(
                    targetPackage,
                    useXiaomiAttribution,
                )
            })
        }
        bridgeActive = enqueueHooked

        tryInstall("createNotificationChannelsForPackage") {
            findMethodExact(
                classINotificationManager,
                "createNotificationChannelsForPackage",
                String::class.java,
                Int::class.javaPrimitiveType!!,
                findClass("android.content.pm.ParceledListSlice", null),
            ).hook(hookPermission(0))
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            tryInstall("cancelNotificationWithTag(R+)") {
                findMethodExact(
                    classINotificationManager,
                    "cancelNotificationWithTag",
                    String::class.java,
                    String::class.java,
                    String::class.java,
                    Int::class.javaPrimitiveType!!,
                    Int::class.javaPrimitiveType!!,
                ).hook(hookPermission(0) {
                    val targetPackage = args.getOrNull(0) as? String ?: return@hookPermission
                    args[1] = NotificationBridgePolicy.operationPackage(
                        targetPackage,
                        useXiaomiAttribution,
                    )
                })
            }
        } else {
            tryInstall("cancelNotificationWithTag") {
                findMethodExact(
                    classINotificationManager,
                    "cancelNotificationWithTag",
                    String::class.java,
                    String::class.java,
                    Int::class.javaPrimitiveType!!,
                    Int::class.javaPrimitiveType!!,
                ).hook(hookPermission(0))
            }
        }

        tryInstall("deleteNotificationChannel") {
            findMethodExact(
                classINotificationManager,
                "deleteNotificationChannel",
                String::class.java,
                String::class.java,
            ).hook(hookPermission(0))
        }

        tryInstall("getAppActiveNotifications") {
            findMethodExact(
                classINotificationManager,
                "getAppActiveNotifications",
                String::class.java,
                Int::class.javaPrimitiveType!!,
            ).hook(hookPermission(0))
        }

        val deleteNotificationChannelHook: HookContext.() -> Unit = {
            doBefore {
                val packageName = args.getOrNull(0) as? String ?: return@doBefore
                if (Binder.getCallingUid() == Process.SYSTEM_UID) {
                    try {
                        args[1] = getPackageUid(packageName)
                    } catch (error: Throwable) {
                        XLog.e(TAG, "unable to resolve channel package uid", error)
                    }
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            tryInstall("PreferencesHelper.deleteNotificationChannel(U)") {
                findClass(
                    "com.android.server.notification.PreferencesHelper",
                    classINotificationManager.classLoader,
                ).hookMethod(
                    "deleteNotificationChannel",
                    String::class.java,
                    Int::class.javaPrimitiveType!!,
                    String::class.java,
                    Int::class.javaPrimitiveType!!,
                    Boolean::class.javaPrimitiveType!!,
                    callback = deleteNotificationChannelHook,
                )
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tryInstall("PreferencesHelper.deleteNotificationChannel") {
                findClass(
                    "com.android.server.notification.PreferencesHelper",
                    classINotificationManager.classLoader,
                ).hookMethod(
                    "deleteNotificationChannel",
                    String::class.java,
                    Int::class.javaPrimitiveType!!,
                    String::class.java,
                    callback = deleteNotificationChannelHook,
                )
            }
        } else {
            tryInstall("RankingHelper.deleteNotificationChannel") {
                findClass(
                    "com.android.server.notification.RankingHelper",
                    classINotificationManager.classLoader,
                ).hookMethod(
                    "deleteNotificationChannel",
                    String::class.java,
                    Int::class.javaPrimitiveType!!,
                    String::class.java,
                    callback = deleteNotificationChannelHook,
                )
            }
        }

        tryInstall("updateNotificationChannelGroupForPackage") {
            findMethodExact(
                classINotificationManager,
                "updateNotificationChannelGroupForPackage",
                String::class.java,
                Int::class.javaPrimitiveType!!,
                NotificationChannelGroup::class.java,
            ).hook(hookPermission(0))
        }

        tryInstall("getNotificationChannelGroupForPackage") {
            findMethodExact(
                classINotificationManager,
                "getNotificationChannelGroupForPackage",
                String::class.java,
                String::class.java,
                Int::class.javaPrimitiveType!!,
            ).hook(hookPermission(1))
        }

        tryInstall("getNotificationChannelGroupsForPackage") {
            findMethodExact(
                classINotificationManager,
                "getNotificationChannelGroupsForPackage",
                String::class.java,
                Int::class.javaPrimitiveType!!,
                Boolean::class.javaPrimitiveType!!,
            ).hook(hookPermission(0))
        }

        tryInstall("deleteNotificationChannelGroup") {
            findMethodExact(
                classINotificationManager,
                "deleteNotificationChannelGroup",
                String::class.java,
                String::class.java,
            ).hook(hookPermission(0))
        }

        return bridgeActive
    }
}
