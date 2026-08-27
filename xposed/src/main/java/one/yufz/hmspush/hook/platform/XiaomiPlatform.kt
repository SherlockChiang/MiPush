package one.yufz.hmspush.hook.platform

import android.os.Build
import de.robv.android.xposed.XposedHelpers
import one.yufz.hmspush.hook.XLog
import java.util.Locale

/**
 * Detects whether Xiaomi's private notification attribution path is valid.
 *
 * The module is loaded into several unrelated processes. Build properties and
 * the MIUI framework class are therefore probed in the process where the hook
 * is about to be installed; no state is shared with the target applications,
 * which may intentionally expose fake MIUI APIs for push SDK compatibility.
 */
object XiaomiPlatform {
    private const val TAG = "XiaomiPlatform"
    private const val MIUI_VERSION_PROPERTY = "ro.miui.ui.version.name"
    private const val MIUI_VERSION_CODE_PROPERTY = "ro.miui.ui.version.code"
    private const val HYPEROS_VERSION_PROPERTY = "ro.mi.os.version.name"
    private const val MIUI_BUILD_CLASS = "miui.os.Build"

    /** Pure policy used by unit tests and by the runtime probe below. */
    fun shouldUseSystemNotificationBridge(
        manufacturer: String?,
        brand: String?,
        miuiVersion: String?,
        hyperOsVersion: String?,
        miuiBuildClassPresent: Boolean,
    ): Boolean {
        if (!isXiaomiManufacturer(manufacturer, brand)) {
            return false
        }

        // A Xiaomi product name alone is not enough: Xiaomi devices running
        // AOSP/LineageOS must retain the portable public NotificationManager
        // path. Require an actual MIUI/HyperOS framework signal as well.
        return miuiBuildClassPresent
                || !miuiVersion.isNullOrBlank()
                || !hyperOsVersion.isNullOrBlank()
    }

    fun isXiaomiManufacturer(manufacturer: String?, brand: String? = null): Boolean {
        return sequenceOf(manufacturer, brand)
            .filterNotNull()
            .map { it.trim().lowercase(Locale.ROOT) }
            .any { value ->
                value == "xiaomi"
                        || value == "redmi"
                        || value == "poco"
                        || value == "blackshark"
                        || value.startsWith("xiaomi ")
                        || value.startsWith("redmi ")
                        || value.startsWith("poco ")
                        || value.startsWith("blackshark ")
            }
    }

    /** Probe only immutable vendor signals; do not use FakeDevice properties. */
    fun isSupported(classLoader: ClassLoader? = null): Boolean {
        val manufacturer = Build.MANUFACTURER
        val brand = Build.BRAND
        if (!isXiaomiManufacturer(manufacturer, brand)) {
            XLog.d(TAG, "skip private notification bridge for manufacturer=$manufacturer brand=$brand")
            return false
        }

        val miuiVersion = readSystemProperty(MIUI_VERSION_PROPERTY)
        val miuiVersionCode = readSystemProperty(MIUI_VERSION_CODE_PROPERTY)
        val hyperOsVersion = readSystemProperty(HYPEROS_VERSION_PROPERTY)
        val miuiBuildClassPresent = hasMiuiBuildClass(classLoader)
        val supported = shouldUseSystemNotificationBridge(
            manufacturer,
            brand,
            miuiVersion.ifBlank { miuiVersionCode },
            hyperOsVersion,
            miuiBuildClassPresent,
        )
        XLog.d(
            TAG,
            "private notification bridge supported=$supported " +
                "miuiClass=$miuiBuildClassPresent miuiVersionPresent=${miuiVersion.isNotBlank() || miuiVersionCode.isNotBlank()} " +
                "hyperOsVersionPresent=${hyperOsVersion.isNotBlank()}",
        )
        return supported
    }

    private fun hasMiuiBuildClass(classLoader: ClassLoader?): Boolean {
        return try {
            XposedHelpers.findClass(
                MIUI_BUILD_CLASS,
                classLoader ?: XiaomiPlatform::class.java.classLoader,
            )
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun readSystemProperty(key: String): String {
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getDeclaredMethod("get", String::class.java)
            method.isAccessible = true
            (method.invoke(null, key) as? String).orEmpty()
        } catch (_: Throwable) {
            ""
        }
    }
}
