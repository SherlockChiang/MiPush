package one.yufz.hmspush.hook.systemui

import one.yufz.hmspush.common.HMS_PACKAGE_NAME

/** Limits the template workaround to plain conversations posted by our XMSF bridge. */
object BridgedMessagingThemePolicy {
    private const val MESSAGING_TEMPLATE = "android.app.Notification\$MessagingStyle"

    fun isCompactMessagingLayout(className: String?, tag: String?): Boolean =
        className == "com.android.internal.widget.CompactMessagingLayout" &&
                tag == "compactMessagingHUN"

    fun isMessagingLayout(className: String?, tag: String?): Boolean =
        className == "com.android.internal.widget.ConversationLayout" ||
                className == "com.android.internal.widget.MessagingLayout" ||
                isCompactMessagingLayout(className, tag)

    enum class TextRole { PRIMARY, SECONDARY, METADATA, ACTION }

    fun textRole(resourceName: String?): TextRole? = when (resourceName) {
        "title", "alt_title", "conversation_text", "message_name" -> TextRole.PRIMARY
        "text", "header_text", "header_text_secondary" -> TextRole.SECONDARY
        "app_name_text", "app_name_text_divider", "time", "chronometer", "time_divider",
        "header_text_divider", "header_text_secondary_divider", "verification_text",
        "verification_divider", "expand_button_number" -> TextRole.METADATA
        "action0" -> TextRole.ACTION
        else -> null
    }

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
