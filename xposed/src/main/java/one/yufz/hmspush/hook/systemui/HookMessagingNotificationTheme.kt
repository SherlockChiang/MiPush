package one.yufz.hmspush.hook.systemui

import android.app.Notification
import android.content.Context
import android.service.notification.StatusBarNotification
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import one.yufz.hmspush.hook.XLog
import java.util.ArrayDeque

/** Use HyperOS's light foreground palette for bridged conversations on glass heads-up cards. */
class HookMessagingNotificationTheme {
    private var compatibilityFailureReported = false
    private var remainingBindingLogs = 12
    private val frameDiagnostics = MessagingThemeDiagnostics()

    fun hook(classLoader: ClassLoader) {
        // This is a HyperOS material workaround, not a global Android theme override.
        if (XposedHelpers.findClassIfExists(MATERIAL_CLASS, classLoader) == null) return
        val row = XposedHelpers.findClassIfExists(
            "com.android.systemui.statusbar.notification.row.ExpandableNotificationRow", classLoader,
        ) ?: return
        val content = XposedHelpers.findClassIfExists(
            "com.android.systemui.statusbar.notification.row.NotificationContentView", classLoader,
        ) ?: return
        XposedBridge.hookAllMethods(content, "setHeadsUpChild", guardedHook {
            val parent = XposedHelpers.getObjectField(it.thisObject, "mContainingNotification")
                ?: return@guardedHook
            normalizeHeadsUp(parent)
        })
        // The row callback covers RemoteViews re-applies as well as fresh inflation.
        val bindings = XposedBridge.hookAllMethods(row, "onNotificationUpdated", guardedHook {
            normalizeHeadsUp(it.thisObject)
            observeHeadsUpFrame(it.thisObject)
        })
        XposedBridge.hookAllMethods(row, "setHeadsUp", guardedHook {
            if (it.args.firstOrNull() == true) normalizeHeadsUp(it.thisObject)
            observeHeadsUpFrame(it.thisObject)
        })
        XLog.d(TAG, "installed glass heads-up palette hooks=${bindings.size}")
    }

    private fun observeHeadsUpFrame(row: Any) {
        val view = row as? View ?: return
        val sbn = readNotification(row) ?: return
        if (!shouldNormalize(sbn)) return
        frameDiagnostics.observe(view, {
            val current = readNotification(row)
            current != null && shouldNormalize(current) &&
                    XposedHelpers.getBooleanField(row, "mIsHeadsUp")
        }, { headsUpView(row) })
    }

    private fun guardedHook(callback: (XC_MethodHook.MethodHookParam) -> Unit): XC_MethodHook =
        object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (param.hasThrowable()) return
                try {
                    callback(param)
                } catch (error: Throwable) {
                    // Vendor internals are optional; a mismatch must not abort SystemUI binding.
                    if (!compatibilityFailureReported) {
                        compatibilityFailureReported = true
                        XLog.e(TAG, "glass conversation palette unavailable", error)
                    }
                }
            }
        }

    private fun headsUpWrapper(row: Any): Any? {
        val content = XposedHelpers.callMethod(row, "getPrivateLayout") ?: return null
        return XposedHelpers.getObjectField(content, "mHeadsUpWrapper")
    }

    private fun headsUpView(row: Any): View? = headsUpWrapper(row)?.let {
        XposedHelpers.getObjectField(it, "mView") as? View
    }

    private fun normalizeHeadsUp(row: Any) {
        val sbn = readNotification(row) ?: return
        if (!shouldNormalize(sbn)) return
        val wrapper = headsUpWrapper(row) ?: return
        val layout = XposedHelpers.getObjectField(wrapper, "mView") as? View ?: return
        if (!BridgedMessagingThemePolicy.isMessagingLayout(layout.javaClass.name, layout.tag as? String)) return
        val palette = glassPalette(row) ?: return

        if (!BridgedMessagingThemePolicy.isCompactMessagingLayout(layout.javaClass.name, layout.tag as? String)) {
            val groups = XposedHelpers.callMethod(layout, "getMessagingGroups") as? List<*> ?: return
            // Layout setters retain the palette for the next bind; existing groups must
            // also be updated so their current message views and remote-input history agree.
            XposedHelpers.callMethod(layout, "setSenderTextColor", palette.primary)
            XposedHelpers.callMethod(layout, "setMessageTextColor", palette.secondary)
            for (group in groups.take(MAX_GROUPS)) {
                if (group != null) XposedHelpers.callMethod(group, "setTextColors", palette.primary, palette.secondary)
            }
        }
        // Header, compact body and time use separate TextViews outside the message groups.
        // Restrict writes to standard template IDs; never recolor custom controls or inputs.
        val pending = ArrayDeque<View>()
        pending.add(layout)
        var visited = 0
        while (pending.isNotEmpty() && visited++ < MAX_VIEWS) {
            val view = pending.removeFirst()
            val name = runCatching { view.resources.getResourceEntryName(view.id) }.getOrNull()
            if (name == "remote_input" || name == "smart_reply_container") continue
            if (view is TextView) {
                val color = when (BridgedMessagingThemePolicy.textRole(name)) {
                    BridgedMessagingThemePolicy.TextRole.PRIMARY -> palette.primary
                    BridgedMessagingThemePolicy.TextRole.SECONDARY -> palette.secondary
                    BridgedMessagingThemePolicy.TextRole.METADATA -> palette.metadata
                    BridgedMessagingThemePolicy.TextRole.ACTION -> palette.action
                    null -> null
                }
                if (color != null && view.currentTextColor != color) view.setTextColor(color)
            }
            if (view is ViewGroup) {
                for (index in 0 until minOf(view.childCount, MAX_VIEWS)) {
                    if (pending.size + visited >= MAX_VIEWS) break
                    pending.add(view.getChildAt(index))
                }
            }
        }
        if (remainingBindingLogs > 0) {
            remainingBindingLogs--
            XLog.d(TAG, "glass heads-up id=${sbn.id} layout=${layout.javaClass.simpleName}" +
                    " primary=${hex(palette.primary)} body=${hex(palette.secondary)}" +
                    " metadata=${hex(palette.metadata)}")
        }
    }

    private fun glassPalette(row: Any): Palette? {
        val view = row as? View ?: return null
        val injector = XposedHelpers.callMethod(row, "getInjector") ?: return null
        val material = XposedHelpers.getObjectField(injector, "notificationMaterialStateInteractor") ?: return null
        val glass = XposedHelpers.callMethod(material, "isGlassMaterialType") as? Boolean ?: return null
        if (!glass) return null
        // Match the light foreground used for glass shade cards, even when the
        // device's global UI mode is light. Do not sample an already-wrong body color.
        val context = XposedHelpers.callStaticMethod(material.javaClass, "wrapDarkContext", view.context, true)
            as? Context ?: return null
        fun color(name: String): Int? {
            val id = context.resources.getIdentifier(name, "color", "com.android.systemui")
            return if (id == 0) null else context.getColor(id)
        }
        // Resolve the complete palette before any view is changed. No hardcoded fallback.
        return Palette(
            color("notification_primary_text_color_light") ?: return null,
            color("notification_secondary_text_color_light") ?: return null,
            color("notification_time_color") ?: return null,
            color("notification_action_text_color") ?: return null,
        )
    }

    private fun shouldNormalize(sbn: StatusBarNotification): Boolean {
        val notification = sbn.notification
        val operationPackage = XposedHelpers.callMethod(sbn, "getOpPkg") as? String
        return BridgedMessagingThemePolicy.shouldNormalize(
            operationPackage, sbn.packageName,
            notification.extras?.getString(Notification.EXTRA_TEMPLATE),
            notification.contentView != null || notification.bigContentView != null ||
                    notification.headsUpContentView != null,
            notification.color != Notification.COLOR_DEFAULT,
            notification.extras?.getBoolean("android.colorized", false) == true,
        )
    }

    private fun readNotification(row: Any): StatusBarNotification? {
        val adapterNotification = runCatching {
            val adapter = XposedHelpers.callMethod(row, "getEntryAdapter") ?: return@runCatching null
            XposedHelpers.callMethod(adapter, "getSbn")
        }.getOrNull() as? StatusBarNotification
        if (adapterNotification != null) return adapterNotification
        val entry = XposedHelpers.callMethod(row, "getEntry") ?: return null
        val getter = runCatching { XposedHelpers.callMethod(entry, "getSbn") }.getOrNull() as? StatusBarNotification
        return getter ?: XposedHelpers.getObjectField(entry, "mSbn") as? StatusBarNotification
    }

    private fun hex(color: Int): String = "0x${color.toUInt().toString(16)}"
    private data class Palette(val primary: Int, val secondary: Int, val metadata: Int, val action: Int)

    companion object {
        private const val TAG = "MessagingTheme"
        private const val MATERIAL_CLASS = "com.android.systemui.statusbar.notification.style.domain.NotificationMaterialStateInteractor"
        private const val MAX_GROUPS = 25
        private const val MAX_VIEWS = 160
    }
}
