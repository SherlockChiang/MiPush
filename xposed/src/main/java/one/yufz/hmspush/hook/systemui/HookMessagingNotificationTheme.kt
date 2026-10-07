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
    private var remainingBindingLogs = 16
    private var remainingCompactBindingLogs = 16

    fun hook(classLoader: ClassLoader) {
        // HyperOS can bind a compact heads-up separately from the full row.
        // Normalize after its own binding, including subsequent re-applies.
        val compactWrapper = XposedHelpers.findClassIfExists(
            "com.android.systemui.statusbar.notification.row.wrapper.NotificationCompactMessagingTemplateViewWrapper",
            classLoader,
        )
        if (compactWrapper != null) {
            val hooks = XposedBridge.hookAllMethods(compactWrapper, "onContentUpdated",
                guardedHook { normalize(it.thisObject, it.args.firstOrNull()) })
            XLog.d(TAG, "installed compact binding hooks=${hooks.size}")
            val content = XposedHelpers.findClassIfExists(
                "com.android.systemui.statusbar.notification.row.NotificationContentView",
                classLoader,
            )
            if (content != null) {
                XposedBridge.hookAllMethods(content, "setHeadsUpChild", guardedHook {
                    val wrapper = XposedHelpers.getObjectField(it.thisObject, "mHeadsUpWrapper")
                        ?: return@guardedHook
                    val row = XposedHelpers.getObjectField(it.thisObject, "mContainingNotification")
                        ?: return@guardedHook
                    normalize(wrapper, row)
                })
            }
        }
        // Run after the whole row has bound all three templates and vendor
        // adjustments, rather than depending on an intermediate super call.
        val row = XposedHelpers.findClassIfExists(
            "com.android.systemui.statusbar.notification.row.ExpandableNotificationRow",
            classLoader,
        )
        if (row != null) {
            val hooks = XposedBridge.hookAllMethods(row, "onNotificationUpdated",
                guardedHook { normalizeRow(it.thisObject) })
            if (hooks.isNotEmpty()) {
                XLog.d(TAG, "installed row binding hooks=${hooks.size}")
                return
            }
        }
        val wrapper = XposedHelpers.findClassIfExists(
            "com.android.systemui.statusbar.notification.row.wrapper.NotificationTemplateViewWrapper",
            classLoader,
        ) ?: return
        val hooks = XposedBridge.hookAllMethods(wrapper, "onContentUpdated",
            guardedHook { normalize(it.thisObject, it.args.firstOrNull()) })
        XLog.d(TAG, "installed legacy template binding hooks=${hooks.size}")
    }

    private fun guardedHook(callback: (XC_MethodHook.MethodHookParam) -> Unit): XC_MethodHook =
        object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (param.hasThrowable()) return
                try {
                    callback(param)
                } catch (error: Throwable) {
                    // Missing vendor fields/methods are optional compatibility failures.
                    // They must never abort a SystemUI notification binding.
                    if (!compatibilityFailureReported) {
                        compatibilityFailureReported = true
                        XLog.e(TAG, "conversation theme compatibility unavailable", error)
                    }
                }
            }
        }

    private fun normalizeRow(row: Any) {
        val sbn = readNotification(row) ?: return
        if (!shouldNormalize(sbn)) return
        val layouts = XposedHelpers.getObjectField(row, "mLayouts") as? Array<*> ?: return
        for (content in layouts.take(MAX_LAYOUTS)) {
            if (content == null) continue
            for (field in WRAPPER_FIELDS) {
                val wrapper = XposedHelpers.getObjectField(content, field) ?: continue
                normalizeLayout(wrapper, sbn)
            }
        }
    }

    private fun normalize(wrapper: Any, suppliedRow: Any?) {
        val row = suppliedRow ?: XposedHelpers.getObjectField(wrapper, "mRow") ?: return
        val sbn = readNotification(row) ?: return
        if (shouldNormalize(sbn)) normalizeLayout(wrapper, sbn)
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

    private fun normalizeLayout(wrapper: Any, sbn: StatusBarNotification) {
        val layout = XposedHelpers.getObjectField(wrapper, "mView") as? View ?: return
        // CompactMessagingLayout has separate title/body TextViews, not groups.
        if (BridgedMessagingThemePolicy.isCompactMessagingLayout(
                layout.javaClass.name, layout.tag as? String,
            )) {
            normalizeCompact(wrapper, layout, sbn)
            return
        }
        val groups = runCatching { XposedHelpers.callMethod(layout, "getMessagingGroups") }
            .getOrNull() as? List<*>
        if (groups == null) {
            logBinding(sbn, layout, "unsupported layout; unchanged")
            return
        }
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
        val reference = senderColors.firstOrNull()?.second
        if (reference == null) {
            logBinding(sbn, layout, "groups=${groups.size} no visible text; unchanged")
            return
        }
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
        logBinding(sbn, layout, "groups=${groups.size} foreground=${hex(reference)} changed=$changed")
    }

    private fun normalizeCompact(wrapper: Any, layout: View, sbn: StatusBarNotification) {
        val title = textField(wrapper, "titleView") ?: layout.findViewById<TextView>(android.R.id.title)
        val secondary = textField(wrapper, "headerTextSecondary")
            ?: androidTextView(layout, "header_text_secondary")
        val summary = textField(wrapper, "subText") ?: androidTextView(layout, "header_text")
        // With a displayed group title, the summary is the message body and
        // secondary is its sender. Otherwise secondary itself is the body.
        val summaryIsBody = summary != null && summary.visibility == View.VISIBLE &&
                summary.text.isNotEmpty()
        val body = if (summaryIsBody) summary else secondary
        if (body == null || body.visibility != View.VISIBLE || body.text.isEmpty() ||
            Color.alpha(body.currentTextColor) == 0) {
            logBinding(sbn, layout, "compact no visible body; unchanged")
            return
        }
        val reference = body.currentTextColor
        val before = title?.currentTextColor
        var changed = setTextColorIfDifferent(title, reference)
        if (summaryIsBody) changed = setTextColorIfDifferent(secondary, reference) || changed
        logBinding(sbn, layout,
            "compact title=${before?.let(::hex)} body=${hex(reference)} changed=$changed")
    }

    private fun textField(wrapper: Any, field: String): TextView? =
        runCatching { XposedHelpers.getObjectField(wrapper, field) }.getOrNull() as? TextView

    private fun androidTextView(root: View, name: String): TextView? {
        val id = root.resources.getIdentifier(name, "id", "android")
        return if (id == 0) null else root.findViewById(id)
    }

    private fun setTextColorIfDifferent(view: TextView?, color: Int): Boolean {
        if (view == null || view.currentTextColor == color) return false
        view.setTextColor(color)
        return true
    }

    private fun hex(color: Int): String = "0x${color.toUInt().toString(16)}"

    private fun logBinding(sbn: StatusBarNotification, layout: View, result: String) {
        if (BridgedMessagingThemePolicy.isCompactMessagingLayout(
                layout.javaClass.name, layout.tag as? String,
            )) {
            if (remainingCompactBindingLogs <= 0) return
            remainingCompactBindingLogs--
        } else {
            if (remainingBindingLogs <= 0) return
            remainingBindingLogs--
        }
        XLog.d(TAG, "bound id=${sbn.id} layout=${layout.javaClass.simpleName} $result")
    }

    private fun readNotification(row: Any): StatusBarNotification? {
        // Use the same adapter accessor as the current template wrapper first;
        // older SystemUI versions expose the notification directly on entry.
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
        private const val MAX_LAYOUTS = 3
        private val WRAPPER_FIELDS = arrayOf("mContractedWrapper", "mExpandedWrapper", "mHeadsUpWrapper")
        private val TITLE_FIELDS = arrayOf("mTitle", "mTitleInHeader", "conversationTitleView")
    }
}
