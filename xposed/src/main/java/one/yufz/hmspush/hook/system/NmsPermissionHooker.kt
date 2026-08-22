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
import one.yufz.hmspush.common.ANDROID_PACKAGE_NAME
import one.yufz.hmspush.common.HMS_PACKAGE_NAME
import one.yufz.hmspush.hook.XLog
import one.yufz.xposed.HookCallback
import one.yufz.xposed.HookContext
import one.yufz.xposed.hook
import one.yufz.xposed.hookMethod
import java.util.ArrayDeque

object NmsPermissionHooker {
    private const val TAG = "NmsPermissionHooker"

    private fun fromHms() = try {
        Binder.getCallingUid() == getPackageUid(HMS_PACKAGE_NAME)
    } catch (e: Throwable) {
        false
    }

    private fun getPackageUid(packageName: String) = getContext().packageManager.getPackageUid(packageName, 0)

    private fun getContext(): Context = AndroidAppHelper.currentApplication()

    /**
     * Binder identity is needed to let XMSF operate on a third-party target,
     * but it must not be cleared for XMSF's own notifications.  HyperOS
     * resolves the package UID after the hook and rejects a self notification
     * when the caller has become system (uid 1000):
     * "Caller com.xiaomi.xmsf:1000 cannot post for pkg com.xiaomi.xmsf".
     *
     * Keep the token per thread so it can be restored after the original
     * system-server method returns, including when that method throws.
     */
    private val clearedIdentities = ThreadLocal.withInitial { ArrayDeque<Long>() }

    private fun tryHookPermission(packageName: String): Boolean {
        if (!fromHms()) {
            return false
        }

        clearedIdentities.get().addLast(Binder.clearCallingIdentity())
        return true
    }

    private fun restoreCallingIdentity() {
        val identities = clearedIdentities.get()
        if (identities.isNotEmpty()) {
            Binder.restoreCallingIdentity(identities.removeLast())
        }
    }

    private fun hookPermission(targetPackageNameParamIndex: Int, hookExtra: (XC_MethodHook.MethodHookParam.() -> Unit)? = null): HookCallback = {
        doBefore {
            if (tryHookPermission(args[targetPackageNameParamIndex] as String)) {
                hookExtra?.invoke(this)
            }
        }
        doAfter {
            restoreCallingIdentity()
        }
    }

    fun hook(classINotificationManager: Class<*>) {
        //boolean areNotificationsEnabledForPackage(String pkg, int uid);
        findMethodExact(classINotificationManager, "areNotificationsEnabledForPackage", String::class.java, Int::class.java)
            .hook(hookPermission(0))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            //NotificationChannel getNotificationChannelForPackage(String pkg, int uid, String channelId, String conversationId, boolean includeDeleted);
            findMethodExact(classINotificationManager, "getNotificationChannelForPackage", String::class.java, Int::class.java, String::class.java, String::class.java, Boolean::class.java)
                .hook(hookPermission(0))
        } else {
            //NotificationChannel getNotificationChannelForPackage(String pkg, int uid, String channelId, boolean includeDeleted);
            findMethodExact(classINotificationManager, "getNotificationChannelForPackage", String::class.java, Int::class.java, String::class.java, Boolean::class.java)
                .hook(hookPermission(0))
        }

        //ParceledListSlice getNotificationChannelsForPackage(String pkg, int uid, boolean includeDeleted);
        findMethodExact(classINotificationManager, "getNotificationChannelsForPackage", String::class.java, Int::class.java, Boolean::class.java)
            .hook(hookPermission(0))

        //void enqueueNotificationWithTag(String pkg, String opPkg, String tag, int id, Notification notification, int userId)
        findMethodExact(classINotificationManager, "enqueueNotificationWithTag", String::class.java, String::class.java, String::class.java, Int::class.java, Notification::class.java, Int::class.java)
            // XMSF's own framework notification must use the platform
            // operation package while the identity is cleared to system.
            // Third-party calls retain opPkg=com.xiaomi.xmsf so HyperOS can
            // resolve the target focus renderer.
            .hook(hookPermission(0) {
                if (args[0] == HMS_PACKAGE_NAME) {
                    args[1] = ANDROID_PACKAGE_NAME
                }
            })

        //void createNotificationChannelsForPackage(String pkg, int uid, in ParceledListSlice channelsList);
        findMethodExact(classINotificationManager, "createNotificationChannelsForPackage", String::class.java, Int::class.java, findClass("android.content.pm.ParceledListSlice", null))
            .hook(hookPermission(0))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            //void cancelNotificationWithTag(String pkg, String opPkg, String tag, int id, int userId);
            findMethodExact(classINotificationManager, "cancelNotificationWithTag", String::class.java, String::class.java, String::class.java, Int::class.java, Int::class.java)
                .hook(hookPermission(0) {
                    if (args[0] == HMS_PACKAGE_NAME) {
                        args[1] = ANDROID_PACKAGE_NAME
                    }
                })
        } else {
            //void cancelNotificationWithTag(String pkg, String opPkg, String tag, int id, int userId);
            findMethodExact(classINotificationManager, "cancelNotificationWithTag", String::class.java, String::class.java, Int::class.java, Int::class.java)
                .hook(hookPermission(0))
        }

        //void deleteNotificationChannel(String pkg, String channelId);
        findMethodExact(classINotificationManager, "deleteNotificationChannel", String::class.java, String::class.java)
            .hook(hookPermission(0))

        //ParceledListSlice getAppActiveNotifications(String callingPkg, int userId);
        findMethodExact(classINotificationManager, "getAppActiveNotifications", String::class.java, Int::class.java)
            .hook(hookPermission(0))

        //ParceledListSlice getNotificationChannelsForPackage(String pkg, int uid, boolean includeDeleted);
        findMethodExact(classINotificationManager, "getNotificationChannelsForPackage", String::class.java, Int::class.java, Boolean::class.java)
            .hook(hookPermission(0))

        val deleteNotificationChannelHook: HookContext.() -> Unit = {
            doBefore {
                val packageName = args[0] as String
                if (Binder.getCallingUid() == Process.SYSTEM_UID) {
                    args[1] = getPackageUid(packageName)
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                findClass("com.android.server.notification.PreferencesHelper", classINotificationManager.classLoader)
                    //public boolean deleteNotificationChannel(String pkg, int uid, String channelId, int callingUid, boolean fromSystemOrSystemUi)
                    .hookMethod(
                        "deleteNotificationChannel", String::class.java, Int::class.java, String::class.java, Int::class.java, Boolean::class.java,
                        callback = deleteNotificationChannelHook
                    )
            } catch (e: NoSuchMethodError) {
                //Samsung One UI 7 delete this method
                XLog.d(TAG, "hook deleteNotificationChannel error, NoSuchMethodError")
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            findClass("com.android.server.notification.PreferencesHelper", classINotificationManager.classLoader)
                //public boolean deleteNotificationChannel(String pkg, int uid, String channelId)
                .hookMethod("deleteNotificationChannel", String::class.java, Int::class.java, String::class.java,
                    callback = deleteNotificationChannelHook
                )
        } else {
            findClass("com.android.server.notification.RankingHelper", classINotificationManager.classLoader)
                //public void deleteNotificationChannel(String pkg, int uid, String channelId)
                .hookMethod("deleteNotificationChannel", String::class.java, Int::class.java, String::class.java,
                    callback = deleteNotificationChannelHook
                )
        }

        //void updateNotificationChannelGroupForPackage(String pkg, int uid, in NotificationChannelGroup group);
        findMethodExact(classINotificationManager, "updateNotificationChannelGroupForPackage", String::class.java, Int::class.java, NotificationChannelGroup::class.java)
            .hook(hookPermission(0))

        //NotificationChannelGroup getNotificationChannelGroupForPackage(String groupId, String pkg, int uid);
        findMethodExact(classINotificationManager, "getNotificationChannelGroupForPackage", String::class.java, String::class.java, Int::class.java)
            .hook(hookPermission(1))

        //ParceledListSlice getNotificationChannelGroupsForPackage(String pkg, int uid, boolean includeDeleted);
        findMethodExact(classINotificationManager, "getNotificationChannelGroupsForPackage", String::class.java, Int::class.java, Boolean::class.java)
            .hook(hookPermission(0))

        //void deleteNotificationChannelGroup(String pkg, String channelGroupId);
        findMethodExact(classINotificationManager, "deleteNotificationChannelGroup", String::class.java, String::class.java)
            .hook(hookPermission(0))
    }
}
