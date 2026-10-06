package one.yufz.hmspush.hook.systemui

import one.yufz.hmspush.common.HMS_PACKAGE_NAME
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgedMessagingThemePolicyTest {
    private fun matches(
        operation: String? = HMS_PACKAGE_NAME,
        target: String? = "com.example.client",
        template: String? = "android.app.Notification\$MessagingStyle",
        custom: Boolean = false,
        explicitColor: Boolean = false,
        colorized: Boolean = false,
    ) = BridgedMessagingThemePolicy.shouldNormalize(
        operation, target, template, custom, explicitColor, colorized,
    )

    @Test fun forwardedConversationsArePackageIndependent() {
        assertTrue(matches(target = "com.example.client"))
        assertTrue(matches(target = "org.example.another"))
    }

    @Test fun applicationOwnedAndSystemNotificationsAreUntouched() {
        assertFalse(matches(operation = "com.example.client"))
        assertFalse(matches(operation = "android"))
        assertFalse(matches(target = HMS_PACKAGE_NAME))
        assertFalse(matches(target = null))
        assertFalse(matches(target = ""))
    }

    @Test fun otherStylesAndMissingStyleAreUntouched() {
        assertFalse(matches(template = "android.app.Notification\$BigTextStyle"))
        assertFalse(matches(template = null))
    }

    @Test fun customAndExplicitPresentationArePreserved() {
        assertFalse(matches(custom = true))
        assertFalse(matches(explicitColor = true))
        assertFalse(matches(colorized = true))
    }

    @Test fun compactConversationTemplateIsRecognizedWithoutMessagingGroups() {
        assertTrue(BridgedMessagingThemePolicy.isCompactMessagingLayout(
            "com.android.internal.widget.CompactMessagingLayout", "compactMessagingHUN",
        ))
    }

    @Test fun otherCompactAndCustomLayoutsAreNotRecolored() {
        assertFalse(BridgedMessagingThemePolicy.isCompactMessagingLayout(
            "android.widget.FrameLayout", "compactMessagingHUN",
        ))
        assertFalse(BridgedMessagingThemePolicy.isCompactMessagingLayout(
            "com.android.internal.widget.CompactMessagingLayout", "compactHUN",
        ))
        assertFalse(BridgedMessagingThemePolicy.isCompactMessagingLayout(null, null))
    }
}
