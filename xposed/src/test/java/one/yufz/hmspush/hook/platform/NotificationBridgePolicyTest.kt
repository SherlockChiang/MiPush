package one.yufz.hmspush.hook.platform

import one.yufz.hmspush.common.ANDROID_PACKAGE_NAME
import one.yufz.hmspush.common.HMS_PACKAGE_NAME
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationBridgePolicyTest {
    private val targetPackage = "com.example.client"

    @Test
    fun aospAttributesThirdPartyNotificationsThroughAndroid() {
        assertEquals(
            ANDROID_PACKAGE_NAME,
            NotificationBridgePolicy.operationPackage(targetPackage, useXiaomiAttribution = false),
        )
    }

    @Test
    fun xiaomiRetainsXmsfAttributionForThirdPartyNotifications() {
        assertEquals(
            HMS_PACKAGE_NAME,
            NotificationBridgePolicy.operationPackage(targetPackage, useXiaomiAttribution = true),
        )
    }

    @Test
    fun xmsfSelfNotificationsAlwaysUseAndroidAttribution() {
        assertEquals(
            ANDROID_PACKAGE_NAME,
            NotificationBridgePolicy.operationPackage(HMS_PACKAGE_NAME, useXiaomiAttribution = false),
        )
        assertEquals(
            ANDROID_PACKAGE_NAME,
            NotificationBridgePolicy.operationPackage(HMS_PACKAGE_NAME, useXiaomiAttribution = true),
        )
    }
}
