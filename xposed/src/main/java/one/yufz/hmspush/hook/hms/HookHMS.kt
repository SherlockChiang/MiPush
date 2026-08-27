package one.yufz.hmspush.hook.hms

import de.robv.android.xposed.callbacks.XC_LoadPackage
import one.yufz.hmspush.hook.XLog
import one.yufz.hmspush.hook.platform.XiaomiPlatform
import one.yufz.xposed.*

class HookHMS {
    companion object {
        private const val TAG = "HookHMS"
    }

    fun hook(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (!XiaomiPlatform.isSupported(lpparam.classLoader)) {
            XLog.d(TAG, "skip Xiaomi notification bridge on non-MIUI platform")
            return
        }

        HookHmsNotificationManager.hook()
        if (HookPushNC.canHook(lpparam.classLoader)) {
            HookPushNC.hook(lpparam.classLoader)
        }
    }

}
