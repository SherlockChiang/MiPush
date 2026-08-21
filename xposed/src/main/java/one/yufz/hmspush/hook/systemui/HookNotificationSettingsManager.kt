package one.yufz.hmspush.hook.systemui

import de.robv.android.xposed.XposedHelpers
import one.yufz.hmspush.hook.XLog
import one.yufz.xposed.hook

class HookNotificationSettingsManager : ISystemUIPluginHooker {
    companion object {
        private const val TAG = "FocusNotification"
    }

    override fun hook(pluginLoader: ClassLoader) {
        try {
            XLog.d(TAG, "hook start")
            val classNotificationSettingsManager = XposedHelpers.findClass(
                "miui.systemui.notification.NotificationSettingsManager",
                pluginLoader
            )

            XLog.d(TAG, "hook method")
            val customMethods = classNotificationSettingsManager.declaredMethods
                .filter { it.name == "canCustomFocus" }
            val showMethods = classNotificationSettingsManager.declaredMethods
                .filter { it.name == "canShowFocus" }
            // HyperOS has shipped multiple overloads of these methods. Hook
            // every overload and tolerate a ROM that omits one of them; a
            // single missing method must not abort the other focus hooks.
            customMethods.forEach { method ->
                method.hook {
                    replace { true }
                }
            }
            showMethods.forEach { method ->
                method.hook {
                    replace { true }
                }
            }
            XLog.d(TAG, "hooked canCustomFocus=${customMethods.size}, canShowFocus=${showMethods.size}")
            XLog.d(TAG, "hook end")
        } catch (e: Throwable) {
            XLog.e(
                TAG,
                "hook NotificationSettingsManager failure: " + e.message,
                e
            )
        }
    }
}
