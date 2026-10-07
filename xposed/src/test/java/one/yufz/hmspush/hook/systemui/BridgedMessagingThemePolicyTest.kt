package one.yufz.hmspush.hook.systemui

import one.yufz.hmspush.common.HMS_PACKAGE_NAME
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test fun onlyPlatformConversationLayoutsUseThePalette() {
        assertTrue(BridgedMessagingThemePolicy.isMessagingLayout("com.android.internal.widget.ConversationLayout", null))
        assertTrue(BridgedMessagingThemePolicy.isMessagingLayout("com.android.internal.widget.MessagingLayout", null))
        assertTrue(BridgedMessagingThemePolicy.isMessagingLayout("com.android.internal.widget.CompactMessagingLayout", "compactMessagingHUN"))
        assertFalse(BridgedMessagingThemePolicy.isMessagingLayout("android.widget.FrameLayout", "messaging"))
        assertFalse(BridgedMessagingThemePolicy.isMessagingLayout("com.example.ConversationLayout", null))
    }

    @Test fun conversationHeaderBodyAndMetadataHaveSeparatePaletteRoles() {
        val policy = BridgedMessagingThemePolicy
        assertEquals(BridgedMessagingThemePolicy.TextRole.PRIMARY, policy.textRole("message_name"))
        assertEquals(BridgedMessagingThemePolicy.TextRole.PRIMARY, policy.textRole("title"))
        assertEquals(BridgedMessagingThemePolicy.TextRole.SECONDARY, policy.textRole("header_text_secondary"))
        assertEquals(BridgedMessagingThemePolicy.TextRole.METADATA, policy.textRole("time"))
        assertEquals(BridgedMessagingThemePolicy.TextRole.METADATA, policy.textRole("time_divider"))
        assertNull(policy.textRole("remote_input_text"))
        assertNull(policy.textRole("custom_label"))
        assertNull(policy.textRole(null))
    }
}
