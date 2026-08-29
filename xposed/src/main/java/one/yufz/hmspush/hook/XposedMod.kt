package one.yufz.hmspush.hook

import android.app.AndroidAppHelper
import android.os.Process
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import one.yufz.hmspush.common.HMS_CORE_PROCESS
import one.yufz.hmspush.common.HMS_PACKAGE_NAME
import one.yufz.hmspush.common.doOnce
import one.yufz.hmspush.hook.fakedevice.FakeDevice
import one.yufz.hmspush.hook.hms.HookHMS
import one.yufz.hmspush.hook.platform.XiaomiPlatform
import one.yufz.hmspush.hook.system.HookSystemService
import one.yufz.hmspush.hook.systemui.HookNotificationSettingsManager
import one.yufz.hmspush.hook.systemui.HookSystemUIPlugin
import one.yufz.xposed.hook


class XposedMod : IXposedHookLoadPackage {
    companion object {
        private const val TAG = "XposedMod"
        private const val SYSTEM_UI_PROCESS = "com.android.systemui"
    }

    @Throws(Throwable::class)
    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        doOnce(lpparam.classLoader) {
            try {
                hook(lpparam)
            } catch (error: Throwable) {
                // A module exception must not escape into system_server (or
                // an app process) and turn an optional hook into a host crash.
                XLog.e(
                    TAG,
                    "hook failed; skip package=${lpparam.packageName} process=${lpparam.processName}",
                    error,
                )
            }
        }
    }

    private fun hook(lpparam: LoadPackageParam) {
        val hostProcessName = currentProcessName()
        val appUid = lpparam.appInfo?.uid
        val hostUid = Process.myUid()
        XLog.d(
            TAG,
            "Loaded app: ${lpparam.packageName} process:${lpparam.processName} " +
                "host:$hostProcessName hostUid:$hostUid appUid:$appUid",
        )

        when (
            LoadPackagePolicy.route(
                packageName = lpparam.packageName,
                loadProcessName = lpparam.processName,
                hostProcessName = hostProcessName,
                hostUid = hostUid,
            )
        ) {
            LoadPackagePolicy.Route.SYSTEM_HOOK -> {
                HookSystemService().hook(lpparam.classLoader)
                return
            }

            LoadPackagePolicy.Route.SYSTEM_SKIP -> {
                XLog.d(
                    TAG,
                    "skip app hooks in system host: package=${lpparam.packageName} " +
                        "process=${lpparam.processName} host=$hostProcessName " +
                        "hostUid=$hostUid appUid=$appUid",
                )
                return
            }

            LoadPackagePolicy.Route.APP -> Unit
        }

        if (lpparam.packageName == SYSTEM_UI_PROCESS ||
            lpparam.processName == SYSTEM_UI_PROCESS
        ) {
            if (XiaomiPlatform.isSupported(lpparam.classLoader)) {
                removeHyperOSFocusNotificationPackageLimit(lpparam)
            } else {
                XLog.d(TAG, "skip Xiaomi SystemUI hooks on non-MIUI platform")
            }
            return
        }

        if (lpparam.packageName == HMS_PACKAGE_NAME) {
            if (lpparam.processName == HMS_CORE_PROCESS) {
                HookHMS().hook(lpparam)
            }
            return
        }

//        if (lpparam.packageName == "com.android.systemui") {
//            HookSystemUI().hook(lpparam.classLoader)
//            return
//        }

        FakeDevice.fake(lpparam)
    }

    private fun currentProcessName(): String? {
        return try {
            AndroidAppHelper.currentProcessName()
        } catch (_: Throwable) {
            null
        }
    }

    private fun removeHyperOSFocusNotificationPackageLimit(lpparam: LoadPackageParam) {
        HookSystemUIPlugin(
            "miui.systemui.plugin",
            HookNotificationSettingsManager()
        ).hook(lpparam.classLoader)

        HookSystemUIPlugin("miui.systemui.plugin") { pluginLoader ->
            val tag = "HookFocusNotifUtils"
            try {
                val classFocusNotifUtils = XposedHelpers.findClass(
                    "miui.systemui.notification.focus.FocusNotifUtils",
                    pluginLoader
                )

                val methods = classFocusNotifUtils.declaredMethods
                    .filter { it.name == "canShowFocus" }
                methods.forEach { method ->
                    method.hook {
                        replace { true }
                    }
                }
                XLog.d(tag, "hooked canShowFocus overloads=${methods.size}")
            } catch (e: Throwable) {
                XLog.e(
                    tag,
                    "hook failure: " + e.message,
                    e
                )
            }
        }.hook(lpparam.classLoader)
    }
}
