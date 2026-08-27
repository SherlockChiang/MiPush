package one.yufz.hmspush.hook.platform

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XiaomiPlatformTest {
    @Test
    fun sonyWithNoMiuiSignalsUsesPortablePath() {
        assertFalse(
            XiaomiPlatform.shouldUseSystemNotificationBridge(
                manufacturer = "Sony",
                brand = "Sony",
                miuiVersion = "",
                hyperOsVersion = "",
                miuiBuildClassPresent = false,
            ),
        )
    }

    @Test
    fun aospOnXiaomiHardwareStillUsesPortablePath() {
        assertFalse(
            XiaomiPlatform.shouldUseSystemNotificationBridge(
                manufacturer = "Xiaomi",
                brand = "Xiaomi",
                miuiVersion = "",
                hyperOsVersion = "",
                miuiBuildClassPresent = false,
            ),
        )
    }

    @Test
    fun miuiAndHyperOsSignalsEnableSystemBridge() {
        assertTrue(
            XiaomiPlatform.shouldUseSystemNotificationBridge(
                manufacturer = "Xiaomi",
                brand = "Xiaomi",
                miuiVersion = "V14",
                hyperOsVersion = "",
                miuiBuildClassPresent = false,
            ),
        )
        assertTrue(
            XiaomiPlatform.shouldUseSystemNotificationBridge(
                manufacturer = "POCO",
                brand = "POCO",
                miuiVersion = "",
                hyperOsVersion = "OS2.0",
                miuiBuildClassPresent = false,
            ),
        )
    }

    @Test
    fun compatibilityClassAloneIsNotEnoughOnNonXiaomiHardware() {
        assertFalse(
            XiaomiPlatform.shouldUseSystemNotificationBridge(
                manufacturer = "Google",
                brand = "Pixel",
                miuiVersion = "V14",
                hyperOsVersion = "OS2.0",
                miuiBuildClassPresent = true,
            ),
        )
    }
}
