package one.yufz.hmspush.hook.platform

import one.yufz.hmspush.common.ANDROID_PACKAGE_NAME
import one.yufz.hmspush.common.HMS_PACKAGE_NAME

/** Selects the operation package used for package-attributed notifications. */
object NotificationBridgePolicy {
    fun operationPackage(targetPackage: String, useXiaomiAttribution: Boolean): String {
        return if (useXiaomiAttribution && targetPackage != HMS_PACKAGE_NAME) {
            HMS_PACKAGE_NAME
        } else {
            ANDROID_PACKAGE_NAME
        }
    }
}
