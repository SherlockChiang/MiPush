package one.yufz.hmspush.hook.hms

import de.robv.android.xposed.callbacks.XC_LoadPackage
import one.yufz.xposed.*

class HookHMS {
    fun hook(lpparam: XC_LoadPackage.LoadPackageParam) {
        // The package-attribution bridge is portable. Only the SystemUI focus
        // renderer is Xiaomi-specific; HookPushNC must also be installed on
        // AOSP/Sony so the target package can own the StatusBarNotification.
        HookHmsNotificationManager.hook()
        if (HookPushNC.canHook(lpparam.classLoader)) {
            HookPushNC.hook(lpparam.classLoader)
        }
    }

}
