package one.yufz.hmspush.hook.systemui

import one.yufz.hmspush.common.HMS_PACKAGE_NAME

/** Limits the template workaround to plain conversations posted by our XMSF bridge. */
object BridgedMessagingThemePolicy {
    private const val MESSAGING_TEMPLATE = "android.app.Notification\$MessagingStyle"

    fun isCompactMessagingLayout(className: String?, tag: String?): Boolean =
        className == "com.android.internal.widget.CompactMessagingLayout" &&
                tag == "compactMessagingHUN"

    fun shouldNormalize(
        operationPackage: String?,
        targetPackage: String?,
        template: String?,
        hasCustomView: Boolean,
        hasExplicitColor: Boolean,
        colorized: Boolean,
    ): Boolean = operationPackage == HMS_PACKAGE_NAME &&
            !targetPackage.isNullOrBlank() && targetPackage != HMS_PACKAGE_NAME &&
            template == MESSAGING_TEMPLATE && !hasCustomView &&
            !hasExplicitColor && !colorized
}
