package one.yufz.hmspush.hook.systemui

import android.app.Notification
import android.graphics.Color
import android.service.notification.StatusBarNotification
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import one.yufz.hmspush.hook.XLog
import java.util.ArrayDeque

/**
 * Some HyperOS templates recolor the message body but leave the sender/title on
 * the framework's old foreground. Read the rendered body after binding, rather
 * than selecting black/white from XMSF's independently configured app theme.
 */
class HookMessagingNotificationTheme {
    private var compatibilityFailureReported = false

    fun hook(classLoader: ClassLoader) {
        val wrapper = XposedHelpers.findClassIfExists(
            "com.android.systemui.statusbar.notification.row.wrapper.NotificationTemplateViewWrapper",
            classLoader,
        ) ?: return
        val hooks = XposedBridge.hookAllMethods(wrapper, "onContentUpdated", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (param.hasThrowable()) return
                try {
                    normalize(param.thisObject, param.args.firstOrNull())
                } catch (error: Throwable) {
                    // Missing vendor fields/methods are optional compatibility failures.
                    // They must never abort a SystemUI notification binding.
                    if (!compatibilityFailureReported) {
                        compatibilityFailureReported = true
                        XLog.e(TAG, "conversation theme compatibility unavailable", error)
                    }
                }
            }
        })
        XLog.d(TAG, "installed template binding hooks=${hooks.size}")
    }

    private fun normalize(wrapper: Any, suppliedRow: Any?) {
        val row = suppliedRow ?: XposedHelpers.getObjectField(wrapper, "mRow") ?: return
        val sbn = readNotification(row) ?: return
        val notification = sbn.notification
        val operationPackage = XposedHelpers.callMethod(sbn, "getOpPkg") as? String
        if (!BridgedMessagingThemePolicy.shouldNormalize(
                operationPackage, sbn.packageName,
                notification.extras?.getString(Notification.EXTRA_TEMPLATE),
                notification.contentView != null || notification.bigContentView != null ||
                        notification.headsUpContentView != null,
                notification.color != Notification.COLOR_DEFAULT,
                notification.extras?.getBoolean("android.colorized", false) == true,
            )) return

        val layout = XposedHelpers.getObjectField(wrapper, "mView") as? View ?: return
        // A MessagingStyle compact/public layout need not be a MessagingLayout.
        val groups = runCatching { XposedHelpers.callMethod(layout, "getMessagingGroups") }
            .getOrNull() as? List<*> ?: return
        val senderColors = groups.take(MAX_GROUPS).mapNotNull { group ->
            if (group == null) return@mapNotNull null
            val messages = XposedHelpers.callMethod(group, "getMessageContainer") as? View
                ?: return@mapNotNull null
            val body = findMessageText(messages) ?: return@mapNotNull null
            val sender = XposedHelpers.callMethod(group, "getSenderView") as? TextView
                ?: return@mapNotNull null
            val color = body.currentTextColor
            if (Color.alpha(color) == 0) null else sender to color
        }
        val reference = senderColors.firstOrNull()?.second ?: return
        var changed = false
        for ((sender, color) in senderColors) {
            if (sender.currentTextColor != color) {
                sender.setTextColor(color)
                changed = true
            }
        }
        // ConversationLayout updates its title here; MessagingLayout stores the
        // foreground for subsequent group binds. Do not recolor the messages.
        XposedHelpers.callMethod(layout, "setSenderTextColor", reference)
        for (field in TITLE_FIELDS) {
            val title = runCatching { XposedHelpers.getObjectField(wrapper, field) }
                .getOrNull() as? TextView ?: continue
            if (title.currentTextColor != reference) {
                title.setTextColor(reference)
                changed = true
            }
        }
        if (changed) {
            XLog.d(TAG, "normalized conversation sender/title id=${sbn.id} foreground=0x${reference.toUInt().toString(16)}")
        }
    }

    private fun readNotification(row: Any): StatusBarNotification? {
        // Recent HyperOS wraps NotificationEntry.mSbn in ExpandedNotification;
        // use the same adapter accessor as its own template wrapper first.
        val adapterNotification = runCatching {
            val adapter = XposedHelpers.callMethod(row, "getEntryAdapter")
                ?: return@runCatching null
            XposedHelpers.callMethod(adapter, "getSbn")
        }.getOrNull() as? StatusBarNotification
        if (adapterNotification != null) return adapterNotification

        val entry = XposedHelpers.callMethod(row, "getEntry") ?: return null
        val getterNotification = runCatching { XposedHelpers.callMethod(entry, "getSbn") }
            .getOrNull() as? StatusBarNotification
        return getterNotification ?: XposedHelpers.getObjectField(entry, "mSbn")
            as? StatusBarNotification
    }

    private fun findMessageText(root: View): TextView? {
        val pending = ArrayDeque<View>()
        pending.addLast(root)
        var visited = 0
        while (pending.isNotEmpty() && visited++ < MAX_VIEWS) {
            val view = pending.removeFirst()
            if (view.visibility != View.VISIBLE) continue
            if (view is TextView && view.text.isNotEmpty()) return view
            if (view is ViewGroup) {
                for (index in 0 until minOf(view.childCount, MAX_VIEWS)) {
                    if (pending.size >= MAX_VIEWS) break
                    pending.addLast(view.getChildAt(index))
                }
            }
        }
        return null
    }

    companion object {
        private const val TAG = "MessagingTheme"
        private const val MAX_GROUPS = 25
        private const val MAX_VIEWS = 64
        private val TITLE_FIELDS = arrayOf("mTitle", "mTitleInHeader", "conversationTitleView")
    }
}
